package com.example.timeapp.ui.backup

import com.example.timeapp.data.L

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.timeapp.data.BookmarkHtml
import com.example.timeapp.ui.MiscViewModel
import com.example.timeapp.ui.components.EmptyState
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_HTML_BYTES = 5_000_000

/**
 * Settings › Data & backup › Import bookmarks. Like CSV import: every link is shown before
 * anything is saved, links already saved are unticked (not dropped), entries that aren't web
 * links say why they're skipped, and the import is one statement. Not in the design canvas.
 */
@Composable
fun BookmarkImportScreen(onBack: () -> Unit, viewModel: MiscViewModel) {
    val colors = TimeThemeTokens.colors
    val status = TimeThemeTokens.status
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by viewModel.bookmarks.collectAsState()
    var rows by remember { mutableStateOf<List<BookmarkHtml.Row>?>(null) }
    var chosen by remember { mutableStateOf(emptySet<Int>()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var imported by remember { mutableStateOf<Int?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val html = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        if (bytes.size > MAX_HTML_BYTES) null else bytes.decodeToString()
                    }
                }.getOrNull()
            }
            val parsed = html?.let { BookmarkHtml.parse(it) }
            when {
                parsed == null -> loadError = L("Couldn't read that file, or it's larger than 5 MB.")
                parsed.isEmpty() -> loadError = L("No bookmarks found in that file.")
                else -> {
                    loadError = null
                    val checked = BookmarkHtml.check(parsed, saved)
                    rows = checked
                    chosen = checked.indices.filter { checked[it].importable && !checked[it].duplicate }.toSet()
                }
            }
        }
    }

    Scaffold(topBar = { TimeTopBar(title = L("Import bookmarks"), onBack = onBack) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.s16)) {
            imported?.let { n ->
                item {
                    EmptyState(
                        icon = Icons.Rounded.Bookmarks,
                        headline = if (n == 1) L("1 link imported") else L("%1\$s links imported", n),
                        supporting = L("They're in Links, in the folders they came from."),
                        actionLabel = L("Done"),
                        onAction = onBack,
                        modifier = Modifier.padding(vertical = Dimens.s24),
                    )
                }
                return@LazyColumn
            }
            val current = rows
            if (current == null) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Bookmarks,
                        headline = L("Import from a browser"),
                        supporting = L("Export your bookmarks from a browser as an HTML file, then choose it here. You'll see every link before anything is saved."),
                        actionLabel = L("Choose a bookmarks file"),
                        onAction = { picker.launch(arrayOf("text/html", "text/*", "application/octet-stream")) },
                        modifier = Modifier.padding(vertical = Dimens.s24),
                    )
                    loadError?.let { Text(it, color = status.dangerInk, style = MaterialTheme.typography.bodySmall) }
                }
                return@LazyColumn
            }
            item {
                val dupes = current.count { it.duplicate }
                val refused = current.count { it.error != null }
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s12).background(colors.accC2, Shapes.lg).padding(Dimens.s14)) {
                    Text(L("%1\$s of %2\$s links ticked", chosen.size, current.size), style = MaterialTheme.typography.bodyLarge, color = colors.accInk)
                    if (dupes > 0) Text(L("%1\$s already saved — left unticked", dupes), style = MaterialTheme.typography.bodySmall, color = colors.accInk)
                    if (refused > 0) Text(L("%1\$s aren't web links and can't be saved", refused), style = MaterialTheme.typography.bodySmall, color = colors.accInk)
                }
                Button(
                    onClick = { viewModel.importBookmarks(current.filterIndexed { i, _ -> i in chosen }) { imported = it } },
                    enabled = chosen.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s12),
                ) { Text(if (chosen.size == 1) L("Import 1 link") else L("Import %1\$s links", chosen.size)) }
            }
            itemsIndexed(current) { index, row ->
                val ticked = index in chosen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.touchMin)
                        .clickable(enabled = row.importable) { chosen = if (ticked) chosen - index else chosen + index }
                        .padding(vertical = Dimens.s4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = ticked, onCheckedChange = null, enabled = row.importable)
                    Column(modifier = Modifier.weight(1f).padding(start = Dimens.s8)) {
                        Text(row.parsed.title, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${row.parsed.folder} · ${com.example.timeapp.data.displayUrl(row.parsed.url)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        when {
                            row.error != null -> Text(row.error, style = MaterialTheme.typography.bodySmall, color = status.dangerInk)
                            row.duplicate -> Text(L("Already saved — tick to import anyway"), style = MaterialTheme.typography.bodySmall, color = status.warnInk)
                        }
                    }
                }
            }
            item { Text("", modifier = Modifier.padding(bottom = Dimens.s24)) }
        }
    }
}
