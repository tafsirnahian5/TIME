package com.example.timeapp.data

import java.util.Locale

/**
 * The app's language (Phase 17). English is the source language and the key: every piece of
 * text a person reads goes through [L] with its English wording, and in Bangla that wording is
 * looked up in [BnStrings]; anything not (yet) in the catalog stays English rather than
 * breaking. Set once, before any screen exists, in TimeApplication — changing the language
 * restarts the app, so enum labels and other text built once pick it up too.
 *
 * Only the *look* changes: stored data, amounts (poisha), dates (ISO), backups, CSV/.ics/HTML
 * exports and imports are language-independent, and user-typed text is never translated.
 */
object Lang {
    @Volatile var bangla: Boolean = false

    val locale: Locale get() = if (bangla) Locale.forLanguageTag("bn-BD") else Locale.UK

    private const val BN_DIGITS = "০১২৩৪৫৬৭৮৯"

    /** ASCII digits → Bangla digits in Bangla mode; unchanged in English. Display only. */
    fun digits(s: String): String =
        if (!bangla) s else buildString(s.length) { s.forEach { c -> append(if (c in '0'..'9') BN_DIGITS[c - '0'] else c) } }
}

/**
 * Translates [text] (its English wording) and, with [args], fills its `%1$s`-style slots.
 * Numbers among the arguments get the language's digits; string arguments — often user-typed
 * titles — are inserted exactly as given.
 */
fun L(text: String, vararg args: Any?): String {
    val template = if (Lang.bangla) BnStrings.map[text] ?: text else text
    if (args.isEmpty()) return template
    val shown = args.map { a -> if (a is Number) Lang.digits(a.toString()) else a }.toTypedArray()
    return try {
        String.format(Locale.ROOT, template, *shown)
    } catch (e: java.util.IllegalFormatException) {
        String.format(Locale.ROOT, text, *shown)
    }
}
