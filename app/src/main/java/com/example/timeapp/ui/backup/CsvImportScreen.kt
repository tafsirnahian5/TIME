package com.example.timeapp.ui.backup

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.heading

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

import com.example.timeapp.data.displayPattern

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.timeapp.data.Category
import com.example.timeapp.data.CsvImport
import com.example.timeapp.data.Direction
import com.example.timeapp.data.toTaka
import com.example.timeapp.ui.components.EmptyState
import com.example.timeapp.ui.components.FilterDropdownChip
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.money.MoneyViewModel
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/** Bigger than any plausible personal statement; refusing beats freezing on a stray file. */
private const val MAX_BYTES = 2_000_000
private val previewDate get() = displayPattern("d MMM yyyy")

/**
 * Settings › Data & backup › Import transactions. The PDF's "preview column mapping, decimal
 * parsing, dates and duplicates before a transactional import": nothing is written until
 * Import, every skipped line says why, likely duplicates are shown unticked, and the import
 * itself is one database transaction.
 *
 * Not in the design canvas; built from the shared tokens like Links.
 */
@Composable
fun CsvImportScreen(
    onBack: () -> Unit,
    viewModel: MoneyViewModel,
    moneyCategories: List<Category>,
) {
    val colors = TimeThemeTokens.colors
    val status = TimeThemeTokens.status
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val money by viewModel.uiState.collectAsState()

    var fileText by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var imported by remember { mutableStateOf<Int?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readNBytesCompat(MAX_BYTES + 1)
                        if (bytes.size > MAX_BYTES) null else bytes.decodeToString()
                    }
                }.getOrNull()
            }
            if (text == null) {
                loadError = L("Couldn't read that file, or it's larger than 2 MB.")
            } else {
                loadError = null
                fileText = text
            }
        }
    }

    val table = remember(fileText) { fileText?.let { CsvImport.parse(it) }.orEmpty() }
    var hasHeader by remember(table) { mutableStateOf(true) }
    val header = if (hasHeader) table.firstOrNull().orEmpty() else emptyList()
    val body = if (hasHeader) table.drop(1) else table
    val columnCount = table.maxOfOrNull { it.size } ?: 0
    val columnNames = (0 until columnCount).map { i -> header.getOrNull(i)?.takeIf { it.isNotBlank() } ?: L("Column %1\$s", (i + 1)) }
    var mapping by remember(table, hasHeader) {
        mutableStateOf(if (table.isEmpty()) null else CsvImport.guessMapping(header, body))
    }
    var walletId by remember(money.activeWallets) { mutableStateOf(money.activeWallets.firstOrNull()?.id) }

    val zone = ZoneId.systemDefault()
    val existing = remember(money.history) {
        money.history.mapNotNull { r ->
            val day = r.settledAt?.atZone(zone)?.toLocalDate() ?: return@mapNotNull null
            CsvImport.fingerprintOf(day, r.direction, r.paidMinor, r.title)
        }.toSet()
    }
    val rows = remember(body, mapping, existing) {
        mapping?.let { CsvImport.buildRows(body, it, existing, firstLine = if (hasHeader) 2 else 1) }.orEmpty()
    }
    var chosen by remember(rows) { mutableStateOf(rows.filter { it.importable && !it.duplicate }.map { it.line }.toSet()) }
    val categoriesByName = moneyCategories.associate { it.name.trim().lowercase() to it.id }
    val unknownCategories = rows.filter { it.line in chosen }.mapNotNull { it.categoryName?.trim()?.lowercase() }
        .filter { it !in categoriesByName }.distinct()

    Scaffold(topBar = { TimeTopBar(title = L("Import transactions"), onBack = onBack) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.s16)) {
            imported?.let { count ->
                item {
                    EmptyState(
                        icon = Icons.Rounded.UploadFile,
                        headline = if (count == 1) L("1 transaction imported") else L("%1\$s transactions imported", count),
                        supporting = L("They're in Money › History now, and wallet balances include them. Delete any from there if something looks wrong."),
                        actionLabel = L("Done"),
                        onAction = onBack,
                        modifier = Modifier.padding(vertical = Dimens.s24),
                    )
                }
                return@LazyColumn
            }
            if (fileText == null || mapping == null) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.UploadFile,
                        headline = L("Import from a CSV file"),
                        supporting = L("A bank statement or a spreadsheet export. You'll see every row before anything is saved, and can untick any of them."),
                        actionLabel = L("Choose a CSV file"),
                        onAction = { picker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) },
                        modifier = Modifier.padding(vertical = Dimens.s24),
                    )
                    loadError?.let { Text(it, color = status.dangerInk, style = MaterialTheme.typography.bodySmall) }
                    if (fileText != null && table.isEmpty()) {
                        Text(L("That file has no rows."), color = status.dangerInk, style = MaterialTheme.typography.bodySmall)
                    }
                    if (money.activeWallets.isEmpty()) {
                        Text(
                            L("Add a wallet first — imported transactions need one to go into."),
                            style = MaterialTheme.typography.bodySmall,
                            color = status.warnInk,
                        )
                    }
                }
                return@LazyColumn
            }
            val m = mapping!!
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8), verticalAlignment = Alignment.CenterVertically) {
                    Text(L("First row is a header"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = hasHeader, onCheckedChange = { hasHeader = it })
                }
                SectionLabel(L("COLUMNS"))
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    ColumnPicker(L("Date"), m.date, columnNames, required = true) { mapping = m.copy(date = it) }
                    ColumnPicker(L("Amount"), m.amount, columnNames, required = true) { mapping = m.copy(amount = it) }
                    ColumnPicker(L("Title"), m.title, columnNames, required = true) { mapping = m.copy(title = it) }
                }
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    ColumnPicker(L("Type"), m.type, columnNames) { mapping = m.copy(type = it) }
                    ColumnPicker(L("Category"), m.category, columnNames) { mapping = m.copy(category = it) }
                    ColumnPicker(L("Note"), m.note, columnNames) { mapping = m.copy(note = it) }
                }
                FilterDropdownChip(
                    label = L("Dates: %1\$s", m.dateFormat),
                    active = true,
                    options = CsvImport.dateFormats.map { it to it },
                    onPick = { mapping = m.copy(dateFormat = it) },
                )
                if (m.type < 0) {
                    SectionLabel(L("NO TYPE COLUMN — READ AMOUNTS AS"))
                    FlowRow {
                        listOf(
                            CsvImport.SignRule.SIGNED to L("Minus = expense"),
                            CsvImport.SignRule.ALL_EXPENSES to L("All expenses"),
                            CsvImport.SignRule.ALL_INCOME to L("All income"),
                        ).forEach { (rule, label) -> Pill(label, m.signRule == rule) { mapping = m.copy(signRule = rule) } }
                    }
                }
                SectionLabel(L("INTO WALLET"))
                FlowRow {
                    money.activeWallets.forEach { w -> Pill(w.name, w.id == walletId) { walletId = w.id } }
                }
                val skipped = rows.count { !it.importable }
                val dupes = rows.count { it.importable && it.duplicate }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s16).background(colors.accC2, Shapes.lg).padding(Dimens.s14),
                ) {
                    Text(L("%1\$s of %2\$s rows ticked to import", chosen.size, rows.size), style = MaterialTheme.typography.bodyLarge, color = colors.accInk)
                    if (dupes > 0) Text(L("%1\$s look like duplicates — left unticked", dupes), style = MaterialTheme.typography.bodySmall, color = colors.accInk)
                    if (skipped > 0) Text(L("%1\$s can't be imported — each says why below", skipped), style = MaterialTheme.typography.bodySmall, color = colors.accInk)
                    if (unknownCategories.isNotEmpty()) {
                        Text(
                            L("No category called %1\$s", (unknownCategories.take(3).joinToString { "\"$it\"" })) +
                                (if (unknownCategories.size > 3) L(" and %1\$s more", (unknownCategories.size - 3)) else "") +
                                L(" — those import uncategorised."),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.accInk,
                        )
                    }
                }
                Button(
                    onClick = {
                        val wallet = walletId ?: return@Button
                        viewModel.importTransactions(rows.filter { it.line in chosen }, wallet, categoriesByName) { imported = it }
                    },
                    enabled = chosen.isNotEmpty() && walletId != null,
                    modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s12),
                ) { Text(if (chosen.size == 1) L("Import 1 transaction") else L("Import %1\$s transactions", chosen.size)) }
            }
            items(rows, key = { it.line }) { row ->
                val ticked = row.line in chosen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.touchMin)
                        .clickable(enabled = row.importable) {
                            chosen = if (ticked) chosen - row.line else chosen + row.line
                        }
                        .padding(vertical = Dimens.s4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = ticked, onCheckedChange = null, enabled = row.importable)
                    Column(modifier = Modifier.weight(1f).padding(start = Dimens.s8)) {
                        Text(row.title.ifEmpty { L("(no title)") }, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            L("Line %1\$s", row.line) + (row.date?.let { " · ${it.format(previewDate)}" } ?: "") +
                                (row.categoryName?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        when {
                            row.error != null -> Text(row.error, style = MaterialTheme.typography.bodySmall, color = status.dangerInk)
                            row.duplicate -> Text(L("Possible duplicate — tick to import anyway"), style = MaterialTheme.typography.bodySmall, color = status.warnInk)
                        }
                    }
                    if (row.amountMinor != null && row.direction != null) {
                        Text(
                            (if (row.direction == Direction.INCOME) "+" else "-") + row.amountMinor.toTaka(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (row.direction == Direction.INCOME) status.okInk else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            item { Text("", modifier = Modifier.padding(bottom = Dimens.s24)) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(top = Dimens.s16, bottom = Dimens.s8))
}

@Composable
private fun ColumnPicker(label: String, index: Int, columns: List<String>, required: Boolean = false, onPick: (Int) -> Unit) {
    val options = (if (required) emptyList() else listOf(L("None") to -1)) + columns.mapIndexed { i, name -> name to i }
    FilterDropdownChip(
        label = "$label: " + (columns.getOrNull(index) ?: L("None")),
        active = index >= 0,
        options = options,
        onPick = onPick,
    )
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = TimeThemeTokens.colors
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(end = Dimens.s8, bottom = Dimens.s8)
            .background(if (selected) colors.accC else Color.Transparent, Shapes.pill)
            .then(if (selected) Modifier else Modifier.border(Dimens.hairline, TimeThemeTokens.status.controlOutline, Shapes.pill))
            .semantics { this.selected = selected }.clickable(onClick = onClick)
            .heightIn(min = Dimens.touchMin)
            .padding(horizontal = Dimens.s14, vertical = Dimens.s14),
    )
}

/** InputStream.readNBytes is API 33+; minSdk is 26. */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
