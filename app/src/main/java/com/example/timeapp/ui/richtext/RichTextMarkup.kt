package com.example.timeapp.ui.richtext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration

/**
 * The markdown-lite grammar this app supports, exactly: bold `**x**`, italic `*x*`, one
 * heading level `# x`, bullet `- x`, numbered `1. x` (renumbered from 1 per contiguous run —
 * the typed digit is discarded), checklist `- [ ] x` / `- [x] x`, links `[text](url)`, and —
 * the PDF's fuller editor, Phase 14 — strikethrough `~~x~~`, block quote `> x` and inline code
 * `` `x` `` (its content shown literally, never parsed). Nothing beyond these: no tables,
 * images, nested lists or extra heading levels.
 */
sealed class RichTextBlock(open val lineIndex: Int) {
    data class Heading(override val lineIndex: Int, val text: String) : RichTextBlock(lineIndex)
    data class Bullet(override val lineIndex: Int, val text: String) : RichTextBlock(lineIndex)
    data class Numbered(override val lineIndex: Int, val text: String, val displayIndex: Int) : RichTextBlock(lineIndex)
    data class Checklist(override val lineIndex: Int, val text: String, val checked: Boolean) : RichTextBlock(lineIndex)
    data class Quote(override val lineIndex: Int, val text: String) : RichTextBlock(lineIndex)
    data class Paragraph(override val lineIndex: Int, val text: String) : RichTextBlock(lineIndex)
    data class Blank(override val lineIndex: Int) : RichTextBlock(lineIndex)
}

private val checklistRegex = Regex("^- \\[( |x|X)\\] (.*)$")
private val bulletRegex = Regex("^- (.+)$")
private val numberedRegex = Regex("^\\d+\\.\\s(.+)$")
private val headingRegex = Regex("^# (.+)$")
private val quoteRegex = Regex("^> ?(.+)$")

/** Classifies each line independently, in a fixed mutually-exclusive order: checklist lines
 * would also match the bullet regex, so checklist must be checked first. */
fun parseMarkdownLite(raw: String): List<RichTextBlock> {
    val lines = raw.split("\n")
    val blocks = mutableListOf<RichTextBlock>()
    var numberedRun = 0
    lines.forEachIndexed { index, line ->
        val checklistMatch = if (line.isBlank()) null else checklistRegex.find(line)
        val numberedMatch = if (checklistMatch != null || line.isBlank()) null else numberedRegex.find(line)
        when {
            line.isBlank() -> {
                blocks += RichTextBlock.Blank(index)
                numberedRun = 0
            }
            checklistMatch != null -> {
                val checked = checklistMatch.groupValues[1].equals("x", ignoreCase = true)
                blocks += RichTextBlock.Checklist(index, checklistMatch.groupValues[2], checked)
                numberedRun = 0
            }
            numberedMatch != null -> {
                numberedRun += 1
                blocks += RichTextBlock.Numbered(index, numberedMatch.groupValues[1], numberedRun)
            }
            else -> {
                numberedRun = 0
                val bulletMatch = bulletRegex.find(line)
                val headingMatch = if (bulletMatch != null) null else headingRegex.find(line)
                val quoteMatch = if (bulletMatch != null || headingMatch != null) null else quoteRegex.find(line)
                when {
                    bulletMatch != null -> blocks += RichTextBlock.Bullet(index, bulletMatch.groupValues[1])
                    headingMatch != null -> blocks += RichTextBlock.Heading(index, headingMatch.groupValues[1])
                    quoteMatch != null -> blocks += RichTextBlock.Quote(index, quoteMatch.groupValues[1])
                    else -> blocks += RichTextBlock.Paragraph(index, line)
                }
            }
        }
    }
    return blocks
}

/** Inline code/bold/strikethrough/italic/link parsing for one line's text, one left-to-right
 * scan. Inline code is checked first and its content is appended literally (so `` `**` `` shows
 * two stars); bold (`**`) is checked before single-star italic so a bold span is never mis-split
 * into two italics. An opening marker with no matching close before end-of-line degrades to a
 * literal character — this must never throw or swallow the rest of the line. */
fun parseInlineSpans(text: String, linkColor: Color, codeBackground: Color = Color.Unspecified): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end == -1 || end == i + 1) {
                    append(text[i]); i += 1
                } else {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                }
            }
            text.regionMatches(i, "~~", 0, 2) -> {
                val end = text.indexOf("~~", i + 2)
                if (end == -1) {
                    append(text[i]); i += 1
                } else {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                }
            }
            text.regionMatches(i, "**", 0, 2) -> {
                val end = text.indexOf("**", i + 2)
                if (end == -1) {
                    append(text[i]); i += 1
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                }
            }
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end == -1) {
                    append(text[i]); i += 1
                } else {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                }
            }
            text[i] == '[' -> {
                val closeBracket = text.indexOf(']', i + 1)
                val hasParenOpen = closeBracket != -1 && closeBracket + 1 < text.length && text[closeBracket + 1] == '('
                val closeParen = if (hasParenOpen) text.indexOf(')', closeBracket + 2) else -1
                if (hasParenOpen && closeParen != -1) {
                    val linkText = text.substring(i + 1, closeBracket)
                    val url = text.substring(closeBracket + 2, closeParen)
                    withLink(LinkAnnotation.Url(url)) {
                        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                            append(linkText)
                        }
                    }
                    i = closeParen + 1
                } else {
                    append(text[i]); i += 1
                }
            }
            else -> { append(text[i]); i += 1 }
        }
    }
}

/** The one primitive every screen's checklist-tap handler calls before saving — flips a single
 * line's `- [ ] `/`- [x] ` marker and rejoins, leaving every other line untouched. */
fun toggleChecklistLine(raw: String, lineIndex: Int): String {
    val lines = raw.split("\n").toMutableList()
    if (lineIndex !in lines.indices) return raw
    val line = lines[lineIndex]
    val match = checklistRegex.find(line) ?: return raw
    val checked = match.groupValues[1].equals("x", ignoreCase = true)
    val newMarker = if (checked) "- [ ] " else "- [x] "
    lines[lineIndex] = newMarker + match.groupValues[2]
    return lines.joinToString("\n")
}

data class ChecklistItem(val text: String, val checked: Boolean)

/**
 * Pulls every checklist line out of [raw] — for Task Detail's "Turn into steps". Returns the
 * items in order and the text without them; blank lines the removal leaves at either end are
 * dropped, and a run of blank lines it creates collapses to one, so the rest reads as before.
 */
fun extractChecklist(raw: String): Pair<List<ChecklistItem>, String> {
    val items = mutableListOf<ChecklistItem>()
    val kept = mutableListOf<String>()
    raw.split("\n").forEach { line ->
        val match = checklistRegex.find(line)
        if (match != null) {
            items += ChecklistItem(match.groupValues[2], match.groupValues[1].equals("x", ignoreCase = true))
        } else if (!(line.isBlank() && kept.lastOrNull()?.isBlank() == true)) {
            kept += line
        }
    }
    return items to kept.joinToString("\n").trim('\n')
}
