package com.example.timeapp.ui.components

import com.example.timeapp.data.L

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Shared confirmation before any destructive delete — never lose user data silently. */
@Composable
fun ConfirmDeleteDialog(
    itemLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    detail: String? = null,
    /** True where the delete offers Undo afterwards (tasks, bills, links, ledger rows). This
     * dialog said "This can't be undone" to all of them while an Undo appeared right after. */
    undoable: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("Delete %1\$s?", itemLabel)) },
        text = { Text(listOfNotNull(detail, if (undoable) L("You can undo it for a few seconds afterwards.") else L("This can't be undone.")).joinToString(" ")) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(L("Delete")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(L("Cancel")) }
        },
    )
}
