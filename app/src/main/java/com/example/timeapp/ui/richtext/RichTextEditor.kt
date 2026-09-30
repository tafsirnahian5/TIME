package com.example.timeapp.ui.richtext

import com.example.timeapp.data.clampedNote
import com.example.timeapp.data.L

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.example.timeapp.ui.theme.Dimens

/** Pure [TextFieldValue] transforms behind [RichTextEditor]'s toolbar — kept separate from the
 * composable so the insert/toggle logic is directly testable without a Compose environment. */
object RichTextEditing {

    /** Bold/italic: wraps a non-empty selection in `marker…marker` keeping it selected; on an
     * empty selection, inserts an empty `markermarker` pair with the cursor placed between them. */
    fun wrapSelection(value: TextFieldValue, marker: String): TextFieldValue {
        val start = value.selection.min
        val end = value.selection.max
        val text = value.text
        val newText = text.substring(0, start) + marker + text.substring(start, end) + marker + text.substring(end)
        val newSelection = if (start == end) {
            TextRange(start + marker.length)
        } else {
            TextRange(start + marker.length, end + marker.length)
        }
        return TextFieldValue(newText, newSelection)
    }

    /** Heading/bullet/checklist/numbered: toggles [prefix] on every line touched by the current
     * selection — removing it (matched by [stripRegex]) if already present, else prepending it. */
    fun toggleLinePrefix(value: TextFieldValue, prefix: String, stripRegex: Regex): TextFieldValue {
        val text = value.text
        val selStart = value.selection.min
        val selEnd = value.selection.max
        val lineStart = text.lastIndexOf('\n', selStart - 1) + 1
        val lineEndSearch = text.indexOf('\n', selEnd)
        val lineEnd = if (lineEndSearch == -1) text.length else lineEndSearch
        val block = text.substring(lineStart, lineEnd)
        val lines = block.split("\n")
        var delta = 0
        val newLines = lines.map { line ->
            val stripped = stripRegex.find(line)
            if (stripped != null) {
                val replacement = line.removeRange(stripped.range.first, stripped.range.last + 1)
                delta += replacement.length - line.length
                replacement
            } else {
                delta += prefix.length
                prefix + line
            }
        }
        val newBlock = newLines.joinToString("\n")
        val newText = text.substring(0, lineStart) + newBlock + text.substring(lineEnd)
        val newSelEnd = (selEnd + delta).coerceIn(lineStart, newText.length)
        val newSelStart = (selStart + (if (newLines.isNotEmpty()) newLines.first().length - lines.first().length else 0)).coerceIn(lineStart, newText.length)
        return TextFieldValue(newText, TextRange(newSelStart.coerceAtMost(newSelEnd), newSelEnd))
    }

    /** Replaces the current selection with `[selectedText](url)`, or inserts `[link text](url)`
     * with "link text" pre-selected when there's no selection to wrap. */
    fun insertLink(value: TextFieldValue, url: String): TextFieldValue {
        val start = value.selection.min
        val end = value.selection.max
        val text = value.text
        val linkLabel = if (start == end) L("link text") else text.substring(start, end)
        val inserted = "[$linkLabel]($url)"
        val newText = text.substring(0, start) + inserted + text.substring(end)
        val labelStart = start + 1
        val labelEnd = labelStart + linkLabel.length
        return TextFieldValue(newText, TextRange(labelStart, labelEnd))
    }
}

/**
 * Editor undo/redo (Phase 14). Pure and clock-injected so it is testable. Typing coalesces:
 * changes less than [coalesceMs] apart replace the newest step instead of adding one, so one
 * Undo takes back a burst of typing rather than a single letter. A toolbar action, a paste-sized
 * jump, or a pause always starts a new step. Selection-only changes are not steps.
 */
class EditHistory(initial: TextFieldValue, private val limit: Int = 100, private val coalesceMs: Long = 1_000) {
    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()
    var current: TextFieldValue = initial
        private set
    private var lastAt = Long.MIN_VALUE
    private var lastWasTyping = false

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()

    /** Records [value] as the editor's new state. [boundary] marks a toolbar action or an
     * outside change (a checklist toggled in Preview) — always its own step. */
    fun record(value: TextFieldValue, now: Long, boundary: Boolean = false) {
        if (value.text == current.text) {
            current = value
            return
        }
        val typing = !boundary && kotlin.math.abs(value.text.length - current.text.length) <= 1
        val coalesce = typing && lastWasTyping && now - lastAt in 0 until coalesceMs
        if (!coalesce) {
            undoStack.addLast(current)
            while (undoStack.size > limit) undoStack.removeFirst()
        }
        redoStack.clear()
        current = value
        lastAt = now
        lastWasTyping = typing
    }

    fun undo(): TextFieldValue? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        current = previous
        lastWasTyping = false
        return previous
    }

    fun redo(): TextFieldValue? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        current = next
        lastWasTyping = false
        return next
    }
}

private val numberedStripRegex = Regex("^\\d+\\.\\s")
private val checklistStripRegex = Regex("^- \\[( |x|X)\\] ")
private val bulletStripRegex = Regex("^- ")
private val headingStripRegex = Regex("^# ")
private val quoteStripRegex = Regex("^> ?")

/** The compact toolbar + text field behind [RichTextField]'s "Write" segment. A standalone
 * composable in case a screen ever wants the editor without the Write/Preview switch. */
@Composable
fun RichTextEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    minLines: Int = 3,
    placeholder: String? = null,
) {
    var showLinkDialog by remember { mutableStateOf(false) }
    var clipped by remember { mutableStateOf(false) }
    val history = remember { EditHistory(value) }
    // Bumped on every history change so canUndo/canRedo recompose the toolbar.
    var historyVersion by remember { mutableIntStateOf(0) }
    // A change made outside the editor (Preview's checklist toggle, a screen reset) becomes
    // its own undo step, so Undo never jumps over it.
    if (value.text != history.current.text) {
        history.record(value, System.currentTimeMillis(), boundary = true)
    }
    fun apply(next: TextFieldValue, boundary: Boolean) {
        history.record(next, System.currentTimeMillis(), boundary)
        historyVersion++
        onValueChange(next)
    }
    val canUndo = historyVersion.let { history.canUndo }
    val canRedo = historyVersion.let { history.canRedo }

    Column(modifier = modifier) {
        // Twelve tools don't fit a phone's width; the row scrolls sideways instead of clipping.
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.s4),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
            IconButton(onClick = { history.undo()?.let { historyVersion++; onValueChange(it) } }, enabled = canUndo) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = L("Undo"))
            }
            IconButton(onClick = { history.redo()?.let { historyVersion++; onValueChange(it) } }, enabled = canRedo) {
                Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = L("Redo"))
            }
            IconButton(onClick = { apply(RichTextEditing.wrapSelection(value, "**"), true) }) {
                Icon(Icons.Rounded.FormatBold, contentDescription = L("Bold"))
            }
            IconButton(onClick = { apply(RichTextEditing.wrapSelection(value, "*"), true) }) {
                Icon(Icons.Rounded.FormatItalic, contentDescription = L("Italic"))
            }
            IconButton(onClick = { apply(RichTextEditing.wrapSelection(value, "~~"), true) }) {
                Icon(Icons.Rounded.FormatStrikethrough, contentDescription = L("Strikethrough"))
            }
            IconButton(onClick = { apply(RichTextEditing.wrapSelection(value, "`"), true) }) {
                Icon(Icons.Rounded.Code, contentDescription = L("Inline code"))
            }
            IconButton(onClick = { apply(RichTextEditing.toggleLinePrefix(value, "# ", headingStripRegex), true) }) {
                Icon(Icons.Rounded.Title, contentDescription = L("Heading"))
            }
            IconButton(onClick = { apply(RichTextEditing.toggleLinePrefix(value, "> ", quoteStripRegex), true) }) {
                Icon(Icons.Rounded.FormatQuote, contentDescription = L("Quote"))
            }
            IconButton(onClick = { apply(RichTextEditing.toggleLinePrefix(value, "- ", bulletStripRegex), true) }) {
                Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, contentDescription = L("Bullet list"))
            }
            IconButton(onClick = { apply(RichTextEditing.toggleLinePrefix(value, "1. ", numberedStripRegex), true) }) {
                Icon(Icons.Rounded.FormatListNumbered, contentDescription = L("Numbered list"))
            }
            IconButton(onClick = { apply(RichTextEditing.toggleLinePrefix(value, "- [ ] ", checklistStripRegex), true) }) {
                Icon(Icons.Rounded.CheckBox, contentDescription = L("Checklist"))
            }
            IconButton(onClick = { showLinkDialog = true }) {
                Icon(Icons.Rounded.Link, contentDescription = L("Link"))
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = { next ->
                // A note has a ceiling (data/TextLimits.kt — past it a row can't be read back).
                // An oversized paste keeps what fits and says so, rather than failing silently.
                if (next.text.length > com.example.timeapp.data.MAX_NOTE_CHARS) {
                    val kept = next.text.clampedNote()
                    clipped = true
                    apply(TextFieldValue(kept, androidx.compose.ui.text.TextRange(minOf(next.selection.start, kept.length))), true)
                } else {
                    clipped = false
                    apply(next, false)
                }
            },
            minLines = minLines,
            placeholder = placeholder?.let { { Text(it) } },
            supportingText = when {
                clipped -> { { Text(L("A note can hold up to %1\$s characters — the rest of what you pasted wasn't added.", com.example.timeapp.data.MAX_NOTE_CHARS)) } }
                value.text.length > com.example.timeapp.data.MAX_NOTE_CHARS * 9 / 10 -> { { Text(L("%1\$s of %2\$s characters", value.text.length, com.example.timeapp.data.MAX_NOTE_CHARS)) } }
                else -> null
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showLinkDialog) {
        LinkDialog(
            onDismiss = { showLinkDialog = false },
            onConfirm = { url ->
                showLinkDialog = false
                apply(RichTextEditing.insertLink(value, url), true)
            },
        )
    }
}

@Composable
private fun LinkDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("Add link")) },
        text = {
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text(L("URL")) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(url) }, enabled = url.isNotBlank()) { Text(L("Add")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancel")) } },
    )
}
