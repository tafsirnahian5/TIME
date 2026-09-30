package com.example.timeapp.ui.richtext

import com.example.timeapp.data.L

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.timeapp.ui.components.SegmentedToggle
import com.example.timeapp.ui.theme.Dimens

/**
 * The actual integration surface for every free-text field in the app: a Write/Preview
 * [SegmentedToggle] over [RichTextEditor]/[RichTextView]. Preview mode wires the renderer's
 * checklist-tap callback straight back through [onValueChange] via [toggleChecklistLine], so
 * tap-to-check works for free everywhere this is used — swapping an existing plain
 * `OutlinedTextField` for this is the whole integration for most call sites.
 */
@Composable
fun RichTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    minLines: Int = 3,
) {
    var mode by remember { mutableIntStateOf(0) } // 0 = Write, 1 = Preview
    // tfv only resyncs from `value` when they've diverged (an external change — e.g. a
    // checklist toggled from Preview, or the screen reset the field after Save) rather than on
    // every recomposition, so typing in the editor never loses cursor position.
    var tfv by remember { mutableStateOf(TextFieldValue(value)) }
    if (value != tfv.text) {
        tfv = TextFieldValue(value, selection = TextRange(value.length))
    }

    Column(modifier = modifier) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = Dimens.s6))
        }
        SegmentedToggle(
            options = listOf(L("Write"), L("Preview")),
            selectedIndex = mode,
            onSelect = { mode = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s10),
        )
        if (mode == 0) {
            RichTextEditor(
                value = tfv,
                onValueChange = { newValue ->
                    tfv = newValue
                    onValueChange(newValue.text)
                },
                minLines = minLines,
                placeholder = placeholder,
            )
        } else {
            RichTextView(
                raw = value,
                onToggleChecklistItem = { lineIndex -> onValueChange(toggleChecklistLine(value, lineIndex)) },
            )
        }
    }
}
