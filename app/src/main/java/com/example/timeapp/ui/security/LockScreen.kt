package com.example.timeapp.ui.security

import androidx.compose.ui.unit.dp
import com.example.timeapp.data.L

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.TimeThemeTokens
import kotlinx.coroutines.delay


/**
 * The real gate — [com.example.timeapp.ui.TimeApp] composes this instead of the app
 * whenever [SecurityViewModel.locked] is true. Never shows anything from underneath it.
 */
@Composable
fun LockScreen(onUnlocked: () -> Unit, viewModel: SecurityViewModel) {
    val colors = TimeThemeTokens.colors
    val ui by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var showResetDialog by remember { mutableStateOf(false) }

    val lockedOut = nowMs < ui.lockoutUntilEpochMs
    val remainingSeconds = ((ui.lockoutUntilEpochMs - nowMs) / 1000).coerceAtLeast(0)

    LaunchedEffect(lockedOut) {
        while (true) {
            delay(1000)
            nowMs = System.currentTimeMillis()
        }
    }

    fun tryUnlock() {
        viewModel.attemptUnlock(pin) { ok ->
            if (ok) {
                onUnlocked()
            } else {
                error = L("Wrong PIN.")
                pin = ""
            }
        }
    }

    fun onDigit(d: Char) {
        if (lockedOut || pin.length >= ui.pinLength) return
        error = null
        pin += d
        if (pin.length == ui.pinLength) tryUnlock()
    }

    fun onBackspace() {
        if (pin.isNotEmpty()) pin = pin.dropLast(1)
    }

    val biometricAvailable = ui.biometricEnabled &&
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    // The recovery path re-authenticates with the PHONE's own screen lock (fingerprint/face/
    // PIN/pattern) — never a typed phrase the lock screen itself would have to display, which
    // would just be a printed bypass code. If the phone has no secure lock at all, there is no
    // stronger credential to fall back to, so the recovery option is hidden rather than faked.
    val deviceCredentialAllowed = BiometricManager.Authenticators.DEVICE_CREDENTIAL or BiometricManager.Authenticators.BIOMETRIC_WEAK
    val deviceCredentialAvailable = BiometricManager.from(context).canAuthenticate(deviceCredentialAllowed) == BiometricManager.BIOMETRIC_SUCCESS

    fun showBiometricPrompt() {
        val activity = context as? FragmentActivity ?: return
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.unlockViaBiometric()
                    onUnlocked()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(L("Unlock T!ME"))
                .setNegativeButtonText(L("Use PIN"))
                .build(),
        )
    }

    fun showDeviceCredentialPrompt() {
        val activity = context as? FragmentActivity ?: return
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.resetPinKeepData(onDone = onUnlocked)
                }
            },
        )
        // DEVICE_CREDENTIAL cannot be combined with a custom negative-button text — the system
        // supplies its own Cancel affordance for this authenticator combination.
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(L("Confirm it's you"))
                .setSubtitle(L("Verify this phone's screen lock to reset T!ME's PIN"))
                .setAllowedAuthenticators(deviceCredentialAllowed)
                .build(),
        )
    }

    // Offer biometric automatically once, on first composition, so returning users don't
    // have to tap an extra button — PIN entry still works if they dismiss it.
    LaunchedEffect(Unit) { if (biometricAvailable) showBiometricPrompt() }

    // Surface, not a bare Box: every other screen sits in a Scaffold, which paints the theme
    // background *and* provides the matching content colour. Composed on its own, this screen
    // fell through to the Activity window background (light) — and a plain Box leaves
    // LocalContentColor at its black default, so simply painting a dark background behind it
    // turns the keypad black-on-black. Surface sets both halves together.
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(Dimens.screenH),
    ) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.weight(1f))
            com.example.timeapp.ui.components.TimeMark(size = 64.dp)
            Text(L("Unlock your workspace"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = Dimens.s16, bottom = Dimens.s24))
            PinDots(length = ui.pinLength, filled = pin.length, modifier = Modifier.padding(bottom = Dimens.s16))
            if (lockedOut) {
                Text(
                    L("Too many attempts. Try again in %1\$s:%2\$s.", (remainingSeconds / 60), ((remainingSeconds % 60).toString().padStart(2, '0'))),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Box(modifier = Modifier.weight(1f))
            NumericKeypad(
                onDigit = ::onDigit,
                onBackspace = ::onBackspace,
                enabled = !lockedOut,
                onBiometric = if (biometricAvailable) ::showBiometricPrompt else null,
                modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s20),
            )
            if (deviceCredentialAvailable) {
                TextButton(onClick = { showResetDialog = true }) {
                    Text(L("Forgot PIN? Reset and keep data"), style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text(
                    L("Forgot PIN? Resetting needs this phone's own screen lock, which isn't set up."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Dimens.s24),
                )
            }
        }
    }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(L("Reset PIN?")) },
            text = {
                Text(
                    L("This clears your PIN and app-lock settings but keeps all your tasks, money records, and ideas — anyone with access to this phone will be able to open T!ME until you set a new PIN. You'll be asked to verify this phone's own screen lock first."),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { showResetDialog = false; showDeviceCredentialPrompt() }) { Text(L("Continue")) }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text(L("Cancel")) } },
        )
    }
}
