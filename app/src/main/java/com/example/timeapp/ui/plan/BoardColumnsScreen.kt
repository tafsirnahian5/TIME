package com.example.timeapp.ui.plan

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import com.example.timeapp.data.BoardColumn
import com.example.timeapp.data.BoardStatuses
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.canDeleteColumn
import com.example.timeapp.data.label
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/**
 * The Board's columns: the PDF's "custom columns, each mapped to a stable semantic state".
 * A column's state is fixed once made — it's what keeps timers and reports meaningful — so
 * editing changes the name and the soft limit only. A state's last column can't be deleted.
 * Not in the design canvas; token-built.
 */
@Composable
fun BoardColumnsScreen(onBack: () -> Unit, viewModel: PlanViewModel) {
    val colors = TimeThemeTokens.colors
    val columns by viewModel.boardColumns.collectAsState()
    var editing by remember { mutableStateOf<BoardColumn?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TimeTopBar(title = L("Board columns"), onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }, containerColor = colors.acc, shape = Shapes.lg) {
                Icon(Icons.Rounded.Add, contentDescription = L("New column"), tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.s16)) {
            item {
                Text(
                    L("Each column stands for one state — starting a timer still means Doing, finishing still means Done. A limit only warns when a column holds more than it; it never stops a move."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Dimens.s12),
                )
            }
            itemsIndexed(columns, key = { _, c -> c.id }) { index, column ->
                val deletable = canDeleteColumn(column, columns)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Dimens.s8)
                        .background(colors.card, Shapes.lg)
                        .clickable { editing = column }
                        .padding(start = Dimens.s16),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(vertical = Dimens.s12)) {
                        Text(column.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            column.status.label() + (column.wipLimit?.let { L(" · limit %1\$s", it) } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { viewModel.moveBoardColumn(columns, column, -1) }, enabled = index > 0) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = L("Move %1\$s left", column.name))
                    }
                    IconButton(onClick = { viewModel.moveBoardColumn(columns, column, 1) }, enabled = index < columns.lastIndex) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = L("Move %1\$s right", column.name))
                    }
                    IconButton(onClick = { viewModel.deleteBoardColumn(column.id) }, enabled = deletable) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = if (deletable) L("Delete %1\$s", column.name) else L("%1\$s is the only %2\$s column", column.name, column.status.label()),
                        )
                    }
                }
            }
            item {
                Text(
                    L("A state's only column can't be deleted. Cards in a deleted column move to the first column of their state."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimens.s8, bottom = Dimens.s34 * 2),
                )
            }
        }
    }

    editing?.let { column ->
        ColumnDialog(
            title = L("Edit column"),
            initial = column,
            statusEditable = false,
            onDismiss = { editing = null },
            onConfirm = { name, _, limit -> viewModel.updateBoardColumn(column.copy(name = name, wipLimit = limit)); editing = null },
        )
    }
    if (creating) {
        ColumnDialog(
            title = L("New column"),
            initial = null,
            statusEditable = true,
            onDismiss = { creating = false },
            onConfirm = { name, status, limit -> viewModel.addBoardColumn(name, status, limit); creating = false },
        )
    }
}

@Composable
private fun ColumnDialog(
    title: String,
    initial: BoardColumn?,
    statusEditable: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, EntryStatus, Int?) -> Unit,
) {
    val colors = TimeThemeTokens.colors
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var status by remember { mutableStateOf(initial?.status ?: EntryStatus.DOING) }
    var limit by remember { mutableStateOf(initial?.wipLimit?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(L("Name")) }, placeholder = { Text(L("Review")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(L("Counts as"), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Dimens.s16, bottom = Dimens.s8))
                if (statusEditable) {
                    FlowRow {
                        BoardStatuses.forEach { s ->
                            val selected = s == status
                            Text(
                                s.label(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(end = Dimens.s8, bottom = Dimens.s8)
                                    .background(if (selected) colors.accC else Color.Transparent, Shapes.pill)
                                    .then(if (selected) Modifier else Modifier.border(Dimens.hairline, TimeThemeTokens.status.controlOutline, Shapes.pill))
                                    .semantics { this.selected = selected }.clickable { status = s }
                                    .heightIn(min = Dimens.touchMin)
                                    .padding(horizontal = Dimens.s14, vertical = Dimens.s14),
                            )
                        }
                    }
                } else {
                    Text(L("%1\$s — fixed once a column is made.", status.label()), style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(
                    value = limit,
                    onValueChange = { limit = it.filter(Char::isDigit).take(3) },
                    label = { Text(L("Limit (optional)")) },
                    placeholder = { Text(L("No limit")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.s12),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, status, limit.toIntOrNull()?.takeIf { it > 0 }) }, enabled = name.isNotBlank()) {
                Text(if (initial == null) L("Create") else L("Save"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancel")) } },
    )
}
