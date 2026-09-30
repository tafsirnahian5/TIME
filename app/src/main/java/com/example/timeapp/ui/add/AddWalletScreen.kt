package com.example.timeapp.ui.add

import com.example.timeapp.data.L

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.example.timeapp.data.WalletKind
import com.example.timeapp.data.isPartialTakaInput
import com.example.timeapp.ui.components.ChipRow
import com.example.timeapp.ui.components.TimeTopBar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import com.example.timeapp.ui.theme.Dimens

private val kinds = listOf(L("Cash"), L("Bank"), L("Mobile"))

/** The chips show translated labels, so they're matched against the same translations — this
 * compared with English words, and in Bangla every wallet was saved as Cash. */
private fun kindFor(label: String): WalletKind = when (label) {
    L("Bank") -> WalletKind.BANK
    L("Mobile") -> WalletKind.MOBILE
    else -> WalletKind.CASH
}

/** New wallet — not in the real design canvas (see AddSheet.kt); manually maintained, no bank connection. */
@Composable
fun AddWalletScreen(
    onBack: () -> Unit,
    onSave: (name: String, kind: WalletKind, openingBalanceTaka: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(L("Cash")) }
    // Empty with a "0" hint: a pre-typed 0 made typing 5000 read "05000".
    var openingBalance by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TimeTopBar(
                title = L("New wallet"),
                onBack = onBack,
                actions = {
                    TextButton(onClick = { onSave(name, kindFor(kind), openingBalance.ifBlank { "0" }) }, enabled = name.isNotBlank()) { Text(L("Save")) }
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(L("Name")) },
                placeholder = { Text(L("Cash")) },
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.s6, bottom = Dimens.s16),
                singleLine = true,
            )
            Text(L("Kind"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = Dimens.s10))
            ChipRow(
                options = kinds,
                selected = setOf(kind),
                onToggle = { kind = it },
                modifier = Modifier.padding(bottom = Dimens.s20),
            )
            OutlinedTextField(
                value = openingBalance,
                onValueChange = { openingBalance = it.takeIf(::isPartialTakaInput) ?: openingBalance },
                label = { Text(L("Opening balance (Tk)")) },
                placeholder = { Text("0") },
                supportingText = { Text(L("What's in it right now. Leave empty for nothing.")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
            )
        }
    }
}
