package com.example.timeapp.ui.add

import com.example.timeapp.data.L

import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.timeapp.data.Direction
import com.example.timeapp.data.isPartialTakaInput
import com.example.timeapp.data.parseTakaToMinor
import com.example.timeapp.data.toTakaInput
import com.example.timeapp.data.Wallet
import com.example.timeapp.ui.components.ChipRow
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.richtext.RichTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import com.example.timeapp.ui.components.InlineAmountInput
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.LocalDate

private val directionLabels = listOf(L("Expense"), L("Income"), L("Transfer"))
/** Matched against the same translated labels the chips show: comparing with English words
 * meant that in Bangla, Income and Transfer were both recorded as an Expense. */
private fun directionFor(label: String): Direction = when (label) {
    L("Income") -> Direction.INCOME
    L("Transfer") -> Direction.TRANSFER
    else -> Direction.COST
}

/**
 * B3b — New transaction: money that already moved (posts immediately), distinct from a
 * planned cost. Transfer reuses MoneyViewModel.transfer; Income/Expense insert a settled
 * row directly via the new MoneyRepository.addTransaction.
 */
@Composable
fun AddTransactionScreen(
    onBack: () -> Unit,
    onSave: (direction: Direction, title: String, amountTaka: String, fromWalletId: Long, toWalletId: Long?, note: String?, feeTaka: String, links: com.example.timeapp.data.MoneyLinks) -> Unit,
    wallets: List<Wallet>,
    existingRecord: com.example.timeapp.data.MoneyRecord? = null,
    categories: List<com.example.timeapp.data.Category> = emptyList(),
    onCreateCategory: (String, Long, (Long) -> Unit) -> Unit = { _, _, _ -> },
    tasks: List<com.example.timeapp.data.Entry> = emptyList(),
) {
    var categoryId by remember { mutableStateOf(existingRecord?.categoryId) }
    var taskId by remember { mutableStateOf(existingRecord?.taskId) }
    val colors = TimeThemeTokens.colors
    var direction by remember {
        mutableStateOf(
            when (existingRecord?.direction) {
                Direction.INCOME -> L("Income")
                Direction.TRANSFER -> L("Transfer")
                else -> L("Expense")
            },
        )
    }
    var title by remember { mutableStateOf(existingRecord?.title ?: "") }
    var amount by remember { mutableStateOf(existingRecord?.paidMinor?.toTakaInput() ?: "") }
    var fromWalletId by remember { mutableStateOf(existingRecord?.walletId ?: wallets.firstOrNull()?.id) }
    var toWalletId by remember {
        mutableStateOf(existingRecord?.transferToWalletId ?: wallets.getOrNull(1)?.id ?: wallets.firstOrNull()?.id)
    }
    var note by remember { mutableStateOf(existingRecord?.note ?: "") }
    // Editor draft (Phase 15): typed text survives leaving the form or the app being killed.
    val draft = com.example.timeapp.ui.components.rememberDraft("transaction", enabled = existingRecord == null, fields = mapOf("title" to title, "amount" to amount, "note" to note)) { d -> title = d["title"].orEmpty(); amount = d["amount"].orEmpty(); note = d["note"].orEmpty() }
    var fee by remember { mutableStateOf("") }

    val isTransfer = direction == L("Transfer")
    val valid = title.isNotBlank() && (parseTakaToMinor(amount) ?: 0L) > 0 && fromWalletId != null &&
        (!isTransfer || (toWalletId != null && toWalletId != fromWalletId))

    Scaffold(
        topBar = {
            TimeTopBar(
                title = if (existingRecord != null) L("Edit money in or out") else L("Record money in or out"),
                onBack = onBack,
                actions = {
                    TextButton(
                        onClick = {
                            draft.clear(); onSave(
                                directionFor(direction), title, amount, fromWalletId!!,
                                if (isTransfer) toWalletId else null,
                                if (isTransfer) null else note.ifBlank { null },
                                if (isTransfer) fee else "",
                                com.example.timeapp.data.MoneyLinks(
                                    categoryId = categoryId.takeIf { !isTransfer },
                                    taskId = taskId.takeIf { !isTransfer },
                                    projectId = tasks.firstOrNull { it.id == taskId }?.projectId.takeIf { !isTransfer },
                                ),
                            )
                        },
                        enabled = valid,
                    ) { Text(L("Save")) }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)
                // These forms are taller than a phone screen once Details is expanded,
                // the notes Preview is showing, or a large display size is set. Without
                // this they were a plain Column: everything past the fold was clipped and
                // simply unreachable, including the reminder switch. imePadding keeps the
                // focused field above the keyboard instead of behind it.
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Dimens.s16)) {
            com.example.timeapp.ui.components.DraftBanner(draft, Modifier.padding(top = Dimens.s8))
            ChipRow(
                options = directionLabels,
                selected = setOf(direction),
                onToggle = { direction = it },
                modifier = Modifier.padding(top = Dimens.s10, bottom = Dimens.s16),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.accC2, Shapes.xl)
                    .padding(vertical = Dimens.s20, horizontal = Dimens.s22),
            ) {
                Text(L("Amount"), style = MaterialTheme.typography.bodyMedium)
                // The same in-card amount the planned-cost and record-payment forms use. This
                // was a bordered OutlinedTextField, which drew a box inside the accent card with
                // "Tk" stranded at its bottom-left, and had no decimal keyboard.
                InlineAmountInput(
                    value = amount,
                    onValueChange = { amount = it },
                    modifier = Modifier.padding(top = Dimens.s8),
                )
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(L("Title")) },
                placeholder = { Text(if (isTransfer) L("Wallet transfer") else L("Lunch with the team")) },
                modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s20),
                singleLine = true,
            )
            Text(if (isTransfer) L("From wallet") else L("Wallet"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = Dimens.s10))
            if (wallets.isEmpty()) {
                Text(
                    L("No wallets yet — add one first (Add › Wallet). Money in or out has to go to a wallet."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Dimens.s20),
                )
            }
            ChipRow(
                options = wallets.map { it.name },
                selected = setOfNotNull(wallets.firstOrNull { it.id == fromWalletId }?.name),
                onToggle = { name -> fromWalletId = wallets.firstOrNull { it.name == name }?.id },
                modifier = Modifier.padding(bottom = Dimens.s20),
            )
            if (isTransfer) {
                Text(L("To wallet"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = Dimens.s10))
                ChipRow(
                    options = wallets.filter { it.id != fromWalletId }.map { it.name },
                    selected = setOfNotNull(wallets.firstOrNull { it.id == toWalletId }?.name),
                    onToggle = { name -> toWalletId = wallets.firstOrNull { it.name == name }?.id },
                    modifier = Modifier.padding(bottom = Dimens.s20),
                )
                if (existingRecord == null) {
                    OutlinedTextField(
                        value = fee,
                        onValueChange = { fee = it.takeIf { v -> com.example.timeapp.data.isPartialTakaInput(v) } ?: fee },
                        label = { Text(L("Fee (optional)")) },
                        supportingText = { Text(L("Recorded as a separate expense from the first wallet — the transfer itself isn't spending.")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s20),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    )
                }
            } else {
                // Expenses used to be saved with no category at all — so a category's budget
                // never saw anything recorded here.
                Text(L("Category"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = Dimens.s10))
                com.example.timeapp.ui.components.CategoryPicker(
                    categories = categories,
                    selectedId = categoryId,
                    onSelect = { categoryId = if (categoryId == it) null else it },
                    onCreate = { n, c -> onCreateCategory(n, c) { id -> categoryId = id } },
                    modifier = Modifier.padding(bottom = Dimens.s16),
                )
                if (tasks.isNotEmpty()) {
                    com.example.timeapp.ui.components.FilterDropdownChip(
                        label = tasks.firstOrNull { it.id == taskId }?.let { L("For: ") + it.title } ?: L("For a task (optional)"),
                        active = taskId != null,
                        options = listOf<Pair<String, Long?>>(L("No task") to null) + tasks.map { it.title to it.id },
                        onPick = { taskId = it },
                        modifier = Modifier.padding(bottom = Dimens.s16),
                    )
                }
                RichTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = L("Notes"),
                    modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s20),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s10),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(L("Date"), modifier = Modifier.weight(1f))
                val postedOn = existingRecord?.settledAt?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate()
                Text(
                    if (postedOn == null) L("Today, ") + LocalDate.now().format(displayPattern("MMM d"))
                    else postedOn.format(displayPattern("EEE d MMM")),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().background(colors.accS, Shapes.lg).padding(Dimens.s14).padding(top = Dimens.s6),
            ) {
                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = colors.acc)
                Text(
                    L("This already happened, so the wallet's balance changes now — unlike a bill to pay."),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = Dimens.s10),
                )
            }
        }
    }
}
