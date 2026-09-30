package com.example.timeapp

import com.example.timeapp.data.BudgetStanding
import com.example.timeapp.data.budgetAlerts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class BudgetAlertsTest {
    private val sept = YearMonth.of(2026, 9)
    private fun b(spent: Long, planned: Long = 0, limit: Long = 2_000_000) = BudgetStanding("all", "Monthly budget", limit, spent, planned)

    @Test
    fun `the most serious level wins`() {
        assertEquals(emptyList<Any>(), budgetAlerts(sept, listOf(b(1_000_000))))
        assertTrue(budgetAlerts(sept, listOf(b(1_600_000))).single().key.startsWith("80|"))
        // The PDF's worked example: Tk 6,000 paid + Tk 8,000 outstanding against a Tk 12,000 budget.
        assertTrue(budgetAlerts(sept, listOf(b(600_000, 800_000, 1_200_000))).single().key.startsWith("projected|"))
        assertTrue(budgetAlerts(sept, listOf(b(2_100_000))).single().key.startsWith("over|"))
    }

    @Test
    fun `keys carry the month so each level fires once a month`() {
        assertEquals("80|all|2026-09", budgetAlerts(sept, listOf(b(1_700_000))).single().key)
        assertEquals("80|all|2026-10", budgetAlerts(sept.plusMonths(1), listOf(b(1_700_000))).single().key)
    }

    @Test
    fun `no limit, no alert`() {
        assertEquals(emptyList<Any>(), budgetAlerts(sept, listOf(b(5_000_000, limit = 0))))
    }
}
