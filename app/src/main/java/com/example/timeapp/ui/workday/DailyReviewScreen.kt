package com.example.timeapp.ui.workday

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.richtext.RichTextField
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.LocalDate

/** Settings › Workday & calendar › Daily review — completed work, a free-text reflection,
 * and tomorrow's carryover. No XP copy: no reward engine exists to back it (see Tier 11 plan). */
@Composable
fun DailyReviewScreen(onBack: () -> Unit, onDone: () -> Unit, viewModel: ReviewViewModel) {
    val colors = TimeThemeTokens.colors
    val ui by viewModel.dailyUiState.collectAsState()
    var note by remember(ui.note) { mutableStateOf(ui.note ?: "") }

    Scaffold(topBar = { TimeTopBar(title = L("Daily review"), onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimens.s16)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                LocalDate.now().format(displayPattern("EEE d MMM")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.s10, bottom = Dimens.s14),
            )

            Column(modifier = Modifier.fillMaxWidth().background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s16)) {
                Text(L("COMPLETED TODAY"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s10))
                if (ui.completedToday.isEmpty()) {
                    Text(L("Nothing marked done today yet."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ui.completedToday.forEach { entry ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s6), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(18.dp).background(colors.acc, Shapes.xs), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
                        }
                        Text(entry.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Dimens.s10))
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s16)) {
                Text(L("WHAT WENT WELL?"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = Dimens.s10))
                RichTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = L("Write a short note about today…"),
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16, bottom = Dimens.s20).background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s16)) {
                Text(L("CARRYING FORWARD TOMORROW"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s10))
                if (ui.carryoverTomorrow.isEmpty()) {
                    Text(L("Nothing unscheduled or overdue."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ui.carryoverTomorrow.forEach { entry ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s6), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(8.dp).background(
                                if (entry.status.name == "OVERDUE") TimeThemeTokens.status.dangerInk else com.example.timeapp.ui.theme.MarkerMoney,
                                CircleShape,
                            ),
                        )
                        Text(entry.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Dimens.s10))
                    }
                }
            }

            Button(
                onClick = { viewModel.saveDailyReview(note, onDone = onDone) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(L("Save review")) }
            Box(modifier = Modifier.padding(bottom = Dimens.s16))
        }
    }
}
