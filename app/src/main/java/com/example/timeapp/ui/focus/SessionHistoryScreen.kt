package com.example.timeapp.ui.focus

import com.example.timeapp.data.L

import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
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
import com.example.timeapp.data.SessionMode
import com.example.timeapp.data.SessionRecord
import com.example.timeapp.ui.components.Illustration
import com.example.timeapp.ui.components.EmptyState
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.stats.formatHm
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.ZoneId

/**
 * Focus › History: the last two weeks of work sessions by day, flagged ones first. Tap one to
 * fix its times (kept with a reason), add a note, add 5 minutes or confirm it. Built from the
 * shared tokens — no design canvas covers it.
 */
@Composable
fun SessionHistoryScreen(onBack: () -> Unit, viewModel: FocusViewModel) {
    val history by viewModel.history.collectAsState()
    val review by viewModel.needsReview.collectAsState()
    var editing by remember { mutableStateOf<SessionRecord?>(null) }
    val zone = ZoneId.systemDefault()
    val dayFmt = displayPattern("EEEE d MMM")

    Scaffold(topBar = { TimeTopBar(title = L("Session history"), onBack = onBack) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.s16)) {
            if (history.isEmpty() && review.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.History,
                        illustration = Illustration.FOCUS,
                        headline = L("No sessions yet"),
                        supporting = L("Start a task or a Focus timer — each stretch of work shows up here, where you can fix its times or add a note."),
                        modifier = Modifier.padding(vertical = Dimens.s24),
                    )
                }
            }
            if (review.isNotEmpty()) {
                item { Label(L("NEEDS REVIEW"), TimeThemeTokens.status.warnInk) }
                items(review, key = { "r${it.id}" }) { SessionRow(it, showDate = true) { editing = it } }
            }
            history.groupBy { it.startedAt.atZone(zone).toLocalDate() }.forEach { (day, sessions) ->
                item(key = "d$day") {
                    val total = sessions.sumOf { it.durationMs ?: 0L }
                    Label("${day.format(dayFmt).uppercase()} · ${formatHm(total)}")
                }
                items(sessions, key = { it.id }) { SessionRow(it, showDate = false) { editing = it } }
            }
        }
    }
    editing?.let { s -> SessionEditDialog(s, viewModel.sessionActions, onDismiss = { editing = null }) }
}

@Composable
private fun Label(text: String, color: androidx.compose.ui.graphics.Color = TimeThemeTokens.status.neutralInk) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(top = Dimens.s16, bottom = Dimens.s6))
}

@Composable
internal fun SessionRow(s: SessionRecord, showDate: Boolean, onClick: () -> Unit) {
    val date = if (showDate) s.startedAt.atZone(ZoneId.systemDefault()).format(displayPattern("d MMM · ")) else ""
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin).clickable(onClick = onClick).padding(vertical = Dimens.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(s.title ?: L("Focus, no task"), style = MaterialTheme.typography.bodyLarge)
            Text(
                date + sessionSpanLabel(s) + modeLabel(s.mode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            s.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
        }
        if (s.needsReview) {
            Text(
                L("Review"),
                style = MaterialTheme.typography.labelSmall,
                color = TimeThemeTokens.status.warnInk,
                modifier = Modifier.background(TimeThemeTokens.status.warn, Shapes.pill).padding(horizontal = Dimens.s10, vertical = Dimens.s4),
            )
        }
    }
}

private fun modeLabel(mode: SessionMode) = when (mode) {
    SessionMode.TASK, SessionMode.STOPWATCH -> ""
    SessionMode.COUNTDOWN -> L(" · countdown")
    SessionMode.POMODORO -> L(" · Pomodoro")
}
