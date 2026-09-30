package com.example.timeapp.data.notify

import com.example.timeapp.data.L

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.timeapp.MainActivity
import com.example.timeapp.data.AppSettingsDataStore
import com.example.timeapp.data.BudgetStanding
import com.example.timeapp.data.CategoryKind
import com.example.timeapp.data.NotifyChannel
import com.example.timeapp.data.budgetAlerts
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.toDomain
import com.example.timeapp.data.isQuiet
import com.example.timeapp.data.netSpendMinor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Watches the ledger, plans and budgets and posts a budget alert (`budgetAlerts`) the first time
 * each level is reached in a month — 80%, projected to go over, over — for the monthly budget
 * and each category with a limit. Sent keys are remembered, so an edit that dips below and back
 * doesn't repeat it. Money channel; respects its switch, quiet hours and content redaction.
 */
object BudgetWatcher {
    private const val ID_BASE = -3000

    @OptIn(FlowPreview::class)
    fun start(context: Context, db: TimeDatabase, scope: CoroutineScope) {
        val app = context.applicationContext
        val store = AppSettingsDataStore(app)
        scope.launch {
            combine(
                db.moneyRecordDao().observeHistory(),
                db.moneyRecordDao().observePlans(),
                db.categoryDao().observeAll(),
                store.settings,
            ) { history, plans, categories, settings -> Quad(history, plans, categories, settings) }
                .debounce(1_000)
                .collect { (history, plans, categories, settings) ->
                    val zone = ZoneId.systemDefault()
                    val month = YearMonth.now()
                    val today = LocalDate.now()
                    val thisMonth = history.map { it.toDomain() }.filter { it.settledAt != null && YearMonth.from(it.settledAt.atZone(zone)) == month }
                    // Still owed this month: open plans due by its end, overdue included.
                    val owed = plans.map { it.toDomain() }.filter { it.isOpen && it.dueOn != null && !it.dueOn.isAfter(month.atEndOfMonth()) }
                    val standings = buildList {
                        // Only a budget the user actually set — never the untouched default.
                        if (settings.monthlyBudgetSet) {
                            add(BudgetStanding("all", L("Monthly budget"), settings.monthlyBudgetMinor, thisMonth.sumOf { it.netSpendMinor }, owed.sumOf { it.remainingMinor }))
                        }
                        categories.filter { it.kind == CategoryKind.MONEY && !it.archived && (it.budgetMinor ?: 0) > 0 }.forEach { c ->
                            add(
                                BudgetStanding(
                                    "cat${c.id}", c.name, c.budgetMinor!!,
                                    thisMonth.filter { it.categoryId == c.id }.sumOf { it.netSpendMinor },
                                    owed.filter { it.categoryId == c.id }.sumOf { it.remainingMinor },
                                ),
                            )
                        }
                    }
                    val sent = settings.budgetAlertsSent
                    val fresh = budgetAlerts(month, standings).filter { it.key !in sent }
                    if (fresh.isEmpty()) return@collect
                    // Remembered even when muted, so turning the channel on later doesn't replay a month.
                    store.setBudgetAlertsSent(sent.filter { it.endsWith("|$month") }.toSet() + fresh.map { it.key })
                    if (!settings.budgetAlertsEnabled || !settings.notifyRemindersEnabled || !settings.notifyMoneyChannel) return@collect
                    // The bell's list keeps each alert even when Android won't show it.
                    fresh.forEach { alert -> com.example.timeapp.data.repository.InboxRepository(db).record(com.example.timeapp.data.InboxKind.BUDGET, alert.title, alert.body, "money") }
                    val manager = NotificationManagerCompat.from(app)
                    if (!manager.areNotificationsEnabled()) return@collect
                    NotificationChannels.ensureCreated(app)
                    fresh.forEach { alert ->
                        val id = ID_BASE - (alert.key.hashCode() and 0xFFF)
                        val open = android.app.PendingIntent.getActivity(
                            app, id,
                            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                .setData(android.net.Uri.parse("timeapp://budget/${alert.key}"))
                                .putExtra(MainActivity.EXTRA_OPEN_ROUTE, "money"),
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                        )
                        val builder = NotificationCompat.Builder(app, NotificationChannels.idFor(NotifyChannel.MONEY))
                            .setSmallIcon(com.example.timeapp.R.drawable.ic_stat_time)
                            .setContentTitle(alert.title)
                            .setContentText(alert.body)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
                            .setContentIntent(open)
                            .setAutoCancel(true)
                        if (settings.isQuiet()) builder.setSilent(true)
                        if (settings.notifyRedactContent) {
                            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                            builder.setPublicVersion(
                                NotificationCompat.Builder(app, NotificationChannels.idFor(NotifyChannel.MONEY))
                                    .setSmallIcon(com.example.timeapp.R.drawable.ic_stat_time)
                                    .setContentTitle("T!ME")
                                    .setContentText(L("A budget update"))
                                    .build(),
                            )
                        }
                        try {
                            manager.notify(id, builder.build())
                        } catch (e: SecurityException) {
                            // Permission revoked — nothing to post.
                        }
                    }
                }
        }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
