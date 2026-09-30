package com.example.timeapp.ui.money

import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Close
import com.example.timeapp.data.L

import androidx.compose.ui.semantics.heading

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import com.example.timeapp.data.parseTakaToMinor
import com.example.timeapp.data.toTakaInput
import com.example.timeapp.data.toTaka
import com.example.timeapp.ui.components.InlineAmountInput
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.defaultMinSize
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/** E3 — Record payment: the only screen where a plan turns into a real ledger row. */
@Composable
fun RecordPaymentScreen(
    planId: Long,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: MoneyViewModel,
    preferredWalletId: Long = 0,
) {
    val colors = TimeThemeTokens.colors
    val ui by viewModel.uiState.collectAsState()
    val plan = ui.plans.firstOrNull { it.id == planId } ?: return
    var amount by remember { mutableStateOf(plan.remainingMinor.toTakaInput()) }
    // The plan's own wallet when it names one that's still open; `wallets.first()` used to
    // ignore it — and crashed outright when no wallet existed yet.
    var walletId by remember {
        mutableStateOf(
            plan.walletId?.takeIf { id -> ui.activeWallets.any { it.id == id } }
                ?: preferredWalletId.takeIf { id -> ui.activeWallets.any { it.id == id } }
                ?: ui.activeWallets.firstOrNull()?.id,
        )
    }
    val amountMinor = parseTakaToMinor(amount) ?: 0L
    // Settling is the default only when this payment covers everything still owed. It used to be
    // preselected whatever was typed, so paying Tk 500 of Tk 1,200.50 closed the bill by default
    // and quietly wrote off the rest. An explicit choice sticks.
    var settleChoice by remember { mutableStateOf<Boolean?>(null) }
    val settle = settleChoice ?: (amountMinor >= plan.remainingMinor)
    // One tap, one payment: a quick double tap used to post it twice before the screen closed.
    var submitted by remember { mutableStateOf(false) }
    var confirmingOverpay by remember { mutableStateOf(false) }
    val overBy = amountMinor - plan.remainingMinor

    fun submit() {
        val wallet = walletId ?: return
        if (submitted) return
        submitted = true
        viewModel.recordPayment(plan.id, amountMinor, wallet, settle)
        onDone()
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.padding(Dimens.s16)) {
                androidx.compose.material3.IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Close, contentDescription = L("Close"))
                }
                Text(L("Bill to pay"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = Dimens.s16))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(54.dp).background(colors.accC2, CircleShape), contentAlignment = Alignment.Center) {
                        // The bill's own category icon (was a shopping cart for every bill).
                        Icon(com.example.timeapp.ui.components.CategoryIcons.of(plan.categoryIcon) ?: Icons.Rounded.Payments, contentDescription = null, tint = colors.acc)
                    }
                    Text(plan.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = Dimens.s14))
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(colors.accS3, Shapes.sheet)
                    .imePadding()
                    .padding(Dimens.s22)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(L("Record payment"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = Dimens.s16))
                Column(
                    modifier = Modifier.fillMaxWidth().background(TimeThemeTokens.colors.card, Shapes.xl).padding(Dimens.s20),
                ) {
                    Text(L("Amount actually paid"), style = MaterialTheme.typography.bodySmall)
                    InlineAmountInput(
                        value = amount,
                        onValueChange = { amount = it },
                        modifier = Modifier.padding(top = Dimens.s8),
                        currencyStyle = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        L("%1\$s was outstanding", plan.remainingMinor.toTaka()),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = Dimens.s8),
                    )
                }
                Text(L("PAID FROM"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(top = Dimens.s16, bottom = Dimens.s10))
                if (ui.activeWallets.isEmpty()) {
                    Text(
                        L("No wallet to pay from yet. Add one from Money › Overview first."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Dimens.s16),
                    )
                }
                Row(modifier = Modifier.padding(bottom = Dimens.s16).horizontalScroll(rememberScrollState())) {
                    ui.activeWallets.forEach { wallet ->
                        val selected = wallet.id == walletId
                        Row(
                            modifier = Modifier
                                .padding(end = Dimens.s8)
                                .clip(Shapes.pill)
                                // These chips drew a selected state but had no click handler at
                                // all, so a payment could only ever come out of the first wallet.
                                .semantics { this.selected = selected }.clickable { walletId = wallet.id }
                                .background(if (selected) colors.accC else Color.Transparent, Shapes.pill)
                                .border(if (selected) 0.dp else 1.dp, TimeThemeTokens.status.controlOutline, Shapes.pill)
                                // The 48dp minimum goes outside the padding: inside it, the chip
                                // grew to 68dp and its pill shape turned into a tall oval.
                                .defaultMinSize(minHeight = Dimens.touchMin)
                                .padding(horizontal = Dimens.s14),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selected) Icon(Icons.Rounded.Wallet, contentDescription = null, tint = colors.accInk, modifier = Modifier.size(16.dp))
                            Text(
                                wallet.name, style = MaterialTheme.typography.labelMedium,
                                color = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = if (selected) Dimens.s6 else 0.dp),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s16).padding(bottom = Dimens.s20),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Event, contentDescription = null)
                    Text(L("Payment date"), modifier = Modifier.weight(1f).padding(start = Dimens.s14))
                    Text(
                        java.time.LocalDate.now().format(displayPattern("EEE d MMM")),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(L("AFTER THIS PAYMENT"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s10))
                ChoiceCard(
                    selected = !settle, onClick = { settleChoice = false },
                    title = L("Keep the bill open"),
                    subtitle = if (overBy >= 0) L("Nothing stays outstanding") else L("%1\$s stays outstanding", (-overBy).toTaka()),
                )
                ChoiceCard(
                    selected = settle, onClick = { settleChoice = true },
                    title = L("Mark the bill settled"),
                    subtitle = run {
                        // Said the other way round when paying *over* the estimate: it used to
                        // read "Tk -300 below", which is the PDF's overpayment case, unexplained.
                        val variance = plan.amountMinor - plan.paidMinor - amountMinor
                        val relation = when {
                            variance > 0 -> L("%1\$s below", variance.toTaka())
                            variance < 0 -> L("%1\$s above", (-variance).toTaka())
                            else -> L("exactly on")
                        }
                        L("Settled at %1\$s · %2\$s the %3\$s estimate. ", ((plan.paidMinor + amountMinor).toTaka()), relation, plan.amountMinor.toTaka()) +
                            L("The estimate is kept for variance reporting.")
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth().background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s14).padding(top = Dimens.s6),
                ) {
                    Icon(Icons.Rounded.Bolt, contentDescription = null, tint = colors.acc)
                    val from = ui.wallets.firstOrNull { it.id == walletId }?.name ?: L("a wallet")
                    Text(
                        L("One transaction: creates the %1\$s expense, links it to the bill, and moves it out of %2\$s.", amountMinor.toTaka(), from),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = Dimens.s10),
                    )
                }
                Button(
                    // Paying more than is outstanding is allowed (the PDF's overpayment case) but
                    // confirmed, since it's as likely to be a typo.
                    onClick = { if (overBy > 0) confirmingOverpay = true else submit() },
                    enabled = amountMinor > 0 && walletId != null && !submitted,
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16),
                ) { Text(L("Record %1\$s payment", amountMinor.toTaka())) }
            }
        }
    }

    if (confirmingOverpay) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmingOverpay = false },
            title = { Text(L("Pay more than is outstanding?")) },
            text = {
                Text(
                    L("%1\$s is %2\$s more than the %3\$s still owed. ", amountMinor.toTaka(), overBy.toTaka(), plan.remainingMinor.toTaka()) +
                        L("It's recorded as paid, and the extra shows as the bill going over its estimate."),
                )
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmingOverpay = false; submit() }) { Text(L("Record it")) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmingOverpay = false }) { Text(L("Change amount")) } },
        )
    }
}

@Composable
internal fun ChoiceCard(selected: Boolean, onClick: () -> Unit, title: String, subtitle: String) {
    val colors = TimeThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Dimens.s10)
            .background(if (selected) colors.accC else Color.Transparent, Shapes.lg)
            .border(1.dp, if (selected) colors.acc else colors.edge, Shapes.lg)
            .semantics { this.selected = selected }.clickable(onClick = onClick)
            .padding(Dimens.s14),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(if (selected) 6.dp else 2.dp, if (selected) colors.acc else TimeThemeTokens.status.controlOutline, CircleShape)
                .background(if (selected) TimeThemeTokens.colors.card else Color.Transparent, CircleShape),
        )
        Column(modifier = Modifier.padding(start = Dimens.s14)) {
            Text(title, color = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Dimens.s4))
        }
    }
}
