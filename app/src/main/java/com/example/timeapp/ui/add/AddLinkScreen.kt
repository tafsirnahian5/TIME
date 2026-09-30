package com.example.timeapp.ui.add

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.example.timeapp.data.Bookmark
import com.example.timeapp.data.UrlCheck
import com.example.timeapp.data.checkUrl
import com.example.timeapp.data.duplicateOf
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.richtext.RichTextField
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/** The starting folders; any folder the user has made since is offered alongside them.
 * "Unsorted" comes first and is the default — a new link used to land in "Work" unasked. */
val DefaultLinkFolders = listOf(L("Unsorted"), L("Work"), L("Coding"), L("Personal"))

/**
 * New/Edit link. The URL is checked as it's typed — web links only, a bare domain gets
 * https:// — and a link already saved is flagged (the PDF's "bookmark duplicate" state) with
 * a way to the existing one. A duplicate can still be saved: the warning informs, it doesn't
 * decide. Folders are the user's own; the three defaults are just a start.
 */
@Composable
fun AddLinkScreen(
    onBack: () -> Unit,
    onSave: (title: String, url: String, folder: String, note: String?, favourite: Boolean) -> Unit,
    existingBookmark: Bookmark? = null,
    initialUrl: String? = null,
    allBookmarks: List<Bookmark> = emptyList(),
    onOpenExisting: (Long) -> Unit = {},
) {
    val colors = TimeThemeTokens.colors
    // A shared link arrives as a URL with no title; seed a readable one from its host so
    // Save is reachable immediately, rather than blocking on a required field.
    var title by remember {
        mutableStateOf(
            existingBookmark?.title
                ?: initialUrl?.let { runCatching { android.net.Uri.parse(it).host }.getOrNull() }
                ?: "",
        )
    }
    var url by remember { mutableStateOf(existingBookmark?.url ?: initialUrl.orEmpty()) }
    var folder by remember { mutableStateOf(existingBookmark?.folder ?: DefaultLinkFolders.first()) }
    var note by remember { mutableStateOf(existingBookmark?.note ?: "") }
    // Editor draft (Phase 15): typed text survives leaving the form or the app being killed.
    val draft = com.example.timeapp.ui.components.rememberDraft("link", enabled = existingBookmark == null && initialUrl == null, fields = mapOf("title" to title, "url" to url, "note" to note)) { d -> title = d["title"].orEmpty(); url = d["url"].orEmpty(); note = d["note"].orEmpty() }
    var favourite by remember { mutableStateOf(existingBookmark?.favourite ?: false) }
    var creatingFolder by remember { mutableStateOf(false) }
    var extraFolders by remember { mutableStateOf(emptyList<String>()) }
    val folders = (DefaultLinkFolders + allBookmarks.map { it.folder } + extraFolders + folder)
        .map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    val check = checkUrl(url)
    val duplicate = (check as? UrlCheck.Ok)?.let { allBookmarks.duplicateOf(it.url, exceptId = existingBookmark?.id) }

    Scaffold(
        topBar = {
            TimeTopBar(
                title = if (existingBookmark != null) L("Edit link") else L("New link"),
                onBack = onBack,
                actions = {
                    TextButton(
                        onClick = { (check as? UrlCheck.Ok)?.let { draft.clear(); onSave(title.trim(), it.url, folder, note.ifBlank { null }, favourite) } },
                        enabled = title.isNotBlank() && check is UrlCheck.Ok,
                    ) { Text(L("Save")) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Dimens.s16),
        ) {
            com.example.timeapp.ui.components.DraftBanner(draft, Modifier.padding(top = Dimens.s8))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(L("URL")) },
                placeholder = { Text("example.com") },
                isError = url.isNotBlank() && check is UrlCheck.Invalid,
                supportingText = when {
                    url.isNotBlank() && check is UrlCheck.Invalid -> { { Text(check.reason) } }
                    check is UrlCheck.Ok && com.example.timeapp.data.hasCredentials(check.url) -> {
                        { Text(L("This address includes a sign-in name or password. It's saved as typed, but never shown in lists.")) }
                    }
                    else -> null
                },
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.s6, bottom = Dimens.s8),
                singleLine = true,
            )
            duplicate?.let { d ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s12).background(TimeThemeTokens.status.warn, Shapes.md).padding(Dimens.s12),
                ) {
                    Text(
                        L("Already saved as “%1\$s” in %2\$s.", d.title, d.folder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TimeThemeTokens.status.warnInk,
                    )
                    TextButton(onClick = { onOpenExisting(d.id) }) { Text(L("Edit that one instead")) }
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(L("Title")) },
                modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s16),
                singleLine = true,
            )
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s16), verticalAlignment = Alignment.CenterVertically) {
                Text(L("Favourite"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = favourite, onCheckedChange = { favourite = it })
            }
            Text(L("Folder"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = Dimens.s10))
            FlowRow(modifier = Modifier.padding(bottom = Dimens.s20)) {
                folders.forEach { f -> FolderChip(f, f == folder) { folder = f } }
                Text(
                    L("+ New"),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.acc,
                    modifier = Modifier
                        .padding(bottom = Dimens.s8)
                        .border(Dimens.hairline, colors.line, Shapes.pill)
                        .clickable { creatingFolder = true }
                        .heightIn(min = Dimens.touchMin)
                        .padding(horizontal = Dimens.s14)
                        .wrapContentHeight(),
                )
            }
            RichTextField(
                value = note,
                onValueChange = { note = it },
                label = L("Notes"),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (creatingFolder) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creatingFolder = false },
            title = { Text(L("New folder")) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(L("Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    extraFolders = extraFolders + name.trim()
                    folder = name.trim()
                    creatingFolder = false
                }, enabled = name.isNotBlank()) { Text(L("Create")) }
            },
            dismissButton = { TextButton(onClick = { creatingFolder = false }) { Text(L("Cancel")) } },
        )
    }
}

@Composable
private fun FolderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = TimeThemeTokens.colors
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(end = Dimens.s8, bottom = Dimens.s8)
            .background(if (selected) colors.accC else Color.Transparent, Shapes.pill)
            .then(if (selected) Modifier else Modifier.border(Dimens.hairline, colors.line, Shapes.pill))
            .semantics { this.selected = selected }.clickable(onClick = onClick)
            .heightIn(min = Dimens.touchMin)
            .padding(horizontal = Dimens.s14)
            .wrapContentHeight(),
    )
}
