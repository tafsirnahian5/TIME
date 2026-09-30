package com.example.timeapp.data.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.timeapp.MainActivity
import com.example.timeapp.R
import com.example.timeapp.data.AppSettingsDataStore
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.toDomain
import com.example.timeapp.data.notify.ReminderReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The home-screen habit widget (Phase 12): plain RemoteViews like [TodayWidget]. A plain habit
 * checks off with one tap — the same broadcast as a reminder's *Done*, which reveals nothing
 * and so needs no unlock; a measurable, timed or done one opens it in the app (through the
 * lock). With the lock on it shows counts only. Redrawn by [TodayWidgetUpdater].
 */
class HabitsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                refresh(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val ROWS = listOf(R.id.habit_row1, R.id.habit_row2, R.id.habit_row3, R.id.habit_row4)

        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, HabitsWidget::class.java))
            if (ids.isEmpty()) return
            val db = TimeDatabase.getInstance(context)
            val habits = db.habitDao().observeAll().first().map { it.toDomain() }.filterNot { it.archived }
            val marks = db.habitDao().observeAllChecks().first().groupBy { it.habitId }
                .mapValues { (_, list) -> list.associate { it.date to it.mark } }
            val locked = AppSettingsDataStore(context).settings.first().lockEnabled
            val today = LocalDate.now()
            val content = habitWidgetContent(habits, marks, locked, today)
            ids.forEach { manager.updateAppWidget(it, render(context, content, today)) }
        }

        private fun render(context: Context, content: HabitWidgetContent, today: LocalDate): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_habits)
            views.setTextViewText(R.id.habit_widget_heading, content.heading)
            ROWS.forEachIndexed { i, viewId ->
                val row = content.rows.getOrNull(i)
                views.setViewVisibility(viewId, if (row == null) android.view.View.GONE else android.view.View.VISIBLE)
                if (row == null) return@forEachIndexed
                views.setTextViewText(viewId, row.text)
                val click = if (row.checkable) {
                    PendingIntent.getBroadcast(
                        context, row.habitId.toInt(),
                        ReminderReceiver.habitDoneIntent(context, row.habitId, today),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                } else {
                    PendingIntent.getActivity(
                        context, 10_000 + row.habitId.toInt(),
                        Intent(context, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            .setData(android.net.Uri.parse("timeapp://widget-habit/${row.habitId}"))
                            .putExtra(MainActivity.EXTRA_OPEN_ROUTE, "habits/${row.habitId}"),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                }
                views.setOnClickPendingIntent(viewId, click)
            }
            views.setTextViewText(R.id.habit_widget_footer, content.footer)
            val open = PendingIntent.getActivity(
                context, 9_999,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(MainActivity.EXTRA_OPEN_ROUTE, "habits"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.habit_widget_root, open)
            return views
        }
    }
}
