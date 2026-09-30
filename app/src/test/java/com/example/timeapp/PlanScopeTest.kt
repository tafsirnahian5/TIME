package com.example.timeapp

import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.db.normaliseTag
import com.example.timeapp.ui.plan.PlanScope
import com.example.timeapp.ui.plan.PlanUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanScopeTest {
    private fun task(id: Long, projectId: Long? = null, tags: List<String> = emptyList()) =
        Entry(id, EntryKind.TASK, "t$id", status = EntryStatus.PLANNED, projectId = projectId, tags = tags)

    private val entries = listOf(task(1, 10, listOf("android")), task(2, 10), task(3, null, listOf("android", "ui")), task(4))

    @Test
    fun `no scope shows everything`() {
        assertEquals(4, PlanUiState(entries).scopedEntries.size)
    }

    @Test
    fun `project and tag narrow together`() {
        assertEquals(listOf(1L, 2L), PlanUiState(entries, scope = PlanScope(projectId = 10)).scopedEntries.map { it.id })
        assertEquals(listOf(1L, 3L), PlanUiState(entries, scope = PlanScope(tag = "android")).scopedEntries.map { it.id })
        assertEquals(listOf(1L), PlanUiState(entries, scope = PlanScope(10, "android")).scopedEntries.map { it.id })
    }

    /** "Android", "#android" and " android " must be one tag, not three. */
    @Test
    fun `tag names normalise to one form`() {
        assertEquals("android", normaliseTag("Android"))
        assertEquals("android", normaliseTag(" #android "))
        assertEquals("side-project", normaliseTag("Side  Project"))
        assertEquals("", normaliseTag(" # "))
    }
}
