package com.example.timeapp.ui.setup

import com.example.timeapp.data.L
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.timeapp.ui.security.PIN_MAX_LENGTH
import com.example.timeapp.ui.security.PIN_MIN_LENGTH
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/**
 * A3 — Setup 3: the app lock.
 *
 * Step 1 tells the user "You can add a PIN or fingerprint in step 3" and both earlier
 * screens draw a three-segment progress bar, but no third screen existed — Setup 2's
 * Continue completed onboarding outright, so the app silently broke a promise it had
 * made two screens earlier. This is that step. It offers the real PIN setup (Tier 8's
 * `PinSetupScreen`) and an explicit skip, because a lock the user did not ask for is
 * worse than no lock: everything here stays available in Settings › Security.
 *
 * [pinAlreadySet] reflects a PIN created during this run, so returning from the setup
 * flow confirms the choice rather than silently re-offering it.
 */
@Composable
fun SetupSecurityScreen(
    onBack: () -> Unit,
    onSetPin: () -> Unit,
    onFinish: () -> Unit,
    pinAlreadySet: Boolean,
) {
    val colors = TimeThemeTokens.colors

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(Dimens.screenH)) {
          // Everything above the pinned buttons scrolls, so large text or a small phone can't
          // push the explanation off the screen (a weight spacer can't live in a scroll).
          Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = L("Back"))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.s6),
            ) {
                Box(modifier = Modifier.weight(1f).size(4.dp).background(colors.acc, Shapes.xs))
                Box(modifier = Modifier.weight(1f).size(4.dp).background(colors.acc, Shapes.xs))
                Box(modifier = Modifier.weight(1f).size(4.dp).background(colors.acc, Shapes.xs))
            }
            Text(L("STEP 3 OF 3"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(top = Dimens.s12))
            Text(
                L("Lock T!ME with a PIN"),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = Dimens.s10, bottom = Dimens.s8),
            )
            Text(
                L("Everything stays on this phone. A PIN stops anyone who picks up your unlocked phone from opening T!ME. It's optional."),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = Dimens.s20),
            )

            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(colors.card, Shapes.lg)
                    .border(1.dp, colors.line, Shapes.lg)
                    .padding(Dimens.s16),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (pinAlreadySet) Icons.Rounded.Check else Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = colors.acc,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        if (pinAlreadySet) L("PIN set") else L("A PIN of %1\$s–%2\$s digits", PIN_MIN_LENGTH, PIN_MAX_LENGTH),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = Dimens.s10),
                    )
                }
                Text(
                    if (pinAlreadySet) {
                        L("You can change it, add fingerprint unlock or turn the lock off in Settings › Security & app lock.")
                    } else {
                        L("T!ME asks for it when you come back after a while. You can add fingerprint unlock later in Settings › Security & app lock.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Dimens.s8),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = Dimens.s14),
            ) {
                Icon(
                    Icons.Rounded.Info, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp),
                )
                Text(
                    L("There's no account, so there's no email reset. If you forget the PIN, you can reset it with your phone's own screen lock."),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = Dimens.s8),
                )
            }

          }

            if (!pinAlreadySet) {
                Button(onClick = onSetPin, modifier = Modifier.fillMaxWidth()) { Text(L("Set a PIN")) }
                TextButton(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.s6, bottom = Dimens.s14),
                ) { Text(L("Skip for now")) }
            } else {
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s14)) {
                    Text(L("Finish"))
                }
            }
        }
    }
}
