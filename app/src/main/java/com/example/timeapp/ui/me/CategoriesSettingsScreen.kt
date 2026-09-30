package com.example.timeapp.ui.me

import com.example.timeapp.data.L

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.timeapp.data.Category
import com.example.timeapp.data.CategoryKind
import com.example.timeapp.data.isPartialTakaInput
import com.example.timeapp.data.parseTakaToMinor
import com.example.timeapp.data.toTaka
import com.example.timeapp.data.toTakaInput
import com.example.timeapp.ui.CategoryViewModel
import com.example.timeapp.ui.components.CategoryColorChoices
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/**
 * Settings › Categories — the user's own work and money lists.
 *
 * Categories are archived rather than deleted: records that already reference one keep
 * resolving their label, and an archived category simply stops being offered on new
 * records. Renaming or recolouring propagates everywhere, since records store the id.
 */
@Composable
fun CategoriesSettingsScreen(onBack: () -> Unit, viewModel: CategoryViewModel, onOpenProject: (Long) -> Unit = {}) {
    val all by viewModel.all.collectAsState()
    var editing by remember { mutableStateOf<Category?>(null) }
    var creatingKind by remember { mutableStateOf<CategoryKind?>(null) }
    var merging by remember { mutableStateOf<Category?>(null) }

    Scaffold(topBar = { TimeTopBar(title = L("Categories"), onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimens.s16)
                .verticalScroll(rememberScrollState()),
        ) {
            CategorySection(
                title = L("WORK"),
                categories = all.filter { it.kind == CategoryKind.WORK },
                onEdit = { editing = it },
                onMerge = { merging = it },
                onToggleArchive = { viewModel.setArchived(it, !it.archived) },
                onAdd = { creatingKind = CategoryKind.WORK },
            )
            CategorySection(
                title = L("MONEY"),
                categories = all.filter { it.kind == CategoryKind.MONEY },
                onEdit = { editing = it },
                onMerge = { merging = it },
                onToggleArchive = { viewModel.setArchived(it, !it.archived) },
                onAdd = { creatingKind = CategoryKind.MONEY },
            )
            CategorySection(
                title = L("PROJECTS"),
                categories = all.filter { it.kind == CategoryKind.PROJECT },
                onEdit = { editing = it },
                onMerge = { merging = it },
                onSummary = { onOpenProject(it.id) },
                onToggleArchive = { viewModel.setArchived(it, !it.archived) },
                onAdd = { creatingKind = CategoryKind.PROJECT },
            )
            Text(
                L("Archiving keeps a category or project on the records that already use it; it just stops appearing on new ones. Renaming or recolouring updates every view at once. Merging moves its records to another and archives it. Records with no category show as Uncategorised."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Dimens.s20),
            )
        }
    }

    editing?.let { category ->
        CategoryDialog(
            title = L("Edit category"),
            initialName = category.name,
            initialColor = category.colorArgb,
            showBudget = category.kind == CategoryKind.MONEY,
            initialBudgetMinor = category.budgetMinor,
            initialIcon = category.icon,
            onDismiss = { editing = null },
            onConfirm = { name, color, budgetMinor, icon ->
                viewModel.update(category.copy(name = name, colorArgb = color, budgetMinor = budgetMinor, icon = icon))
                editing = null
            },
        )
    }

    creatingKind?.let { kind ->
        CategoryDialog(
            title = when (kind) {
                CategoryKind.WORK -> L("New work category")
                CategoryKind.MONEY -> L("New money category")
                CategoryKind.PROJECT -> L("New project")
            },
            initialName = "",
            initialColor = CategoryColorChoices.first(),
            showBudget = kind == CategoryKind.MONEY,
            onDismiss = { creatingKind = null },
            onConfirm = { name, color, budgetMinor, icon ->
                viewModel.add(name, color, kind, budgetMinor, icon = icon)
                creatingKind = null
            },
        )
    }

    merging?.let { from ->
        val targets = all.filter { it.kind == from.kind && it.id != from.id && !it.archived }
        AlertDialog(
            onDismissRequest = { merging = null },
            title = { Text(L("Merge “%1\$s” into…", from.name)) },
            text = {
                Column {
                    Text(
                        L("Everything on “%1\$s” moves to the one you pick, and “%2\$s” is archived. Amounts and times don't change.", from.name, from.name),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = Dimens.s8),
                    )
                    if (targets.isEmpty()) Text(L("There's no other category of this kind to merge into."))
                    targets.forEach { t ->
                        Text(
                            t.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.merge(from, t); merging = null }.padding(vertical = Dimens.s14),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { merging = null }) { Text(L("Cancel")) } },
        )
    }
}

@Composable
private fun CategorySection(
    title: String,
    categories: List<Category>,
    onEdit: (Category) -> Unit,
    onToggleArchive: (Category) -> Unit,
    onAdd: () -> Unit,
    onMerge: (Category) -> Unit = {},
    onSummary: ((Category) -> Unit)? = null,
) {
    val colors = TimeThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Dimens.s20, bottom = Dimens.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        IconButton(onClick = onAdd) { Icon(Icons.Rounded.Add, contentDescription = L("Add category"), tint = colors.acc) }
    }
    Column(modifier = Modifier.fillMaxWidth().background(TimeThemeTokens.colors.card, Shapes.lg)) {
        if (categories.isEmpty()) {
            Text(
                L("None yet."),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(Dimens.s16),
            )
        }
        categories.forEach { category ->
            CategoryRow(category, onEdit = { onEdit(category) }, onToggleArchive = { onToggleArchive(category) }, onMerge = { onMerge(category) }, onSummary = onSummary?.let { f -> { f(category) } })
        }
    }
}

@Composable
private fun CategoryRow(category: Category, onEdit: () -> Unit, onToggleArchive: () -> Unit, onMerge: () -> Unit, onSummary: (() -> Unit)? = null) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(Dimens.s14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = com.example.timeapp.ui.components.CategoryIcons.of(category.icon)
        if (icon != null) Icon(icon, contentDescription = null, tint = Color(category.colorArgb), modifier = Modifier.size(Dimens.s20))
        else Box(modifier = Modifier.size(Dimens.s14).background(Color(category.colorArgb), CircleShape))
        Column(modifier = Modifier.weight(1f).padding(start = Dimens.s12)) {
            Text(category.name, style = MaterialTheme.typography.bodyLarge)
            val notes = listOfNotNull(
                if (category.archived) L("Archived") else null,
                category.budgetMinor?.let { L("%1\$s a month", it.toTaka()) },
            )
            if (notes.isNotEmpty()) {
                Text(notes.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = L("More"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(text = { Text(L("Rename, recolour or icon")) }, onClick = { menuExpanded = false; onEdit() })
                if (onSummary != null) DropdownMenuItem(text = { Text(L("Time & money summary")) }, onClick = { menuExpanded = false; onSummary() })
                DropdownMenuItem(text = { Text(L("Merge into…")) }, onClick = { menuExpanded = false; onMerge() })
                DropdownMenuItem(
                    text = { Text(if (category.archived) L("Restore") else L("Archive")) },
                    onClick = { menuExpanded = false; onToggleArchive() },
                )
            }
        }
    }
}

@Composable
private fun CategoryDialog(
    title: String,
    initialName: String,
    initialColor: Long,
    showBudget: Boolean = false,
    initialBudgetMinor: Long? = null,
    initialIcon: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Long, Long?, String?) -> Unit,
) {
    var icon by remember { mutableStateOf(initialIcon) }
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    var budget by remember { mutableStateOf(initialBudgetMinor?.toTakaInput() ?: "") }
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
                if (showBudget) {
                    OutlinedTextField(
                        value = budget,
                        onValueChange = { budget = it.takeIf(::isPartialTakaInput) ?: budget },
                        label = { Text(L("Monthly budget (optional)")) },
                        placeholder = { Text(L("e.g. 5000")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = Dimens.s12),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    )
                }
                Text(L("Icon"), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Dimens.s16, bottom = Dimens.s8))
                androidx.compose.foundation.layout.FlowRow {
                    (listOf<Pair<String?, androidx.compose.ui.graphics.vector.ImageVector?>>(null to null) + com.example.timeapp.ui.components.CategoryIcons.all).forEach { (key, vector) ->
                        val on = key == icon
                        Box(
                            modifier = Modifier.size(Dimens.touchMin).background(if (on) TimeThemeTokens.colors.accC else Color.Transparent, CircleShape)
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (vector != null) Icon(vector, contentDescription = key, modifier = Modifier.size(Dimens.s20))
                            else Box(Modifier.size(Dimens.s12).background(Color(color), CircleShape))
                        }
                    }
                }
                Text(L("Colour"), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Dimens.s16, bottom = Dimens.s8))
                Row {
                    CategoryColorChoices.forEach { choice ->
                        Box(
                            modifier = Modifier
                                .padding(end = Dimens.s10)
                                .size(Dimens.s26)
                                .background(Color(choice), CircleShape)
                                .border(
                                    if (choice == color) Dimens.s2 else Dimens.hairline,
                                    if (choice == color) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    CircleShape,
                                )
                                .clickable { color = choice },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, color, if (budget.isBlank()) null else parseTakaToMinor(budget), icon) },
                // A budget that was typed but doesn't parse blocks Save rather than saving
                // silently as "no budget".
                enabled = name.isNotBlank() && (budget.isBlank() || parseTakaToMinor(budget) != null),
            ) { Text(L("Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancel")) } },
    )
}
