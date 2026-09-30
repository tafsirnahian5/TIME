package com.example.timeapp.data

/**
 * Taka text ↔ poisha. Amounts are integer poisha end to end — never Float/Double, which
 * cannot represent 0.10 + 0.20 exactly.
 *
 * Before this existed every call site did `text.toLongOrNull() ?: 0L * 100`, so typing
 * "1200.50" parsed to null and silently saved **Tk 0**. Parsing now either succeeds exactly
 * or returns null, and callers refuse to save on null instead of writing a wrong number.
 */

/**
 * Parses "1200", "1200.5", "1200.50" → 120000 / 120050 / 120050 poisha.
 * Returns null for anything not exactly representable: blank, non-numeric, more than two
 * fractional digits, or an overflowing magnitude.
 */
fun parseTakaToMinor(input: String): Long? {
    val trimmed = input.trim().replace(",", "")
    if (trimmed.isEmpty()) return null
    val negative = trimmed.startsWith("-")
    val digits = if (negative) trimmed.substring(1) else trimmed
    if (digits.isEmpty()) return null

    val parts = digits.split(".")
    if (parts.size > 2) return null
    val whole = parts[0].ifEmpty { "0" }
    val fraction = parts.getOrNull(1) ?: ""
    if (fraction.length > 2) return null // two decimals maximum, as the forms promise
    if (!whole.all(Char::isDigit) || !fraction.all(Char::isDigit)) return null

    val wholeValue = whole.toLongOrNull() ?: return null
    // "1200.5" means 50 poisha, not 5 — pad before parsing.
    val fractionValue = fraction.padEnd(2, '0').toLongOrNull() ?: return null
    // Exact arithmetic, not a magnitude pre-check: at the boundary `whole * 100` can still
    // fit while `+ fraction` overflows, which a simple range test silently lets through.
    val total = try {
        Math.addExact(Math.multiplyExact(wholeValue, 100L), fractionValue)
    } catch (e: ArithmeticException) {
        return null
    }
    return if (negative) -total else total
}

/** True while [input] is a legal partial amount, so the field can reject bad keystrokes
 * without fighting the user mid-typing ("12.", "0.5" and "" are all still in progress). */
fun isPartialTakaInput(input: String): Boolean {
    if (input.isEmpty()) return true
    if (input.count { it == '.' } > 1) return false
    if (!input.all { it.isDigit() || it == '.' }) return false
    val fraction = input.substringAfter('.', "")
    return fraction.length <= 2
}

/** Poisha → "1,200.50", dropping the decimals when they are zero so whole amounts stay clean.
 * [toTaka] stays the display formatter for headline figures; this one is for edit fields. */
fun Long.toTakaInput(): String {
    val whole = this / 100
    val fraction = (this % 100).toInt()
    return if (fraction == 0) whole.toString() else "$whole.${fraction.toString().padStart(2, '0')}"
}
