package com.example.timeapp.ui.money

import com.example.timeapp.data.L
import androidx.compose.foundation.verticalScroll

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

import com.example.timeapp.data.displayPattern

import androidx.compose.material.icons.automirrored.rounded.Undo
import com.example.timeapp.data.walletEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCard
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.example.timeapp.data.Direction
import com.example.timeapp.data.isPartialTakaInput
import com.example.timeapp.data.Wallet as WalletModel
import com.example.timeapp.data.parseTakaToMinor
import com.example.timeapp.data.toTaka
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.YearMonth
import java.time.ZoneId

/** E4 — Wallet detail: manually maintained, no bank connection; wallets are never deleted. */
@Composable
fun WalletDetailScreen(
    walletId: Long,
    onBack: () -> Unit,
    viewModel: MoneyViewModel,
) {
    val colors = TimeThemeTokens.colors
    val ui by viewModel.uiState.collectAsState()
    val wallet = ui.wallets.firstOrNull { it.id == walletId } ?: return
    val balance by viewModel.walletBalance(walletId).collectAsState(initial = 0L)
    var showTransfer by remember { mutableStateOf(false) }
    var showAdjust by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmingArchive by remember { mutableStateOf(false) }

    val walletHistory = ui.history.filter { it.walletId == walletId || it.transferToWalletId == walletId }
    val thisMonth = walletHistory.filter {
        it.settledAt != null && YearMonth.from(it.settledAt.atZone(ZoneId.systemDefault())) == YearMonth.now()
    }
    // Money in and out of *this* wallet, refunds included as money in.
    val inMinor = thisMonth.sumOf { it.walletEffect(walletId).coerceAtLeast(0) }
    val outMinor = thisMonth.sumOf { -it.walletEffect(walletId).coerceAtMost(0) }

    Scaffold(
        topBar = {
            TimeTopBar(title = wallet.name, onBack = onBack) {
                Box {
                    androidx.compose.material3.IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = L("More"))
                    }
                    androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        androidx.compose.material3.DropdownMenuItem(text = { Text(L("Rename")) }, onClick = { menuOpen = false; showRename = true })
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(if (wallet.isArchived) L("Restore") else L("Archive")) },
                            onClick = {
                                menuOpen = false
                                if (wallet.isArchived) viewModel.setWalletArchived(walletId, false) else confirmingArchive = true
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = Dimens.s16)) {
            Column(modifier = Modifier.fillMaxWidth().background(colors.accC2, Shapes.xl).padding(Dimens.s20)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Dimens.s12)) {
                    Box(modifier = Modifier.size(40.dp).background(TimeThemeTokens.colors.card, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Wallet, contentDescription = null, tint = TimeThemeTokens.status.okInk)
                    }
                    Text(L("Posted balance"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = Dimens.s12))
                }
                Text(balance.toTaka(), style = MaterialTheme.typography.displaySmall)
                Text(
                    L("Derived from %1\$s posted records. Manually maintained — no bank connection.", walletHistory.size),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Dimens.s8),
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s16), horizontalArrangement = Arrangement.spacedBy(Dimens.cardGap)) {
                val monthLabel = YearMonth.now().format(displayPattern("MMMM"))
                MiniStat(L("In · %1\$s", monthLabel), "+${inMinor.toTaka()}", TimeThemeTokens.status.okInk, Modifier.weight(1f))
                MiniStat(L("Out · %1\$s", monthLabel), "−${outMinor.toTaka()}", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s20), horizontalArrangement = Arrangement.spacedBy(Dimens.s10)) {
                OutlinedButton(onClick = { showTransfer = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(19.dp))
                    Text(L("Transfer"), modifier = Modifier.padding(start = Dimens.s8))
                }
                OutlinedButton(onClick = { showAdjust = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(19.dp))
                    Text(L("Adjust"), modifier = Modifier.padding(start = Dimens.s8))
                }
            }
            Text(L("RECENT IN THIS WALLET"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s6))
            val recent = walletHistory.sortedByDescending { it.settledAt }.take(10)
            if (recent.isEmpty()) {
                WalletMovementRow(Icons.Rounded.AddCard, L("Opening balance"), L("%1\$s · what it started with — not income", wallet.openingDate.format(displayPattern("d MMM"))), wallet.openingBalanceMinor.toTaka())
            }
            recent.forEach { r ->
                val icon = when (r.direction) {
                    Direction.TRANSFER -> Icons.Rounded.SwapHoriz
                    Direction.INCOME -> Icons.Rounded.ArrowDownward
                    Direction.COST -> Icons.Rounded.Receipt
                    Direction.REFUND -> Icons.AutoMirrored.Rounded.Undo
                    Direction.LOAN_OUT, Direction.LOAN_IN -> Icons.Rounded.Handshake
                }
                val dateLabel = r.settledAt?.atZone(ZoneId.systemDefault())?.toLocalDate()?.format(displayPattern("d MMM")) ?: ""
                val meta = if (r.linkedToPlan) L("%1\$s · paid toward a bill", dateLabel) else dateLabel
                val sign = if (r.walletEffect(walletId) >= 0) "+" else "−"
                WalletMovementRow(icon, r.title, meta, "$sign${r.paidMinor.toTaka()}")
            }
            Row(modifier = Modifier.padding(vertical = Dimens.s16)) {
                Icon(Icons.Rounded.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                Text(
                    if (wallet.isArchived) L("Archived — out of the wallet list and every picker; its history and balance are kept. Restore it from the menu.")
                    else L("Wallets are never deleted. Archive one from the menu to retire it with its history."),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = Dimens.s8),
                )
            }
        }
    }

    if (showTransfer) {
        TransferDialog(
            otherWallets = ui.activeWallets.filter { it.id != walletId },
            onDismiss = { showTransfer = false },
            onConfirm = { targetWalletId, amountTaka, feeTaka ->
                parseTakaToMinor(amountTaka)?.let { viewModel.transfer(walletId, targetWalletId, it, parseTakaToMinor(feeTaka) ?: 0L) }
                showTransfer = false
            },
        )
    }
    if (showAdjust) {
        AdjustDialog(
            onDismiss = { showAdjust = false },
            // The dialog already hands back poisha. This used to multiply by 100 again, so
            // adjusting by Tk 50 moved Tk 5,000.
            onConfirm = { deltaMinor ->
                viewModel.adjustBalance(walletId, deltaMinor)
                showAdjust = false
            },
        )
    }
    if (confirmingArchive) {
        AlertDialog(
            onDismissRequest = { confirmingArchive = false },
            title = { Text(L("Archive %1\$s?", wallet.name)) },
            text = {
                Text(
                    L("It leaves the wallet list and every picker. Its history stays, and so does its balance") +
                        if (balance != 0L) {
                            L(" — it still holds %1\$s, which keeps counting in your total. ", balance.toTaka()) +
                                L("Adjust it to zero first if that money is gone.")
                        } else ".",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmingArchive = false; viewModel.setWalletArchived(walletId, true) }) { Text(L("Archive")) }
            },
            dismissButton = { TextButton(onClick = { confirmingArchive = false }) { Text(L("Cancel")) } },
        )
    }
    if (showRename) {
        RenameWalletDialog(
            currentName = wallet.name,
            onDismiss = { showRename = false },
            onConfirm = { newName ->
                viewModel.renameWallet(walletId, newName)
                showRename = false
            },
        )
    }
}

@Composable
private fun RenameWalletDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("Rename wallet")) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text(L("Name")) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(L("Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancel")) } },
    )
}

@Composable
private fun TransferDialog(
    otherWallets: List<WalletModel>,
    onDismiss: () -> Unit,
    onConfirm: (targetWalletId: Long, amountTaka: String, feeTaka: String) -> Unit,
) {
    var target by remember { mutableStateOf(otherWallets.firstOrNull()?.id) }
    var amount by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("Transfer")) },
        text = {
            Column {
                Text(L("To wallet"), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = Dimens.s8))
                otherWallets.forEach { w ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(selected = target == w.id, onClick = { target = w.id })
                        Text(w.name, modifier = Modifier.padding(start = Dimens.s8))
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.takeIf(::isPartialTakaInput) ?: amount },
                    label = { Text(L("Amount (Tk)")) },
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.s10),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = fee,
                    onValueChange = { fee = it.takeIf(::isPartialTakaInput) ?: fee },
                    label = { Text(L("Fee (optional)")) },
                    supportingText = { Text(L("A separate expense from this wallet.")) },
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { target?.let { onConfirm(it, amount, fee) } },
                enabled = target != null && parseTakaToMinor(amount) != null,
            ) { Text(L("Transfer")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancel")) } },
    )
}

@Composable
private fun AdjustDialog(
    onDismiss: () -> Unit,
    onConfirm: (deltaMinor: Long) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var isIncrease by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("Adjust balance")) },
        text = {
            Column {
                Row(modifier = Modifier.padding(bottom = Dimens.s10)) {
                    TextButton(onClick = { isIncrease = true }) { Text(if (isIncrease) L("● Increase") else L("○ Increase")) }
                    TextButton(onClick = { isIncrease = false }) { Text(if (!isIncrease) L("● Decrease") else L("○ Decrease")) }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.takeIf(::isPartialTakaInput) ?: amount },
                    label = { Text(L("Amount (Tk)")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val magnitude = parseTakaToMinor(amount) ?: 0L
                    onConfirm(if (isIncrease) magnitude else -magnitude)
                },
                enabled = parseTakaToMinor(amount) != null,
            ) { Text(L("Apply")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancel")) } },
    )
}

@Composable
private fun MiniStat(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(TimeThemeTokens.colors.card, Shapes.lg).border(1.dp, TimeThemeTokens.colors.line, Shapes.lg).padding(Dimens.s14)) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.padding(top = Dimens.s4))
    }
}

@Composable
private fun WalletMovementRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, meta: String, amount: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s10), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null)
        Column(modifier = Modifier.weight(1f).padding(start = Dimens.s14)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(meta, style = MaterialTheme.typography.bodySmall)
        }
        Text(amount, style = MaterialTheme.typography.bodyLarge)
    }
}
