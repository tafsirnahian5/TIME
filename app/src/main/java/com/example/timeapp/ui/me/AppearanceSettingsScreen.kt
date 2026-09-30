package com.example.timeapp.ui.me

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import com.example.timeapp.data.L

import androidx.compose.ui.semantics.heading

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.timeapp.ui.AppViewModel
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Colorway
import com.example.timeapp.ui.theme.TimeThemeTokens
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.colorsFor

/** G4 — Settings › Appearance: theme mode + the five accent colorways (new screen). */
@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    viewModel: AppViewModel,
) {
    val ui by viewModel.uiState.collectAsState()
    var choosingHaptics by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Scaffold(topBar = { TimeTopBar(title = L("Appearance"), onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.s16).verticalScroll(rememberScrollState())) {
            Text(L("THEME"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(top = Dimens.s4, bottom = Dimens.s10))
            // The shared toggle — this used to be a hand-built copy painted with the *light* colour
            // set (`colorsFor(colorway)`), so in dark mode it was a near-white pill with the
            // unselected labels almost invisible, and it lacked the shared 48dp segments.
            val modes = listOf("system", "light", "dark")
            com.example.timeapp.ui.components.SegmentedToggle(
                options = listOf(L("Follow system"), L("Light"), L("Dark")),
                selectedIndex = modes.indexOf(ui.themeMode).coerceAtLeast(0),
                onSelect = { viewModel.setThemeMode(modes[it]) },
                modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s20),
            )
            // Wallpaper colours (Phase 15; canvas): Android 12+ only, and the accent swatches step
            // back while it's on, since the wallpaper decides the accent then.
            val dynamicSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
            com.example.timeapp.ui.components.SettingsGroup(L("COLOUR")) {
                com.example.timeapp.ui.components.SettingsSwitchRow(
                    L("Use wallpaper colours"), ui.dynamicColor && dynamicSupported,
                    subtitle = if (dynamicSupported) L("Android 12 and later. Status colours stay the same.") else L("Needs Android 12 or later."),
                    enabled = dynamicSupported,
                ) { viewModel.setDynamicColor(it) }
            }
            Text(L("ACCENT"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(top = Dimens.s20, bottom = Dimens.s12))
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s8).alpha(if (ui.dynamicColor && dynamicSupported) 0.38f else 1f)) {
                Colorway.entries.forEach { colorway ->
                    val swatch = colorsFor(colorway)
                    val selected = colorway == ui.colorway
                    Box(
                        modifier = Modifier
                            .padding(end = Dimens.s10)
                            .size(56.dp)
                            .background(swatch.acc, CircleShape)
                            .clickable { viewModel.setColorway(colorway) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Rounded.Check, contentDescription = L(colorway.label), tint = Color.White)
                    }
                }
            }
            Text(
                if (ui.dynamicColor && dynamicSupported) L("Greyed out while wallpaper colours are on.")
                else L("Accent recolours buttons, chips and charts. Category colours are set per category and never change with the theme."),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = Dimens.s4),
            )
            com.example.timeapp.ui.components.SettingsGroup(L("DARK MODE")) {
                com.example.timeapp.ui.components.SettingsSwitchRow(
                    L("Pure black"), ui.pureBlack,
                    subtitle = L("True black backgrounds in dark mode — saves power on OLED screens"),
                ) { viewModel.setPureBlack(it) }
            }
            com.example.timeapp.ui.components.SettingsGroup(L("MOTION")) {
                com.example.timeapp.ui.components.SettingsSwitchRow(
                    L("Reduce celebrations"), ui.reduceCelebrations,
                    subtitle = L("No confetti or tick animation. The milestone banner still shows."),
                ) { viewModel.setReduceCelebrations(it) }
            }
            com.example.timeapp.ui.components.SettingsGroup(L("FEEL")) {
                com.example.timeapp.ui.components.SettingsValueRow(
                    L("Haptic feedback"), hapticLabel(ui.hapticLevel),
                    subtitle = L("When you tick something off, drag a card, or start and stop Focus. Android's own touch-feedback switch still applies."),
                ) { choosingHaptics = true }
                // Android's own touch-vibration switch silences every app's haptics, T!ME's too
                // (found on a real phone: every click logged "ignored_for_settings").
                val context = androidx.compose.ui.platform.LocalContext.current
                val systemOff = androidx.compose.runtime.remember {
                    android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0
                }
                if (systemOff && ui.hapticLevel != com.example.timeapp.data.HapticLevel.OFF) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.s16, vertical = Dimens.s10)) {
                        Text(
                            L("Touch vibration is off in Android's settings, so you won't feel these until it's on."),
                            style = MaterialTheme.typography.bodySmall, color = TimeThemeTokens.status.warnInk,
                        )
                        androidx.compose.material3.TextButton(onClick = {
                            runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)) }
                        }) { Text(L("Open sound & vibration")) }
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(Dimens.s20))
            // A disabled "Launcher icon › Match the accent" card sat here ("Not available yet",
            // over a blank preview). A setting appears only once it works, and the owner keeps
            // their own T! icon on every colourway — so it's gone rather than promised.
            // A "Character presentation" switch (levels, XP) and a static "Text size — System
            // default" row used to sit here. Neither did anything: XP is deliberately not part of
            // T!ME, and text size follows the system. Removed rather than left as fake controls.
        }
    }

    if (choosingHaptics) {
        val context = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { choosingHaptics = false },
            title = { Text(L("Haptic feedback")) },
            text = {
                Column {
                    com.example.timeapp.data.HapticLevel.entries.forEach { level ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin)
                                .selectable(selected = level == ui.hapticLevel, role = androidx.compose.ui.semantics.Role.RadioButton) {
                                    // A sample at that strength, so it can be felt before closing.
                                    com.example.timeapp.data.Haptics.perform(context, com.example.timeapp.data.HapticEvent.CONFIRM, at = level)
                                    viewModel.setHapticLevel(level)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.RadioButton(selected = level == ui.hapticLevel, onClick = null)
                            Text(hapticLabel(level), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Dimens.s12))
                        }
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { choosingHaptics = false }) { Text(L("Done")) } },
        )
    }
}

private fun hapticLabel(level: com.example.timeapp.data.HapticLevel): String = when (level) {
    com.example.timeapp.data.HapticLevel.OFF -> L("Off")
    com.example.timeapp.data.HapticLevel.LIGHT -> L("Light")
    com.example.timeapp.data.HapticLevel.MEDIUM -> L("Medium")
    com.example.timeapp.data.HapticLevel.STRONG -> L("Strong")
}
