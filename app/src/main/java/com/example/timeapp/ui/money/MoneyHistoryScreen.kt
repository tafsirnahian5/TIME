package com.example.timeapp.ui.money

import com.example.timeapp.data.L

import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.example.timeapp.ui.components.FilterDropdownChip
import com.example.timeapp.data.totalEffect
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.example.timeapp.data.Direction
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.toTaka
import com.example.timeapp.ui.richtext.RichTextView
import com.example.timeapp.ui.components.Illustration
import com.example.timeapp.ui.components.EmptyState
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.YearMonth
import java.time.ZoneId
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.example.timeapp.ui.components.ConfirmDeleteDialog

/** E2 — Money · History: settled transactions, reverse chronological, day-grouped. */
@Composable
fun MoneyHistoryScreen(
    onOpenOverview: () -> Unit,
    onOpenPlanned: () -> Unit,
    onEditRecord: (Long) -> Unit,
    viewModel: MoneyViewModel,
    onRefund: (Long) -> Unit = {},
    categories: List<com.example.timeapp.data.Category> = emptyList(),
    onOpenCalendar: () -> Unit = {},
) {
    val colors = TimeThemeTokens.colors
    val ui by viewModel.uiState.collectAsState()
    var displayedMonth by remember { mutableStateOf(YearMonth.now()) }
    var filter by remember { mutableStateOf(LedgerFilter()) }
    // "All time" is the PDF's date-range search: looking for last year's receipt shouldn't
    // mean paging back month by month.
    var allTime by remember { mutableStateOf(false) }
    val receipts by viewModel.attachments.collectAsState()
    val query = filter.query
    val inRange = if (allTime) ui.history.filter { it.settledAt != null } else ui.history.filter {
        it.settledAt != null && YearMonth.from(it.settledAt.atZone(ZoneId.systemDefault())) == displayedMonth
    }
    val filtered = inRange.applyLedgerFilter(filter)
    val refundedByOriginal = ui.history.filter { it.direction == Direction.REFUND && it.refundOfId != null }
        .groupBy({ it.refundOfId!! }, { it.paidMinor })
        .mapValues { it.value.sum() }
    val grouped = filtered.sortedByDescending { it.settledAt }
        .groupBy { it.settledAt!!.atZone(ZoneId.systemDefault()).toLocalDate() }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.s12).heightIn(min = Dimens.appBarH),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.example.timeapp.ui.components.TabTitle(L("Money"), com.example.timeapp.ui.components.TabHelps.money, Modifier.weight(1f))
            }
        },
    ) { padding ->
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            MoneyTabs(selectedIndex = 2, onOpenOverview = onOpenOverview, onOpenPlanned = onOpenPlanned)
            androidx.compose.material3.TextButton(onClick = onOpenCalendar, modifier = Modifier.padding(horizontal = Dimens.s8)) { Text(L("Calendar view")) }
            OutlinedTextField(
                value = query,
                onValueChange = { filter = filter.copy(query = it) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text(L("Search note, payee or amount")) },
                singleLine = true,
                shape = Shapes.pill,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.s16, vertical = Dimens.s12),
            )
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Dimens.s16)) {
                FilterDropdownChip(
                    label = when (filter.type) {
                        Direction.COST -> L("Expenses")
                        Direction.INCOME -> L("Income")
                        Direction.TRANSFER -> L("Transfers")
                        Direction.REFUND -> L("Refunds")
                        Direction.LOAN_OUT, Direction.LOAN_IN -> L("Loans")
                        null -> L("Any type")
                    },
                    active = filter.type != null,
                    options = listOf<Pair<String, Direction?>>(
                        L("Any type") to null, L("Expenses") to Direction.COST, L("Income") to Direction.INCOME,
                        L("Transfers") to Direction.TRANSFER, L("Refunds") to Direction.REFUND,
                    ),
                    onPick = { filter = filter.copy(type = it) },
                )
                FilterDropdownChip(
                    label = ui.wallets.firstOrNull { it.id == filter.walletId }?.name ?: L("Any wallet"),
                    active = filter.walletId != null,
                    options = listOf<Pair<String, Long?>>(L("Any wallet") to null) + ui.wallets.map { it.name to it.id },
                    onPick = { filter = filter.copy(walletId = it) },
                )
                if (categories.isNotEmpty()) {
                    FilterDropdownChip(
                        label = categories.firstOrNull { it.id == filter.categoryId }?.name ?: L("Any category"),
                        active = filter.categoryId != null,
                        options = listOf<Pair<String, Long?>>(L("Any category") to null) + categories.map { it.name to it.id },
                        onPick = { filter = filter.copy(categoryId = it) },
                    )
                }
                FilterDropdownChip(
                    label = if (allTime) L("All time") else L("One month"),
                    active = allTime,
                    options = listOf(L("One month") to false, L("All time") to true),
                    onPick = { allTime = it },
                )
            }
            if (!allTime) Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.s16, vertical = Dimens.s6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { displayedMonth = displayedMonth.minusMonths(1) }) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = L("Previous month"))
                }
                val suffix = if (displayedMonth == YearMonth.now()) L(" · To date") else ""
                Text(
                    displayedMonth.format(displayPattern("MMMM yyyy")) + suffix,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { displayedMonth = displayedMonth.plusMonths(1) }) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = L("Next month"))
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.s16)) {
                if (grouped.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                            illustration = if (query.isBlank() && !filter.narrowsBeyondQuery) Illustration.MONEY else null,
                            headline = if (query.isBlank() && !filter.narrowsBeyondQuery) {
                                if (allTime) L("Nothing recorded yet") else L("Nothing recorded this month")
                            } else L("No matches"),
                            supporting = if (query.isBlank() && !filter.narrowsBeyondQuery) {
                                L("Money you record as spent or received shows up here, newest first.")
                            } else {
                                L("No recorded transaction %1\$smatches these filters.", (if (allTime) "" else L("in this month ")))
                            },
                            actionLabel = if (filter != LedgerFilter()) L("Clear filters") else null,
                            onAction = if (filter != LedgerFilter()) ({ filter = LedgerFilter() }) else null,
                            modifier = Modifier.padding(vertical = Dimens.s24),
                        )
                    }
                }
                grouped.forEach { (date, records) ->
                    item {
                        val net = records.sumOf { it.totalEffect }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s10, bottom = Dimens.s4)) {
                            Text(
                                date.format(displayPattern("EEE d MMM")).uppercase(),
                                style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f),
                            )
                            Text((if (net >= 0) "+" else "") + net.toTaka(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    items(records) { record ->
                        HistoryRow(
                            record,
                            ui.wallets,
                            receipts = receipts[record.id].orEmpty(),
                            categories = categories,
                            refundedMinor = refundedByOriginal[record.id] ?: 0L,
                            onEdit = { onEditRecord(record.id) },
                            onDelete = { viewModel.deleteHistoryRecord(record.id) },
                            onRefund = { onRefund(record.id) },
                        )
                    }
                }
                item {
                    Row(modifier = Modifier.padding(vertical = Dimens.s14)) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                        Text(
                            L("Transfers and opening balances are excluded from income and spending reports. Refunds count as less spending, not income."),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = Dimens.s8),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    record: MoneyRecord,
    wallets: List<com.example.timeapp.data.Wallet>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    categories: List<com.example.timeapp.data.Category> = emptyList(),
    refundedMinor: Long = 0L,
    onRefund: () -> Unit = {},
    receipts: List<com.example.timeapp.data.db.AttachmentEntity> = emptyList(),
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var showingReceipts by remember { mutableStateOf(false) }
    // Tokens, not hex: the transfer pair used to be two light-mode literals, a bright lavender
    // disc on a dark page. (The old title == "Lunch"/"Groceries" icon picks are gone too —
    // they only ever matched the demo data.)
    val (icon, bg, fg) = when (record.direction) {
        Direction.TRANSFER -> Triple(Icons.Rounded.SwapHoriz, TimeThemeTokens.colors.accC, TimeThemeTokens.colors.accInk)
        Direction.INCOME -> Triple(Icons.Rounded.ArrowDownward, TimeThemeTokens.status.ok, TimeThemeTokens.status.okInk)
        Direction.REFUND -> Triple(Icons.AutoMirrored.Rounded.Undo, TimeThemeTokens.status.ok, TimeThemeTokens.status.okInk)
        Direction.COST -> Triple(
            com.example.timeapp.ui.components.CategoryIcons.of(categories.firstOrNull { it.id == record.categoryId }?.icon) ?: Icons.Rounded.Receipt,
            TimeThemeTokens.status.warn, TimeThemeTokens.status.warnInk,
        )
        Direction.LOAN_OUT, Direction.LOAN_IN -> Triple(Icons.Rounded.Handshake, TimeThemeTokens.status.neutral, TimeThemeTokens.status.neutralInk)
    }
    val categoryName = categories.firstOrNull { it.id == record.categoryId }?.name
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(38.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = fg)
        }
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f).padding(start = Dimens.s14)) {
            Text(record.title, style = MaterialTheme.typography.bodyLarge)
            // One Text: as two Texts in a Row the second wrapped inside its own narrow column.
            Text(
                listOfNotNull(
                    when (record.direction) {
                        Direction.TRANSFER -> L("Transfer · not income or spending")
                        Direction.INCOME -> L("Income · %1\$s", (walletName(record.walletId, wallets)))
                        Direction.REFUND -> L("Refund · back into %1\$s", (walletName(record.walletId, wallets)))
                        // Used to repeat the title here; the category is what the title lacks.
                        Direction.COST -> listOfNotNull(categoryName, walletName(record.walletId, wallets)).joinToString(" · ")
                        Direction.LOAN_OUT, Direction.LOAN_IN -> L("Loan · not income or spending")
                    },
                    // A refund carries the bill link too, but it wasn't "paid toward" it.
                    L("paid toward a bill").takeIf { record.linkedToPlan && record.direction != Direction.REFUND },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
            // Read-only: a settled ledger row has no update path anywhere else in the app —
            // this deliberately doesn't add one just for notes.
            if (refundedMinor > 0) {
                Text(
                    if (refundedMinor >= record.paidMinor) L("Refunded in full") else L("%1\$s refunded", refundedMinor.toTaka()),
                    style = MaterialTheme.typography.bodySmall,
                    color = TimeThemeTokens.status.okInk,
                )
            }
            if (!record.note.isNullOrBlank()) {
                RichTextView(raw = record.note, modifier = Modifier.padding(top = Dimens.s4))
            }
        }
        val sign = when (record.direction) {
            Direction.INCOME, Direction.REFUND, Direction.LOAN_IN -> "+"
            Direction.TRANSFER -> ""
            Direction.COST, Direction.LOAN_OUT -> "-"
        }
        val incoming = record.direction == Direction.INCOME || record.direction == Direction.REFUND
        Text(
            "$sign${record.paidMinor.toTaka()}",
            style = MaterialTheme.typography.bodyLarge,
            color = if (incoming) TimeThemeTokens.status.okInk else MaterialTheme.colorScheme.onSurface,
        )
        Box {
            // Full 48dp target (rule 6); it was a 28dp button.
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = L("More"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                // A transfer moves money between two wallets, so editing one needs a different
                // form than this screen offers — it can be deleted and re-entered instead.
                // Refunds too: editing one here would turn it into a plain income/expense.
                // Loan rows belong to their loan (Money › Loans), like refunds to their expense.
                if (record.direction == Direction.COST || record.direction == Direction.INCOME) {
                    DropdownMenuItem(text = { Text(L("Edit")) }, onClick = { menuExpanded = false; onEdit() })
                }
                if (record.direction == Direction.COST && refundedMinor < record.paidMinor) {
                    DropdownMenuItem(text = { Text(L("Refund")) }, onClick = { menuExpanded = false; onRefund() })
                }
                if (record.direction == Direction.COST || record.direction == Direction.INCOME || record.direction == Direction.REFUND) {
                    DropdownMenuItem(
                        text = { Text(if (receipts.isEmpty()) L("Receipts") else L("Receipts (%1\$s)", receipts.size)) },
                        onClick = { menuExpanded = false; showingReceipts = true },
                    )
                }
                DropdownMenuItem(text = { Text(L("Delete")) }, onClick = { menuExpanded = false; confirmingDelete = true })
            }
        }
    }

    if (confirmingDelete) {
        ConfirmDeleteDialog(
            undoable = true,
            itemLabel = record.title,
            onConfirm = { confirmingDelete = false; onDelete() },
            onDismiss = { confirmingDelete = false },
            detail = listOfNotNull(
                if (refundedMinor > 0) L("Its refunds are deleted with it.") else null,
                if (receipts.isNotEmpty()) L("Its receipts go too.") else null,
            ).joinToString(" ").ifEmpty { null },
        )
    }
    if (showingReceipts) ReceiptsDialog(record.id, record.title, receipts, onDismiss = { showingReceipts = false })
}

private fun walletName(id: Long?, wallets: List<com.example.timeapp.data.Wallet>): String =
    wallets.firstOrNull { it.id == id }?.name ?: ""
