package com.example.timeapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.timeapp.data.L
import com.example.timeapp.ui.components.HelpPoint
import com.example.timeapp.ui.components.Illustration
import com.example.timeapp.ui.components.IllustrationImage
import com.example.timeapp.ui.components.TabHelp
import com.example.timeapp.ui.components.TabHelps
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

private class Guide(val picture: Illustration, val help: TabHelp)

private fun guides() = listOf(
    Guide(Illustration.WELCOME, TabHelps.today),
    Guide(Illustration.PLAN, TabHelps.plan),
    Guide(Illustration.FOCUS, TabHelps.focus),
    Guide(Illustration.MONEY, TabHelps.money),
    Guide(Illustration.HABITS, TabHelps.habits),
    Guide(Illustration.IDEAS, TabHelps.ideas),
    Guide(Illustration.LINKS, TabHelps.links),
    Guide(Illustration.PLAN, TabHelps.reminders),
    Guide(Illustration.PRIVATE, TabHelps.yourData),
)

/** The points of [help] that mention every word of [query]; all of them when the guide's own name matches. */
private fun matching(help: TabHelp, query: String): List<HelpPoint> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return help.points
    val head = "${help.tab} ${help.purpose}".lowercase()
    if (words.all { it in head }) return help.points
    return help.points.filter { p -> "${p.title} ${p.body}".lowercase().let { t -> words.all { it in t } } }
}

/**
 * Settings › Help & guides (Phase 19D): replay the welcome tour, bring the tab tips back, and a
 * short illustrated guide per area — the same words as each tab's *?* sheet, so help can't drift
 * between two places. Searchable; follows the app language like everything else.
 */
@Composable
fun HelpScreen(onBack: () -> Unit, onWatchTour: () -> Unit, onShowTipsAgain: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var tipsReset by remember { mutableStateOf(false) }
    val all = guides()
    val shown = all.mapNotNull { g -> matching(g.help, query).takeIf { it.isNotEmpty() }?.let { g to it } }

    Scaffold(topBar = { TimeTopBar(title = L("Help & guides"), onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Dimens.s16, end = Dimens.s16, bottom = Dimens.s24),
            verticalArrangement = Arrangement.spacedBy(Dimens.s12),
        ) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text(L("Search help")) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isBlank()) {
                item {
                    Column(Modifier.fillMaxWidth().background(TimeThemeTokens.colors.card, Shapes.lg)) {
                        ActionRow(Icons.Rounded.PlayCircle, L("Watch the welcome tour again"), L("Six short pages on what T!ME is for"), onWatchTour)
                        ActionRow(
                            Icons.Rounded.Replay, L("Show tips again"),
                            if (tipsReset) L("Done — each tab explains itself again the next time you open it")
                            else L("Each tab's first-visit tip and Today's getting-started card"),
                        ) { onShowTipsAgain(); tipsReset = true }
                    }
                }
                item {
                    Text(
                        L("GUIDES"), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Dimens.s8).semantics { heading() },
                    )
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        L("Nothing in the guides matches \"%1\$s\". Try another word, or search Settings for a setting.", query.trim()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Dimens.s16),
                    )
                }
            }
            items(shown, key = { it.first.help.tab }) { (guide, points) ->
                GuideCard(guide, points, forceOpen = query.isNotBlank())
            }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val colors = TimeThemeTokens.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick)
            .padding(horizontal = Dimens.s16, vertical = Dimens.s10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(colors.accC, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.accInk)
        }
        Column(Modifier.weight(1f).padding(start = Dimens.s14)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GuideCard(guide: Guide, points: List<HelpPoint>, forceOpen: Boolean) {
    val colors = TimeThemeTokens.colors
    var open by rememberSaveable(guide.help.tab) { mutableStateOf(false) }
    val expanded = open || forceOpen
    val state = if (expanded) L("Expanded") else L("Collapsed")
    Column(Modifier.fillMaxWidth().background(colors.card, Shapes.lg)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { open = !open }
                .semantics(mergeDescendants = true) { stateDescription = state }
                .padding(horizontal = Dimens.s16, vertical = Dimens.s10),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(guide.help.tab, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(guide.help.purpose, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!forceOpen) {
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
            }
        }
        if (expanded) {
            Column(
                Modifier.fillMaxWidth().padding(start = Dimens.s16, end = Dimens.s16, bottom = Dimens.s16),
                verticalArrangement = Arrangement.spacedBy(Dimens.s14),
            ) {
                if (!forceOpen) IllustrationImage(guide.picture, Modifier.align(Alignment.CenterHorizontally), width = 180.dp)
                points.forEach { p ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(36.dp).background(colors.accC, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(p.icon, contentDescription = null, tint = colors.accInk, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.padding(start = Dimens.s12)) {
                            Text(p.title, style = MaterialTheme.typography.titleSmall)
                            Text(p.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
