package com.example.timeapp.data.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.timeapp.MainActivity
import com.example.timeapp.R
import com.example.timeapp.data.AppSettingsDataStore
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.toDomain
import com.example.timeapp.data.totalEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The Money widget (Phase 15): bills due in the next week, and the total balance only when the
 * user turned amounts on (Money settings) — a home screen is seen by whoever holds the phone.
 * Counts only while locked. Redrawn with the others by TodayWidgetUpdater.
 */
class MoneyWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { refresh(context.applicationContext) } finally { pending.finish() }
        }
    }

    companion object {
        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MoneyWidget::class.java))
            if (ids.isEmpty()) return
            val db = TimeDatabase.getInstance(context)
            val settings = AppSettingsDataStore(context).settings.first()
            // Balance = every wallet's opening balance plus every ledger row's effect — the one
            // definition (totalEffect), archived wallets included, as the Overview counts it.
            val opening = db.walletDao().observeAll().first().sumOf { it.openingBalanceMinor }
            val ledger = db.moneyRecordDao().observeHistory().first().sumOf { it.toDomain().totalEffect }
            val plans = db.moneyRecordDao().observePlans().first().map { it.toDomain() }
            val content = moneyWidgetContent(opening + ledger, plans, settings.moneyWidgetAmounts, settings.lockEnabled, LocalDate.now())
            ids.forEach { manager.updateAppWidget(it, render(context, content)) }
        }

        private fun render(context: Context, content: MoneyWidgetContent): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_money)
            views.setTextViewText(R.id.money_widget_heading, content.heading)
            views.setTextViewText(R.id.money_widget_note, content.note)
            views.setViewVisibility(R.id.money_widget_note, if (content.note.isEmpty()) View.GONE else View.VISIBLE)
            views.setTextViewText(R.id.money_widget_balance, content.balance)
            views.setTextViewText(R.id.money_widget_line, content.line)
            views.setTextViewText(R.id.money_widget_footer, content.footer)
            val open = PendingIntent.getActivity(
                context, 29_999,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(MainActivity.EXTRA_OPEN_ROUTE, "money"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.money_widget_root, open)
            return views
        }
    }
}
