package com.example.timeapp.ui.plan

import com.example.timeapp.data.L

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import com.example.timeapp.data.Category
import com.example.timeapp.data.SavedFilter
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/**
 * Project and tag filters for the Plan List and Board — the PDF's "filters for project, tag,
 * priority, deadline and unscheduled work" — plus the user's **saved filters** (Phase 14): a
 * starred chip applies its project/tag in one tap, *+ Save filter* names the current one, and
 * holding a saved chip removes it. Drawn only once there is something to filter by, so a user
 * who never makes a project never sees an empty control.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlanScopeRow(
    projects: List<Category>,
    tags: List<String>,
    scope: PlanScope,
    onScope: (PlanScope) -> Unit,
    modifier: Modifier = Modifier,
    savedFilters: List<SavedFilter> = emptyList(),
    onSavedFiltersChange: (List<SavedFilter>) -> Unit = {},
) {
    if (projects.isEmpty() && tags.isEmpty() && !scope.isActive && savedFilters.isEmpty()) return
    var naming by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<SavedFilter?>(null) }
    val colors = TimeThemeTokens.colors
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Dimens.screenHList),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        savedFilters.forEach { f ->
            val on = f.scope == scope
            Text(
                "★ ${f.name}",
                style = MaterialTheme.typography.labelMedium,
                color = if (on) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(end = Dimens.s8)
                    .heightIn(min = Dimens.touchMin)
                    .padding(vertical = Dimens.s8)
                    .background(if (on) colors.accC else Color.Transparent, Shapes.pill)
                    .border(Dimens.hairline, if (on) Color.Transparent else colors.edge, Shapes.pill)
                    .semantics {
                        this.selected = on
                        onLongClick(label = L("Remove saved filter")) { removing = f; true }
                    }
                    .combinedClickable(
                        onClick = { onScope(if (on) PlanScope() else f.scope) },
                        onLongClick = { removing = f },
                    )
                    .padding(horizontal = Dimens.s12, vertical = Dimens.s6),
            )
        }
        if (projects.isNotEmpty() || scope.projectId != null) {
            com.example.timeapp.ui.components.FilterDropdownChip(
                label = projects.firstOrNull { it.id == scope.projectId }?.name?.let { L("Project: %1\$s", it) } ?: L("Any project"),
                active = scope.projectId != null,
                options = listOf<Pair<String, Long?>>(L("Any project") to null) + projects.map { it.name to it.id },
                onPick = { onScope(scope.copy(projectId = it)) },
            )
        }
        if (tags.isNotEmpty() || scope.tag != null) {
            com.example.timeapp.ui.components.FilterDropdownChip(
                label = scope.tag?.let { "#$it" } ?: L("Any tag"),
                active = scope.tag != null,
                options = listOf<Pair<String, String?>>(L("Any tag") to null) + tags.map { "#$it" to it },
                onPick = { onScope(scope.copy(tag = it)) },
            )
        }
        if (scope.isActive && savedFilters.none { it.scope == scope }) {
            TextButton(onClick = { naming = true }) { Text(L("+ Save filter")) }
        }
    }

    if (naming) {
        var name by remember {
            mutableStateOf(
                listOfNotNull(projects.firstOrNull { it.id == scope.projectId }?.name, scope.tag?.let { "#$it" }).joinToString(" "),
            )
        }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text(L("Save this filter")) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(L("Name")) }, singleLine = true)
            },
            confirmButton = {
                TextButton(
                    enabled = com.example.timeapp.data.SavedFilters.clean(name).isNotEmpty(),
                    onClick = {
                        onSavedFiltersChange(savedFilters + SavedFilter(com.example.timeapp.data.SavedFilters.clean(name), scope))
                        naming = false
                    },
                ) { Text(L("Save")) }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text(L("Cancel")) } },
        )
    }
    removing?.let { f ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(L("Remove “%1\$s”?", f.name)) },
            text = { Text(L("Only the saved filter goes. No task changes.")) },
            confirmButton = {
                TextButton(onClick = { onSavedFiltersChange(savedFilters - f); removing = null }) { Text(L("Remove")) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(L("Cancel")) } },
        )
    }
}
