package com.example.timeapp.ui.security

import com.example.timeapp.data.L

import androidx.compose.runtime.collectAsState

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


/** Settings › Security › Change PIN — verify the current PIN, then reuse [PinSetupScreen]'s
 * enter-then-confirm body for the new one. */
@Composable
fun PinChangeScreen(onBack: () -> Unit, onDone: () -> Unit, viewModel: SecurityViewModel) {
    var verified by remember { mutableStateOf(false) }
    var oldPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val pinLength = viewModel.uiState.collectAsState().value.pinLength

    if (verified) {
        PinSetupScreen(onBack = onBack, onDone = onDone, viewModel = viewModel)
        return
    }

    fun onDigit(d: Char) {
        if (oldPin.length < pinLength) {
            error = null
            oldPin += d
        }
    }

    fun onBackspace() {
        if (oldPin.isNotEmpty()) oldPin = oldPin.dropLast(1)
    }

    LaunchedEffect(oldPin) {
        if (oldPin.length == pinLength) {
            viewModel.attemptUnlock(oldPin) { ok ->
                if (ok) {
                    verified = true
                } else {
                    error = L("That's not your current PIN.")
                    oldPin = ""
                }
            }
        }
    }

    Scaffold(topBar = { TimeTopBar(title = L("Change PIN"), onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Dimens.screenH),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.weight(1f))
            Text(L("Enter your current PIN"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = Dimens.s20))
            PinDots(length = pinLength, filled = oldPin.length, modifier = Modifier.padding(bottom = Dimens.s16))
            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Box(modifier = Modifier.weight(1f))
            NumericKeypad(onDigit = ::onDigit, onBackspace = ::onBackspace, modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s20))
        }
    }
}
