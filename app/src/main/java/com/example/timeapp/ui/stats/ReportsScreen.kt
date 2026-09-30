package com.example.timeapp.ui.stats

import com.example.timeapp.data.L

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/** Settings › Reports & goals — a small sub-list; "Savings goals" stays honestly disabled. */
@Composable
fun ReportsScreen(onBack: () -> Unit, onOpenTimeStats: () -> Unit, onOpenMoneyStats: () -> Unit, onOpenHabitStats: () -> Unit = {}, onOpenSavings: () -> Unit = {}, onOpenWorkload: () -> Unit = {}) {
    val colors = TimeThemeTokens.colors
    Scaffold(topBar = { TimeTopBar(title = L("Reports & goals"), onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimens.s16)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg).border(Dimens.hairline, colors.line, Shapes.lg)) {
                Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenTimeStats).padding(Dimens.s16), verticalAlignment = Alignment.CenterVertically) {
                    Text(L("Time report"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.s14).background(colors.line).height(Dimens.hairline)) {}
                Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenMoneyStats).padding(Dimens.s16), verticalAlignment = Alignment.CenterVertically) {
                    Text(L("Money report"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.s14).background(colors.line).height(Dimens.hairline)) {}
                Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenHabitStats).padding(Dimens.s16), verticalAlignment = Alignment.CenterVertically) {
                    Text(L("Habit report"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.s14).background(colors.line).height(Dimens.hairline)) {}
                Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenWorkload).padding(Dimens.s16), verticalAlignment = Alignment.CenterVertically) {
                    Text(L("Workload — next 7 days"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.s14).background(colors.line).height(Dimens.hairline)) {}
                Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSavings).padding(Dimens.s16), verticalAlignment = Alignment.CenterVertically) {
                    Text(L("Savings goals"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
