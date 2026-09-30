package com.example.timeapp.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What an item in the bell list is about — picks its icon. Stored by name. */
enum class InboxKind { TASK, BOOKING, BILL, HABIT, LOAN, TIMER, FOCUS, MISSED, BUDGET, BACKUP, WORK, OTHER }

/** A line in the app's own notification list (see `InboxItemEntity`). */
data class InboxItem(
    val id: Long,
    val kind: InboxKind,
    val title: String,
    val body: String,
    val route: String?,
    val createdAt: Instant,
    val read: Boolean,
)

/** How long the list keeps things, and how many at most. */
const val INBOX_KEEP_DAYS = 30L
const val INBOX_KEEP_MAX = 200

enum class InboxSection { TODAY, YESTERDAY, EARLIER }

/**
 * Newest first, grouped Today / Yesterday / Earlier in the phone's time zone. Pure, so the day
 * boundary is tested (a 23:59 item and a 00:01 item land in different groups).
 */
fun inboxSections(items: List<InboxItem>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Pair<InboxSection, List<InboxItem>>> {
    val sorted = items.sortedWith(compareByDescending<InboxItem> { it.createdAt }.thenByDescending { it.id })
    val bySection = sorted.groupBy { item ->
        when (item.createdAt.atZone(zone).toLocalDate()) {
            today -> InboxSection.TODAY
            today.minusDays(1) -> InboxSection.YESTERDAY
            else -> InboxSection.EARLIER
        }
    }
    return InboxSection.entries.mapNotNull { s -> bySection[s]?.let { s to it } }
}
