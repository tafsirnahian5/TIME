package com.example.timeapp.ui.settings

import com.example.timeapp.data.L

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.example.timeapp.ui.AppViewModel
import com.example.timeapp.ui.components.SegmentedToggle
import com.example.timeapp.ui.components.SettingsDivider
import com.example.timeapp.ui.components.SettingsGroup
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import java.time.ZoneId

/**
 * Settings › Language & region (Phase 15; canvas): the clock, date order and currency label,
 * applied app-wide through DisplayFormats. Only how things *look* — stored amounts and dates
 * never change. The time zone follows the phone (T!ME re-anchors reminders when it changes).
 * Bangla is the last roadmap phase, so the language row says English is all there is for now.
 */
@Composable
fun LanguageRegionScreen(onBack: () -> Unit, appViewModel: AppViewModel) {
    val s by appViewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var switchingTo by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { TimeTopBar(title = L("Language & region"), onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.s16).verticalScroll(rememberScrollState())) {
            SettingsGroup(L("TIME")) {
                Column(Modifier.padding(Dimens.s16)) {
                    Text(L("Time zone"), style = MaterialTheme.typography.bodyLarge)
                    val zone = ZoneId.systemDefault()
                    val offset = zone.rules.getOffset(java.time.Instant.now())
                    Text(L("Follows the phone · %1\$s (UTC%2\$s)", zone.id, offset), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SettingsDivider()
                Column(Modifier.padding(Dimens.s16)) {
                    Text(L("Clock"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = Dimens.s10))
                    val options = listOf("PHONE", "24", "12")
                    SegmentedToggle(
                        options = listOf(L("Phone default"), L("24-hour"), L("12-hour")),
                        selectedIndex = options.indexOf(s.clockFormat).coerceAtLeast(0),
                        onSelect = { appViewModel.setClockFormat(options[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            SettingsGroup(L("DATES")) {
                Column(Modifier.padding(Dimens.s16)) {
                    Text(L("Date order"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = Dimens.s10))
                    val options = listOf("DAY_FIRST", "MONTH_FIRST")
                    SegmentedToggle(
                        // Samples built the way dates are really shown ("Sept" in English, Bangla month names
                        // and digits in Bangla) — hard-coded "Sep" didn't match what the app printed.
                        options = listOf("d MMM", "MMM d").map { p ->
                            val f = java.time.format.DateTimeFormatter.ofPattern(p, com.example.timeapp.data.Lang.locale)
                            com.example.timeapp.data.Lang.digits(java.time.LocalDate.of(2026, 9, 28).format(f))
                        },
                        selectedIndex = options.indexOf(s.dateOrder).coerceAtLeast(0),
                        onSelect = { appViewModel.setDateOrder(options[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        L("Week start lives in Workday & calendar."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Dimens.s10),
                    )
                }
            }
            SettingsGroup(L("MONEY")) {
                Column(Modifier.padding(Dimens.s16)) {
                    Text(L("Currency label"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = Dimens.s10))
                    val options = listOf("Tk", "৳")
                    SegmentedToggle(
                        options = listOf(L("Tk 1,200.50"), com.example.timeapp.data.Lang.digits("৳1,200.50")),
                        selectedIndex = options.indexOf(s.currencyLabel).coerceAtLeast(0),
                        onSelect = { appViewModel.setCurrencyLabel(options[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        L("Only how amounts look. Stored amounts never change."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Dimens.s10),
                    )
                }
            }
            SettingsGroup(L("LANGUAGE")) {
                Column(Modifier.padding(Dimens.s16)) {
                    Text(L("App language"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = Dimens.s10))
                    // Each language names itself, so it can be found whichever one is showing.
                    val options = listOf("en", "bn")
                    SegmentedToggle(
                        options = listOf("English", "বাংলা"),
                        selectedIndex = options.indexOf(s.appLanguage).coerceAtLeast(0),
                        onSelect = { if (options[it] != s.appLanguage) switchingTo = options[it] },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        L("Only how T!ME reads. Your own text, amounts, backups and exports stay as they are."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Dimens.s10),
                    )
                }
            }
            Box(Modifier.padding(bottom = Dimens.s24))
        }
    }
    switchingTo?.let { tag ->
        // Shown in the language being switched to, since that's the one the person can read.
        val bangla = tag == "bn"
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { switchingTo = null },
            title = { Text(if (bangla) "বাংলায় চালু করবেন?" else "Switch to English?") },
            text = { Text(if (bangla) "T!ME আবার চালু হবে। আপনার কোনো তথ্য বদলাবে না।" else "T!ME will restart. None of your data changes.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    appViewModel.setAppLanguage(tag) { com.example.timeapp.ui.components.restartApp(context) }
                }) { Text(if (bangla) "চালু করুন" else "Restart") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { switchingTo = null }) { Text(if (bangla) "বাতিল" else "Cancel") }
            },
        )
    }
}
