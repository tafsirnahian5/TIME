package com.example.timeapp.data.widget

import android.content.Context
import com.example.timeapp.data.AppSettingsDataStore
import com.example.timeapp.data.db.TimeDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Keeps the Today widget in step with the data — the PDF's "refresh on meaningful state
 * changes". Any change to tasks, habits, check-ins or the lock setting redraws it, debounced
 * so a burst of writes (an import, a recurrence expansion) is one redraw.
 */
object TodayWidgetUpdater {
    @OptIn(FlowPreview::class)
    fun start(context: Context, db: TimeDatabase, scope: CoroutineScope) {
        scope.launch {
            combine(
                db.entryDao().observeAll(),
                db.habitDao().observeAll(),
                db.habitDao().observeAllChecks(),
                combine(db.moneyRecordDao().observeHistory(), db.moneyRecordDao().observePlans(), db.walletDao().observeAll()) { a, b, c -> Triple(a.size, b, c) },
                AppSettingsDataStore(context).settings,
            ) { _, _, _, money, settings -> Triple(settings.lockEnabled, settings.moneyWidgetAmounts, money) }
                .debounce(1_000)
                .collect {
                    TodayWidget.refresh(context)
                    HabitsWidget.refresh(context)
                    MoneyWidget.refresh(context)
                    FocusWidget.refresh(context)
                }
        }
    }
}
