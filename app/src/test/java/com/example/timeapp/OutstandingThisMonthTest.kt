package com.example.timeapp

import com.example.timeapp.data.Direction
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.PlanStatus
import com.example.timeapp.data.outstandingThisMonth
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Today and Money › Planned both label their headline figure "outstanding this month"
 * while previously summing *every* open plan, so a bill due in December inflated
 * September's number. Both now call [outstandingThisMonth]; these pin the scoping rules
 * so the two screens cannot drift apart again.
 */
class OutstandingThisMonthTest {

    private val today = LocalDate.of(2026, 9, 26)

    private fun plan(
        id: Long,
        amount: Long,
        paid: Long = 0,
        due: LocalDate? = null,
        status: PlanStatus? = PlanStatus.PLANNED,
    ) = MoneyRecord(
        id = id,
        direction = Direction.COST,
        title = "plan $id",
        amountMinor = amount,
        paidMinor = paid,
        dueOn = due,
        status = status,
    )

    @Test
    fun `a plan due later this month counts`() {
        val plans = listOf(plan(1, 5_000, due = LocalDate.of(2026, 9, 30)))
        assertEquals(5_000L, plans.outstandingThisMonth(today))
    }

    @Test
    fun `a plan due next month does not count`() {
        val plans = listOf(plan(1, 5_000, due = LocalDate.of(2026, 10, 1)))
        assertEquals(0L, plans.outstandingThisMonth(today))
    }

    /** The regression this was written for: December rent must not appear in September. */
    @Test
    fun `a bill months away is excluded`() {
        val plans = listOf(
            plan(1, 1_200_00, due = LocalDate.of(2026, 9, 28)),
            plan(2, 9_999_00, due = LocalDate.of(2026, 12, 1)),
        )
        assertEquals(1_200_00L, plans.outstandingThisMonth(today))
    }

    /** Carryover is the whole reason the neighbouring card says "included above". */
    @Test
    fun `an overdue plan from an earlier month still counts`() {
        val plans = listOf(plan(1, 3_000, due = LocalDate.of(2026, 7, 4), status = PlanStatus.OVERDUE))
        assertEquals(3_000L, plans.outstandingThisMonth(today))
    }

    @Test
    fun `an undated plan counts as owed now`() {
        val plans = listOf(plan(1, 2_500, due = null, status = PlanStatus.UNSCHEDULED))
        assertEquals(2_500L, plans.outstandingThisMonth(today))
    }

    @Test
    fun `only the unpaid remainder counts`() {
        val plans = listOf(plan(1, 5_000, paid = 2_000, due = LocalDate.of(2026, 9, 20)))
        assertEquals(3_000L, plans.outstandingThisMonth(today))
    }

    /** A settled plan is kept for variance reporting but is no longer owed. */
    @Test
    fun `settled and ledger rows are excluded`() {
        val plans = listOf(
            plan(1, 5_000, due = LocalDate.of(2026, 9, 20), status = PlanStatus.SETTLED),
            plan(2, 7_000, due = LocalDate.of(2026, 9, 20), status = null),
        )
        assertEquals(0L, plans.outstandingThisMonth(today))
    }

    /** End-of-month boundary: the last day of the month is inside the window. */
    @Test
    fun `the last day of the month is included`() {
        val plans = listOf(plan(1, 400, due = LocalDate.of(2026, 9, 30)))
        assertEquals(400L, plans.outstandingThisMonth(LocalDate.of(2026, 9, 1)))
    }

    /** February, so the clamp uses the real month length rather than a fixed 30/31. */
    @Test
    fun `a short month uses its own length`() {
        val feb = LocalDate.of(2027, 2, 10)
        val plans = listOf(
            plan(1, 100, due = LocalDate.of(2027, 2, 28)),
            plan(2, 900, due = LocalDate.of(2027, 3, 1)),
        )
        assertEquals(100L, plans.outstandingThisMonth(feb))
    }

    @Test
    fun `an empty list is zero, not an error`() {
        assertEquals(0L, emptyList<MoneyRecord>().outstandingThisMonth(today))
    }
}
