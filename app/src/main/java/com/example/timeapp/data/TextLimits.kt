package com.example.timeapp.data

/**
 * How much text one field may hold. Android reads a database row through a 2 MB window: a row
 * bigger than that can't be read at all, and because the lists load every row, one pasted book
 * in a note made the whole task list fail on every launch (BreakItTest found it). 100,000
 * characters is about thirty pages and at most 400 KB stored, so a row stays far below the limit.
 *
 * The editors stop at these limits and say so ([com.example.timeapp.ui.richtext.RichTextEditor]);
 * the storage mappings clamp again as the last line of defence, so nothing can reach the
 * database that would make it unreadable.
 */
const val MAX_NOTE_CHARS = 100_000
const val MAX_TITLE_CHARS = 1_000

/** [this] cut to at most [max] characters, never between the two halves of an emoji. */
fun String.clampedTo(max: Int): String {
    if (length <= max) return this
    var end = max
    if (end > 0 && Character.isHighSurrogate(this[end - 1])) end--
    return substring(0, end)
}

fun String.clampedNote(): String = clampedTo(MAX_NOTE_CHARS)
fun String.clampedTitle(): String = clampedTo(MAX_TITLE_CHARS)
