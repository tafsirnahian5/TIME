package com.example.timeapp.ui.money

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

import com.example.timeapp.data.displayPattern

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.example.timeapp.data.Wallet
import com.example.timeapp.data.isPartialTakaInput
import com.example.timeapp.data.parseTakaToMinor
import com.example.timeapp.data.toTaka
import com.example.timeapp.ui.components.DatePickerPrompt
import com.example.timeapp.ui.components.Illustration
import com.example.timeapp.ui.components.EmptyState
import com.example.timeapp.ui.components.SegmentedToggle
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.LocalDate

private val dayFmt get() = displayPattern("d MMM")

/**
 * Money › Loans & dues (Phase 13, after XPENC and MoneyWallet): who owes you and whom you owe,
 * each with its running balance. The money moves as its own ledger rows — never income or
 * spending. Token-built; no design canvas covers it.
 */
@Composable
fun LoansScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, viewModel: LoansViewModel, wallets: List<Wallet>) {
    val ui by viewModel.uiState.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var showClosed by remember { mutableStateOf(false) }
    val colors = TimeThemeTokens.colors
    Scaffold(
        topBar = { TimeTopBar(title = L("Loans & dues"), onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true }, containerColor = colors.acc, contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = Shapes.lg, icon = { Icon(Icons.Rounded.Add, contentDescription = null) }, text = { Text(L("Loan")) },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.s16)) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s12), horizontalArrangement = Arrangement.spacedBy(Dimens.cardGap)) {
                    Summary(L("Owed to you"), ui.owedToYouMinor.toTaka(), Modifier.weight(1f))
                    Summary(L("You owe"), ui.youOweMinor.toTaka(), Modifier.weight(1f))
                }
                Text(
                    L("Lending, borrowing and repayments move your wallets but never count as income or spending."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Dimens.s8),
                )
            }
            if (ui.open.isEmpty() && ui.closed.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Handshake,
                        illustration = Illustration.MONEY,
                        headline = L("No loans"),
                        supporting = L("Lent someone money, or borrowed some? Keep it here with every repayment, so nobody has to remember."),
                        actionLabel = L("Add a loan"),
                        onAction = { adding = true },
                        modifier = Modifier.padding(vertical = Dimens.s24),
                    )
                }
            }
            listOf(L("OWED TO YOU") to ui.open.filter { it.loan.lent }, L("YOU OWE") to ui.open.filterNot { it.loan.lent }).forEach { (label, rows) ->
                if (rows.isNotEmpty()) {
                    item { Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = Dimens.s12, bottom = Dimens.s6)) }
                    items(rows, key = { it.loan.id }) { LoanRowView(it) { onOpen(it.loan.id) } }
                }
            }
            if (ui.closed.isNotEmpty()) {
                item {
                    TextButton(onClick = { showClosed = !showClosed }) { Text(if (showClosed) L("Hide closed") else L("Closed (%1\$s)", ui.closed.size)) }
                }
                if (showClosed) items(ui.closed, key = { it.loan.id }) { LoanRowView(it) { onOpen(it.loan.id) } }
            }
            item { Box(Modifier.padding(bottom = Dimens.s34 * 2)) }
        }
    }
    if (adding) {
        NewLoanDialog(
            wallets = wallets,
            onDismiss = { adding = false },
            onSave = { person, lent, amount, wallet, due, note, remind ->
                viewModel.create(person, lent, amount, wallet, due, note, remind)
                adding = false
            },
        )
    }
}

@Composable
private fun Summary(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(TimeThemeTokens.colors.accS, Shapes.lg).padding(Dimens.s14)) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Dimens.s4))
    }
}

@Composable
private fun LoanRowView(row: LoanRow, onClick: () -> Unit) {
    val today = LocalDate.now()
    val overdue = row.loan.isOpen && row.loan.dueOn?.isBefore(today) == true && row.balance.outstandingMinor > 0
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s8).background(TimeThemeTokens.colors.card, Shapes.lg)
            .border(Dimens.hairline, TimeThemeTokens.colors.line, Shapes.lg).clickable(onClick = onClick).padding(Dimens.s14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.loan.person, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(
                    (if (row.loan.lent) L("lent ") else L("borrowed ")) + row.loan.startedOn.format(dayFmt),
                    row.loan.dueOn?.let { (if (overdue) L("was due ") else L("due ")) + it.format(dayFmt) },
                    if (!row.loan.isOpen) "closed" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (overdue) TimeThemeTokens.status.dangerInk else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(row.balance.outstandingMinor.toTaka(), style = MaterialTheme.typography.titleSmall)
    }
}

/** Wallet chips shared by the loan dialogs. */
@Composable
internal fun WalletChips(wallets: List<Wallet>, selected: Long?, onSelect: (Long) -> Unit) {
    val colors = TimeThemeTokens.colors
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Dimens.s8)) {
        wallets.forEach { w ->
            val on = w.id == selected
            Text(
                w.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (on) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.background(if (on) colors.accC else Color.Transparent, Shapes.pill)
                    .border(Dimens.hairline, TimeThemeTokens.status.controlOutline, Shapes.pill)
                    .semantics { this.selected = on }.clickable { onSelect(w.id) }.heightIn(min = Dimens.touchMin).padding(horizontal = Dimens.s14, vertical = Dimens.s14),
            )
        }
    }
}

@Composable
private fun NewLoanDialog(
    wallets: List<Wallet>,
    onDismiss: () -> Unit,
    onSave: (String, Boolean, Long, Long, LocalDate?, String?, Boolean) -> Unit,
) {
    var lent by remember { mutableStateOf(true) }
    var person by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var walletId by remember { mutableStateOf(wallets.firstOrNull()?.id) }
    var due by remember { mutableStateOf<LocalDate?>(null) }
    var note by remember { mutableStateOf("") }
    var remind by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val minor = parseTakaToMinor(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("New loan")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SegmentedToggle(options = listOf(L("I lent"), L("I borrowed")), selectedIndex = if (lent) 0 else 1, onSelect = { lent = it == 0 })
                OutlinedTextField(person, { person = it.take(40) }, label = { Text(if (lent) L("To whom") else L("From whom")) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8))
                OutlinedTextField(amount, { amount = it.takeIf(::isPartialTakaInput) ?: amount }, label = { Text(L("Amount (Tk)")) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                Text(if (lent) L("Paid out of") else L("Received into"), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Dimens.s12, bottom = Dimens.s6))
                if (wallets.isEmpty()) Text(L("Add a wallet first (Money › Overview)."), style = MaterialTheme.typography.bodySmall)
                WalletChips(wallets, walletId) { walletId = it }
                TextButton(onClick = { picking = true }) { Text(due?.let { L("Due ") + it.format(dayFmt) } ?: L("Set a due date (optional)")) }
                if (due != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(L("Remind me on the day"), modifier = Modifier.weight(1f))
                        Switch(checked = remind, onCheckedChange = { remind = it })
                    }
                }
                OutlinedTextField(note, { note = it.take(200) }, label = { Text(L("Note")) }, modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8))
            }
        },
        confirmButton = {
            TextButton(
                enabled = person.isNotBlank() && minor != null && minor > 0 && walletId != null,
                onClick = { onSave(person, lent, minor!!, walletId!!, due, note, remind && due != null) },
            ) { Text(L("Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancel")) } },
    )
    if (picking) DatePickerPrompt(due ?: LocalDate.now().plusWeeks(2)) { d -> picking = false; d?.let { due = it } }
}
