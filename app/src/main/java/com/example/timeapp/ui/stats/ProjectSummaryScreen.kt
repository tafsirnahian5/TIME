package com.example.timeapp.ui.stats

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.ProjectSummary
import com.example.timeapp.data.toTaka
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/**
 * A project's time and money side by side (Phase 13; the PDF's project summary): tracked time on
 * its tasks, spending and what's still planned for it, and the tasks and money rows behind the
 * numbers. Token-built; no design canvas covers it.
 */
@Composable
fun ProjectSummaryScreen(
    name: String,
    summary: ProjectSummary,
    tasks: List<Entry>,
    money: List<MoneyRecord>,
    onBack: () -> Unit,
    onOpenTask: (Long) -> Unit,
) {
    Scaffold(topBar = { TimeTopBar(title = name, onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.s16)) {
            Row(Modifier.fillMaxWidth().padding(top = Dimens.s12), horizontalArrangement = Arrangement.spacedBy(Dimens.s10)) {
                Stat(L("Tracked"), formatHm(summary.trackedMs), Modifier.weight(1f))
                Stat(L("Spent"), summary.spentMinor.toTaka(), Modifier.weight(1f))
                Stat(L("Planned"), summary.plannedMinor.toTaka(), Modifier.weight(1f))
            }
            Text(
                L("%1\$s of %2\$s tasks done. Spending counts money linked to the project or one of its tasks, net of refunds.", summary.tasksDone, summary.tasks),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Dimens.s10),
            )
            if (tasks.isNotEmpty()) {
                Text(L("TASKS"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(top = Dimens.s8, bottom = Dimens.s4))
                tasks.forEach { t ->
                    Row(Modifier.fillMaxWidth().clickable { onOpenTask(t.id) }.padding(vertical = Dimens.s10)) {
                        Text(t.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(if (t.status == EntryStatus.DONE) L("Done") else t.status.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (money.isNotEmpty()) {
                Text(L("MONEY"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(top = Dimens.s12, bottom = Dimens.s4))
                money.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = Dimens.s8)) {
                        Text(r.title + if (r.isOpen) L(" · planned") else "", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text((if (r.isOpen) r.remainingMinor else r.paidMinor).toTaka(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(TimeThemeTokens.colors.accS, Shapes.lg).padding(Dimens.s12)) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Dimens.s4))
    }
}
