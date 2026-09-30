package com.example.timeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/** Pill segmented control — `acc-c` fill on the selected segment. */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TimeThemeTokens.colors
    Row(
        modifier = modifier
            .background(colors.accS2, Shapes.pill)
            .padding(Dimens.s2),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            // Equal segments, each a 48dp target (rule 6), announced as a tab with its state —
            // it used to be a bare clickable that TalkBack read with no "selected".
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.touchMin - Dimens.s4)
                    .background(if (selected) colors.accC else Color.Transparent, Shapes.pill)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(horizontal = Dimens.s8, vertical = Dimens.s8),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
