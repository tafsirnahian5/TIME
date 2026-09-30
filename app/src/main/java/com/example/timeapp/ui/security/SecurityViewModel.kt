package com.example.timeapp.ui.security

import kotlinx.coroutines.flow.flowOn
import com.example.timeapp.data.PinHash
import com.example.timeapp.data.PinPepper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timeapp.data.AppSettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SecurityUiState(
    val lockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoLockSeconds: Int = 30,
    val hideInRecents: Boolean = true,
    val failedAttempts: Int = 0,
    val lockoutUntilEpochMs: Long = 0L,
    val hasPin: Boolean = false,
    /** How many digits the stored PIN has, so entry can finish by itself at that length. */
    val pinLength: Int = PIN_MIN_LENGTH,
)

/** The PIN's allowed length — the PDF's 6–12 digits. The one definition: every PIN screen and
 * onboarding's step 3 read these. */
internal const val PIN_MIN_LENGTH = 6
internal const val PIN_MAX_LENGTH = 12

private const val LOCKOUT_THRESHOLD = 5
private const val LOCKOUT_BASE_SECONDS = 30L
private const val LOCKOUT_MAX_SECONDS = 30L * 60L

/**
 * Owns the real PIN/biometric lock: hashing, verification, lockout backoff, and the
 * in-memory `locked` flag [TimeApp] gates the whole app behind. A PIN is never stored in
 * plaintext — only a PBKDF2 verifier bound to a Keystore key ([PinHash], [PinPepper]).
 */
class SecurityViewModel(private val store: AppSettingsDataStore) : ViewModel() {

    val uiState: StateFlow<SecurityUiState> = store.settings.map {
        SecurityUiState(
            lockEnabled = it.lockEnabled,
            biometricEnabled = it.biometricEnabled,
            autoLockSeconds = it.autoLockSeconds,
            hideInRecents = it.hideInRecents,
            failedAttempts = it.failedAttempts,
            lockoutUntilEpochMs = it.lockoutUntilEpochMs,
            hasPin = it.pinHash != null,
            pinLength = it.pinLength,
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, SecurityUiState())

    // Null means "we haven't read the real lockEnabled value from DataStore yet" — mirrors
    // AppViewModel.onboardingComplete's pattern so TimeApp can wait for it instead of
    // briefly flashing unlocked content on cold start.
    private val _locked = MutableStateFlow<Boolean?>(null)
    val locked: StateFlow<Boolean?> = _locked

    // Only meaningful within the current process lifetime — see onBackground's kdoc.
    private var backgroundedAtElapsedRealtime: Long? = null

    init {
        viewModelScope.launch {
            _locked.value = store.settings.first().lockEnabled
        }
    }

    /** Called from an ON_STOP lifecycle event, anchored to SystemClock.elapsedRealtime()
     * (immune to wall-clock changes) — matches FocusViewModel's existing anchoring pattern. */
    fun onBackground(nowElapsedRealtime: Long) {
        if (uiState.value.lockEnabled) backgroundedAtElapsedRealtime = nowElapsedRealtime
    }

    /** Called from an ON_START lifecycle event. If a process death happened while
     * backgrounded, [backgroundedAtElapsedRealtime] is lost — but [locked] then already
     * initializes to `lockEnabled` on the fresh process, which is the safe default anyway. */
    fun onForeground(nowElapsedRealtime: Long) {
        val backgroundedAt = backgroundedAtElapsedRealtime ?: return
        backgroundedAtElapsedRealtime = null
        val state = uiState.value
        if (!state.lockEnabled) return
        val seconds = state.autoLockSeconds
        if (seconds < 0) return // "never while foregrounded"
        if (nowElapsedRealtime - backgroundedAt >= seconds * 1000L) _locked.value = true
    }

    /** Settings' "Lock now" action, and Tier 9's Restore flow forcing re-auth before a
     * destructive replace. A no-op when no lock is enabled. */
    fun lockNow() {
        if (uiState.value.lockEnabled) _locked.value = true
    }

    /** Called after a real BiometricPrompt success (the fingerprint/face shortcut) —
     * clears the lock the same way a correct PIN does. Unlike PIN entry, biometric
     * success has no ViewModel method of its own to fall through to, so LockScreen
     * must call this directly rather than relying on a caller-supplied callback. */
    fun unlockViaBiometric() {
        _locked.value = false
    }

    fun setupPin(pin: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val salt = PinHash.newSalt()
            store.setPin(hashNew(pin, salt), salt, pin.length)
            _locked.value = false
            onDone()
        }
    }

    fun setBiometricEnabled(enabled: Boolean) = viewModelScope.launch { store.setBiometricEnabled(enabled) }
    fun setHideInRecents(value: Boolean) = viewModelScope.launch { store.setHideInRecents(value) }

    /** The PDF: lock "immediately after device lock". The screen turning off is that moment;
     * an auto-lock of Never (-1) opts out of this too. */
    fun onScreenOff() {
        val state = uiState.value
        if (state.lockEnabled && state.autoLockSeconds >= 0) _locked.value = true
    }
    fun setAutoLockSeconds(seconds: Int) = viewModelScope.launch { store.setAutoLockSeconds(seconds) }

    /** Turning the lock off needs the current PIN (fresh authentication, the PDF) — being
     * inside an unlocked app isn't proof it's the owner holding the phone. */
    fun disableLock(pin: String, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        if (!verifyPinSuspend(pin)) {
            onResult(false)
            return@launch
        }
        store.clearPin()
        _locked.value = false
        onResult(true)
    }

    /** The scoped-down "forgot PIN" recovery: clears the lock but keeps every record. */
    fun resetPinKeepData(onDone: () -> Unit) {
        viewModelScope.launch {
            store.clearPin()
            _locked.value = false
            onDone()
        }
    }

    /** Attempts to unlock with [pin]; drives the real lockout backoff on failure. */
    fun attemptUnlock(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(tryUnlock(pin)) }
    }

    private suspend fun tryUnlock(pin: String): Boolean {
        val now = System.currentTimeMillis()
        // Read the stored counts, not the UI's copy: two quick wrong tries must both count.
        val state = store.settings.first()
        if (now < state.lockoutUntilEpochMs) return false
        val ok = verifyPinSuspend(pin)
        if (ok) {
            store.resetFailedAttempts()
            store.setLockoutUntil(0L)
            _locked.value = false
        } else {
            val newCount = state.failedAttempts + 1
            store.recordFailedAttempt(newCount)
            if (newCount >= LOCKOUT_THRESHOLD) {
                val overThreshold = (newCount - LOCKOUT_THRESHOLD).coerceAtMost(6)
                val durationSeconds = (LOCKOUT_BASE_SECONDS shl overThreshold).coerceAtMost(LOCKOUT_MAX_SECONDS)
                store.setLockoutUntil(now + durationSeconds * 1000)
            }
        }
        return ok
    }

    private suspend fun verifyPinSuspend(pin: String): Boolean {
        val settings = store.settings.first()
        val hash = settings.pinHash ?: return false
        val salt = settings.pinSalt ?: return false
        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { PinHash.verify(pin, salt, hash, PinPepper::mac) }
        // A PIN stored a weaker way (Tier 8's single SHA-256, or before the Keystore secret)
        // is re-stored in the strongest form once it's proven right.
        if (ok && PinHash.needsUpgrade(hash, keystoreAvailable = PinPepper.mac(ByteArray(1)) != null)) {
            val newSalt = PinHash.newSalt()
            store.setPin(hashNew(pin, newSalt), newSalt, pin.length)
        }
        return ok
    }

    private suspend fun hashNew(pin: String, salt: String): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { PinHash.hash(pin, salt, mac = PinPepper::mac) }

    class Factory(private val store: AppSettingsDataStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SecurityViewModel(store) as T
    }
}
