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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * The home-screen Today widget: plain RemoteViews, no widget library. Content comes from
 * [todayWidgetContent]; it is redrawn when the data changes (see [TodayWidgetUpdater]) and on
 * the system's periodic update — never on a per-second loop. A tap opens the app, which puts
 * the lock screen first when the lock is on.
 */
class TodayWidget : AppWidgetProvider() {
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
        /** Rebuilds every placed Today widget from the database. Cheap when none are placed. */
        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isEmpty()) return
            val db = TimeDatabase.getInstance(context)
            val entries = db.entryDao().observeAll().first().map { it.toDomain() }
            val habits = db.habitDao().observeAll().first().map { it.toDomain() }.filterNot { it.archived }
            val marks = db.habitDao().observeAllChecks().first().groupBy { it.habitId }
                .mapValues { (_, list) -> list.associate { it.date to it.mark } }
            val locked = AppSettingsDataStore(context).settings.first().lockEnabled
            val content = todayWidgetContent(entries, habits, marks, locked, Instant.now(), ZoneId.systemDefault())
            ids.forEach { manager.updateAppWidget(it, render(context, content)) }
        }

        private fun render(context: Context, content: WidgetContent): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            views.setTextViewText(R.id.widget_heading, content.heading)
            val lineIds = listOf(R.id.widget_line1, R.id.widget_line2, R.id.widget_line3, R.id.widget_line4)
            lineIds.forEachIndexed { i, id ->
                val text = content.lines.getOrNull(i)
                views.setTextViewText(id, text ?: "")
                views.setViewVisibility(id, if (text == null) android.view.View.GONE else android.view.View.VISIBLE)
            }
            views.setTextViewText(R.id.widget_footer, content.footer)
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            return views
        }
    }
}
