package com.example.timeapp.ui.ideas

import com.example.timeapp.data.displayName
import androidx.compose.material.icons.rounded.Add
import com.example.timeapp.data.L

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.timeapp.data.CollectionKind
import com.example.timeapp.ui.components.Illustration
import com.example.timeapp.ui.components.EmptyState
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens

/** A collection's ideas — not in the real design canvas (Ideas has no PDF spec at all, and the
 * canvas never drew a generic collection screen, only the pinned Bucket list). Reuses the
 * simple row pattern from BucketListScreen for any non-Bucket collection. The user's own
 * collections can be renamed and deleted; deleting moves their ideas to Sparks. */
@Composable
fun CollectionDetailScreen(
    collectionId: Long,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    viewModel: IdeasViewModel,
    onAdd: () -> Unit = {},
) {
    val ui by viewModel.uiState.collectAsState()
    val allIdeas by viewModel.allIdeas.collectAsState()
    val collection = ui.collections.firstOrNull { it.id == collectionId }
    val ideas = allIdeas.filter { it.collectionId == collectionId }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val ownCollection = collection?.kind == CollectionKind.LIST

    Scaffold(
        topBar = {
            TimeTopBar(title = collection?.displayName ?: L("Collection"), onBack = onBack) {
                if (ownCollection) {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = L("More for this collection"))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(L("Rename")) }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(text = { Text(L("Delete")) }, onClick = { menu = false; deleting = true })
                        }
                    }
                }
            }
        },
        // A collection had no way to add an idea to it at all.
        floatingActionButton = {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = com.example.timeapp.ui.theme.TimeThemeTokens.colors.accC,
                contentColor = com.example.timeapp.ui.theme.TimeThemeTokens.colors.accInk,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(L("New idea")) },
            )
        },
    ) { padding ->
        if (ideas.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Description,
                illustration = Illustration.IDEAS,
                headline = L("Nothing here yet"),
                supporting = L("Ideas you save to this collection show up here."),
                actionLabel = L("Add an idea"),
                onAction = onAdd,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.s16)) {
                items(ideas) { idea ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenNote(idea.id) }.padding(vertical = Dimens.s10),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(idea.title, style = MaterialTheme.typography.bodyLarge)
                            if (!idea.body.isNullOrBlank()) {
                                Text(idea.body, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                        }
                        if (idea.promotedToPlan) {
                            Text(L("In Plan"), style = MaterialTheme.typography.labelSmall, color = com.example.timeapp.ui.theme.TimeThemeTokens.colors.acc)
                        }
                    }
                }
            }
        }
    }

    if (renaming && collection != null) {
        var name by remember { mutableStateOf(collection.name) }
        var problem by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(L("Rename collection")) },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; problem = null },
                    label = { Text(L("Name")) }, singleLine = true,
                    isError = problem != null,
                    supportingText = problem?.let { p -> { Text(p) } },
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    viewModel.renameCollection(collection.id, name) { err -> if (err == null) renaming = false else problem = err }
                }) { Text(L("Save")) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(L("Cancel")) } },
        )
    }
    if (deleting && collection != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(L("Delete “%1\$s”?", collection.name)) },
            text = {
                Text(
                    if (ideas.isEmpty()) L("The collection is empty, so nothing else changes.")
                    else if (ideas.size == 1) L("Its 1 idea moves to Sparks — nothing is deleted but the collection.")
                    else L("Its %1\$s ideas move to Sparks — nothing is deleted but the collection.", ideas.size),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCollection(collection.id) { err -> if (err == null) { deleting = false; onBack() } }
                }) { Text(L("Delete")) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(L("Cancel")) } },
        )
    }
}
