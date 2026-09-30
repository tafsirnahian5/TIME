package com.example.timeapp.ui.me

import androidx.compose.ui.unit.dp
import com.example.timeapp.data.L
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.timeapp.BuildConfig
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.TimeThemeTokens

/** Settings › About — version info and the app's own local-first framing. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val colors = TimeThemeTokens.colors
    Scaffold(topBar = { TimeTopBar(title = L("About"), onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = Dimens.s16)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = Dimens.s24, bottom = Dimens.s10)) {
                com.example.timeapp.ui.components.TimeMark(size = 48.dp, label = null)
                Text("T!ME", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = Dimens.s12))
            }
            Text(
                L("Version %1\$s (%2\$s)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = Dimens.s20),
            )
            Text(
                L("Local-first — no account, no server, no network permission. Everything you enter is written to this phone's storage and stays there unless you export it yourself."),
                style = MaterialTheme.typography.bodyLarge,
            )
            // Settings promised "Version, licenses"; the licenses weren't on the page.
            // Same list as docs/THIRD_PARTY_NOTICES.md — keep the two in step.
            Text(
                L("OPEN-SOURCE LICENSES"),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.semantics { heading() }.padding(top = Dimens.s24, bottom = Dimens.s8),
            )
            Text(
                L("Built with these libraries, all under the Apache License 2.0:"),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = Dimens.s8),
            )
            listOf(
                "Jetpack Compose · Material 3 · Material Icons Extended",
                "AndroidX Activity, Core, Lifecycle, Navigation, Fragment",
                "AndroidX Room",
                "AndroidX DataStore",
                "AndroidX Biometric",
                "AndroidX WorkManager",
                "Kotlin · kotlinx.coroutines · kotlinx.serialization",
            ).forEach { name ->
                Text("• $name", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = Dimens.s4))
            }
            Text(
                L("Roboto and the Material icons are also Apache License 2.0. The licence text: apache.org/licenses/LICENSE-2.0. No code was copied from other apps."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.s8, bottom = Dimens.s24),
            )
        }
    }
}
