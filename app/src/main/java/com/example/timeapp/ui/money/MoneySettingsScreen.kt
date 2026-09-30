package com.example.timeapp.ui.money

import com.example.timeapp.ui.components.SettingsSwitchRow
import com.example.timeapp.data.L

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.example.timeapp.data.Wallet
import com.example.timeapp.data.isPartialTakaInput
import com.example.timeapp.data.parseTakaToMinor
import com.example.timeapp.data.toTaka
import com.example.timeapp.data.toTakaInput
import com.example.timeapp.ui.AppViewModel
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/**
 * Settings › Money settings (Phase 13; the row was a disabled placeholder until Phase 8 removed
 * it): the default wallet, the monthly budget, whether overdue plans count in the Overview's
 * forecast, budget alerts, and where category budgets and loans live.
 */
@Composable
fun MoneySettingsScreen(onBack: () -> Unit, onOpenCategories: () -> Unit, appViewModel: AppViewModel, wallets: List<Wallet>) {
    val s by appViewModel.uiState.collectAsState()
    var pickingWallet by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf(false) }
    Scaffold(topBar = { TimeTopBar(title = L("Money settings"), onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.s16)) {
            Card {
                Row(Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin).clickable { pickingWallet = true }.padding(Dimens.s14), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(L("Default wallet"), style = MaterialTheme.typography.bodyLarge)
                        Text(L("Preselected when you record money"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(wallets.firstOrNull { it.id == s.defaultWalletId }?.name ?: L("First wallet"), color = MaterialTheme.colorScheme.primary)
                }
                Row(Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin).clickable { editingBudget = true }.padding(Dimens.s14), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(L("Monthly budget"), style = MaterialTheme.typography.bodyLarge)
                        Text(if (s.monthlyBudgetSet) L("Budget alerts watch it") else L("Tap to set one — alerts ignore it until you do"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // The untouched default (Tk 20,000) was shown as if it were the user's budget.
                    Text(if (s.monthlyBudgetSet) s.monthlyBudgetMinor.toTaka() else L("Not set"), color = MaterialTheme.colorScheme.primary)
                }
                SettingsSwitchRow(L("Count late bills from earlier months"), s.includeOverdueCarryover, L("In Money › Overview's \"bills still to pay\"")) { appViewModel.setIncludeOverdueCarryover(it) }
                SettingsSwitchRow(L("Budget alerts"), s.budgetAlertsEnabled, L("At 80%, when bills still to pay would go over, and when over")) { appViewModel.setBudgetAlertsEnabled(it) }
                SettingsSwitchRow(L("Show amounts on the Money widget"), s.moneyWidgetAmounts, L("Off: the home-screen widget hides your balance")) { appViewModel.setMoneyWidgetAmounts(it) }
            }
            Card {
                Row(Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin).clickable(onClick = onOpenCategories).padding(Dimens.s14)) {
                    Column {
                        Text(L("Categories and their budgets"), style = MaterialTheme.typography.bodyLarge)
                        Text(L("Settings › Categories"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (pickingWallet) {
        AlertDialog(
            onDismissRequest = { pickingWallet = false },
            title = { Text(L("Default wallet")) },
            text = {
                Column {
                    (listOf<Wallet?>(null) + wallets).forEach { w ->
                        Row(Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin).clickable { appViewModel.setDefaultWalletId(w?.id ?: 0); pickingWallet = false }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = (w?.id ?: 0L) == s.defaultWalletId, onClick = { appViewModel.setDefaultWalletId(w?.id ?: 0); pickingWallet = false })
                            Text(w?.name ?: L("First wallet"))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingWallet = false }) { Text(L("Close")) } },
        )
    }
    if (editingBudget) {
        var draft by remember { mutableStateOf(if (s.monthlyBudgetSet) s.monthlyBudgetMinor.toTakaInput() else "") }
        AlertDialog(
            onDismissRequest = { editingBudget = false },
            title = { Text(L("Monthly budget")) },
            text = { OutlinedTextField(draft, { draft = it.takeIf(::isPartialTakaInput) ?: draft }, label = { Text(L("Amount (Tk)")) }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)) },
            confirmButton = { TextButton(onClick = { parseTakaToMinor(draft)?.let { appViewModel.setMonthlyBudgetMinor(it) }; editingBudget = false }) { Text(L("Save")) } },
            dismissButton = { TextButton(onClick = { editingBudget = false }) { Text(L("Cancel")) } },
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg).border(Dimens.hairline, TimeThemeTokens.colors.line, Shapes.lg)) { content() }
}

