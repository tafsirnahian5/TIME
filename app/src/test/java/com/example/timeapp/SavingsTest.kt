package com.example.timeapp

import com.example.timeapp.data.GoalAllocation
import com.example.timeapp.data.SavingsGoal
import com.example.timeapp.data.earmarkedByWallet
import com.example.timeapp.data.goalPace
import com.example.timeapp.data.savedFor
import com.example.timeapp.data.unearmarkedMinor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SavingsTest {
    private val t = Instant.EPOCH
    private val allocs = listOf(
        GoalAllocation(1, goalId = 1, walletId = 10, amountMinor = 500_000, at = t),
        GoalAllocation(2, goalId = 1, walletId = 11, amountMinor = 200_000, at = t),
        GoalAllocation(3, goalId = 2, walletId = 10, amountMinor = 100_000, at = t),
        GoalAllocation(4, goalId = 1, walletId = 10, amountMinor = -50_000, at = t), // taken back
    )

    @Test
    fun `saved and earmarked sums include take-backs`() {
        assertEquals(650_000L, allocs.savedFor(1))
        assertEquals(mapOf(10L to 550_000L, 11L to 200_000L), allocs.earmarkedByWallet())
    }

    /** The PDF: earmarking never changes the balance; free money is shown beside it. */
    @Test
    fun `unearmarked is balance minus earmarks, never negative`() {
        assertEquals(450_000L, unearmarkedMinor(1_000_000, 550_000))
        assertEquals(0L, unearmarkedMinor(300_000, 550_000))
    }

    @Test
    fun `pace spreads what's left over the months to the target, rounding up`() {
        val goal = SavingsGoal(1, "Laptop", 12_000_000, targetDate = LocalDate.of(2026, 12, 15))
        val pace = goalPace(goal, savedMinor = 2_000_000, today = LocalDate.of(2026, 9, 28))
        assertEquals(4, pace.monthsLeft) // Sep, Oct, Nov, Dec
        assertEquals(2_500_000L, pace.perMonthMinor)
        val odd = goalPace(goal.copy(targetMinor = 1_000_001), 0, LocalDate.of(2026, 9, 28))
        assertEquals(250_001L, odd.perMonthMinor)
    }

    @Test
    fun `no date means no pace, and a passed date with money left is late`() {
        val open = goalPace(SavingsGoal(1, "Trip", 500_000), 100_000, LocalDate.of(2026, 9, 28))
        assertNull(open.perMonthMinor)
        val late = goalPace(SavingsGoal(1, "Trip", 500_000, LocalDate.of(2026, 9, 1)), 100_000, LocalDate.of(2026, 9, 28))
        assertTrue(late.late)
        assertTrue(goalPace(SavingsGoal(1, "Trip", 500_000), 600_000, LocalDate.of(2026, 9, 28)).reached)
    }
}
