package com.example.timeapp

import com.example.timeapp.data.Direction
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.PlanStatus
import com.example.timeapp.data.WorkSessionSpan
import com.example.timeapp.data.projectSummary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ProjectSummaryTest {
    private val t = Instant.parse("2026-09-28T09:00:00Z")

    @Test
    fun `time and money meet on a project`() {
        val entries = listOf(
            Entry(1, EntryKind.TASK, "Design", status = EntryStatus.DONE, projectId = 9),
            Entry(2, EntryKind.TASK, "Build", status = EntryStatus.PLANNED, projectId = 9),
            Entry(3, EntryKind.TASK, "Other", status = EntryStatus.PLANNED),
        )
        val sessions = listOf(WorkSessionSpan(1, t, t.plusSeconds(3600)), WorkSessionSpan(3, t, t.plusSeconds(600)))
        val history = listOf(
            MoneyRecord(10, Direction.COST, "Domain", amountMinor = 150_000, paidMinor = 150_000, settledAt = t, projectId = 9),
            MoneyRecord(11, Direction.COST, "Fonts", amountMinor = 50_000, paidMinor = 50_000, settledAt = t, taskId = 2),
            MoneyRecord(12, Direction.COST, "Lunch", amountMinor = 30_000, paidMinor = 30_000, settledAt = t),
        )
        val plans = listOf(MoneyRecord(13, Direction.COST, "Hosting", amountMinor = 200_000, status = PlanStatus.PLANNED, projectId = 9))
        val s = projectSummary(9, entries, sessions, history, plans)
        assertEquals(3_600_000L, s.trackedMs)
        assertEquals(2 to 1, s.tasks to s.tasksDone)
        assertEquals(200_000L, s.spentMinor)
        assertEquals(200_000L, s.plannedMinor)
    }
}
