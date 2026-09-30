package com.example.timeapp.data

/** The pages Settings search can open (Phase 15). */
enum class SettingsPage(val label: String) {
    PROFILE(L("Profile")), CATEGORIES(L("Categories")), WORKDAY(L("Workday & calendar")), REPORTS(L("Reports & goals")),
    MONEY(L("Money settings")), NOTIFICATIONS(L("Notifications")), APPEARANCE(L("Appearance")), TIMERS(L("Timers & focus")),
    LANGUAGE(L("Language & region")), SECURITY(L("Security & app lock")), DATA(L("Data & backup")), ABOUT(L("About")),
    HELP(L("Help & guides")),
}

/** One searchable setting: what it's called, where it lives, and other words people use for it. */
data class SettingEntry(val title: String, val page: SettingsPage, val keywords: List<String> = emptyList())

/**
 * Every real setting, by the page that holds it. **Keep in step with the pages** — a setting
 * listed here must exist where it says (the PDF: a setting appears only once it works).
 */
val SETTINGS_INDEX: List<SettingEntry> = listOf(
    SettingEntry(L("Name"), SettingsPage.PROFILE, listOf("profile", "avatar", "title")),
    SettingEntry(L("Work categories"), SettingsPage.CATEGORIES, listOf("category", "colour", "color", "icon", "merge")),
    SettingEntry(L("Money categories and budgets"), SettingsPage.CATEGORIES, listOf("category", "budget", "limit")),
    SettingEntry(L("Projects"), SettingsPage.CATEGORIES, listOf("project")),
    SettingEntry(L("Work hours"), SettingsPage.WORKDAY, listOf("start", "end", "office", "hours")),
    SettingEntry(L("Rest days"), SettingsPage.WORKDAY, listOf("weekend", L("day off"), "holiday")),
    SettingEntry(L("Week starts on"), SettingsPage.WORKDAY, listOf("week", "saturday", "sunday", "monday")),
    SettingEntry(L("Calendar opens on"), SettingsPage.WORKDAY, listOf("month", "week", "day", "agenda", "view")),
    SettingEntry(L("Weekly work target"), SettingsPage.REPORTS, listOf("target", "goal", "hours")),
    SettingEntry(L("Default wallet"), SettingsPage.MONEY, listOf("wallet", "account")),
    SettingEntry(L("Monthly budget"), SettingsPage.MONEY, listOf("budget", "limit", "spending")),
    SettingEntry(L("Count late bills from earlier months"), SettingsPage.MONEY, listOf("carryover", "forecast", "overdue")),
    SettingEntry(L("Budget alerts"), SettingsPage.MONEY, listOf("alert", "warning", "overspend", "80%")),
    SettingEntry(L("Show amounts on the Money widget"), SettingsPage.MONEY, listOf("widget", L("home screen"), "balance")),
    SettingEntry(L("Notification channels"), SettingsPage.NOTIFICATIONS, listOf("notification", "mute", "sound", "channel")),
    SettingEntry(L("Quiet hours"), SettingsPage.NOTIFICATIONS, listOf("quiet", "night", L("do not disturb"), "silence")),
    SettingEntry(L("Hide details on the lock screen"), SettingsPage.NOTIFICATIONS, listOf(L("lock screen"), "private", "redact")),
    SettingEntry(L("Theme"), SettingsPage.APPEARANCE, listOf("dark", "light", "night", "mode")),
    SettingEntry(L("Accent colour"), SettingsPage.APPEARANCE, listOf("colour", "color", "violet", "orange", "cyan", "forest", "navy")),
    SettingEntry(L("Use wallpaper colours"), SettingsPage.APPEARANCE, listOf("dynamic", L("material you"), "colour", "color")),
    SettingEntry(L("Pure black"), SettingsPage.APPEARANCE, listOf("amoled", "oled", "black", "dark")),
    SettingEntry(L("Reduce celebrations"), SettingsPage.APPEARANCE, listOf("confetti", "animation", "motion")),
    SettingEntry(L("Haptic feedback"), SettingsPage.APPEARANCE, listOf("haptics", "vibration", "vibrate", "buzz", "touch feedback")),
    SettingEntry(L("Pomodoro lengths"), SettingsPage.TIMERS, listOf("pomodoro", "work", "break", L("long break"))),
    SettingEntry(L("Start the next phase by itself"), SettingsPage.TIMERS, listOf(L("auto start"), "pomodoro")),
    SettingEntry(L("Countdown length"), SettingsPage.TIMERS, listOf("countdown", "timer")),
    SettingEntry(L("Ring like an alarm"), SettingsPage.TIMERS, listOf("alarm", "ring", "timer", "sound", "quiet")),
    SettingEntry(L("Tone"), SettingsPage.TIMERS, listOf("alarm sound", "ringtone", "custom tone", "music", "audio file", "sound")),
    SettingEntry(L("Volume"), SettingsPage.TIMERS, listOf("alarm volume", "loud", "quiet", "preview")),
    SettingEntry(L("Gradually louder"), SettingsPage.TIMERS, listOf("fade in", "gradual", "increasing volume", "ramp")),
    SettingEntry(L("Vibration"), SettingsPage.TIMERS, listOf("alarm vibration", "vibration pattern", "heartbeat", "pulse")),
    SettingEntry(L("Say it aloud"), SettingsPage.TIMERS, listOf("tts", "text to speech", "speak", "voice", "announce")),
    SettingEntry(L("Stop ringing after"), SettingsPage.TIMERS, listOf("auto stop", "silence", "timeout")),
    SettingEntry(L("Break reminder"), SettingsPage.TIMERS, listOf("break", "rest", "reminder")),
    SettingEntry(L("Timer presets"), SettingsPage.TIMERS, listOf("preset")),
    SettingEntry(L("Clock"), SettingsPage.LANGUAGE, listOf("24-hour", "12-hour", "am", "pm", L("time format"))),
    SettingEntry(L("Date order"), SettingsPage.LANGUAGE, listOf(L("date format"), "day", "month")),
    SettingEntry(L("Currency label"), SettingsPage.LANGUAGE, listOf("currency", "taka", "tk", "৳", "bdt")),
    SettingEntry(L("App language"), SettingsPage.LANGUAGE, listOf("language", "english", "bangla", "bengali")),
    SettingEntry(L("PIN lock"), SettingsPage.SECURITY, listOf("pin", "password", "lock", "security")),
    SettingEntry(L("Fingerprint unlock"), SettingsPage.SECURITY, listOf("biometric", "fingerprint", "face")),
    SettingEntry(L("Back up now"), SettingsPage.DATA, listOf("backup", "export", "save")),
    SettingEntry(L("Restore a backup"), SettingsPage.DATA, listOf("restore", "import")),
    SettingEntry(L("Automatic backups"), SettingsPage.DATA, listOf("backup", "automatic", "schedule", "daily", "weekly", "folder")),
    SettingEntry(L("Backup password"), SettingsPage.DATA, listOf("password", "encrypt", "protect")),
    SettingEntry(L("Remind me to back up"), SettingsPage.DATA, listOf("reminder", "backup")),
    SettingEntry(L("Export CSV"), SettingsPage.DATA, listOf("csv", "export", "spreadsheet")),
    SettingEntry(L("Import transactions"), SettingsPage.DATA, listOf("csv", "import", "bank")),
    SettingEntry(L("Links export and import"), SettingsPage.DATA, listOf("bookmarks", "html", "json", "links")),
    SettingEntry(L("Calendar export and import"), SettingsPage.DATA, listOf("ics", "calendar", "google")),
    SettingEntry(L("Load demo data"), SettingsPage.DATA, listOf("demo", "sample")),
    SettingEntry(L("Delete all data"), SettingsPage.DATA, listOf("delete", "wipe", "reset", "erase")),
    SettingEntry(L("Version"), SettingsPage.ABOUT, listOf("about", "licenses", "version")),
    SettingEntry(L("Watch the welcome tour again"), SettingsPage.HELP, listOf("help", "tour", "tutorial", "walkthrough", "intro")),
    SettingEntry(L("Show tips again"), SettingsPage.HELP, listOf("help", "tips", "hints", "getting started")),
    SettingEntry(L("Guides"), SettingsPage.HELP, listOf("help", "how", "guide", "manual")),
)

/**
 * Matches in order of how strongly they fit: the title starting with the query, the title
 * containing it, a keyword, then the page's name. Case-insensitive; blank finds nothing.
 */
fun searchSettings(query: String, index: List<SettingEntry> = SETTINGS_INDEX): List<SettingEntry> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    fun rank(e: SettingEntry): Int? = when {
        e.title.lowercase().startsWith(q) -> 0
        e.title.lowercase().contains(q) -> 1
        e.keywords.any { it.lowercase().contains(q) } -> 2
        e.page.label.lowercase().contains(q) -> 3
        else -> null
    }
    return index.mapNotNull { e -> rank(e)?.let { it to e } }.sortedBy { it.first }.map { it.second }
}
