package com.example.timeapp.ui.plan

import com.example.timeapp.data.L
import com.example.timeapp.data.whenLine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.timeapp.data.BoardSort
import com.example.timeapp.data.Entry
import com.example.timeapp.data.sortBoardColumn
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.ui.components.RootTopBar
import com.example.timeapp.ui.components.RootTopBarTrailing
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/** C1b — Plan · Board. Columns are the user's own (Settings-free: *Edit columns* here), each
 * standing for one EntryStatus; the default six are Inbox/Ready/Doing/Paused/Blocked/Done.
 * Cards move through each card's menu — always — and, since Phase 14, by **long-press and
 * drag** too (the PDF: drag is an enhancement, never the only way to move work). A **sort
 * mode** (Manual / Priority / Due date) orders each column without touching the manual order,
 * and saved filters sit in the scope row. Layout: the Phase 14–15 canvas. */
@Composable
fun PlanBoardScreen(
    onAdd: () -> Unit,
    onOpenList: () -> Unit,
    onOpenMonth: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: PlanViewModel,
    projects: List<com.example.timeapp.data.Category> = emptyList(),
    onEditColumns: () -> Unit = {},
    savedFilters: List<com.example.timeapp.data.SavedFilter> = emptyList(),
    onSavedFiltersChange: (List<com.example.timeapp.data.SavedFilter>) -> Unit = {},
    sort: BoardSort = BoardSort.MANUAL,
    onSort: (BoardSort) -> Unit = {},
) {
    val colors = TimeThemeTokens.colors
    val ui by viewModel.uiState.collectAsState()
    val tagNames by viewModel.tagNames.collectAsState()
    val columns by viewModel.boardColumns.collectAsState()
    // Drag state. Bounds are in root coordinates; the ghost card is drawn over everything in
    // the root Box, because a column's own scroll container would clip it at its edge.
    var drag by remember { mutableStateOf<BoardDrag?>(null) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    val columnBounds = remember { mutableStateMapOf<Long, Rect>() }
    val cardBounds = remember { mutableStateMapOf<Long, Rect>() }
    fun columnAt(point: Offset) = columns.firstOrNull { c -> columnBounds[c.id]?.let { point.x in it.left..it.right } == true }

    Scaffold(
        topBar = {
            RootTopBar(
                help = com.example.timeapp.ui.components.TabHelps.plan,
                title = L("Plan"), onSearch = onSearch,
                onOpenSettings = onOpenSettings,
                trailing = RootTopBarTrailing.SETTINGS,
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).onGloballyPositioned { rootOrigin = it.boundsInRoot().topLeft }) {
            Column(modifier = Modifier.fillMaxSize()) {
                PlanBoardCalendarToggle(selectedIndex = 1, onOpenMonth = onOpenMonth, onOpenList = onOpenList)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Dimens.screenHList, vertical = Dimens.s4)) {
                    SortButton(sort, onSort)
                    Text(
                        if (sort == BoardSort.MANUAL) L("Hold a card to drag it.") else L("Manual order is kept — switch back any time."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(start = Dimens.s8),
                    )
                    androidx.compose.material3.TextButton(onClick = onEditColumns) { Text(L("Edit columns")) }
                }
                PlanScopeRow(projects, tagNames, ui.scope, viewModel::setScope, savedFilters = savedFilters, onSavedFiltersChange = onSavedFiltersChange)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Dimens.screenHList, vertical = Dimens.s4),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.s10),
                ) {
                    columns.forEach { column ->
                        // The whole column in its stored order, so a reorder while filtered
                        // never loses the position of cards the filter is hiding.
                        val fullColumn = ui.entries
                            .filter { com.example.timeapp.data.boardColumnFor(it, columns)?.id == column.id }
                            .sortedWith(compareBy<Entry>({ it.boardOrder }, { it.id }))
                        val visibleIds = ui.scopedEntries.map { it.id }.toSet()
                        // A sort mode is only a view: the manual order (fullColumn) is untouched.
                        val items = sortBoardColumn(fullColumn.filter { it.id in visibleIds }, sort)
                        val dropHere = drag?.let { d -> columnAt(d.pointer)?.id == column.id } == true
                        BoardColumnView(
                            column, items, fullColumn.size, columns,
                            manual = sort == BoardSort.MANUAL,
                            dropTarget = dropHere,
                            draggingId = drag?.entry?.id,
                            onColumnBounds = { columnBounds[column.id] = it },
                            onCardBounds = { id, r -> cardBounds[id] = r },
                            onDragStart = { entry, local ->
                                val r = cardBounds[entry.id] ?: return@BoardColumnView
                                com.example.timeapp.data.Haptics.perform(com.example.timeapp.data.HapticEvent.PICK_UP)
                                drag = BoardDrag(entry, column.id, grab = local, size = r.size, pointer = r.topLeft + local)
                            },
                            onDrag = { delta -> drag = drag?.let { it.copy(pointer = it.pointer + delta) } },
                            onDragEnd = {
                                val d = drag
                                drag = null
                                if (d != null) {
                                    com.example.timeapp.data.Haptics.perform(com.example.timeapp.data.HapticEvent.TICK)
                                    val target = columnAt(d.pointer)
                                    if (target != null && target.id != d.fromColumnId) {
                                        viewModel.moveTo(d.entry, target.status, target.id)
                                    } else if (target != null && sort == BoardSort.MANUAL) {
                                        // Same column: the drop point between visible cards
                                        // sets its place, mapped onto the full manual order.
                                        val others = items.filter { it.id != d.entry.id }
                                        val before = others.count { o -> (cardBounds[o.id]?.center?.y ?: Float.MAX_VALUE) < d.pointer.y }
                                        val anchor = others.getOrNull(before)
                                        val newIndex = if (anchor == null) fullColumn.size - 1 else fullColumn.filter { it.id != d.entry.id }.indexOf(anchor)
                                        viewModel.reorder(fullColumn, d.entry, newIndex)
                                    }
                                }
                            },
                            onDragCancel = { drag = null },
                            onMoveTo = { entry, target -> viewModel.moveTo(entry, target.status, target.id) },
                            onReorder = { entry, offset ->
                                // Offsets are relative to what's visible; map to the full column.
                                val visibleIndex = items.indexOf(entry)
                                val targetVisible = if (offset == Int.MIN_VALUE) 0 else visibleIndex + offset
                                val anchor = items.getOrNull(targetVisible) ?: return@BoardColumnView
                                viewModel.reorder(fullColumn, entry, fullColumn.indexOf(anchor))
                            },
                            onOpenTask = onOpenTask,
                            waitingCount = { ui.unfinishedPrerequisites(it).size },
                            onArchive = viewModel::archive,
                            onArchiveDone = viewModel::archiveDone,
                        )
                    }
                }
            }
            drag?.let { d ->
                val density = LocalDensity.current
                Box(
                    modifier = Modifier
                        .offset { IntOffset((d.pointer.x - d.grab.x - rootOrigin.x).roundToInt(), (d.pointer.y - d.grab.y - rootOrigin.y).roundToInt()) }
                        .size(with(density) { d.size.width.toDp() }, with(density) { d.size.height.toDp() })
                        .zIndex(10f)
                        .graphicsLayer { rotationZ = -1.5f; shadowElevation = 16f }
                        .background(TimeThemeTokens.colors.card, Shapes.lg)
                        .border(2.dp, colors.acc, Shapes.lg)
                        .padding(Dimens.s12),
                ) {
                    Text(d.entry.title, style = MaterialTheme.typography.bodyLarge)
                }
            }
            // The same button as the List view's: this was a Row painted accent with accInk text —
            // pale-on-pale in dark mode, so it looked disabled, and it had no button role.
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = colors.accC,
                contentColor = colors.accInk,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(L("Task")) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = Dimens.s16, bottom = Dimens.s16),
            )
        }
    }
}

/** A card being dragged: where it came from, where on it the finger is, and the finger now. */
private data class BoardDrag(
    val entry: Entry,
    val fromColumnId: Long,
    val grab: Offset,
    val size: androidx.compose.ui.geometry.Size,
    val pointer: Offset,
)

/** The Board's sort mode — a menu so the three choices and the current one are all named. */
@Composable
private fun SortButton(sort: BoardSort, onSort: (BoardSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        androidx.compose.material3.OutlinedButton(onClick = { open = true }, shape = Shapes.md) {
            Text(L("Sort: %1\$s", sort.label), style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            BoardSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(if (option == sort) "✓ ${option.label}" else option.label) },
                    onClick = { open = false; onSort(option) },
                )
            }
        }
    }
}

@Composable
private fun BoardColumnView(
    column: com.example.timeapp.data.BoardColumn,
    items: List<Entry>,
    totalCount: Int,
    allColumns: List<com.example.timeapp.data.BoardColumn>,
    onMoveTo: (Entry, com.example.timeapp.data.BoardColumn) -> Unit,
    onReorder: (Entry, Int) -> Unit,
    onOpenTask: (Long) -> Unit,
    waitingCount: (Entry) -> Int = { 0 },
    onArchive: (Entry) -> Unit = {},
    onArchiveDone: () -> Unit = {},
    manual: Boolean = true,
    dropTarget: Boolean = false,
    draggingId: Long? = null,
    onColumnBounds: (Rect) -> Unit = {},
    onCardBounds: (Long, Rect) -> Unit = { _, _ -> },
    onDragStart: (Entry, Offset) -> Unit = { _, _ -> },
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
) {
    val colors = TimeThemeTokens.colors
    // Each column scrolls on its own: a busy column used to clip its lower cards, unreachable.
    Column(
        modifier = Modifier.width(158.dp).fillMaxSize()
            .onGloballyPositioned { onColumnBounds(it.boundsInRoot()) }
            .then(if (dropTarget) Modifier.border(2.dp, colors.acc, Shapes.lg).padding(Dimens.s4) else Modifier)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.s8),
    ) {
        // The soft WIP limit: over it, the count turns to a warning — it never blocks a move.
        val over = column.wipLimit != null && totalCount > column.wipLimit
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Dimens.s2)) {
            Text(column.name, style = MaterialTheme.typography.titleSmall)
            Text(
                if (column.wipLimit != null) "$totalCount / ${column.wipLimit}" else "$totalCount",
                style = MaterialTheme.typography.labelMedium,
                color = if (over) TimeThemeTokens.status.warnInk else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Dimens.s6)
                    .background(if (over) TimeThemeTokens.status.warn else colors.ring, Shapes.pill)
                    .padding(horizontal = Dimens.s8, vertical = Dimens.s2 / 2),
            )
        }
        if (over) {
            Text(
                L("Over its limit of %1\$s", column.wipLimit),
                style = MaterialTheme.typography.bodySmall,
                color = TimeThemeTokens.status.warnInk,
            )
        }
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, colors.edge2, Shapes.lg)
                    .padding(vertical = Dimens.s20, horizontal = Dimens.s12),
            ) {
                Text(L("Nothing here"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items.forEachIndexed { index, entry ->
            BoardCard(
                entry, index, items.size, column, allColumns, onMoveTo, onReorder, onOpenTask,
                waitingOn = waitingCount(entry), onArchive = onArchive, manual = manual,
                modifier = Modifier
                    .onGloballyPositioned { onCardBounds(entry.id, it.boundsInRoot()) }
                    .graphicsLayer { alpha = if (entry.id == draggingId) 0.35f else 1f }
                    .pointerInput(entry.id) {
                        // `local` is where the finger is on the card; the screen turns it into
                        // root coordinates with the card's recorded bounds.
                        detectDragGesturesAfterLongPress(
                            onDragStart = { local -> onDragStart(entry, local) },
                            onDrag = { change, amount -> change.consume(); onDrag(amount) },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel,
                        )
                    },
            )
        }
        // A finished column only grows; this clears it without losing any tracked time.
        if (column.status == EntryStatus.DONE && items.isNotEmpty()) {
            androidx.compose.material3.TextButton(onClick = onArchiveDone, modifier = Modifier.fillMaxWidth()) {
                Text(L("Archive done"))
            }
        }
        // Clear of the FAB at the bottom of the screen.
        Box(Modifier.padding(bottom = Dimens.s34 * 2))
    }
}

@Composable
private fun BoardCard(
    entry: Entry,
    index: Int,
    count: Int,
    current: com.example.timeapp.data.BoardColumn,
    allColumns: List<com.example.timeapp.data.BoardColumn>,
    onMoveTo: (Entry, com.example.timeapp.data.BoardColumn) -> Unit,
    onReorder: (Entry, Int) -> Unit,
    onOpenTask: (Long) -> Unit,
    waitingOn: Int = 0,
    onArchive: (Entry) -> Unit = {},
    manual: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = TimeThemeTokens.colors
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TimeThemeTokens.colors.card, Shapes.lg)
            .border(1.dp, colors.line, Shapes.lg)
            .clickable { onOpenTask(entry.id) }
            .padding(Dimens.s12),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                entry.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                color = if (entry.status == EntryStatus.DONE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (entry.status == EntryStatus.DONE) androidx.compose.ui.text.style.TextDecoration.LineThrough else androidx.compose.ui.text.style.TextDecoration.None,
            )
            Box {
                // Full 48dp target (rule 6) around the small glyph; it was a 20dp button.
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = L("Move"), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    // Reordering within the column — the accessible alternative to dragging. Only
                    // in Manual: under a sort, "up" would mean nothing the user can see.
                    if (manual && index > 0) {
                        if (index > 1) DropdownMenuItem(text = { Text(L("Move to top")) }, onClick = { menuExpanded = false; onReorder(entry, Int.MIN_VALUE) })
                        DropdownMenuItem(text = { Text(L("Move up")) }, onClick = { menuExpanded = false; onReorder(entry, -1) })
                    }
                    if (manual && index < count - 1) {
                        DropdownMenuItem(text = { Text(L("Move down")) }, onClick = { menuExpanded = false; onReorder(entry, 1) })
                    }
                    allColumns.filter { it.id != current.id }.forEach { column ->
                        DropdownMenuItem(
                            text = { Text(L("Move to %1\$s", column.name)) },
                            onClick = { menuExpanded = false; onMoveTo(entry, column) },
                        )
                    }
                    DropdownMenuItem(text = { Text(L("Archive")) }, onClick = { menuExpanded = false; onArchive(entry) })
                }
            }
        }
        if (entry.status == EntryStatus.DOING) {
            Box(modifier = Modifier.padding(top = Dimens.s8)) {
                Text(
                    L("Tracking"),
                    style = MaterialTheme.typography.labelMedium,
                    color = TimeThemeTokens.status.okInk,
                    modifier = Modifier.background(TimeThemeTokens.status.ok, Shapes.pill).padding(horizontal = Dimens.s10, vertical = Dimens.s2),
                )
            }
        } else {
            if (waitingOn > 0 && entry.status != EntryStatus.DONE) {
                Text(
                    L("Waiting on %1\$s", waitingOn),
                    style = MaterialTheme.typography.labelMedium,
                    color = TimeThemeTokens.status.neutralInk,
                    modifier = Modifier.padding(top = Dimens.s8).background(TimeThemeTokens.status.neutral, Shapes.pill).padding(horizontal = Dimens.s10, vertical = Dimens.s2),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Dimens.s8)) {
                val whenLine = entry.whenLine()
                entry.category?.let {
                    com.example.timeapp.ui.components.CategoryMark(it.color, it.icon)
                    Text(" ${whenLine ?: it.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } ?: whenLine?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
