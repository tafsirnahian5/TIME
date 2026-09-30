package com.example.timeapp

import com.example.timeapp.data.StartFacts
import com.example.timeapp.data.StartStep
import com.example.timeapp.data.doneCount
import com.example.timeapp.data.isDone
import com.example.timeapp.data.showGettingStarted

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GettingStartedTest {
    private val none = StartFacts(false, false, false, false, false)

    @Test fun freshInstallShowsAllOpen() {
        assertTrue(showGettingStarted(none, dismissed = false))
        assertEquals(0, none.doneCount())
    }

    @Test fun eachStepTicksFromItsOwnFact() {
        val f = none.copy(hasWallet = true, hasHabit = true)
        assertTrue(f.isDone(StartStep.WALLET))
        assertTrue(f.isDone(StartStep.HABIT))
        assertFalse(f.isDone(StartStep.BILL))
        assertEquals(2, f.doneCount())
    }

    @Test fun hiddenWhenDismissedOrComplete() {
        assertFalse(showGettingStarted(none, dismissed = true))
        assertFalse(showGettingStarted(StartFacts(true, true, true, true, true), dismissed = false))
    }
}
