package com.example.timeapp.ui.me

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.WorkHistory
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Person
import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.withStyle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

private data class SettingsRow(
    val title: String,
    val subtitle: String,
    val enabled: Boolean,
    val onClick: () -> Unit = {},
    /** Phase 20C — every row gets a picture, so the list isn't a wall of text. */
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
)

/**
 * The real Settings hub — replaces the top bar's old inline Notifications/Appearance
 * dropdown. A row appears only once what it opens works (the PDF: "a setting appears only
 * when implemented"): four greyed-out placeholders — Character & XP, Timers & focus, Money
 * settings, Language & region — were removed rather than left promising pages that don't
 * exist. They come back as real pages when those are built.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenHelp: () -> Unit = {},
    onOpenSecurity: () -> Unit,
    onOpenDataBackup: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenWorkday: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenMoneySettings: () -> Unit = {},
    onOpenTimers: () -> Unit = {},
    onOpenLanguage: () -> Unit = {},
) {
    val colors = TimeThemeTokens.colors
    var query by remember { mutableStateOf("") }
    val openPage: (com.example.timeapp.data.SettingsPage) -> Unit = { page ->
        when (page) {
            com.example.timeapp.data.SettingsPage.PROFILE -> onOpenProfile()
            com.example.timeapp.data.SettingsPage.CATEGORIES -> onOpenCategories()
            com.example.timeapp.data.SettingsPage.WORKDAY -> onOpenWorkday()
            com.example.timeapp.data.SettingsPage.REPORTS -> onOpenReports()
            com.example.timeapp.data.SettingsPage.MONEY -> onOpenMoneySettings()
            com.example.timeapp.data.SettingsPage.NOTIFICATIONS -> onOpenNotifications()
            com.example.timeapp.data.SettingsPage.APPEARANCE -> onOpenAppearance()
            com.example.timeapp.data.SettingsPage.TIMERS -> onOpenTimers()
            com.example.timeapp.data.SettingsPage.LANGUAGE -> onOpenLanguage()
            com.example.timeapp.data.SettingsPage.SECURITY -> onOpenSecurity()
            com.example.timeapp.data.SettingsPage.DATA -> onOpenDataBackup()
            com.example.timeapp.data.SettingsPage.ABOUT -> onOpenAbout()
            com.example.timeapp.data.SettingsPage.HELP -> onOpenHelp()
        }
    }
    val personal = listOf(
        SettingsRow(L("Profile"), L("Name, title and avatar"), enabled = true, icon = Icons.Rounded.Person, onClick = onOpenProfile),
        SettingsRow(L("Categories"), L("Your own work and money categories, and projects"), enabled = true, icon = Icons.Rounded.Category, onClick = onOpenCategories),
    )
    val workAndMoney = listOf(
        SettingsRow(L("Workday & calendar"), L("Work hours, rest days, week start, My Workday"), enabled = true, icon = Icons.Rounded.WorkHistory, onClick = onOpenWorkday),
        SettingsRow(L("Reports & goals"), L("Time and Money statistics"), enabled = true, icon = Icons.Rounded.Insights, onClick = onOpenReports),
        SettingsRow(L("Money settings"), L("Default wallet, monthly budget, forecast, budget alerts"), enabled = true, icon = Icons.Rounded.AccountBalanceWallet, onClick = onOpenMoneySettings),
    )
    val preferences = listOf(
        SettingsRow(L("Notifications"), L("Channels, quiet hours, test notification"), enabled = true, icon = Icons.Rounded.Notifications, onClick = onOpenNotifications),
        SettingsRow(L("Appearance"), L("Theme, accent colour, pure black, celebrations, haptics"), enabled = true, icon = Icons.Rounded.Palette, onClick = onOpenAppearance),
        SettingsRow(L("Timers & focus"), L("Pomodoro lengths, alarm-style ringing, presets"), enabled = true, icon = Icons.Rounded.Timer, onClick = onOpenTimers),
        SettingsRow(L("Language & region"), L("App language, clock, date order, currency label"), enabled = true, icon = Icons.Rounded.Language, onClick = onOpenLanguage),
    )
    val dataAndSecurity = listOf(
        SettingsRow(L("Security & app lock"), L("PIN and biometric unlock"), enabled = true, icon = Icons.Rounded.Lock, onClick = onOpenSecurity),
        SettingsRow(L("Data & backup"), L("Backups (manual and automatic), restore, import and export"), enabled = true, icon = Icons.Rounded.Backup, onClick = onOpenDataBackup),
    )
    val more = listOf(
        SettingsRow(L("Help & guides"), L("How each part works, the welcome tour, tips"), enabled = true, icon = Icons.AutoMirrored.Rounded.HelpOutline, onClick = onOpenHelp),
        SettingsRow(L("About"), L("Version, licenses"), enabled = true, icon = Icons.Rounded.Info, onClick = onOpenAbout),
    )

    Scaffold(topBar = { TimeTopBar(title = L("Settings"), onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimens.s16)
                .verticalScroll(rememberScrollState()),
        ) {
            // Settings search (Phase 15; canvas): the filled pill, results in place of the groups.
            androidx.compose.material3.OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text(L("Search settings")) },
                singleLine = true,
                shape = Shapes.pill,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = colors.accS2,
                    focusedContainerColor = colors.accS2,
                    unfocusedBorderColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8),
            )
            if (query.isNotBlank()) {
                val results = com.example.timeapp.data.searchSettings(query)
                Text(
                    when (results.size) { 0 -> L("No setting matches “%1\$s”", query.trim()); 1 -> L("1 setting matches"); else -> L("%1\$s settings match", results.size) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Dimens.s4, top = Dimens.s14, bottom = Dimens.s8),
                )
                if (results.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().background(colors.card, Shapes.lg).border(Dimens.hairline, colors.line, Shapes.lg),
                    ) {
                        results.forEachIndexed { i, r ->
                            if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = Dimens.s14).height(Dimens.hairline).background(colors.line))
                            Column(
                                Modifier.fillMaxWidth().clickable { openPage(r.page) }.padding(horizontal = Dimens.s16, vertical = Dimens.s12),
                            ) {
                                Text(highlight(r.title, query.trim(), colors.accC), style = MaterialTheme.typography.bodyLarge)
                                Text(r.page.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                SettingsGroup(L("PERSONAL"), personal, colors.line)
                SettingsGroup(L("WORK & MONEY"), workAndMoney, colors.line)
                SettingsGroup(L("PREFERENCES"), preferences, colors.line)
                SettingsGroup(L("DATA & SECURITY"), dataAndSecurity, colors.line)
                SettingsGroup(L("MORE"), more, colors.line)
            }
            Box(modifier = Modifier.height(Dimens.s24))
        }
    }
}

@Composable
private fun SettingsGroup(label: String, rows: List<SettingsRow>, lineColor: Color) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(top = Dimens.s20, bottom = Dimens.s8).semantics { heading() },
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TimeThemeTokens.colors.card, Shapes.lg)
            .border(Dimens.hairline, lineColor, Shapes.lg),
    ) {
        rows.forEachIndexed { index, row ->
            SettingsRowView(row)
            if (index != rows.lastIndex) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.s14).height(Dimens.hairline).background(lineColor))
            }
        }
    }
}

@Composable
private fun SettingsRowView(row: SettingsRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = row.enabled, onClick = row.onClick)
            .padding(Dimens.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        row.icon?.let { icon ->
            Box(
                Modifier.padding(end = Dimens.s14).size(40.dp).background(TimeThemeTokens.colors.accC, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = TimeThemeTokens.colors.accInk, modifier = Modifier.size(22.dp)) }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (row.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                row.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.s2),
            )
        }
        if (row.enabled) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** [text] with the part matching [query] marked, as the canvas draws it. */
private fun highlight(text: String, query: String, mark: Color): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        val i = text.indexOf(query, ignoreCase = true)
        if (i < 0 || query.isEmpty()) { append(text); return@buildAnnotatedString }
        append(text.substring(0, i))
        withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, background = mark)) {
            append(text.substring(i, i + query.length))
        }
        append(text.substring(i + query.length))
    }
