package com.example.timeapp.ui.components

import com.example.timeapp.data.L

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.example.timeapp.data.SeriesScope
import com.example.timeapp.ui.theme.Dimens

/**
 * Asks which occurrences of a repeating record an edit or delete applies to. Never guessed:
 * both answers change different data, so the person picks, with "only this" preselected as
 * the smaller change.
 *
 * [onlyThisEnabled] is false when the edit changes the repeat rule itself — one occurrence
 * cannot have a rule of its own, so the only honest answer is "this and future", and the
 * dialog says why instead of offering a choice that would silently drop the change.
 */
@Composable
fun SeriesScopeDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (SeriesScope) -> Unit,
    onDismiss: () -> Unit,
    onlyThisEnabled: Boolean = true,
    note: String? = null,
) {
    var scope by remember { mutableStateOf(if (onlyThisEnabled) SeriesScope.ONLY_THIS else SeriesScope.THIS_AND_FUTURE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                ScopeOption(L("This occurrence"), scope == SeriesScope.ONLY_THIS, enabled = onlyThisEnabled) { scope = SeriesScope.ONLY_THIS }
                ScopeOption(L("This and following"), scope == SeriesScope.THIS_AND_FUTURE) { scope = SeriesScope.THIS_AND_FUTURE }
                val caption = note ?: if (!onlyThisEnabled) L("The repeat rule changed, so this applies from here on.") else null
                caption?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Dimens.s8),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(scope) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancel")) } },
    )
}

@Composable
private fun ScopeOption(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.touchMin)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Dimens.s12),
        )
    }
}
