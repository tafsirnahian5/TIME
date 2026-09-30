package com.example.timeapp.ui.components

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/** A filter chip that opens a menu of [options]; tinted while [active]. 48dp tall (rule 6). */
@Composable
fun <T> FilterDropdownChip(
    label: String,
    active: Boolean,
    options: List<Pair<String, T>>,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TimeThemeTokens.colors
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier.padding(end = Dimens.s8)) {
        Text(
            "$label ▾",
            style = MaterialTheme.typography.labelMedium,
            color = if (active) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .heightIn(min = Dimens.touchMin)
                .background(if (active) colors.accC else Color.Transparent, Shapes.pill)
                .semantics { this.selected = active }.clickable { open = true }
                .padding(horizontal = Dimens.s14)
                .wrapContentHeight(),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, value) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(value) })
            }
        }
    }
}
