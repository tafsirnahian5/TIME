package com.example.timeapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.TimeThemeTokens

/** Picture (or icon) + line + optional action — a designed empty state, not a placeholder (see A3). */
@Composable
fun EmptyState(
    icon: ImageVector,
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    /** A picture for "nothing here yet" (Phase 18D); a filtered-to-nothing list keeps the icon. */
    illustration: Illustration? = null,
) {
    val colors = TimeThemeTokens.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.s24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.s12),
    ) {
        if (illustration != null) {
            IllustrationImage(illustration, width = 200.dp)
        } else {
            Icon(
                icon,
                contentDescription = null,
                tint = colors.acc,
                modifier = Modifier.size(48.dp),
            )
        }
        Text(
            headline,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (supporting != null) {
            Text(
                supporting,
                style = MaterialTheme.typography.bodyMedium,
                // Was a raw light-mode grey (Ink4): dim on a dark page.
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = Dimens.s8)) {
                Text(actionLabel)
            }
        }
    }
}
