package com.example.timeapp.ui.money

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.timeapp.data.Direction
import com.example.timeapp.data.Wallet
import com.example.timeapp.data.isPartialTakaInput
import com.example.timeapp.data.parseTakaToMinor
import com.example.timeapp.data.toTaka
import com.example.timeapp.data.toTakaInput
import com.example.timeapp.ui.components.DatePickerPrompt
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.ZoneId

/** One loan: where it stands, every movement, and the next step (repay, add more, close). */
@Composable
fun LoanDetailScreen(loanId: Long, onBack: () -> Unit, viewModel: LoansViewModel, wallets: List<Wallet>) {
    val ui by viewModel.uiState.collectAsState()
    val reminded by viewModel.remindedIds.collectAsState()
    val row = ui.find(loanId) ?: return
    val loan = row.loan
    val bal = row.balance
    var menu by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<Boolean?>(null) } // true = repayment, false = more
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val fmt = displayPattern("d MMM yyyy")
    Scaffold(
        topBar = {
            TimeTopBar(title = loan.person, onBack = onBack) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = L("More")) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(L("Edit details")) }, onClick = { menu = false; editing = true })
                        DropdownMenuItem(text = { Text(if (loan.isOpen) L("Close") else L("Reopen")) }, onClick = { menu = false; viewModel.setClosed(loan, loan.isOpen) })
                        DropdownMenuItem(text = { Text(L("Delete")) }, onClick = { menu = false; deleting = true })
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.s16)) {
            Column(Modifier.fillMaxWidth().padding(top = Dimens.s8).background(TimeThemeTokens.colors.accC2, Shapes.xl).padding(Dimens.s20)) {
                Text(if (loan.lent) L("%1\$s owes you", loan.person) else L("You owe %1\$s", loan.person), style = MaterialTheme.typography.bodyMedium)
                Text(bal.outstandingMinor.toTaka(), style = MaterialTheme.typography.displaySmall)
                Text(
                    L("%1\$s %2\$s · %3\$s repaid", bal.principalMinor.toTaka(), (if (loan.lent) "lent" else "borrowed"), bal.repaidMinor.toTaka()) +
                        (loan.dueOn?.let { L(" · due ") + it.format(fmt) } ?: "") +
                        (if (bal.overpaidMinor > 0) L(" · %1\$s over", bal.overpaidMinor.toTaka()) else ""),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!loan.isOpen) Text(L("Closed — its history is kept."), style = MaterialTheme.typography.bodySmall)
                loan.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Dimens.s6)) }
            }
            if (loan.isOpen) {
                Row(Modifier.fillMaxWidth().padding(vertical = Dimens.s16), horizontalArrangement = Arrangement.spacedBy(Dimens.s10)) {
                    Button(onClick = { moving = true }, enabled = bal.outstandingMinor > 0, modifier = Modifier.weight(1f)) { Text(L("Record repayment")) }
                    OutlinedButton(onClick = { moving = false }, modifier = Modifier.weight(1f)) { Text(if (loan.lent) L("Lend more") else L("Borrow more")) }
                }
            }
            Text(L("MOVEMENTS"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(top = Dimens.s8, bottom = Dimens.s6))
            row.rows.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = Dimens.s8), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            (r.settledAt?.atZone(ZoneId.systemDefault())?.format(fmt) ?: "") + " · " + (wallets.firstOrNull { it.id == r.walletId }?.name ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text((if (r.direction == Direction.LOAN_IN) "+" else "−") + r.paidMinor.toTaka(), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
    moving?.let { repayment ->
        var amount by remember { mutableStateOf(if (repayment) bal.outstandingMinor.toTakaInput() else "") }
        var walletId by remember { mutableStateOf(wallets.firstOrNull()?.id) }
        var refused by remember { mutableStateOf(false) }
        val minor = parseTakaToMinor(amount)
        AlertDialog(
            onDismissRequest = { moving = null },
            title = { Text(if (repayment) L("Record repayment") else if (loan.lent) L("Lend more") else L("Borrow more")) },
            text = {
                Column {
                    OutlinedTextField(amount, { amount = it.takeIf(::isPartialTakaInput) ?: amount }, label = { Text(L("Amount (Tk)")) }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                    if (repayment) Text(L("Up to %1\$s is outstanding.", bal.outstandingMinor.toTaka()), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Dimens.s4))
                    Text(if (loan.lent == repayment) L("Received into") else L("Paid out of"), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Dimens.s12, bottom = Dimens.s6))
                    WalletChips(wallets, walletId) { walletId = it }
                    if (refused) Text(L("That's more than is outstanding."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                // Busy until the database answers: the dialog closes only then, so a quick second
                // tap used to record the repayment twice.
                var busy by remember { mutableStateOf(false) }
                TextButton(enabled = !busy && minor != null && minor > 0 && walletId != null, onClick = {
                    if (busy) return@TextButton
                    busy = true
                    if (repayment) viewModel.repay(loan.id, minor!!, walletId!!) { ok -> if (ok) moving = null else { refused = true; busy = false } }
                    else { viewModel.addMore(loan.id, minor!!, walletId!!); moving = null }
                }) { Text(L("Save")) }
            },
            dismissButton = { TextButton(onClick = { moving = null }) { Text(L("Cancel")) } },
        )
    }
    if (editing) {
        var person by remember { mutableStateOf(loan.person) }
        var note by remember { mutableStateOf(loan.note.orEmpty()) }
        var due by remember { mutableStateOf(loan.dueOn) }
        var remind by remember { mutableStateOf(loan.id in reminded) }
        var picking by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(L("Loan details")) },
            text = {
                Column {
                    OutlinedTextField(person, { person = it.take(40) }, label = { Text(L("Person")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { picking = true }) { Text(due?.let { L("Due ") + it.format(fmt) } ?: "Set a due date") }
                    if (due != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(L("Remind me on the day"), modifier = Modifier.weight(1f))
                            Switch(checked = remind, onCheckedChange = { remind = it })
                        }
                        TextButton(onClick = { due = null; remind = false }) { Text(L("No due date")) }
                    }
                    OutlinedTextField(note, { note = it.take(200) }, label = { Text(L("Note")) }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = person.isNotBlank(), onClick = {
                    viewModel.saveDetails(loan.copy(person = person.trim(), note = note.trim().ifEmpty { null }, dueOn = due), remind && due != null)
                    editing = false
                }) { Text(L("Save")) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(L("Cancel")) } },
        )
        if (picking) DatePickerPrompt(due ?: java.time.LocalDate.now()) { d -> picking = false; d?.let { due = it } }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(L("Delete this loan?")) },
            text = { Text(L("Its %1\$s movements are deleted too, so your wallet balances go back to as if it never happened. To keep the record, close it instead.", row.rows.size)) },
            confirmButton = { TextButton(onClick = { deleting = false; viewModel.delete(loan.id); onBack() }) { Text(L("Delete")) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(L("Cancel")) } },
        )
    }
}
