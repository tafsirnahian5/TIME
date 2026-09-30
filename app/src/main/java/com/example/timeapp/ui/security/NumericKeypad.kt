package com.example.timeapp.ui.security

import com.example.timeapp.data.L

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.TimeThemeTokens

/** The row of filled/empty dots shown above the keypad on PIN setup and the lock screen. */
@Composable
fun PinDots(length: Int, filled: Int, modifier: Modifier = Modifier) {
    val colors = TimeThemeTokens.colors
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Dimens.s12)) {
        repeat(length) { i ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(if (i < filled) colors.acc else colors.ring, CircleShape),
            )
        }
    }
}

/**
 * A plain 3x4 numeric keypad shared by PIN setup/change and the lock screen. The
 * bottom-left slot shows a biometric shortcut when [onBiometric] is non-null, else it's
 * left empty; bottom-right is always backspace. 48dp+ touch targets per project rule.
 */
@Composable
fun NumericKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    enabled: Boolean = true,
    onBiometric: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val rows = listOf("123", "456", "789")
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.s12)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.s12)) {
                row.forEach { digit ->
                    KeypadKey(modifier = Modifier.weight(1f), enabled = enabled, onClick = { onDigit(digit) }) {
                        Text(digit.toString(), style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.s12)) {
            KeypadKey(modifier = Modifier.weight(1f), enabled = enabled && onBiometric != null, onClick = { onBiometric?.invoke() }) {
                if (onBiometric != null) {
                    Icon(Icons.Rounded.Fingerprint, contentDescription = L("Use fingerprint"))
                }
            }
            KeypadKey(modifier = Modifier.weight(1f), enabled = enabled, onClick = { onDigit('0') }) {
                Text("0", style = MaterialTheme.typography.headlineSmall)
            }
            KeypadKey(modifier = Modifier.weight(1f), enabled = enabled, onClick = onBackspace) {
                Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = L("Delete"))
            }
        }
    }
}

@Composable
private fun KeypadKey(modifier: Modifier, enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .size(Dimens.touchMin + Dimens.s16)
            .clickable(enabled = enabled, onClick = onClick)
            .background(Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
