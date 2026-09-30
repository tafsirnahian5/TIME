package com.example.timeapp.ui.plan

import com.example.timeapp.data.L

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.SeriesScope
import com.example.timeapp.ui.components.ConfirmDeleteDialog
import com.example.timeapp.ui.components.SeriesScopeDialog
import com.example.timeapp.ui.stats.formatHm

/**
 * Deleting a task. When it has tracked time, a delete takes that time out of every report, so
 * the dialog says how much and offers **Archive** first — the PDF: "preserve historical time
 * with a title snapshot or archive the task". Only an explicit *Delete anyway* removes it, and
 * a repeating task then still asks which occurrences.
 */
@Composable
fun DeleteTaskDialog(
    entry: Entry,
    viewModel: PlanViewModel,
    onDone: () -> Unit,
    onDeleted: () -> Unit = {},
) {
    val tracked by remember(entry.id) { viewModel.trackedMs(entry.id) }.collectAsState(initial = null)
    val trackedMs = tracked ?: return
    // A running task's open segment isn't in the closed total yet, but it is time all the same.
    val hasTime = trackedMs > 0 || entry.status == EntryStatus.DOING
    var archiveDeclined by remember { mutableStateOf(false) }

    fun delete(scope: SeriesScope) {
        viewModel.deleteTask(entry.id, scope)
        onDone()
        onDeleted()
    }

    when {
        hasTime && !archiveDeclined -> AlertDialog(
            onDismissRequest = onDone,
            title = { Text(L("Archive %1\$s instead?", entry.title)) },
            text = {
                // Whole sentences: this was glued from fragments, which breaks in Bangla.
                Text(
                    if (trackedMs > 0) {
                        L("It has %1\$s of tracked time. Deleting it removes that time from your reports; archiving keeps the time and hides the task. You can find it again in Search.", formatHm(trackedMs))
                    } else {
                        L("Its timer is running. Deleting it removes that time from your reports; archiving keeps the time and hides the task. You can find it again in Search.")
                    },
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.archive(entry); onDone() }) { Text(L("Archive")) } },
            dismissButton = {
                Row {
                    TextButton(onClick = onDone) { Text(L("Cancel")) }
                    TextButton(onClick = {
                        if (entry.seriesId != null) archiveDeclined = true else delete(SeriesScope.ONLY_THIS)
                    }) { Text(L("Delete anyway")) }
                }
            },
        )
        entry.seriesId != null -> SeriesScopeDialog(
            title = L("Delete %1\$s?", entry.title),
            confirmLabel = L("Delete"),
            note = L("Finished or tracked occurrences are kept."),
            onConfirm = ::delete,
            onDismiss = onDone,
        )
        else -> ConfirmDeleteDialog(
            undoable = true,
            itemLabel = entry.title,
            onConfirm = { delete(SeriesScope.ONLY_THIS) },
            onDismiss = onDone,
        )
    }
}
