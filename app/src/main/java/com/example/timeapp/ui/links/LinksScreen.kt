package com.example.timeapp.ui.links

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
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
import androidx.compose.ui.platform.LocalContext
import com.example.timeapp.data.Bookmark
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.ui.MiscViewModel
import com.example.timeapp.ui.components.ConfirmDeleteDialog
import com.example.timeapp.ui.components.Illustration
import com.example.timeapp.ui.components.EmptyState
import com.example.timeapp.ui.components.RootTopBar
import com.example.timeapp.ui.components.RootTopBarTrailing
import com.example.timeapp.ui.richtext.RichTextView
import com.example.timeapp.ui.richtext.toggleChecklistLine
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/** Which links the list shows: everything, favourites only, or one folder. */
private sealed class LinkFilter {
    data object All : LinkFilter()
    data object Favourites : LinkFilter()
    data object Archived : LinkFilter()
    data class Folder(val name: String) : LinkFilter()
}

/**
 * Links — saved bookmarks. Not covered by the real design canvas; built from the PDF's
 * bookmarks spec with the shared tokens, so lower-fidelity until a real mock exists. The PDF
 * asks for search over title, URL and note, folder/favourite filters, opening in the external
 * browser, and copy/share — all here now, plus linking a link to the task it's for.
 */
@Composable
fun LinksScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: MiscViewModel,
    tasks: List<Entry> = emptyList(),
    onLinkTask: (entryId: Long, bookmarkId: Long) -> Unit = { _, _ -> },
    onOpenTask: (Long) -> Unit = {},
) {
    val allBookmarks by viewModel.bookmarks.collectAsState()
    // Archived links leave the everyday list; the Archived chip shows only them.
    val bookmarks = allBookmarks.filter { it.archivedAt == null }
    val archived = allBookmarks.filter { it.archivedAt != null }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<LinkFilter>(LinkFilter.All) }
    var linking by remember { mutableStateOf<Bookmark?>(null) }
    var moving by remember { mutableStateOf<Bookmark?>(null) }
    val folders = allBookmarks.map { it.folder }.filter { it.isNotBlank() }.distinct().sorted()
    val pool = if (filter == LinkFilter.Archived) archived else bookmarks
    val shown = pool.filter { b ->
        when (val f = filter) {
            LinkFilter.All, LinkFilter.Archived -> true
            LinkFilter.Favourites -> b.favourite
            is LinkFilter.Folder -> b.folder == f.name
        } && (
            query.isBlank() || b.title.contains(query, ignoreCase = true) ||
                b.url.contains(query, ignoreCase = true) || b.note?.contains(query, ignoreCase = true) == true
            )
    }.sortedWith(compareByDescending<Bookmark> { it.favourite }.thenByDescending { it.id })
    val tasksByBookmark = tasks.flatMap { t -> t.bookmarkIds.map { it to t } }.groupBy({ it.first }, { it.second })

    Scaffold(
        topBar = {
            RootTopBar(
                help = com.example.timeapp.ui.components.TabHelps.links,
                title = L("Links"), onSearch = onSearch,
                onOpenSettings = onOpenSettings,
                trailing = RootTopBarTrailing.MORE_VERT,
            )
        },
        floatingActionButton = {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = TimeThemeTokens.colors.acc,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = Shapes.lg,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(L("Link")) },
            )
        },
    ) { padding ->
        if (allBookmarks.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Link,
                illustration = Illustration.LINKS,
                headline = L("No links saved yet"),
                supporting = L("Paste a URL, or share to T!ME from a browser."),
                actionLabel = L("Add link"),
                onAction = onAdd,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.screenHList)) {
            item {
                // The canvas's filled search pill.
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    placeholder = { Text(L("Search title, address or note")) },
                    singleLine = true,
                    shape = Shapes.pill,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = TimeThemeTokens.colors.accS2,
                        focusedContainerColor = TimeThemeTokens.colors.accS2,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s8),
                )
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = Dimens.s4)) {
                    LinkChip(L("All · %1\$s", bookmarks.size), filter == LinkFilter.All) { filter = LinkFilter.All }
                    LinkChip(L("Favourites"), filter == LinkFilter.Favourites, star = true) { filter = LinkFilter.Favourites }
                    folders.forEach { f -> LinkChip(f, filter == LinkFilter.Folder(f)) { filter = LinkFilter.Folder(f) } }
                    if (archived.isNotEmpty()) {
                        LinkChip(L("Archived · %1\$s", archived.size), filter == LinkFilter.Archived) { filter = LinkFilter.Archived }
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.FilterAlt,
                        headline = L("No matches"),
                        supporting = L("No saved link matches this search or filter."),
                        actionLabel = L("Show all"),
                        onAction = { query = ""; filter = LinkFilter.All },
                        modifier = Modifier.padding(vertical = Dimens.s24),
                    )
                }
            }
            // Favourites first as their own group, as the canvas lays them out.
            val favs = shown.filter { it.favourite }
            val rest = shown.filterNot { it.favourite }
            listOf("FAVOURITES" to favs, (if (favs.isEmpty()) "" else L("EVERYTHING ELSE")) to rest).forEach { (title, group) ->
                if (group.isEmpty()) return@forEach
                item(key = "header-$title") {
                    if (title.isNotEmpty()) {
                        Text(
                            title,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Dimens.s4, top = Dimens.s14, bottom = Dimens.s6),
                        )
                    }
                }
                item(key = "group-$title") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s4)
                            .background(TimeThemeTokens.colors.card, Shapes.xl)
                            .border(Dimens.hairline, TimeThemeTokens.colors.line, Shapes.xl),
                    ) {
                        group.forEachIndexed { i, bookmark ->
                            if (i > 0) Box(Modifier.padding(start = 66.dp).fillMaxWidth().height(Dimens.hairline).background(TimeThemeTokens.colors.line2))
                            BookmarkListItem(
                                bookmark,
                                linkedTasks = tasksByBookmark[bookmark.id].orEmpty(),
                                onEdit = { onEdit(bookmark.id) },
                                onDelete = { viewModel.deleteBookmark(bookmark.id) },
                                onToggleFavourite = { viewModel.updateBookmark(bookmark.copy(favourite = !bookmark.favourite)) },
                                onLinkTask = { linking = bookmark },
                                onOpenTask = onOpenTask,
                                onToggleNote = { lineIndex -> viewModel.updateBookmark(bookmark.copy(note = toggleChecklistLine(bookmark.note ?: "", lineIndex))) },
                                onMove = { moving = bookmark },
                                onArchive = { if (bookmark.archivedAt == null) viewModel.archiveBookmark(bookmark) else viewModel.unarchiveBookmark(bookmark.id) },
                            )
                        }
                    }
                }
            }
            item { Box(Modifier.padding(bottom = Dimens.s34 * 2)) }
        }
    }

    moving?.let { bookmark ->
        MoveFolderDialog(
            current = bookmark.folder,
            folders = folders,
            onMove = { folder -> viewModel.moveBookmark(bookmark, folder); moving = null },
            onDismiss = { moving = null },
        )
    }

    linking?.let { bookmark ->
        val candidates = tasks.filter { it.kind == EntryKind.TASK && it.status != EntryStatus.DONE && bookmark.id !in it.bookmarkIds }
        AlertDialog(
            onDismissRequest = { linking = null },
            title = { Text(L("Link to a task")) },
            text = {
                Column {
                    if (candidates.isEmpty()) Text(L("No open task to link to."), style = MaterialTheme.typography.bodySmall)
                    candidates.take(10).forEach { t ->
                        Text(
                            t.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { onLinkTask(t.id, bookmark.id); linking = null }.padding(vertical = Dimens.s12),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { linking = null }) { Text(L("Cancel")) } },
        )
    }
}

@Composable
private fun BookmarkListItem(
    bookmark: Bookmark,
    linkedTasks: List<Entry>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavourite: () -> Unit,
    onLinkTask: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onToggleNote: (Int) -> Unit,
    onMove: () -> Unit,
    onArchive: () -> Unit,
) {
    var confirmingDelete by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val tile = tileColors(bookmark.folder)
    Row(
        modifier = Modifier.fillMaxWidth().clickable { openInBrowser(context, bookmark.url) }.padding(start = Dimens.s14, top = Dimens.s10, bottom = Dimens.s10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A letter tile from the site's name, coloured by folder — no image loading (stack rule).
        Box(
            modifier = Modifier.size(40.dp).background(tile.first, Shapes.md),
            contentAlignment = Alignment.Center,
        ) {
            Text(siteLetter(bookmark), style = MaterialTheme.typography.titleSmall, color = tile.second)
        }
        Column(modifier = Modifier.weight(1f).padding(start = Dimens.s12)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(bookmark.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (bookmark.favourite) {
                    Icon(Icons.Rounded.Star, contentDescription = L("Favourite"), tint = TimeThemeTokens.colors.acc, modifier = Modifier.padding(start = Dimens.s4).size(14.dp))
                }
            }
            Text(
                listOfNotNull(hostOf(bookmark.url), bookmark.folder, bookmark.addedAt?.let { L("Added %1\$s", addedLabel(it)) }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            linkedTasks.forEach { t ->
                Text(
                    L("For: %1\$s", t.title),
                    style = MaterialTheme.typography.labelSmall,
                    color = TimeThemeTokens.colors.acc,
                    modifier = Modifier
                        .padding(top = Dimens.s4)
                        .background(TimeThemeTokens.colors.accS, Shapes.pill)
                        .clickable { onOpenTask(t.id) }
                        .padding(horizontal = Dimens.s8, vertical = Dimens.s4),
                )
            }
            if (!bookmark.note.isNullOrBlank()) {
                RichTextView(raw = bookmark.note, modifier = Modifier.padding(top = Dimens.s4), onToggleChecklistItem = onToggleNote)
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = L("More for %1\$s", bookmark.title))
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(text = { Text(L("Copy link")) }, onClick = {
                    menuExpanded = false
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText(bookmark.title, bookmark.url))
                    Toast.makeText(context, L("Link copied"), Toast.LENGTH_SHORT).show()
                })
                DropdownMenuItem(text = { Text(L("Share")) }, onClick = {
                    menuExpanded = false
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, bookmark.url)
                        .putExtra(Intent.EXTRA_SUBJECT, bookmark.title)
                    context.startActivity(Intent.createChooser(send, null))
                })
                DropdownMenuItem(
                    text = { Text(if (bookmark.favourite) L("Remove from favourites") else L("Add to favourites")) },
                    onClick = { menuExpanded = false; onToggleFavourite() },
                )
                DropdownMenuItem(text = { Text(L("Link to a task")) }, onClick = { menuExpanded = false; onLinkTask() })
                DropdownMenuItem(text = { Text(L("Move to folder…")) }, onClick = { menuExpanded = false; onMove() })
                DropdownMenuItem(
                    text = { Text(if (bookmark.archivedAt == null) L("Archive") else L("Unarchive")) },
                    onClick = { menuExpanded = false; onArchive() },
                )
                DropdownMenuItem(text = { Text(L("Edit")) }, onClick = { menuExpanded = false; onEdit() })
                DropdownMenuItem(text = { Text(L("Delete")) }, onClick = { menuExpanded = false; confirmingDelete = true })
            }
        }
    }
    if (confirmingDelete) {
        ConfirmDeleteDialog(
            undoable = true,
            itemLabel = bookmark.title,
            onConfirm = { confirmingDelete = false; onDelete() },
            onDismiss = { confirmingDelete = false },
            detail = if (linkedTasks.isNotEmpty()) L("It's unlinked from its tasks; the tasks stay.") else null,
        )
    }
}

/** "3 Sep", or "3 Sep 2025" outside this year. */
private fun addedLabel(at: java.time.Instant): String {
    val date = at.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    // Through displayPattern like every other date a person reads: this used a raw formatter
    // (so "Sep" beside the app's "Sept", no Bangla months, no date order) over a pattern that
    // was itself run through L() — date patterns are never translated.
    val pattern = if (date.year == java.time.LocalDate.now().year) "d MMM" else "d MMM yyyy"
    return date.format(com.example.timeapp.data.displayPattern(pattern))
}

/** Pick an existing folder or type a new one. */
@Composable
private fun MoveFolderDialog(current: String, folders: List<String>, onMove: (String) -> Unit, onDismiss: () -> Unit) {
    var newFolder by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("Move to folder")) },
        text = {
            Column {
                folders.filter { it != current }.forEach { f ->
                    Text(
                        f,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin).clickable { onMove(f) }.padding(vertical = Dimens.s12),
                    )
                }
                OutlinedTextField(
                    value = newFolder,
                    onValueChange = { newFolder = it },
                    label = { Text(L("New folder")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onMove(newFolder) }, enabled = newFolder.isNotBlank() && newFolder.trim() != current) { Text(L("Move")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancel")) } },
    )
}

/** Opens a saved link in the external browser — the PDF's "may need internet" path. */
fun openInBrowser(context: android.content.Context, url: String) {
    val target = if (url.contains("://")) url else "https://$url"
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, L("No app can open this link"), Toast.LENGTH_SHORT).show()
    }
}

/** "developer.android.com" from a saved URL, for the row's second line. */
private fun hostOf(url: String): String =
    runCatching { java.net.URI(if (url.contains("://")) url else "https://$url").host?.removePrefix("www.") }.getOrNull() ?: url

/** The tile letter: the first letter of the site's name, else of the title. */
private fun siteLetter(b: Bookmark): String =
    (hostOf(b.url).firstOrNull { it.isLetterOrDigit() } ?: b.title.firstOrNull { it.isLetterOrDigit() } ?: '•').uppercaseChar().toString()

/** A stable container/ink pair per folder, from the palette the canvas uses. */
@Composable
private fun tileColors(folder: String): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    val c = TimeThemeTokens.colors
    val s = TimeThemeTokens.status
    val options = listOf(c.accC to c.accInk, s.ok to s.okInk, s.warn to s.warnInk, s.neutral to s.neutralInk)
    return options[(folder.lowercase().hashCode() and Int.MAX_VALUE) % options.size]
}

@Composable
private fun LinkChip(label: String, selected: Boolean, star: Boolean = false, onClick: () -> Unit) {
    val colors = TimeThemeTokens.colors
    Row(
        modifier = Modifier
            .padding(end = Dimens.s8)
            .heightIn(min = Dimens.touchMin)
            .padding(vertical = Dimens.s6)
            .background(if (selected) colors.accC else androidx.compose.ui.graphics.Color.Transparent, Shapes.pill)
            .then(if (selected) Modifier else Modifier.border(Dimens.hairline, colors.edge, Shapes.pill))
            .semantics { this.selected = selected }.clickable(onClick = onClick)
            .padding(horizontal = Dimens.s14, vertical = Dimens.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (star) Icon(Icons.Rounded.Star, contentDescription = null, tint = colors.acc, modifier = Modifier.padding(end = Dimens.s6).size(14.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
