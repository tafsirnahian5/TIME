package com.example.timeapp.ui.security

import com.example.timeapp.data.L

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens


/**
 * Settings › Security › Enable app PIN. Two steps in one screen: enter a 6–12 digit PIN,
 * then confirm it — a mismatch clears and restarts at step one rather than silently
 * accepting a typo.
 */
@Composable
fun PinSetupScreen(onBack: () -> Unit, onDone: () -> Unit, viewModel: SecurityViewModel) {
    var firstEntry by remember { mutableStateOf<String?>(null) }
    var current by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val target = firstEntry?.length ?: PIN_MAX_LENGTH
    fun onDigit(d: Char) {
        if (current.length < target) {
            error = null
            current += d
        }
    }

    /** First step: a 6–12 digit PIN is chosen, then typed again to confirm. */
    fun acceptFirst() {
        firstEntry = current
        current = ""
    }

    fun onBackspace() {
        if (current.isNotEmpty()) current = current.dropLast(1)
    }

    LaunchedEffect(current) {
        val first = firstEntry
        // The confirmation finishes by itself at the chosen length; the first entry waits for
        // Continue (or the 12th digit), since its length is the user's choice.
        if (first == null && current.length == PIN_MAX_LENGTH) acceptFirst()
        if (first != null && current.length == first.length) {
            if (current == first) {
                viewModel.setupPin(current, onDone = onDone)
            } else {
                error = L("PINs didn't match — try again.")
                firstEntry = null
                current = ""
            }
        }
    }

    Scaffold(topBar = { TimeTopBar(title = L("Set app PIN"), onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Dimens.screenH),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.weight(1f))
            Text(
                if (firstEntry == null) L("Choose a PIN of %1\$s to %2\$s digits", PIN_MIN_LENGTH, PIN_MAX_LENGTH) else L("Confirm your PIN"),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = Dimens.s20),
            )
            PinDots(length = firstEntry?.length ?: maxOf(PIN_MIN_LENGTH, current.length), filled = current.length, modifier = Modifier.padding(bottom = Dimens.s16))
            if (firstEntry == null) {
                androidx.compose.material3.TextButton(onClick = ::acceptFirst, enabled = current.length >= PIN_MIN_LENGTH) {
                    Text(L("Continue"))
                }
            }
            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Box(modifier = Modifier.weight(1f))
            NumericKeypad(onDigit = ::onDigit, onBackspace = ::onBackspace, modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s20))
        }
    }
}
