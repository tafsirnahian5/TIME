package com.example.timeapp.ui

import kotlinx.coroutines.flow.flowOn
import com.example.timeapp.data.L

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timeapp.data.AppSettingsDataStore
import com.example.timeapp.data.NotifyChannel
import com.example.timeapp.ui.theme.Colorway
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppSettingsUiState(
    val colorway: Colorway = Colorway.Violet,
    val themeMode: String = "light", // "system" | "light" | "dark" — see AppSettingsDataStore
    val notifyRemindersEnabled: Boolean = true,
    val useExactAlarms: Boolean = false,
    val userName: String = "",
    val userAvatarUri: String? = null,
    val userTitle: String = "",
    val workStart: String = "09:00",
    val workEnd: String = "18:00",
    val timezone: String = L("Asia/Dhaka"),
    val currency: String = "BDT",
    val restDays: Set<String> = setOf("Fri", "Sat"),
    val notifyWorkChannel: Boolean = true,
    val notifyScheduleChannel: Boolean = true,
    val notifyMoneyChannel: Boolean = false,
    val notifyMaintenanceChannel: Boolean = true,
    val notifyRedactContent: Boolean = true,
    val monthlyBudgetMinor: Long = 2_000_000L,
    /** Money › Overview counts overdue plans in "planned remaining" (the PDF's default), or not. */
    val includeOverdueCarryover: Boolean = true,
    /** Nudge to take a break after this many minutes of unbroken work; 0 = off. */
    val focusBreakReminderMinutes: Int = 0,
    /** First day of the week, a `DayOfWeek` name — see `WeekConfig`. */
    val weekStartsOn: String = "MONDAY",
    /** What the Plan calendar opens on: MONTH, WEEK or DAY. */
    val defaultCalendarView: String = "MONTH",
    /** [com.example.timeapp.data.BoardSort] name. */
    val boardSort: String = "MANUAL",
    /** "en" or "bn" — read once at start (Lang); changing it restarts the app. */
    val appLanguage: String = "en",
    val autoBackupEnabled: Boolean = false,
    val autoBackupFrequency: String = "DAILY",
    /** The granted folder (a SAF tree URI) — device-specific, never backed up. */
    val autoBackupTree: String? = null,
    /** The automatic backups' password, sealed by SecretBox; null = unprotected. */
    val autoBackupPasswordSealed: String? = null,
    val lastAutoBackupAt: Long = 0L,
    /** Why the last automatic backup failed, until one succeeds or the folder is chosen again. */
    val lastAutoBackupError: String? = null,
    /** Remind after this many days without a backup; 0 = never. */
    val backupReminderDays: Int = 14,
    val lastBackupReminderAt: Long = 0L,
    val installedAt: Long = 0L,
    /** The Money widget shows the balance only when this is on. */
    val moneyWidgetAmounts: Boolean = false,
    val clockFormat: String = "PHONE",
    val dateOrder: String = "DAY_FIRST",
    val currencyLabel: String = "Tk",
    val dynamicColor: Boolean = false,
    val pureBlack: Boolean = false,
    val hapticLevel: com.example.timeapp.data.HapticLevel = com.example.timeapp.data.HapticLevel.MEDIUM,
    val reduceCelebrations: Boolean = false,
    /** Today's getting-started checklist was closed (Phase 19B). */
    val gettingStartedDismissed: Boolean = false,
    /** False only for the placeholder before DataStore's first read — so a tip never flashes. */
    val loaded: Boolean = false,
    /** First-visit tab tips already shown (Phase 19C), by [com.example.timeapp.ui.components.TabTip] name. */
    val tipsSeen: Set<String> = emptySet(),
    /** [com.example.timeapp.data.SavedFilters]-encoded. */
    val savedFilters: String = "",
    val quietHoursEnabled: Boolean = true,
    /** Utility timers and Focus countdowns ring like an alarm instead of a normal notification. */
    val timerAlarmStyle: Boolean = false,
    /** Budget alerts at 80%, projected overspend and over (Money channel). */
    val budgetAlertsEnabled: Boolean = true,
    /** Whether the user has set the monthly budget (it has a default they never chose). */
    val monthlyBudgetSet: Boolean = false,
    /** The wallet money forms preselect; 0 = none (the first wallet). */
    val defaultWalletId: Long = 0,
    /** Budget alerts already posted this month (keys from `budgetAlerts`). */
    val budgetAlertsSent: Set<String> = emptySet(),
    /** "HH:mm" */
    val quietStart: String = "22:00",
    val quietEnd: String = "07:00",
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val pinLength: Int = 6,
    val lockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    /** With the lock on, Recents shows a blank card instead of the last screen (the PDF). */
    val hideInRecents: Boolean = true,
    val autoLockSeconds: Int = 30,
    val failedAttempts: Int = 0,
    val lockoutUntilEpochMs: Long = 0L,
    val lastBackupEpochMs: Long = 0L,
)

/** App-wide settings, backed by [AppSettingsDataStore] so they survive restart. */
class AppViewModel(val settingsStore: AppSettingsDataStore) : ViewModel() {
    val uiState: StateFlow<AppSettingsUiState> = settingsStore.settings
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, AppSettingsUiState())

    // Null means "DataStore hasn't emitted yet" — distinct from a real false, so TimeNavHost
    // can wait for the real value instead of briefly starting on Setup on every launch.
    private val _onboardingComplete = kotlinx.coroutines.flow.MutableStateFlow<Boolean?>(null)
    val onboardingComplete: StateFlow<Boolean?> = _onboardingComplete
    init {
        viewModelScope.launch {
            settingsStore.onboardingComplete.collect { _onboardingComplete.value = it }
        }
    }

    fun setColorway(colorway: Colorway) = viewModelScope.launch { settingsStore.setColorway(colorway) }
    fun setThemeMode(mode: String) = viewModelScope.launch { settingsStore.setThemeMode(mode) }
    fun setRemindersEnabled(enabled: Boolean) = viewModelScope.launch { settingsStore.setRemindersEnabled(enabled) }
    fun setUseExactAlarms(enabled: Boolean) = viewModelScope.launch { settingsStore.setUseExactAlarms(enabled) }
    fun setOnboardingComplete(complete: Boolean) = viewModelScope.launch { settingsStore.setOnboardingComplete(complete) }

    fun setIdentity(name: String, title: String, avatarUri: String? = null) =
        viewModelScope.launch { settingsStore.setIdentity(name, title, avatarUri) }

    fun setWorkSetup(workStart: String, workEnd: String, timezone: String, currency: String, restDays: Set<String>) =
        viewModelScope.launch { settingsStore.setWorkSetup(workStart, workEnd, timezone, currency, restDays) }

    fun setNotifyChannel(channel: NotifyChannel, enabled: Boolean) =
        viewModelScope.launch { settingsStore.setNotifyChannel(channel, enabled) }

    fun setRedactContent(enabled: Boolean) = viewModelScope.launch { settingsStore.setRedactContent(enabled) }


    fun setMonthlyBudgetMinor(minor: Long) = viewModelScope.launch { settingsStore.setMonthlyBudgetMinor(minor) }
    fun setIncludeOverdueCarryover(include: Boolean) = viewModelScope.launch { settingsStore.setIncludeOverdueCarryover(include) }
    fun setFocusBreakReminderMinutes(minutes: Int) = viewModelScope.launch { settingsStore.setFocusBreakReminderMinutes(minutes) }
    fun setWeekStartsOn(day: java.time.DayOfWeek) = viewModelScope.launch { settingsStore.setWeekStartsOn(day.name) }
    fun setDefaultCalendarView(view: String) = viewModelScope.launch { settingsStore.setDefaultCalendarView(view) }
    fun setBoardSort(sort: com.example.timeapp.data.BoardSort) = viewModelScope.launch { settingsStore.setBoardSort(sort.name) }
    /** Stores the language, then [then] (the restart) once it is safely written. */
    fun setAppLanguage(tag: String, then: () -> Unit) = viewModelScope.launch { settingsStore.setAppLanguage(tag); then() }
    fun setMoneyWidgetAmounts(value: Boolean) = viewModelScope.launch { settingsStore.setMoneyWidgetAmounts(value) }
    fun setClockFormat(value: String) = viewModelScope.launch { settingsStore.setClockFormat(value) }
    fun setDateOrder(value: String) = viewModelScope.launch { settingsStore.setDateOrder(value) }
    fun setCurrencyLabel(value: String) = viewModelScope.launch { settingsStore.setCurrencyLabel(value) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { settingsStore.setDynamicColor(value) }
    fun setPureBlack(value: Boolean) = viewModelScope.launch { settingsStore.setPureBlack(value) }
    fun setHapticLevel(value: com.example.timeapp.data.HapticLevel) = viewModelScope.launch { settingsStore.setHapticLevel(value) }
    fun setReduceCelebrations(value: Boolean) = viewModelScope.launch { settingsStore.setReduceCelebrations(value) }
    fun dismissGettingStarted() = viewModelScope.launch { settingsStore.setGettingStartedDismissed(true) }
    fun markTipSeen(tip: String) = viewModelScope.launch { settingsStore.markTipSeen(tip) }
    fun resetHelp() = viewModelScope.launch { settingsStore.resetHelp() }
    fun setSavedFilters(filters: List<com.example.timeapp.data.SavedFilter>) =
        viewModelScope.launch { settingsStore.setSavedFilters(com.example.timeapp.data.SavedFilters.encode(filters)) }
    fun setWorkHours(start: String, end: String) = viewModelScope.launch { settingsStore.setWorkHours(start, end) }
    fun setDefaultWalletId(id: Long) = viewModelScope.launch { settingsStore.setDefaultWalletId(id) }
    fun setBudgetAlertsEnabled(enabled: Boolean) = viewModelScope.launch { settingsStore.setBudgetAlertsEnabled(enabled) }
    fun setTimerAlarmStyle(enabled: Boolean) = viewModelScope.launch { settingsStore.setTimerAlarmStyle(enabled) }
    fun setQuietHours(enabled: Boolean, start: String, end: String) = viewModelScope.launch { settingsStore.setQuietHours(enabled, start, end) }
    fun setRestDays(days: Set<String>) = viewModelScope.launch { settingsStore.setRestDays(days) }

    class Factory(private val settingsStore: AppSettingsDataStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(settingsStore) as T
    }
}
