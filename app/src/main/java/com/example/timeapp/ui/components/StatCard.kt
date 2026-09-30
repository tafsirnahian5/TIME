package com.example.timeapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.timeapp.ui.theme.CardW
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Ink4
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/** White fill, 1dp hairline border, r12, pad 16 — no shadow (mock uses borders, not elevation). */
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    val colors = TimeThemeTokens.colors
    OutlinedCard(
        modifier = modifier,
        shape = Shapes.md,
        colors = CardDefaults.outlinedCardColors(containerColor = CardW),
        border = BorderStroke(Dimens.hairline, colors.line),
    ) {
        Column(modifier = Modifier.padding(Dimens.cardPad)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = Ink4)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = Dimens.s4),
            )
            if (caption != null) {
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink4,
                    modifier = Modifier.padding(top = Dimens.s2),
                )
            }
        }
    }
}
