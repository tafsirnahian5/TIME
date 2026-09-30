package com.example.timeapp.data

import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Settings › Language & region (Phase 15): how times, dates and amounts **look**. Like
 * [WeekConfig], it's app-wide state set from settings in TimeApplication, read by every display
 * formatter through [displayPattern] — the screens keep writing the day-first, 24-hour pattern
 * they were designed with, and this adapts it. Storage never changes: amounts stay poisha,
 * dates stay ISO, and machine formats (.ics, CSV) never go through here.
 */
object DisplayFormats {
    /** Resolved clock: true = 24-hour. "Phone default" is resolved by the caller. */
    @Volatile var use24h: Boolean = true
    /** "Sep 28" rather than "28 Sep". */
    @Volatile var monthFirst: Boolean = false
    /** "Tk" or "৳" in front of amounts. */
    @Volatile var currencyLabel: String = "Tk"

    private val cache = ConcurrentHashMap<String, DateTimeFormatter>()

    fun formatter(raw: String): DateTimeFormatter {
        val adapted = adaptPattern(raw, use24h, monthFirst)
        // Month and day names in the app's language (not the phone's), and Bangla digits in
        // Bangla. The language is fixed for a process, so the cache never mixes the two.
        return cache.getOrPut(adapted) {
            val f = DateTimeFormatter.ofPattern(adapted, Lang.locale)
            if (Lang.bangla) f.withDecimalStyle(java.time.format.DecimalStyle.of(Lang.locale)) else f
        }
    }

    /** The currency label followed by a space when it's a word ("Tk 1,200") and tight when
     * it's the symbol ("৳1,200"). */
    val currencyPrefix: String get() = if (currencyLabel == "৳") "৳" else "$currencyLabel "
}

/**
 * How long something takes, written one way everywhere a person reads it: "45 min", "1 h",
 * "2 h 30 min". The app used to write the same 150 minutes as "150 min", "2h 30m" and
 * "2 h 30 min" on three screens, and "1h 0m" for an hour.
 */
fun durationText(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> L("%1\$s min", m)
        m == 0L -> L("%1\$s h", h)
        else -> L("%1\$s h %2\$s min", h, m)
    }
}

/** [durationText] for a span in milliseconds (whole minutes, rounded down). */
fun durationTextMs(ms: Long): String = durationText(ms / 60_000)

/** A display formatter for [raw] as the user likes dates and times shown. */
fun displayPattern(raw: String): DateTimeFormatter = DisplayFormats.formatter(raw)

/**
 * Pure: rewrites a day-first 24-hour pattern for the chosen order and clock. Tested in
 * DisplayFormatsTest. Only the shapes the app uses are rewritten; anything else passes through.
 */
fun adaptPattern(raw: String, use24h: Boolean, monthFirst: Boolean): String {
    var p = raw
    if (monthFirst) {
        p = p.replace("d MMM yyyy", "MMM d, yyyy").replace("d MMMM", "MMMM d")
        p = Regex("(?<![M])d MMM(?!M)").replace(p, "MMM d")
    } else {
        p = p.replace("MMM d, yyyy", "d MMM yyyy")
        p = Regex("(?<!M)MMM d(?![a-z])").replace(p, "d MMM")
    }
    if (!use24h) p = p.replace("HH:mm", "h:mm a").replace(Regex("(?<!H)H:mm"), "h:mm a")
    return p
}
