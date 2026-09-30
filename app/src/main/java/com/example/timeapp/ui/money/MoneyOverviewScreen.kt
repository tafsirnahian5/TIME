package com.example.timeapp.ui.money

import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Payments
import com.example.timeapp.data.L

import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.timeapp.data.parseTakaToMinor
import com.example.timeapp.data.toTakaInput
import com.example.timeapp.data.isPartialTakaInput
import com.example.timeapp.data.toTaka
import com.example.timeapp.ui.AppViewModel
import com.example.timeapp.ui.components.RootTopBar
import com.example.timeapp.ui.components.RootTopBarTrailing
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/** E0 — Money · Overview: the four numbers the PDF's "Budgets, forecasts and goals" section
 * specifies, all computed from real Room data — not in the real design canvas; built from the
 * design generated in this pass. */
@Composable
fun MoneyOverviewScreen(
    onOpenPlanned: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenWallet: (Long) -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: MoneyViewModel,
    appViewModel: AppViewModel,
    savings: SavingsUiState = SavingsUiState(),
    onOpenSavings: () -> Unit = {},
    onOpenLoans: () -> Unit = {},
    loans: LoansUiState = LoansUiState(),
    onAddWallet: () -> Unit = {},
) {
    val ui by viewModel.uiState.collectAsState()
    val settings by appViewModel.uiState.collectAsState()
    var showBudgetEdit by remember { mutableStateOf(false) }
    var showArchivedWallets by remember { mutableStateOf(false) }

    val numbers = computeMoneyOverview(ui, includeCarryover = settings.includeOverdueCarryover)
    // Every projected figure says which window it covers (the PDF).
    // The bare date, placed by whole-sentence templates: "to " + date read "where: 30 Sep" in Bangla.
    val windowLabel = numbers.windowEnd.format(displayPattern("d MMM"))
    val availableBalance = numbers.availableBalance
    val paidThisMonth = numbers.paidThisMonth
    val plannedRemaining = numbers.plannedRemaining
    val afterPlannedCosts = numbers.afterPlannedCosts
    val budget = settings.monthlyBudgetMinor
    val spentOrPlanned = paidThisMonth + plannedRemaining

    Scaffold(
        topBar = {
            RootTopBar(
                help = com.example.timeapp.ui.components.TabHelps.money,
                title = L("Money"), onSearch = onSearch,
                onOpenSettings = onOpenSettings,
                trailing = RootTopBarTrailing.MORE_VERT,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            MoneyTabs(selectedIndex = 0, onOpenPlanned = onOpenPlanned, onOpenHistory = onOpenHistory)
            // Scrolls: wallets, the stats, budget and savings run past a phone screen.
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Dimens.s16)) {
                val colors = TimeThemeTokens.colors
                val status = TimeThemeTokens.status
                var showHow by remember { mutableStateOf(false) }
                // Phase 18C: what you have, and what's left after this month's bills — in one card.
                // The Overview used to stack seven figures and a switch labelled "Overdue carryover".
                Column(modifier = Modifier.fillMaxWidth().background(colors.acc, Shapes.xl).padding(Dimens.s22)) {
                    val onAcc = MaterialTheme.colorScheme.onPrimary
                    Text(L("You have"), style = MaterialTheme.typography.bodyMedium, color = onAcc.copy(alpha = 0.85f))
                    Text(availableBalance.toTaka(), style = MaterialTheme.typography.displaySmall, color = onAcc, modifier = Modifier.padding(top = Dimens.s4))
                    Text(
                        L("in %1\$s", ui.activeWallets.joinToString(", ") { it.name }.ifEmpty { L("no wallets yet") }),
                        style = MaterialTheme.typography.bodySmall, color = onAcc.copy(alpha = 0.75f),
                    )
                    if (plannedRemaining > 0) {
                        Box(Modifier.fillMaxWidth().padding(vertical = Dimens.s12).size(1.dp).background(onAcc.copy(alpha = 0.25f)))
                        Text(
                            L("After this month's bills: %1\$s", afterPlannedCosts.toTaka()),
                            style = MaterialTheme.typography.titleSmall, color = onAcc,
                        )
                    }
                    // Earmarks never change the balance above; the free part is shown beside it.
                    if (savings.totalEarmarkedMinor > 0) {
                        Text(
                            L("%1\$s set aside for goals · %2\$s free", savings.totalEarmarkedMinor.toTaka(), ((availableBalance - savings.totalEarmarkedMinor).coerceAtLeast(0).toTaka())),
                            style = MaterialTheme.typography.bodySmall, color = onAcc.copy(alpha = 0.85f),
                            modifier = Modifier.padding(top = Dimens.s4),
                        )
                    }
                }

                // This month, in three plain lines.
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(colors.card, Shapes.lg).padding(vertical = Dimens.s8),
                ) {
                    Text(L("This month"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = Dimens.s16, vertical = Dimens.s8).semantics { heading() })
                    MonthRow(androidx.compose.material.icons.Icons.Rounded.Payments, status.ok, status.okInk, L("Spent so far"), paidThisMonth.toTaka(), null, null)
                    MonthRow(
                        androidx.compose.material.icons.Icons.Rounded.Receipt, status.warn, status.warnInk,
                        L("Bills still to pay"), plannedRemaining.toTaka(),
                        // Bills with no date aren't in this month's forecast; say so rather than
                        // disagreeing silently with Planned and Today, which count them as owed now.
                        ui.plans.filter { it.dueOn == null }.sumOf { it.remainingMinor }.let { undated ->
                            if (undated > 0) L("Due by %1\$s · plus %2\$s with no date", windowLabel, undated.toTaka()) else L("Due by %1\$s", windowLabel)
                        },
                        onOpenPlanned,
                    )
                    if (settings.monthlyBudgetSet && budget > 0) {
                        val left = budget - spentOrPlanned
                        Column(Modifier.fillMaxWidth().clickable { showBudgetEdit = true }.padding(horizontal = Dimens.s16, vertical = Dimens.s10)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(L("Monthly budget"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text(
                                    if (left >= 0) L("%1\$s left", left.toTaka()) else L("%1\$s over", (-left).toTaka()),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (left >= 0) MaterialTheme.colorScheme.onSurface else status.dangerInk,
                                )
                            }
                            Box(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8).size(6.dp).background(colors.ring, Shapes.xs)) {
                                val fraction = (spentOrPlanned.toFloat() / budget).coerceIn(0f, 1f)
                                Box(modifier = Modifier.fillMaxWidth(fraction).size(6.dp).background(if (left >= 0) colors.acc else status.dangerInk, Shapes.xs))
                            }
                            Text(
                                L("%1\$s of %2\$s — spent plus bills still to pay", spentOrPlanned.toTaka(), budget.toTaka()),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Dimens.s6),
                            )
                        }
                    } else {
                        TextButton(onClick = { showBudgetEdit = true }, modifier = Modifier.padding(horizontal = Dimens.s8)) { Text(L("Set a monthly budget")) }
                    }
                    TextButton(onClick = { showHow = !showHow }, modifier = Modifier.padding(horizontal = Dimens.s8)) {
                        Text(if (showHow) L("Hide how this is worked out") else L("See how this is worked out"))
                    }
                    if (showHow) {
                        Column(Modifier.padding(horizontal = Dimens.s16, vertical = Dimens.s4), verticalArrangement = Arrangement.spacedBy(Dimens.s8)) {
                            Text(
                                L("“You have” adds up every wallet. “Spent so far” is this month's spending, not counting transfers between your wallets. “Bills still to pay” is what's left on the bills you planned, due by %1\$s. Planning a bill moves no money until you record a payment.", windowLabel),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            // Overdue plans: their own switch, counted in by default — the PDF's carryover rule.
                            if (numbers.overdueCarryover > 0) {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(L("Count late bills from earlier months"), style = MaterialTheme.typography.bodyMedium)
                                        Text(L("%1\$s is still owed from before this month", numbers.overdueCarryover.toTaka()), style = MaterialTheme.typography.bodySmall, color = status.dangerInk)
                                    }
                                    androidx.compose.material3.Switch(
                                        checked = numbers.includeCarryover,
                                        onCheckedChange = { appViewModel.setIncludeOverdueCarryover(it) },
                                    )
                                }
                            }
                        }
                    }
                }

                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(top = Dimens.s16, bottom = Dimens.s6)) {
                    Text(L("Wallets"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
                    TextButton(onClick = onAddWallet) { Text(L("Add wallet")) }
                }
                val archivedWallets = ui.wallets.filter { it.isArchived }
                Column(Modifier.fillMaxWidth().background(colors.card, Shapes.lg)) {
                    if (ui.activeWallets.isEmpty()) {
                        Text(
                            L("A wallet is where money is kept — cash, a bank account, bKash. Add one to start recording money."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Dimens.s16),
                        )
                    }
                    (ui.activeWallets + if (showArchivedWallets) archivedWallets else emptyList()).forEach { wallet ->
                        val walletBalance by viewModel.walletBalance(wallet.id).collectAsState(initial = wallet.openingBalanceMinor)
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onOpenWallet(wallet.id) }.padding(horizontal = Dimens.s16, vertical = Dimens.s8),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(36.dp).background(colors.accC, androidx.compose.foundation.shape.CircleShape), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                Icon(androidx.compose.material.icons.Icons.Rounded.AccountBalanceWallet, contentDescription = null, tint = colors.accInk, modifier = Modifier.size(20.dp))
                            }
                            Text(
                                if (wallet.isArchived) L("%1\$s · archived", wallet.name) else wallet.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (wallet.isArchived) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f).padding(start = Dimens.s12),
                            )
                            Text(walletBalance.toTaka(), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (archivedWallets.isNotEmpty()) {
                    TextButton(onClick = { showArchivedWallets = !showArchivedWallets }) {
                        Text(if (showArchivedWallets) L("Hide archived wallets") else L("Archived wallets (%1\$s)", archivedWallets.size))
                    }
                }

                Column(Modifier.fillMaxWidth().padding(top = Dimens.s16, bottom = Dimens.s16).background(colors.card, Shapes.lg)) {
                    MoreRow(
                        androidx.compose.material.icons.Icons.Rounded.Savings, L("Savings goals"),
                        if (savings.active.isEmpty()) L("Set money aside toward something — it stays in your wallets.")
                        else savings.active.take(3).joinToString(" · ") { "${it.goal.name} ${it.pace.savedMinor.toTaka()} / ${it.goal.targetMinor.toTaka()}" },
                        onOpenSavings,
                    )
                    MoreRow(
                        androidx.compose.material.icons.Icons.Rounded.Handshake, L("Loans & dues"),
                        if (loans.open.isEmpty()) L("Money you've lent or borrowed, with every repayment.")
                        else L("Owed to you %1\$s · you owe %2\$s", loans.owedToYouMinor.toTaka(), loans.youOweMinor.toTaka()),
                        onOpenLoans,
                    )
                }
            }
        }
    }

    if (showBudgetEdit) {
        // Poisha kept: `budget / 100` prefilled Tk 1,200.50 as "1200", and saving changed it.
        var draft by remember { mutableStateOf(budget.toTakaInput()) }
        AlertDialog(
            onDismissRequest = { showBudgetEdit = false },
            title = { Text(L("Monthly budget")) },
            text = {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it.takeIf(::isPartialTakaInput) ?: draft },
                    label = { Text(L("Amount (Tk)")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    parseTakaToMinor(draft)?.let { appViewModel.setMonthlyBudgetMinor(it) }
                    showBudgetEdit = false
                }) { Text(L("Save")) }
            },
            dismissButton = { TextButton(onClick = { showBudgetEdit = false }) { Text(L("Cancel")) } },
        )
    }
}


@Composable
private fun MonthRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bg: Color, ink: Color, label: String, value: String, caption: String?, onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = Dimens.s16, vertical = Dimens.s8),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).background(bg, androidx.compose.foundation.shape.CircleShape), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(start = Dimens.s12)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun MoreRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val colors = TimeThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(horizontal = Dimens.s16, vertical = Dimens.s10),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).background(colors.accS, androidx.compose.foundation.shape.CircleShape), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.acc, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(start = Dimens.s12)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        Icon(androidx.compose.material.icons.Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
