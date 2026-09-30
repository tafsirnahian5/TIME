package com.example.timeapp.ui.money

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.heading

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.PlanStatus
import com.example.timeapp.data.parseTakaToMinor
import com.example.timeapp.data.toTaka
import com.example.timeapp.data.toTakaInput
import com.example.timeapp.ui.components.InlineAmountInput
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/**
 * Money coming back for an earlier expense. A refund is not income: it goes back into a wallet
 * and takes spending back in the original's category. Capped at what's left to refund.
 *
 * When the expense paid a plan that is already settled, this asks whether to reopen it — the
 * PDF's "refunds must not silently recreate a future obligation" — defaulting to keeping it
 * settled, which is the smaller change.
 */
@Composable
fun RefundScreen(
    recordId: Long,
    onBack: () -> Unit,
    viewModel: MoneyViewModel,
) {
    val colors = TimeThemeTokens.colors
    val ui by viewModel.uiState.collectAsState()
    val original = ui.history.firstOrNull { it.id == recordId }
    var refundable by remember { mutableStateOf<Long?>(null) }
    var plan by remember { mutableStateOf<MoneyRecord?>(null) }
    LaunchedEffect(recordId) {
        refundable = viewModel.refundableMinor(recordId)
        plan = viewModel.linkedPlanOf(recordId)
    }
    var amount by remember(refundable) { mutableStateOf(refundable?.toTakaInput() ?: "") }
    // The refund goes back where the money came from when that wallet is still open.
    val pickable = ui.activeWallets
    var walletId by remember(original) {
        mutableStateOf(original?.walletId?.takeIf { id -> pickable.any { it.id == id } } ?: pickable.firstOrNull()?.id)
    }
    var reopen by remember { mutableStateOf(false) }
    var rejected by remember { mutableStateOf(false) }

    Scaffold(topBar = { TimeTopBar(title = L("Refund"), onBack = onBack) }) { padding ->
        if (original == null || refundable == null) return@Scaffold
        val max = refundable ?: 0L
        val amountMinor = parseTakaToMinor(amount)
        val valid = amountMinor != null && amountMinor > 0 && amountMinor <= max && walletId != null
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Dimens.s16),
        ) {
            Column(modifier = Modifier.fillMaxWidth().background(colors.card, Shapes.lg).padding(Dimens.s16)) {
                Text(original.title, style = MaterialTheme.typography.bodyLarge)
                Text(L("Expense of %1\$s", original.paidMinor.toTaka()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (max == 0L) {
                Text(
                    L("This expense has already been refunded in full."),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = Dimens.s20),
                )
                return@Column
            }
            Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(colors.accC2, Shapes.xl).padding(Dimens.s20)) {
                Text(L("Refund amount"), style = MaterialTheme.typography.bodyMedium, color = colors.accInk)
                InlineAmountInput(value = amount, onValueChange = { amount = it; rejected = false }, modifier = Modifier.padding(top = Dimens.s8))
                Text(
                    when {
                        amountMinor != null && amountMinor > max -> L("That's more than the %1\$s left to refund.", max.toTaka())
                        max < original.paidMinor -> L("Up to %1\$s — part of it was refunded already.", max.toTaka())
                        else -> L("Up to %1\$s.", max.toTaka())
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (amountMinor != null && amountMinor > max) TimeThemeTokens.status.dangerInk else colors.accInk,
                    modifier = Modifier.padding(top = Dimens.s8),
                )
            }
            Text(L("BACK INTO"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(top = Dimens.s16, bottom = Dimens.s10))
            FlowRow {
                pickable.forEach { wallet ->
                    val selected = wallet.id == walletId
                    Text(
                        wallet.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(end = Dimens.s8, bottom = Dimens.s8)
                            .background(if (selected) colors.accC else Color.Transparent, Shapes.pill)
                            .then(if (selected) Modifier else Modifier.border(Dimens.hairline, TimeThemeTokens.status.controlOutline, Shapes.pill))
                            .semantics { this.selected = selected }.clickable { walletId = wallet.id }
                            .defaultMinSize(minHeight = Dimens.touchMin)
                            .padding(horizontal = Dimens.s14, vertical = Dimens.s14),
                    )
                }
            }
            plan?.let { p ->
                val refundNow = amountMinor?.coerceIn(0, max) ?: 0L
                val paidAfter = (p.paidMinor - refundNow).coerceAtLeast(0)
                Text(L("THE BILL IT PAID"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(top = Dimens.s16, bottom = Dimens.s10))
                if (p.status == PlanStatus.SETTLED && paidAfter < p.amountMinor) {
                    ChoiceCard(
                        selected = !reopen, onClick = { reopen = false },
                        title = L("Keep “%1\$s” settled", p.title),
                        subtitle = L("It stays closed, now at %1\$s against a %2\$s estimate.", paidAfter.toTaka(), p.amountMinor.toTaka()),
                    )
                    ChoiceCard(
                        selected = reopen, onClick = { reopen = true },
                        title = L("Reopen it"),
                        subtitle = L("%1\$s becomes outstanding again.", ((p.amountMinor - paidAfter).toTaka())),
                    )
                } else {
                    Text(
                        when (p.status) {
                            PlanStatus.SETTLED -> L("“%1\$s” stays settled — it's still covered after this refund.", p.title)
                            PlanStatus.CANCELLED -> L("“%1\$s” stays cancelled, now with %2\$s paid against it.", p.title, paidAfter.toTaka())
                            else -> L("“%1\$s” will have %2\$s paid, %3\$s outstanding.", p.title, paidAfter.toTaka(), ((p.amountMinor - paidAfter).coerceAtLeast(0).toTaka()))
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text(
                L("A refund isn't income — reports count it as less spending in the original's category."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.s16),
            )
            if (rejected) {
                Text(
                    L("Couldn't record that refund — the amount changed. Check it and try again."),
                    style = MaterialTheme.typography.bodySmall,
                    color = TimeThemeTokens.status.dangerInk,
                    modifier = Modifier.padding(top = Dimens.s8),
                )
            }
            // Busy until the refund is written — the screen leaves only then, and a quick second
            // tap used to record it twice.
            var busy by remember { mutableStateOf(false) }
            Button(
                onClick = {
                    if (busy) return@Button
                    val chosenWallet = walletId ?: return@Button
                    val minor = amountMinor ?: return@Button
                    busy = true
                    viewModel.refund(recordId, minor, chosenWallet, reopen, null) { ok -> if (ok) onBack() else { rejected = true; busy = false } }
                },
                enabled = valid && !busy,
                modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s16),
            ) { Text(if (valid) L("Record %1\$s refund", amountMinor!!.toTaka()) else L("Record refund")) }
        }
    }
}
