package com.example.timeapp.ui.components

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.timeapp.data.Category
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/** The palette offered when creating a category — the same hues the starter set uses. */
val CategoryColorChoices = listOf(
    0xFF6C4ED9, 0xFF2E7DE8, 0xFF12A05C, 0xFFE5A32B, 0xFFB3261E, 0xFF7A5411,
)

/**
 * Picks one of the user's own categories, with a working "+ New" that actually creates one.
 * Replaces the old fixed three-preset chip row whose "+ New" chip was explicitly coded to
 * do nothing.
 */
@Composable
fun CategoryPicker(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onCreate: (name: String, colorArgb: Long) -> Unit,
    modifier: Modifier = Modifier,
    createTitle: String = L("New category"),
) {
    val colors = TimeThemeTokens.colors
    var showCreate by remember { mutableStateOf(false) }

    FlowRow(modifier = modifier.fillMaxWidth()) {
        categories.forEach { category ->
            val selected = category.id == selectedId
            Row(
                modifier = Modifier
                    .padding(end = Dimens.s8, bottom = Dimens.s8)
                    .background(if (selected) colors.accC else Color.Transparent, Shapes.pill)
                    .border(if (selected) 0.dp else Dimens.hairline, colors.line, Shapes.pill)
                    .semantics { this.selected = selected }.clickable { onSelect(category.id) }
                    .padding(horizontal = Dimens.s14, vertical = Dimens.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val icon = CategoryIcons.of(category.icon)
                if (icon != null) androidx.compose.material3.Icon(icon, contentDescription = null, tint = Color(category.colorArgb), modifier = Modifier.size(Dimens.s16))
                else Box(modifier = Modifier.size(Dimens.markerDot).background(Color(category.colorArgb), CircleShape))
                Text(
                    category.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Dimens.s8),
                )
            }
        }
        Text(
            L("+ New"),
            style = MaterialTheme.typography.labelMedium,
            color = colors.acc,
            modifier = Modifier
                .padding(bottom = Dimens.s8)
                .border(Dimens.hairline, colors.line, Shapes.pill)
                .clickable { showCreate = true }
                .padding(horizontal = Dimens.s14, vertical = Dimens.s8),
        )
    }

    if (showCreate) {
        NewCategoryDialog(
            title = createTitle,
            onDismiss = { showCreate = false },
            onConfirm = { name, color ->
                showCreate = false
                onCreate(name, color)
            },
        )
    }
}

@Composable
private fun NewCategoryDialog(title: String, onDismiss: () -> Unit, onConfirm: (String, Long) -> Unit) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(CategoryColorChoices.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(L("Name")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(L("Colour"), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Dimens.s16, bottom = Dimens.s8))
                Row {
                    CategoryColorChoices.forEach { choice ->
                        Box(
                            modifier = Modifier
                                .padding(end = Dimens.s10)
                                .size(Dimens.s26)
                                .background(Color(choice), CircleShape)
                                .border(
                                    if (choice == color) Dimens.s2 else 0.dp,
                                    MaterialTheme.colorScheme.onSurface,
                                    CircleShape,
                                )
                                .clickable { color = choice },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, color) }, enabled = name.isNotBlank()) { Text(L("Create")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancel")) } },
    )
}
