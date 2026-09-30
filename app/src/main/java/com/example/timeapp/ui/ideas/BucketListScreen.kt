package com.example.timeapp.ui.ideas

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.timeapp.data.Idea
import com.example.timeapp.data.toTaka
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.components.Illustration
import com.example.timeapp.ui.components.EmptyState
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material.icons.rounded.Add
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/** I2 — Bucket list: open + costs-money flags; no deadline on any of them. */
@Composable
fun BucketListScreen(onBack: () -> Unit, onAdd: () -> Unit, viewModel: IdeasViewModel) {
    val colors = TimeThemeTokens.colors
    val ui by viewModel.uiState.collectAsState()
    var showOpen by remember { mutableStateOf(true) }
    var showDone by remember { mutableStateOf(true) }
    Scaffold(
        floatingActionButton = {
            // The screen had no way to add a bucket item at all — the only route in was the
            // Ideas tab's FAB, which is not where you are when you are looking at the list.
            FloatingActionButton(onClick = onAdd, containerColor = colors.acc, shape = Shapes.lg) {
                Icon(Icons.Rounded.Add, contentDescription = L("Add to bucket list"), tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
        topBar = {
            TimeTopBar(title = L("Bucket list"), onBack = onBack) {
                Icon(Icons.Rounded.MoreVert, contentDescription = L("More"))
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.s16)) {
            // Nothing yet: the empty state below says so — "0 of 0 done" over an empty bar, and
            // filters with nothing to filter, only added noise.
            if (ui.bucketOpen.isNotEmpty() || ui.bucketDone.isNotEmpty()) item {
                val total = ui.bucketOpen.size + ui.bucketDone.size
                val fraction = if (total > 0) ui.bucketDone.size.toFloat() / total else 0f
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = Dimens.s6)) {
                    Text(L("%1\$s", ui.bucketDone.size), style = MaterialTheme.typography.displaySmall)
                    Text(L("of %1\$s done", total), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Dimens.s8))
                    Box(modifier = Modifier.weight(1f))
                    Text(L("no deadline on any of them"), style = MaterialTheme.typography.bodySmall)
                }
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s14).size(6.dp).background(colors.ring, Shapes.xs)) {
                    Box(modifier = Modifier.fillMaxWidth(fraction).size(6.dp).background(colors.acc, Shapes.xs))
                }
            }
            if (ui.bucketOpen.isNotEmpty() || ui.bucketDone.isNotEmpty()) item {
                Row(modifier = Modifier.padding(bottom = Dimens.s10)) {
                    FilterChipStatic(L("Open"), showOpen, onClick = { showOpen = !showOpen })
                    FilterChipStatic(L("Done"), showDone, onClick = { showDone = !showDone })
                    // "Costs money" and "Places" chips sat here permanently disabled — no data
                    // behind them. Removed: a control appears only once it works.
                }
            }
            if (ui.bucketOpen.isEmpty() && ui.bucketDone.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Landscape,
                        illustration = Illustration.IDEAS,
                        headline = L("Your bucket list is empty"),
                        supporting = L("Things you want to do one day. No deadline, no estimate, no timer — they stay out of the clock until you want them in it."),
                        actionLabel = L("Add something"),
                        onAction = onAdd,
                        modifier = Modifier.padding(vertical = Dimens.s24),
                    )
                }
            }
            if (showOpen) {
                items(ui.bucketOpen) { idea -> OpenIdeaRow(idea, onToggle = { viewModel.toggleDone(idea.id) }) }
            }
            if (showDone) {
                item { Text(L("DONE · %1\$s", ui.bucketDone.size), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = Dimens.s16)) }
                items(ui.bucketDone) { idea -> DoneIdeaRow(idea, onToggle = { viewModel.toggleDone(idea.id) }) }
            }
        }
    }
}

@Composable
private fun FilterChipStatic(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit = {}) {
    val colors = TimeThemeTokens.colors
    val fg = if (!enabled) TimeThemeTokens.status.dim else if (selected) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .padding(end = Dimens.s8)
            .background(if (selected && enabled) colors.accC else Color.Transparent, Shapes.pill)
            .border(if (selected && enabled) 0.dp else 1.dp, if (enabled) TimeThemeTokens.status.controlOutline else TimeThemeTokens.colors.line, Shapes.pill)
            .then(if (enabled) Modifier.semantics { this.selected = selected && enabled }.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Dimens.s14, vertical = Dimens.s8),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
private fun OpenIdeaRow(idea: Idea, onToggle: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s10)) {
        Box(modifier = Modifier.size(22.dp).border(2.dp, TimeThemeTokens.status.controlOutline, CircleShape).clickable(onClick = onToggle))
        Column(modifier = Modifier.weight(1f).padding(start = Dimens.s14)) {
            Text(idea.title, style = MaterialTheme.typography.bodyLarge)
            if (idea.costsMoneyEstimateMinor != null || idea.promotedToPlan) {
                Row(modifier = Modifier.padding(top = Dimens.s8)) {
                    idea.costsMoneyEstimateMinor?.let {
                        Badge("~${it.toTaka()}", Icons.Rounded.Payments, TimeThemeTokens.status.ok, TimeThemeTokens.status.okInk)
                    }
                    if (idea.promotedToPlan) {
                        Badge(L("Now a project in Plan"), Icons.Rounded.ArrowOutward, TimeThemeTokens.colors.accC, TimeThemeTokens.colors.accInk)
                    }
                }
            }
        }
    }
}

@Composable
private fun DoneIdeaRow(idea: Idea, onToggle: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s8), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(22.dp).background(TimeThemeTokens.colors.acc, CircleShape).clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(15.dp))
        }
        Text(
            idea.title, style = MaterialTheme.typography.bodyLarge, textDecoration = TextDecoration.LineThrough,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Dimens.s14),
        )
    }
}

@Composable
private fun Badge(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, bg: Color, fg: Color) {
    Row(
        modifier = Modifier.padding(end = Dimens.s8).background(bg, Shapes.pill).padding(horizontal = Dimens.s10, vertical = Dimens.s6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg, modifier = Modifier.padding(start = Dimens.s4))
    }
}
