package com.example.timeapp.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.timeapp.ui.AppSettingsUiState
import com.example.timeapp.ui.theme.Colorway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

/** Backs [AppSettingsUiState] — the accent colorway, theme mode and onboarding flag survive restart. */
class AppSettingsDataStore(private val context: Context) {
    private object Keys {
        val COLORWAY = stringPreferencesKey("colorway")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NOTIFY_REMINDERS = booleanPreferencesKey("notify_reminders_enabled")
        val USE_EXACT_ALARMS = booleanPreferencesKey("use_exact_alarms")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_AVATAR_URI = stringPreferencesKey("user_avatar_uri")
        val USER_TITLE = stringPreferencesKey("user_title")
        val WORK_START = stringPreferencesKey("work_start")
        val WORK_END = stringPreferencesKey("work_end")
        val TIMEZONE = stringPreferencesKey("timezone")
        val CURRENCY = stringPreferencesKey("currency")
        val REST_DAYS = stringPreferencesKey("rest_days") // comma-joined day labels, e.g. "Fri,Sat"
        val NOTIFY_WORK_CHANNEL = booleanPreferencesKey("notify_work_channel")
        val NOTIFY_SCHEDULE_CHANNEL = booleanPreferencesKey("notify_schedule_channel")
        val NOTIFY_MONEY_CHANNEL = booleanPreferencesKey("notify_money_channel")
        val NOTIFY_MAINTENANCE_CHANNEL = booleanPreferencesKey("notify_maintenance_channel")
        val NOTIFY_REDACT_CONTENT = booleanPreferencesKey("notify_redact_content")
        val MONTHLY_BUDGET_MINOR = longPreferencesKey("monthly_budget_minor")
        val INCLUDE_OVERDUE_CARRYOVER = booleanPreferencesKey("include_overdue_carryover")
        val FOCUS_BREAK_REMINDER_MINUTES = intPreferencesKey("focus_break_reminder_minutes")
        val WEEK_STARTS_ON = stringPreferencesKey("week_starts_on")
        val DEFAULT_CALENDAR_VIEW = stringPreferencesKey("default_calendar_view")
        val BOARD_SORT = stringPreferencesKey("board_sort")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        // Automatic backups (Phase 17). Device-specific (a folder grant, a Keystore-sealed
        // password) — deliberately NOT in the backup allowlist.
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val AUTO_BACKUP_FREQUENCY = stringPreferencesKey("auto_backup_frequency")
        val AUTO_BACKUP_TREE = stringPreferencesKey("auto_backup_tree")
        val AUTO_BACKUP_PASSWORD = stringPreferencesKey("auto_backup_password_sealed")
        val LAST_AUTO_BACKUP_AT = longPreferencesKey("last_auto_backup_at")
        val LAST_AUTO_BACKUP_ERROR = stringPreferencesKey("last_auto_backup_error")
        val BACKUP_REMINDER_DAYS = intPreferencesKey("backup_reminder_days")
        val LAST_BACKUP_REMINDER_AT = longPreferencesKey("last_backup_reminder_at")
        val INSTALLED_AT = longPreferencesKey("installed_at")
        val MONEY_WIDGET_AMOUNTS = booleanPreferencesKey("money_widget_amounts")
        val CLOCK_FORMAT = stringPreferencesKey("clock_format")
        val DATE_ORDER = stringPreferencesKey("date_order")
        val CURRENCY_LABEL = stringPreferencesKey("currency_label")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
        val HAPTIC_LEVEL = stringPreferencesKey("haptic_level")
        val REDUCE_CELEBRATIONS = booleanPreferencesKey("reduce_celebrations")
        // Phase 19 — device-specific help state, deliberately not in backups.
        val GETTING_STARTED_DISMISSED = booleanPreferencesKey("getting_started_dismissed")
        val TIPS_SEEN = androidx.datastore.preferences.core.stringSetPreferencesKey("tips_seen")
        val SAVED_FILTERS = stringPreferencesKey("saved_filters")
        val QUIET_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val TIMER_ALARM_STYLE = booleanPreferencesKey("timer_alarm_style")
        val BUDGET_ALERTS = booleanPreferencesKey("budget_alerts")
        val DEFAULT_WALLET = longPreferencesKey("default_wallet_id")
        val BUDGET_ALERTS_SENT = androidx.datastore.preferences.core.stringSetPreferencesKey("budget_alerts_sent")
        val QUIET_START = stringPreferencesKey("quiet_start")
        val QUIET_END = stringPreferencesKey("quiet_end")

        // Tier 8 — Security & app lock. PIN_HASH is a salted SHA-256 digest, never plaintext.
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_LENGTH = intPreferencesKey("pin_length")
        val LOCK_ENABLED = booleanPreferencesKey("lock_enabled")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        /** Security, like the lock keys: device-only, never in a backup. */
        val HIDE_IN_RECENTS = booleanPreferencesKey("hide_in_recents")
        val AUTO_LOCK_MINUTES = intPreferencesKey("auto_lock_minutes")
        /** Seconds since Phase 16 (the PDF's 30-second default); minutes are read once for older installs. */
        val AUTO_LOCK_SECONDS = intPreferencesKey("auto_lock_seconds")
        val FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
        val LOCKOUT_UNTIL_EPOCH_MS = longPreferencesKey("lockout_until_epoch_ms")

        // Tier 9 — Backup & Restore.
        val LAST_BACKUP_EPOCH_MS = longPreferencesKey("last_backup_epoch_ms")
    }

    val settings: Flow<AppSettingsUiState> = context.settingsDataStore.data.map { prefs ->
        AppSettingsUiState(
            loaded = true,
            colorway = prefs[Keys.COLORWAY]?.let { runCatching { Colorway.valueOf(it) }.getOrNull() } ?: Colorway.Violet,
            // Defaults to "light", not "system" — the dark palette in ui/theme/Theme.kt is
            // unreviewed (see design_handoff_time_android/README.md "Open questions"); flip
            // this back to "system" once a real dark-theme pass has happened.
            themeMode = prefs[Keys.THEME_MODE] ?: "system",
            notifyRemindersEnabled = prefs[Keys.NOTIFY_REMINDERS] ?: true,
            useExactAlarms = prefs[Keys.USE_EXACT_ALARMS] ?: false,
            userName = prefs[Keys.USER_NAME] ?: "",  // never a stranger's name: a skipped name stays blank
            userAvatarUri = prefs[Keys.USER_AVATAR_URI],
            userTitle = prefs[Keys.USER_TITLE] ?: "",
            workStart = prefs[Keys.WORK_START] ?: "09:00",
            workEnd = prefs[Keys.WORK_END] ?: "18:00",
            timezone = prefs[Keys.TIMEZONE] ?: "Asia/Dhaka",
            currency = prefs[Keys.CURRENCY] ?: "BDT",
            restDays = prefs[Keys.REST_DAYS]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: setOf("Fri", "Sat"),
            notifyWorkChannel = prefs[Keys.NOTIFY_WORK_CHANNEL] ?: true,
            notifyScheduleChannel = prefs[Keys.NOTIFY_SCHEDULE_CHANNEL] ?: true,
            // Defaults to on, like every other channel. It used to default to off, which
            // meant switching "Remind me" on for a bill armed a real alarm that fired and was
            // then silently discarded — and marked delivered, so it never came back. A bill
            // reminder is the single most load-bearing notification this app sends.
            notifyMoneyChannel = prefs[Keys.NOTIFY_MONEY_CHANNEL] ?: true,
            notifyMaintenanceChannel = prefs[Keys.NOTIFY_MAINTENANCE_CHANNEL] ?: true,
            notifyRedactContent = prefs[Keys.NOTIFY_REDACT_CONTENT] ?: true,
            monthlyBudgetMinor = prefs[Keys.MONTHLY_BUDGET_MINOR] ?: 2_000_000L,
            includeOverdueCarryover = prefs[Keys.INCLUDE_OVERDUE_CARRYOVER] ?: true,
            focusBreakReminderMinutes = prefs[Keys.FOCUS_BREAK_REMINDER_MINUTES] ?: 0,
            weekStartsOn = prefs[Keys.WEEK_STARTS_ON] ?: "MONDAY",
            defaultCalendarView = prefs[Keys.DEFAULT_CALENDAR_VIEW] ?: "MONTH",
            boardSort = prefs[Keys.BOARD_SORT] ?: "MANUAL",
            appLanguage = prefs[Keys.APP_LANGUAGE] ?: "en",
            autoBackupEnabled = prefs[Keys.AUTO_BACKUP_ENABLED] ?: false,
            autoBackupFrequency = prefs[Keys.AUTO_BACKUP_FREQUENCY] ?: "DAILY",
            autoBackupTree = prefs[Keys.AUTO_BACKUP_TREE],
            autoBackupPasswordSealed = prefs[Keys.AUTO_BACKUP_PASSWORD],
            lastAutoBackupAt = prefs[Keys.LAST_AUTO_BACKUP_AT] ?: 0L,
            lastAutoBackupError = prefs[Keys.LAST_AUTO_BACKUP_ERROR],
            backupReminderDays = prefs[Keys.BACKUP_REMINDER_DAYS] ?: 14,
            lastBackupReminderAt = prefs[Keys.LAST_BACKUP_REMINDER_AT] ?: 0L,
            installedAt = prefs[Keys.INSTALLED_AT] ?: 0L,
            moneyWidgetAmounts = prefs[Keys.MONEY_WIDGET_AMOUNTS] ?: false,
            clockFormat = prefs[Keys.CLOCK_FORMAT] ?: "PHONE",
            dateOrder = prefs[Keys.DATE_ORDER] ?: "DAY_FIRST",
            currencyLabel = prefs[Keys.CURRENCY_LABEL] ?: "Tk",
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            pureBlack = prefs[Keys.PURE_BLACK] ?: false,
            hapticLevel = prefs[Keys.HAPTIC_LEVEL]?.let { runCatching { HapticLevel.valueOf(it) }.getOrNull() } ?: HapticLevel.MEDIUM,
            reduceCelebrations = prefs[Keys.REDUCE_CELEBRATIONS] ?: false,
            gettingStartedDismissed = prefs[Keys.GETTING_STARTED_DISMISSED] ?: false,
            tipsSeen = prefs[Keys.TIPS_SEEN] ?: emptySet(),
            savedFilters = prefs[Keys.SAVED_FILTERS] ?: "",
            quietHoursEnabled = prefs[Keys.QUIET_ENABLED] ?: true,
            timerAlarmStyle = prefs[Keys.TIMER_ALARM_STYLE] ?: false,
            budgetAlertsEnabled = prefs[Keys.BUDGET_ALERTS] ?: true,
            monthlyBudgetSet = prefs.contains(Keys.MONTHLY_BUDGET_MINOR),
            defaultWalletId = prefs[Keys.DEFAULT_WALLET] ?: 0L,
            budgetAlertsSent = prefs[Keys.BUDGET_ALERTS_SENT] ?: emptySet(),
            quietStart = prefs[Keys.QUIET_START] ?: "22:00",
            quietEnd = prefs[Keys.QUIET_END] ?: "07:00",
            pinHash = prefs[Keys.PIN_HASH],
            pinSalt = prefs[Keys.PIN_SALT],
            pinLength = prefs[Keys.PIN_LENGTH] ?: 6,
            lockEnabled = prefs[Keys.LOCK_ENABLED] ?: false,
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
            hideInRecents = prefs[Keys.HIDE_IN_RECENTS] ?: true,
            autoLockSeconds = prefs[Keys.AUTO_LOCK_SECONDS]
                ?: prefs[Keys.AUTO_LOCK_MINUTES]?.let { if (it < 0) -1 else it * 60 } ?: 30,
            failedAttempts = prefs[Keys.FAILED_ATTEMPTS] ?: 0,
            lockoutUntilEpochMs = prefs[Keys.LOCKOUT_UNTIL_EPOCH_MS] ?: 0L,
            lastBackupEpochMs = prefs[Keys.LAST_BACKUP_EPOCH_MS] ?: 0L,
        )
    }

    val onboardingComplete: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }

    suspend fun setColorway(colorway: Colorway) = context.settingsDataStore.edit { it[Keys.COLORWAY] = colorway.name }
    suspend fun setThemeMode(mode: String) = context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode }
    suspend fun setRemindersEnabled(enabled: Boolean) = context.settingsDataStore.edit { it[Keys.NOTIFY_REMINDERS] = enabled }
    suspend fun setUseExactAlarms(enabled: Boolean) = context.settingsDataStore.edit { it[Keys.USE_EXACT_ALARMS] = enabled }
    suspend fun setOnboardingComplete(complete: Boolean) = context.settingsDataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }

    suspend fun setIdentity(name: String, title: String, avatarUri: String?) =
        context.settingsDataStore.edit {
            it[Keys.USER_NAME] = name
            it[Keys.USER_TITLE] = title
            if (avatarUri != null) it[Keys.USER_AVATAR_URI] = avatarUri
        }

    suspend fun setWorkSetup(workStart: String, workEnd: String, timezone: String, currency: String, restDays: Set<String>) =
        context.settingsDataStore.edit {
            it[Keys.WORK_START] = workStart
            it[Keys.WORK_END] = workEnd
            it[Keys.TIMEZONE] = timezone
            it[Keys.CURRENCY] = currency
            it[Keys.REST_DAYS] = restDays.joinToString(",")
        }

    suspend fun setNotifyChannel(channel: NotifyChannel, enabled: Boolean) = context.settingsDataStore.edit {
        it[
            when (channel) {
                NotifyChannel.WORK -> Keys.NOTIFY_WORK_CHANNEL
                NotifyChannel.SCHEDULE -> Keys.NOTIFY_SCHEDULE_CHANNEL
                NotifyChannel.MONEY -> Keys.NOTIFY_MONEY_CHANNEL
                NotifyChannel.MAINTENANCE -> Keys.NOTIFY_MAINTENANCE_CHANNEL
            },
        ] = enabled
    }

    suspend fun setRedactContent(enabled: Boolean) = context.settingsDataStore.edit { it[Keys.NOTIFY_REDACT_CONTENT] = enabled }

    suspend fun setMonthlyBudgetMinor(minor: Long) =
        context.settingsDataStore.edit { it[Keys.MONTHLY_BUDGET_MINOR] = minor }

    suspend fun setIncludeOverdueCarryover(include: Boolean) =
        context.settingsDataStore.edit { it[Keys.INCLUDE_OVERDUE_CARRYOVER] = include }

    suspend fun setDefaultWalletId(id: Long) = context.settingsDataStore.edit { it[Keys.DEFAULT_WALLET] = id }
    suspend fun setBudgetAlertsEnabled(enabled: Boolean) = context.settingsDataStore.edit { it[Keys.BUDGET_ALERTS] = enabled }
    suspend fun setBudgetAlertsSent(keys: Set<String>) = context.settingsDataStore.edit { it[Keys.BUDGET_ALERTS_SENT] = keys }
    suspend fun setTimerAlarmStyle(enabled: Boolean) = context.settingsDataStore.edit { it[Keys.TIMER_ALARM_STYLE] = enabled }
    suspend fun setQuietHours(enabled: Boolean, start: String, end: String) = context.settingsDataStore.edit {
        it[Keys.QUIET_ENABLED] = enabled
        it[Keys.QUIET_START] = start
        it[Keys.QUIET_END] = end
    }
    suspend fun setWeekStartsOn(day: String) = context.settingsDataStore.edit { it[Keys.WEEK_STARTS_ON] = day }
    suspend fun setDefaultCalendarView(view: String) = context.settingsDataStore.edit { it[Keys.DEFAULT_CALENDAR_VIEW] = view }
    suspend fun setBoardSort(sort: String) = context.settingsDataStore.edit { it[Keys.BOARD_SORT] = sort }
    suspend fun setAppLanguage(tag: String) = context.settingsDataStore.edit { it[Keys.APP_LANGUAGE] = tag }
    suspend fun setAutoBackupEnabled(value: Boolean) = context.settingsDataStore.edit { it[Keys.AUTO_BACKUP_ENABLED] = value }
    suspend fun setAutoBackupFrequency(value: String) = context.settingsDataStore.edit { it[Keys.AUTO_BACKUP_FREQUENCY] = value }
    suspend fun setAutoBackupTree(uri: String?) = context.settingsDataStore.edit {
        if (uri == null) it.remove(Keys.AUTO_BACKUP_TREE) else it[Keys.AUTO_BACKUP_TREE] = uri
        it.remove(Keys.LAST_AUTO_BACKUP_ERROR)
    }
    suspend fun setAutoBackupPasswordSealed(sealed: String?) = context.settingsDataStore.edit {
        if (sealed == null) it.remove(Keys.AUTO_BACKUP_PASSWORD) else it[Keys.AUTO_BACKUP_PASSWORD] = sealed
    }
    suspend fun recordAutoBackup(at: Long?, error: String?) = context.settingsDataStore.edit {
        if (at != null) {
            it[Keys.LAST_AUTO_BACKUP_AT] = at
            it[Keys.LAST_BACKUP_EPOCH_MS] = at
        }
        if (error == null) it.remove(Keys.LAST_AUTO_BACKUP_ERROR) else it[Keys.LAST_AUTO_BACKUP_ERROR] = error
    }
    suspend fun setBackupReminderDays(days: Int) = context.settingsDataStore.edit { it[Keys.BACKUP_REMINDER_DAYS] = days }
    suspend fun setLastBackupReminderAt(at: Long) = context.settingsDataStore.edit { it[Keys.LAST_BACKUP_REMINDER_AT] = at }
    suspend fun ensureInstalledAt(now: Long) = context.settingsDataStore.edit { if (it[Keys.INSTALLED_AT] == null) it[Keys.INSTALLED_AT] = now }
    suspend fun setMoneyWidgetAmounts(value: Boolean) = context.settingsDataStore.edit { it[Keys.MONEY_WIDGET_AMOUNTS] = value }
    suspend fun setClockFormat(value: String) = context.settingsDataStore.edit { it[Keys.CLOCK_FORMAT] = value }
    suspend fun setDateOrder(value: String) = context.settingsDataStore.edit { it[Keys.DATE_ORDER] = value }
    suspend fun setCurrencyLabel(value: String) = context.settingsDataStore.edit { it[Keys.CURRENCY_LABEL] = value }
    suspend fun setDynamicColor(value: Boolean) = context.settingsDataStore.edit { it[Keys.DYNAMIC_COLOR] = value }
    suspend fun setPureBlack(value: Boolean) = context.settingsDataStore.edit { it[Keys.PURE_BLACK] = value }
    suspend fun setHapticLevel(value: HapticLevel) = context.settingsDataStore.edit { it[Keys.HAPTIC_LEVEL] = value.name }
    suspend fun setReduceCelebrations(value: Boolean) = context.settingsDataStore.edit { it[Keys.REDUCE_CELEBRATIONS] = value }
    suspend fun setGettingStartedDismissed(value: Boolean) = context.settingsDataStore.edit { it[Keys.GETTING_STARTED_DISMISSED] = value }
    suspend fun markTipSeen(tip: String) = context.settingsDataStore.edit { it[Keys.TIPS_SEEN] = (it[Keys.TIPS_SEEN] ?: emptySet()) + tip }
    /** Settings › Help › Show tips again: every tab tip and the getting-started card come back. */
    suspend fun resetHelp() = context.settingsDataStore.edit {
        it.remove(Keys.TIPS_SEEN); it.remove(Keys.GETTING_STARTED_DISMISSED)
    }
    suspend fun setSavedFilters(encoded: String) = context.settingsDataStore.edit { it[Keys.SAVED_FILTERS] = encoded }

    suspend fun setWorkHours(start: String, end: String) = context.settingsDataStore.edit {
        it[Keys.WORK_START] = start
        it[Keys.WORK_END] = end
    }

    suspend fun setRestDays(days: Set<String>) = context.settingsDataStore.edit { it[Keys.REST_DAYS] = days.joinToString(",") }

    suspend fun setFocusBreakReminderMinutes(minutes: Int) =
        context.settingsDataStore.edit { it[Keys.FOCUS_BREAK_REMINDER_MINUTES] = minutes.coerceAtLeast(0) }

    // Tier 8 — Security & app lock.
    suspend fun setPin(hash: String, salt: String, length: Int) = context.settingsDataStore.edit {
        it[Keys.PIN_HASH] = hash
        it[Keys.PIN_SALT] = salt
        it[Keys.PIN_LENGTH] = length
        it[Keys.LOCK_ENABLED] = true
    }

    suspend fun clearPin() = context.settingsDataStore.edit {
        it.remove(Keys.PIN_HASH)
        it.remove(Keys.PIN_SALT)
        it[Keys.LOCK_ENABLED] = false
        it[Keys.BIOMETRIC_ENABLED] = false
        it[Keys.FAILED_ATTEMPTS] = 0
        it[Keys.LOCKOUT_UNTIL_EPOCH_MS] = 0L
    }

    suspend fun setBiometricEnabled(enabled: Boolean) = context.settingsDataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    suspend fun setHideInRecents(value: Boolean) = context.settingsDataStore.edit { it[Keys.HIDE_IN_RECENTS] = value }
    suspend fun setAutoLockSeconds(seconds: Int) = context.settingsDataStore.edit { it[Keys.AUTO_LOCK_SECONDS] = seconds }

    suspend fun recordFailedAttempt(count: Int) = context.settingsDataStore.edit { it[Keys.FAILED_ATTEMPTS] = count }
    suspend fun resetFailedAttempts() = context.settingsDataStore.edit { it[Keys.FAILED_ATTEMPTS] = 0 }
    suspend fun setLockoutUntil(epochMs: Long) = context.settingsDataStore.edit { it[Keys.LOCKOUT_UNTIL_EPOCH_MS] = epochMs }

    // Tier 9 — Backup & Restore.
    suspend fun setLastBackupEpochMs(epochMs: Long) = context.settingsDataStore.edit { it[Keys.LAST_BACKUP_EPOCH_MS] = epochMs }

    /** Applies a backup's `settings.json` back into DataStore — deliberately never touches
     * PIN/lock/onboarding keys since it only ever writes the keys named here. */
    suspend fun restoreAllowedSettings(json: org.json.JSONObject) = context.settingsDataStore.edit { prefs ->
        if (json.has("colorway")) prefs[Keys.COLORWAY] = json.getString("colorway")
        if (json.has("theme_mode")) prefs[Keys.THEME_MODE] = json.getString("theme_mode")
        if (json.has("user_name")) prefs[Keys.USER_NAME] = json.getString("user_name")
        if (json.has("user_avatar_uri") && !json.isNull("user_avatar_uri")) prefs[Keys.USER_AVATAR_URI] = json.getString("user_avatar_uri")
        if (json.has("user_title")) prefs[Keys.USER_TITLE] = json.getString("user_title")
        if (json.has("work_start")) prefs[Keys.WORK_START] = json.getString("work_start")
        if (json.has("work_end")) prefs[Keys.WORK_END] = json.getString("work_end")
        if (json.has("timezone")) prefs[Keys.TIMEZONE] = json.getString("timezone")
        if (json.has("currency")) prefs[Keys.CURRENCY] = json.getString("currency")
        if (json.has("rest_days")) prefs[Keys.REST_DAYS] = json.getString("rest_days")
        if (json.has("notify_work_channel")) prefs[Keys.NOTIFY_WORK_CHANNEL] = json.getBoolean("notify_work_channel")
        if (json.has("notify_schedule_channel")) prefs[Keys.NOTIFY_SCHEDULE_CHANNEL] = json.getBoolean("notify_schedule_channel")
        if (json.has("notify_money_channel")) prefs[Keys.NOTIFY_MONEY_CHANNEL] = json.getBoolean("notify_money_channel")
        if (json.has("notify_maintenance_channel")) prefs[Keys.NOTIFY_MAINTENANCE_CHANNEL] = json.getBoolean("notify_maintenance_channel")
        if (json.has("notify_redact_content")) prefs[Keys.NOTIFY_REDACT_CONTENT] = json.getBoolean("notify_redact_content")
        if (json.has("monthly_budget_set") && !json.getBoolean("monthly_budget_set")) {
            prefs.remove(Keys.MONTHLY_BUDGET_MINOR)
        } else if (json.has("monthly_budget_minor")) {
            prefs[Keys.MONTHLY_BUDGET_MINOR] = json.getLong("monthly_budget_minor")
        }
        if (json.has("include_overdue_carryover")) prefs[Keys.INCLUDE_OVERDUE_CARRYOVER] = json.getBoolean("include_overdue_carryover")
        if (json.has("budget_alerts")) prefs[Keys.BUDGET_ALERTS] = json.getBoolean("budget_alerts")
        if (json.has("timer_alarm_style")) prefs[Keys.TIMER_ALARM_STYLE] = json.getBoolean("timer_alarm_style")
        if (json.has("quiet_hours_enabled")) prefs[Keys.QUIET_ENABLED] = json.getBoolean("quiet_hours_enabled")
        if (json.has("quiet_start")) prefs[Keys.QUIET_START] = json.getString("quiet_start")
        if (json.has("quiet_end")) prefs[Keys.QUIET_END] = json.getString("quiet_end")
        if (json.has("week_starts_on")) prefs[Keys.WEEK_STARTS_ON] = json.getString("week_starts_on")
        if (json.has("default_calendar_view")) prefs[Keys.DEFAULT_CALENDAR_VIEW] = json.getString("default_calendar_view")
        if (json.has("board_sort")) prefs[Keys.BOARD_SORT] = json.getString("board_sort")
        if (json.has("app_language")) prefs[Keys.APP_LANGUAGE] = json.getString("app_language")
        if (json.has("money_widget_amounts")) prefs[Keys.MONEY_WIDGET_AMOUNTS] = json.getBoolean("money_widget_amounts")
        if (json.has("clock_format")) prefs[Keys.CLOCK_FORMAT] = json.getString("clock_format")
        if (json.has("date_order")) prefs[Keys.DATE_ORDER] = json.getString("date_order")
        if (json.has("currency_label")) prefs[Keys.CURRENCY_LABEL] = json.getString("currency_label")
        if (json.has("dynamic_color")) prefs[Keys.DYNAMIC_COLOR] = json.getBoolean("dynamic_color")
        if (json.has("pure_black")) prefs[Keys.PURE_BLACK] = json.getBoolean("pure_black")
        if (json.has("haptic_level")) prefs[Keys.HAPTIC_LEVEL] = json.getString("haptic_level")
        if (json.has("reduce_celebrations")) prefs[Keys.REDUCE_CELEBRATIONS] = json.getBoolean("reduce_celebrations")
        if (json.has("saved_filters")) prefs[Keys.SAVED_FILTERS] = json.getString("saved_filters")
        if (json.has("focus_break_reminder_minutes")) prefs[Keys.FOCUS_BREAK_REMINDER_MINUTES] = json.getInt("focus_break_reminder_minutes")
    }
}

enum class NotifyChannel { WORK, SCHEDULE, MONEY, MAINTENANCE }
