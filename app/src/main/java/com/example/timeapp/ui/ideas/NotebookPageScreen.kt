package com.example.timeapp.ui.ideas

import com.example.timeapp.data.displayName
import com.example.timeapp.data.L

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MoveUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.timeapp.ui.MiscViewModel
import com.example.timeapp.ui.components.ConfirmDeleteDialog
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.richtext.RichTextField
import com.example.timeapp.ui.richtext.RichTextView
import com.example.timeapp.ui.richtext.toggleChecklistLine
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.TimeThemeTokens

/**
 * I3 — Notebook page for one real [Idea]. Title and body (with real markdown-lite formatting,
 * including a tappable checklist) are both real and editable. The illustrative fake checklist/
 * tags/linked sections this screen used to show below the body — unrelated placeholder content
 * about the app's own development, always the same regardless of which idea was open — are
 * removed; a real checklist lives inside the body itself now.
 */
@Composable
fun NotebookPageScreen(
    ideaId: Long,
    onBack: () -> Unit,
    onPromoteToPlan: (title: String) -> Unit,
    viewModel: IdeasViewModel,
    miscViewModel: MiscViewModel,
) {
    val colors = TimeThemeTokens.colors
    val ideas by viewModel.allIdeas.collectAsState()
    val idea = ideas.firstOrNull { it.id == ideaId } ?: return
    val ideasUi by viewModel.uiState.collectAsState()
    val collection = ideasUi.collections.firstOrNull { it.id == idea.collectionId }

    var editing by remember(idea.id) { mutableStateOf(false) }
    var titleDraft by remember(idea.id) { mutableStateOf(idea.title) }
    var bodyDraft by remember(idea.id) { mutableStateOf(idea.body ?: "") }
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TimeTopBar(title = "", onBack = onBack) {
                // "Save as bookmark" used to put the idea in Links as the address "idea:N" — not
                // a web link, so it could never be opened. Removed.
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = L("More"))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text(L("Delete")) }, onClick = { menuExpanded = false; confirmingDelete = true })
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.s20)) {
            item {
                Text(
                    // Where it lives; this was "ADDED ·" built from a fake demo label.
                    collection?.let { L("IN %1\$s · NO DUE DATE", it.displayName.uppercase(com.example.timeapp.data.Lang.locale)) } ?: L("NO DUE DATE"),
                    style = MaterialTheme.typography.labelSmall,
                )
                if (editing) {
                    OutlinedTextField(
                        value = titleDraft,
                        onValueChange = { titleDraft = it },
                        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s16),
                        singleLine = true,
                    )
                    RichTextField(
                        value = bodyDraft,
                        onValueChange = { bodyDraft = it },
                        label = L("Notes"),
                        modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s16),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.s10), modifier = Modifier.padding(bottom = Dimens.s20)) {
                        TextButton(onClick = {
                            viewModel.updateIdea(idea.copy(title = titleDraft, body = bodyDraft.ifBlank { null }))
                            editing = false
                        }) { Text(L("Save")) }
                        TextButton(onClick = { editing = false }) { Text(L("Cancel")) }
                    }
                } else {
                    Text(idea.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = Dimens.s16))
                    if (idea.body != null) {
                        RichTextView(
                            raw = idea.body,
                            modifier = Modifier.padding(bottom = Dimens.s16),
                            onToggleChecklistItem = { lineIndex ->
                                viewModel.updateIdea(idea.copy(body = toggleChecklistLine(idea.body, lineIndex)))
                            },
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s20), horizontalArrangement = Arrangement.spacedBy(Dimens.s10)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.markPromoted(idea.id)
                                onPromoteToPlan(idea.title)
                            },
                            enabled = !idea.promotedToPlan,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.MoveUp, contentDescription = null, modifier = Modifier.size(19.dp))
                            Text(if (idea.promotedToPlan) L("Already in Plan") else L("Move to Plan"), modifier = Modifier.padding(start = Dimens.s8))
                        }
                        IconButton(onClick = { editing = true }) { Icon(Icons.Rounded.Edit, contentDescription = L("Edit")) }
                    }
                    // Moving it used to close the page with no word; it now stays and says where it went.
                    if (idea.promotedToPlan) {
                        Text(
                            L("It's a task in Plan now, under No date yet. The idea stays here too."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = Dimens.s16),
                        )
                    }
                }
            }
        }
    }

    if (confirmingDelete) {
        ConfirmDeleteDialog(
            itemLabel = idea.title,
            onConfirm = {
                confirmingDelete = false
                viewModel.deleteIdea(idea.id)
                onBack()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}
