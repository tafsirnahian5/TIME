package com.example.timeapp.data

/**
 * A project's time and money together (Phase 13; the PDF's project summary): tracked time on
 * its tasks, money spent on it (net of refunds) and still planned. A money row belongs to the
 * project when it names the project, or a task in it. Pure; tested in ProjectSummaryTest.
 */
data class ProjectSummary(
    val trackedMs: Long,
    val tasks: Int,
    val tasksDone: Int,
    val spentMinor: Long,
    val plannedMinor: Long,
)

fun projectSummary(projectId: Long, entries: List<Entry>, sessions: List<WorkSessionSpan>, history: List<MoneyRecord>, plans: List<MoneyRecord>): ProjectSummary {
    val mine = entries.filter { it.projectId == projectId }
    val ids = mine.map { it.id }.toSet()
    fun belongs(r: MoneyRecord) = r.projectId == projectId || (r.taskId != null && r.taskId in ids)
    return ProjectSummary(
        trackedMs = sessions.filter { it.entryId in ids }.sumOf { it.durationMs },
        tasks = mine.size,
        tasksDone = mine.count { it.status == EntryStatus.DONE },
        spentMinor = history.filter(::belongs).sumOf { it.netSpendMinor },
        plannedMinor = plans.filter { it.isOpen && belongs(it) }.sumOf { it.remainingMinor },
    )
}
