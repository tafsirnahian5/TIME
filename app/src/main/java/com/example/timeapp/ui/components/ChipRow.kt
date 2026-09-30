package com.example.timeapp.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/** Wrapping row of filter chips — used on capture and Ideas screens. */
@Composable
fun ChipRow(
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TimeThemeTokens.colors
    LazyRow(modifier = modifier) {
        items(options) { option ->
            val isSelected = option in selected
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(option) },
                label = { androidx.compose.material3.Text(option) },
                shape = Shapes.pill,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.accC,
                    selectedLabelColor = colors.accInk,
                ),
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}
