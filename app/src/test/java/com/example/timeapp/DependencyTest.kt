package com.example.timeapp

import com.example.timeapp.data.dependencyWouldCycle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Links are (task → prerequisite): 1 waits on 2, 2 waits on 3. */
class DependencyTest {
    private val links = listOf(1L to 2L, 2L to 3L)

    @Test
    fun `a task can't wait on itself`() = assertTrue(dependencyWouldCycle(links, 4, 4))

    @Test
    fun `closing a loop directly or through a chain is refused`() {
        assertTrue(dependencyWouldCycle(links, 2, 1))
        assertTrue(dependencyWouldCycle(links, 3, 1))
    }

    @Test
    fun `new branches and shortcuts are fine`() {
        assertFalse(dependencyWouldCycle(links, 1, 3)) // redundant but not a loop
        assertFalse(dependencyWouldCycle(links, 4, 1))
        assertFalse(dependencyWouldCycle(links, 3, 4))
    }
}
