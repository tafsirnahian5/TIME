package com.example.timeapp

import com.example.timeapp.data.Direction
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.PlanStatus
import com.example.timeapp.data.db.toEntity
import com.example.timeapp.data.outstandingThisMonth
import com.example.timeapp.data.storedPlanStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The PDF: statuses are Planned, Partly paid, Settled and Cancelled; Overdue is a date badge. */
class PlanStatusTest {
    private val today = LocalDate.of(2026, 9, 28)
    private fun plan(status: PlanStatus?, due: LocalDate?, amount: Long = 1_000, paid: Long = 0) =
        MoneyRecord(1, Direction.COST, "Rent", amountMinor = amount, paidMinor = paid, dueOn = due, status = status)

    /** Adding a date to an undated plan used to leave it Unscheduled — out of every forecast. */
    @Test
    fun `an unpaid plan's stored status follows its date`() {
        assertEquals(PlanStatus.PLANNED, storedPlanStatus(PlanStatus.UNSCHEDULED, today))
        assertEquals(PlanStatus.UNSCHEDULED, storedPlanStatus(PlanStatus.PLANNED, null))
        assertEquals(PlanStatus.PLANNED, plan(PlanStatus.OVERDUE, today.plusDays(9)).toEntity().status)
        assertEquals(PlanStatus.PARTLY_PAID, storedPlanStatus(PlanStatus.PARTLY_PAID, null))
        assertEquals(PlanStatus.CANCELLED, storedPlanStatus(PlanStatus.CANCELLED, today))
    }

    @Test
    fun `overdue is a badge, partly paid or not`() {
        assertEquals(PlanStatus.OVERDUE, plan(PlanStatus.PLANNED, today.minusDays(1)).displayPlanStatus(today))
        assertEquals(PlanStatus.PLANNED, plan(PlanStatus.OVERDUE, today).displayPlanStatus(today))
        val partly = plan(PlanStatus.PARTLY_PAID, today.minusDays(3), paid = 400)
        assertEquals(PlanStatus.PARTLY_PAID, partly.displayPlanStatus(today))
        assertTrue(partly.isOverdue(today))
    }

    /** Cancelling releases what was still owed; it never counts as outstanding again. */
    @Test
    fun `a cancelled plan owes nothing`() {
        val cancelled = plan(PlanStatus.CANCELLED, today.minusDays(3), paid = 400)
        assertFalse(cancelled.isOpen)
        assertFalse(cancelled.isOverdue(today))
        assertEquals(0L, listOf(cancelled).outstandingThisMonth(today))
    }
}
