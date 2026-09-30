package com.example.timeapp.data.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.example.timeapp.MainActivity
import com.example.timeapp.R
import com.example.timeapp.data.AppSettingsDataStore
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.repository.TimerClock
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.notify.FocusActionReceiver
import com.example.timeapp.data.notify.FocusSurface
import com.example.timeapp.data.repository.EntryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The Focus widget (Phase 15): the one work timer, drawn with the system Chronometer so nothing
 * is written per second, and Pause/Resume + Finish through the same FocusActionReceiver the
 * notification uses. Redrawn by FocusSurface on every runtime change.
 */
class FocusWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { refresh(context.applicationContext) } finally { pending.finish() }
        }
    }

    companion object {
        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FocusWidget::class.java))
            if (ids.isEmpty()) return
            val db = TimeDatabase.getInstance(context)
            val rt = EntryRepository(db).timer.observe().first()
            val entries = db.entryDao().observeAll().first()
            val title = rt.entryId?.let { id -> entries.firstOrNull { it.id == id }?.title }
            val paused = entries.firstOrNull { it.status == EntryStatus.PAUSED && it.archivedAt == null }?.title
            val locked = AppSettingsDataStore(context).settings.first().lockEnabled
            val content = focusWidgetContent(rt, title, paused, locked, TimerClock.now())
            ids.forEach { manager.updateAppWidget(it, render(context, content)) }
        }

        private fun render(context: Context, content: FocusWidgetContent): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_focus)
            views.setTextViewText(R.id.focus_widget_heading, content.heading)
            views.setTextViewText(R.id.focus_widget_title, content.title)
            val clock = content.clock
            if (clock == null) {
                views.setViewVisibility(R.id.focus_widget_clock, View.GONE)
            } else {
                views.setViewVisibility(R.id.focus_widget_clock, View.VISIBLE)
                val now = SystemClock.elapsedRealtime()
                val base = if (clock.countDown) now + clock.valueMs else now - clock.valueMs
                views.setChronometer(R.id.focus_widget_clock, base, null, clock.running)
                views.setChronometerCountDown(R.id.focus_widget_clock, clock.countDown)
            }
            listOf(R.id.focus_widget_primary to content.primary, R.id.focus_widget_secondary to content.secondary).forEach { (id, action) ->
                if (action == null) {
                    views.setViewVisibility(id, View.GONE)
                } else {
                    views.setViewVisibility(id, View.VISIBLE)
                    views.setTextViewText(id, action.label)
                    val code = when (action) {
                        FocusWidgetAction.PAUSE -> FocusActionReceiver.PAUSE
                        FocusWidgetAction.RESUME -> FocusActionReceiver.RESUME
                        FocusWidgetAction.FINISH -> FocusActionReceiver.FINISH
                        FocusWidgetAction.SKIP -> FocusActionReceiver.SKIP
                    }
                    views.setOnClickPendingIntent(
                        id,
                        PendingIntent.getBroadcast(
                            context, 20_000 + action.ordinal,
                            FocusActionReceiver.intent(context, code, FocusSurface.ONGOING_ID)
                                .putExtra(FocusActionReceiver.EXTRA_FROM_WIDGET, true),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    )
                }
            }
            val open = PendingIntent.getActivity(
                context, 19_999,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(MainActivity.EXTRA_OPEN_ROUTE, "focus"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.focus_widget_root, open)
            return views
        }
    }
}
