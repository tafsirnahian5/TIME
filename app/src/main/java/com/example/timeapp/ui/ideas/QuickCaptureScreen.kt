package com.example.timeapp.ui.ideas

import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import com.example.timeapp.data.displayName
import com.example.timeapp.data.CollectionKind
import com.example.timeapp.data.IdeaCollection
import com.example.timeapp.data.L

import androidx.compose.ui.semantics.heading

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/** I4 — Quick capture: keyboard-up, one field, save and stay. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun QuickCaptureScreen(
    collections: List<IdeaCollection>,
    initialCollectionId: Long?,
    onBack: () -> Unit,
    onSave: (title: String, collectionId: Long?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    // The chips are the real collections — Sparks first, then the Bucket list, then the user's
    // own. They used to be three hard-coded names ("Reading" never existed), and the save looked
    // the collection up by name, so on a fresh install every idea was silently dropped.
    val ordered = remember(collections) {
        collections.sortedBy { when (it.kind) { CollectionKind.SPARKS -> 0; CollectionKind.BUCKET -> 1; else -> 2 } }
    }
    var chosen by remember { mutableStateOf(initialCollectionId) }
    val selectedId = chosen ?: ordered.firstOrNull { it.kind == CollectionKind.SPARKS }?.id
    val canSave = title.isNotBlank()
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.Close, contentDescription = L("Close"))
                }
                Text(L("New idea"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = Dimens.s6))
                // Disabled until there's something to save — an empty idea used to close the
                // screen as if it had been saved.
                androidx.compose.material3.TextButton(onClick = { onSave(title, selectedId) }, enabled = canSave) {
                    Text(L("Save"))
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.s20).verticalScroll(androidx.compose.foundation.rememberScrollState()).imePadding()) {
            TextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text(L("Learn to develop film at home"), style = MaterialTheme.typography.headlineSmall) },
                textStyle = MaterialTheme.typography.headlineSmall,
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent, focusedContainerColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent, focusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Text(L("SAVE TO"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(top = Dimens.s20, bottom = Dimens.s10))
            androidx.compose.foundation.layout.FlowRow(verticalArrangement = Arrangement.spacedBy(Dimens.s8)) {
                ordered.forEach { c ->
                    val icon = when (c.kind) {
                        CollectionKind.SPARKS -> Icons.Rounded.Bolt
                        CollectionKind.BUCKET -> Icons.AutoMirrored.Rounded.MenuBook
                        CollectionKind.LIST -> Icons.Rounded.AutoStories
                    }
                    SaveToChip(c.displayName, icon, c.id == selectedId) { chosen = c.id }
                }
            }
            Row(modifier = Modifier.padding(top = Dimens.s20)) {
                Icon(Icons.Rounded.EventBusy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = Dimens.s8))
                Text(
                    L("No due date, no estimate, no reminder. Add those later by moving it to Plan."),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SaveToChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val colors = TimeThemeTokens.colors
    Row(
        modifier = Modifier
            .padding(end = Dimens.s8)
            .background(if (selected) colors.accC else Color.Transparent, Shapes.pill)
            .border(if (selected) 0.dp else 1.dp, TimeThemeTokens.status.controlOutline, Shapes.pill)
            .semantics { this.selected = selected }.clickable(onClick = onClick)
            .padding(horizontal = Dimens.s14, vertical = Dimens.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = Dimens.s6))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
