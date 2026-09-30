package com.example.timeapp.ui.money

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import com.example.timeapp.data.SavingsGoal
import com.example.timeapp.data.isPartialTakaInput
import com.example.timeapp.data.parseTakaToMinor
import com.example.timeapp.data.toTaka
import com.example.timeapp.data.toTakaInput
import com.example.timeapp.ui.components.ConfirmDeleteDialog
import com.example.timeapp.ui.components.Illustration
import com.example.timeapp.ui.components.EmptyState
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val monthYear get() = displayPattern("MMM yyyy")

/**
 * Money › Savings goals. Setting money aside earmarks part of a wallet — the wallet's balance
 * doesn't change, and the free (unearmarked) part is shown beside it. Goals never count as
 * spending. Not in the design canvas; token-built.
 */
@Composable
fun SavingsGoalsScreen(onBack: () -> Unit, viewModel: SavingsViewModel) {
    val colors = TimeThemeTokens.colors
    val state by viewModel.uiState.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SavingsGoal?>(null) }
    var moving by remember { mutableStateOf<Pair<GoalRow, Boolean>?>(null) } // row, isSetAside
    var deleting by remember { mutableStateOf<SavingsGoal?>(null) }
    var showArchived by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TimeTopBar(title = L("Savings goals"), onBack = onBack) },
        floatingActionButton = {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = { creating = true },
                containerColor = colors.acc,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = Shapes.lg,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(L("Goal")) },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.s16)) {
            if (state.wallets.isNotEmpty()) {
                item {
                    // The canvas's hero: what's set aside against what's still free, and a bar.
                    val onAcc = MaterialTheme.colorScheme.onPrimary
                    val total = state.wallets.sumOf { it.balanceMinor }.coerceAtLeast(0)
                    val free = state.wallets.sumOf { it.freeMinor }
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s12).background(colors.acc, Shapes.xl).padding(Dimens.s20)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(L("Set aside for goals"), style = MaterialTheme.typography.bodyMedium, color = onAcc.copy(alpha = 0.85f))
                                Text(state.totalEarmarkedMinor.toTaka(), style = MaterialTheme.typography.displaySmall, color = onAcc)
                            }
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                Text(L("Free to spend"), style = MaterialTheme.typography.bodyMedium, color = onAcc.copy(alpha = 0.85f))
                                Text(free.toTaka(), style = MaterialTheme.typography.titleMedium, color = onAcc)
                            }
                        }
                        Box(Modifier.fillMaxWidth().padding(vertical = Dimens.s12).height(Dimens.s10).background(onAcc.copy(alpha = 0.25f), Shapes.pill)) {
                            val f = if (total > 0) (state.totalEarmarkedMinor.toFloat() / total).coerceIn(0f, 1f) else 0f
                            if (f > 0f) Box(Modifier.fillMaxWidth(f).height(Dimens.s10).background(onAcc, Shapes.pill))
                        }
                        Text(
                            L("Balances stay as they are — set-aside money just stops looking free."),
                            style = MaterialTheme.typography.bodySmall,
                            color = onAcc.copy(alpha = 0.85f),
                        )
                    }
                    state.wallets.filter { it.overCommittedMinor > 0 }.forEach { w ->
                        Text(
                            L("%1\$s holds %2\$s less than is set aside from it.", w.wallet.name, w.overCommittedMinor.toTaka()),
                            style = MaterialTheme.typography.bodySmall,
                            color = TimeThemeTokens.status.dangerInk,
                            modifier = Modifier.padding(bottom = Dimens.s8),
                        )
                    }
                }
            }
            if (state.active.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Savings,
                        illustration = Illustration.MONEY,
                        headline = L("No savings goals yet"),
                        supporting = L("A laptop, a trip, an emergency fund — name it, set a target, and put money aside toward it."),
                        actionLabel = L("New goal"),
                        onAction = { creating = true },
                        modifier = Modifier.padding(vertical = Dimens.s16),
                    )
                }
            }
            items(state.active, key = { it.goal.id }) { row ->
                GoalCard(
                    row, state.wallets.associate { it.wallet.id to it.wallet.name },
                    onSetAside = { moving = row to true },
                    onTakeBack = { moving = row to false },
                    onEdit = { editing = row.goal },
                    onArchive = { viewModel.archive(row.goal) },
                    onDelete = { deleting = row.goal },
                )
            }
            if (state.archived.isNotEmpty()) {
                item {
                    TextButton(onClick = { showArchived = !showArchived }) {
                        Text(if (showArchived) L("Hide finished (%1\$s)", state.archived.size) else L("Finished (%1\$s)", state.archived.size))
                    }
                }
                if (showArchived) {
                    items(state.archived, key = { it.goal.id }) { row ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s8)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(row.goal.name, style = MaterialTheme.typography.bodyLarge)
                                Text(L("Target %1\$s", row.goal.targetMinor.toTaka()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { viewModel.restore(row.goal) }) { Text(L("Restore")) }
                        }
                    }
                }
            }
            item { Box(Modifier.padding(bottom = Dimens.s34 * 2)) }
        }
    }

    if (creating) {
        GoalDialog(null, onDismiss = { creating = false }) { name, target, date -> viewModel.addGoal(name, target, date); creating = false }
    }
    editing?.let { goal ->
        GoalDialog(goal, onDismiss = { editing = null }) { name, target, date ->
            viewModel.updateGoal(goal.copy(name = name, targetMinor = target, targetDate = date)); editing = null
        }
    }
    moving?.let { (row, isSetAside) ->
        MoveMoneyDialog(
            row = row,
            setAside = isSetAside,
            // Nothing new is set aside in an archived wallet; taking back from one still works.
            wallets = if (isSetAside) state.wallets.filter { !it.wallet.isArchived } else state.wallets,
            onDismiss = { moving = null },
            onConfirm = { walletId, amount, reject ->
                val done: (Boolean) -> Unit = { ok -> if (ok) moving = null else reject() }
                if (isSetAside) viewModel.setAside(row.goal.id, walletId, amount, done) else viewModel.takeBack(row.goal.id, walletId, amount, done)
            },
        )
    }
    deleting?.let { goal ->
        ConfirmDeleteDialog(
            itemLabel = goal.name,
            detail = L("Anything set aside for it becomes free again. No money moves."),
            onConfirm = { viewModel.deleteGoal(goal.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun GoalCard(
    row: GoalRow,
    walletNames: Map<Long, String>,
    onSetAside: () -> Unit,
    onTakeBack: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = TimeThemeTokens.colors
    val status = TimeThemeTokens.status
    val pace = row.pace
    var menu by remember { mutableStateOf(false) }
    val fraction = if (row.goal.targetMinor > 0) (pace.savedMinor.toFloat() / row.goal.targetMinor).coerceIn(0f, 1f) else 0f
    // Reached goals take the ok palette, as the canvas draws them; late ones an amber ring.
    val reached = pace.reached
    val ink = if (reached) status.okInk else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Dimens.cardGap)
            .then(
                if (reached) Modifier.background(status.ok, Shapes.xl)
                else Modifier.background(colors.card, Shapes.xl).border(Dimens.hairline, colors.line, Shapes.xl),
            )
            .padding(Dimens.s16),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (reached) {
                Box(Modifier.size(60.dp).background(status.okInk, androidx.compose.foundation.shape.CircleShape), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = status.ok, modifier = Modifier.size(28.dp))
                }
            } else {
                com.example.timeapp.ui.components.ProgressRing(
                    fraction = fraction,
                    color = if (pace.late) status.warnInk else colors.acc,
                    track = colors.ring,
                ) { Text(L("%1\$s%%", (fraction * 100).toInt()), style = MaterialTheme.typography.labelMedium) }
            }
            Column(modifier = Modifier.weight(1f).padding(start = Dimens.s14)) {
                Text(row.goal.name, style = MaterialTheme.typography.titleSmall, color = ink)
                Text(
                    (if (reached) L("Reached · ") else "") + L("%1\$s of %2\$s", pace.savedMinor.toTaka(), row.goal.targetMinor.toTaka()) +
                        (row.goal.targetDate?.takeIf { !reached }?.let { L(" · by %1\$s", it.format(monthYear)) } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (reached) status.okInk else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!reached) {
                    Text(
                        when {
                            pace.late -> L("Target date passed · %1\$s to go", pace.remainingMinor.toTaka())
                            pace.perMonthMinor != null -> L("%1\$s a month gets you there", pace.perMonthMinor.toTaka())
                            else -> L("%1\$s to go", pace.remainingMinor.toTaka())
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (pace.late) status.warnInk else status.okInk,
                    )
                }
                if (row.heldByWallet.isNotEmpty()) {
                    Text(
                        L("Held in ") + row.heldByWallet.entries.joinToString { "${walletNames[it.key] ?: "?"} ${it.value.toTaka()}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (reached) status.okInk else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = L("More"), tint = ink) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(L("Edit")) }, onClick = { menu = false; onEdit() })
                    if (!reached) DropdownMenuItem(text = { Text(L("Give up and free it")) }, onClick = { menu = false; onArchive() })
                    DropdownMenuItem(text = { Text(L("Delete")) }, onClick = { menu = false; onDelete() })
                }
            }
        }
        if (reached) {
            androidx.compose.material3.Button(
                onClick = onArchive,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = status.okInk, contentColor = status.ok),
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.s12),
            ) { Text(L("Mark finished and free %1\$s", pace.savedMinor.toTaka())) }
        } else {
            Row(modifier = Modifier.padding(top = Dimens.s12)) {
                OutlinedButton(onClick = onSetAside, modifier = Modifier.weight(1f)) { Text(L("Set aside")) }
                if (pace.savedMinor > 0) {
                    TextButton(onClick = onTakeBack, modifier = Modifier.weight(1f).padding(start = Dimens.s8)) { Text(L("Take back")) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDialog(initial: SavingsGoal?, onDismiss: () -> Unit, onConfirm: (String, Long, LocalDate?) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var amount by remember { mutableStateOf(initial?.targetMinor?.toTakaInput() ?: "") }
    var date by remember { mutableStateOf(initial?.targetDate) }
    var picking by remember { mutableStateOf(false) }
    val target = parseTakaToMinor(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) L("New savings goal") else L("Edit goal")) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(L("What for")) }, placeholder = { Text(L("New laptop")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (isPartialTakaInput(it)) amount = it },
                    label = { Text(L("Target (Tk)")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8)) {
                    TextButton(onClick = { picking = true }) { Text(date?.let { L("By %1\$s", it.format(monthYear)) } ?: "Add a target date") }
                    if (date != null) TextButton(onClick = { date = null }) { Text(L("No date")) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { target?.let { onConfirm(name, it, date) } }, enabled = name.isNotBlank() && target != null && target > 0) {
                Text(if (initial == null) L("Create") else L("Save"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancel")) } },
    )
    if (picking) {
        val state = androidx.compose.runtime.remember { androidx.compose.material3.DatePickerState(locale = com.example.timeapp.ui.components.pickerLocale(), initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()) }
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = false
                }) { Text(L("OK")) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(L("Cancel")) } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun MoveMoneyDialog(
    row: GoalRow,
    setAside: Boolean,
    wallets: List<WalletSavings>,
    onDismiss: () -> Unit,
    onConfirm: (walletId: Long, amount: Long, reject: () -> Unit) -> Unit,
) {
    val colors = TimeThemeTokens.colors
    // Setting aside draws on a wallet's free money; taking back returns what this goal holds there.
    val options = if (setAside) wallets.map { it.wallet.id to it.freeMinor } else row.heldByWallet.toList()
    var walletId by remember { mutableStateOf(options.maxByOrNull { it.second }?.first) }
    val cap = options.firstOrNull { it.first == walletId }?.second ?: 0L
    var amount by remember { mutableStateOf("") }
    var rejected by remember { mutableStateOf(false) }
    val minor = parseTakaToMinor(amount)
    val valid = walletId != null && minor != null && minor > 0 && minor <= cap
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (setAside) L("Set aside for %1\$s", row.goal.name) else L("Take back from %1\$s", row.goal.name)) },
        text = {
            Column {
                Text(if (setAside) L("From") else L("Held in"), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = Dimens.s8))
                FlowRow {
                    options.forEach { (id, available) ->
                        val selected = id == walletId
                        val name = wallets.firstOrNull { it.wallet.id == id }?.wallet?.name ?: "?"
                        Text(
                            "$name · ${available.toTaka()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(end = Dimens.s8, bottom = Dimens.s8)
                                .background(if (selected) colors.accC else Color.Transparent, Shapes.pill)
                                .then(if (selected) Modifier else Modifier.border(Dimens.hairline, TimeThemeTokens.status.controlOutline, Shapes.pill))
                                .semantics { this.selected = selected }.clickable { walletId = id; rejected = false }
                                .heightIn(min = Dimens.touchMin)
                                .padding(horizontal = Dimens.s14)
                                .wrapContentHeight(),
                        )
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (isPartialTakaInput(it)) { amount = it; rejected = false } },
                    label = { Text(L("Amount (Tk)")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = {
                        Text(
                            when {
                                rejected -> L("The wallet changed — that's more than is available now.")
                                minor != null && minor > cap -> L("Up to %1\$s here.", cap.toTaka())
                                setAside -> L("Up to %1\$s free in this wallet.", cap.toTaka())
                                else -> L("Up to %1\$s held here.", cap.toTaka())
                            },
                        )
                    },
                    isError = rejected || (minor != null && minor > cap),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            // Busy until the database answers (the dialog closes only then): a quick second tap
            // used to set the money aside twice.
            var busy by remember { mutableStateOf(false) }
            TextButton(onClick = {
                if (busy) return@TextButton
                busy = true
                onConfirm(walletId!!, minor!!) { rejected = true; busy = false }
            }, enabled = valid && !busy) {
                Text(if (setAside) L("Set aside") else L("Take back"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancel")) } },
    )
}
