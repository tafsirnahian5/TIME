package com.example.timeapp.data.repository

import com.example.timeapp.data.L

import androidx.room.withTransaction
import com.example.timeapp.data.Direction
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.PlanStatus
import com.example.timeapp.data.SeriesOccurrence
import com.example.timeapp.data.SeriesScope
import com.example.timeapp.data.planSeriesSplit
import com.example.timeapp.data.Wallet
import com.example.timeapp.data.walletEffect
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.toDomain
import com.example.timeapp.data.db.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import kotlin.math.abs

class MoneyRepository(private val db: TimeDatabase) {
    private val walletDao = db.walletDao()
    private val moneyDao = db.moneyRecordDao()

    fun observeWallets(): Flow<List<Wallet>> = walletDao.observeAll().map { list -> list.map { it.toDomain() } }
    /** Plans and history resolve their category colour from the category table — see
     * EntryRepository.observeEntries for why this happens at read time. */
    fun observePlans(): Flow<List<MoneyRecord>> = withCategories(moneyDao.observePlans())
    fun observeHistory(): Flow<List<MoneyRecord>> = withCategories(moneyDao.observeHistory())

    private fun withCategories(source: Flow<List<com.example.timeapp.data.db.MoneyRecordEntity>>): Flow<List<MoneyRecord>> =
        combine(source, db.categoryDao().observeAll()) { records, categories ->
            val byId = categories.associateBy { it.id }
            records.map { entity ->
                val resolved = entity.categoryId?.let { byId[it] }
                val domain = entity.toDomain()
                if (resolved == null) domain else domain.copy(categoryColor = resolved.colorArgb, categoryIcon = resolved.icon)
            }
        }

    /** Tier 10 — plans that settled within [from, to), for planned-vs-actual variance. */
    fun observeSettledPlansInRange(from: Instant, to: Instant): Flow<List<MoneyRecord>> =
        moneyDao.observeSettledPlansInRange(from, to).map { list -> list.map { it.toDomain() } }

    /** Ported from the old `SampleData.walletBalance`: opening balance + signed sum of history. */
    fun walletBalance(walletId: Long): Flow<Long> =
        combine(walletDao.observeAll(), moneyDao.observeHistory()) { wallets, history ->
            val wallet = wallets.first { it.id == walletId }
            wallet.openingBalanceMinor + history.sumOf { it.toDomain().walletEffect(walletId) }
        }

    /** B3 — new planned cost: never posts a transaction or touches a wallet balance. */
    suspend fun addPlan(record: MoneyRecord): Long {
        val id = moneyDao.insert(record.toEntity())
        // See EntryRepository.insert — the first occurrence is its own series template.
        if (record.recurrence != null) {
            moneyDao.getById(id)?.let { moneyDao.update(it.copy(seriesId = id)) }
        }
        return id
    }

    suspend fun addWallet(wallet: Wallet) {
        walletDao.insert(wallet.toEntity())
    }

    suspend fun renameWallet(walletId: Long, newName: String) {
        val current = walletDao.observeAll().first().firstOrNull { it.id == walletId } ?: return
        walletDao.update(current.copy(name = newName))
    }

    /** Archive or restore a wallet. Nothing else changes: its records, and so its balance,
     * stay exactly as they were (the PDF: archive wallets with history, never delete them). */
    suspend fun setWalletArchived(walletId: Long, archived: Boolean) {
        val current = walletDao.observeAll().first().firstOrNull { it.id == walletId } ?: return
        walletDao.update(current.copy(archivedAt = if (archived) java.time.Instant.now() else null))
    }

    suspend fun updatePlan(record: MoneyRecord) {
        moneyDao.update(record.toEntity())
    }

    /**
     * Cancels an open plan: what was still owed stops counting anywhere, and every payment
     * already made stays — in the ledger and on the plan (the PDF: "cancelling a partially paid
     * plan releases only its remaining commitment; it does not erase payments").
     */
    suspend fun cancelPlan(id: Long) = db.withTransaction {
        val plan = moneyDao.getById(id) ?: return@withTransaction
        if (plan.status == null || plan.status == PlanStatus.SETTLED || plan.status == PlanStatus.CANCELLED) return@withTransaction
        moneyDao.update(plan.copy(status = PlanStatus.CANCELLED))
    }

    /** Takes a cancellation back: owed again, with whatever was paid still counted. */
    suspend fun reopenCancelledPlan(id: Long) = db.withTransaction {
        val plan = moneyDao.getById(id) ?: return@withTransaction
        if (plan.status != PlanStatus.CANCELLED) return@withTransaction
        val covered = plan.paidMinor >= plan.amountMinor
        moneyDao.update(
            plan.copy(
                status = when {
                    covered -> PlanStatus.SETTLED
                    plan.paidMinor > 0 -> PlanStatus.PARTLY_PAID
                    else -> com.example.timeapp.data.storedPlanStatus(PlanStatus.PLANNED, plan.dueOn)
                },
                settledAt = if (covered) plan.settledAt ?: Instant.now() else null,
            ),
        )
    }

    fun observeCancelledPlans(): Flow<List<MoneyRecord>> = withCategories(moneyDao.observeCancelledPlans())

    /** Copies an open plan — its whole series, plus [alsoSeries] — for Undo. */
    suspend fun snapshotPlanRows(id: Long, alsoSeries: Long? = null): com.example.timeapp.data.db.PlanRowsSnapshot? = db.withTransaction {
        val row = moneyDao.getById(id) ?: return@withTransaction null
        val series = setOfNotNull(row.seriesId, alsoSeries)
        val raw = db.openHelper.writableDatabase
        val ids = setOf(id) + com.example.timeapp.data.db.RowCopy.ids(
            raw, "SELECT id FROM money_records WHERE status IS NOT NULL AND seriesId IN ${com.example.timeapp.data.db.RowCopy.inList(series)}",
        )
        com.example.timeapp.data.db.PlanRowsSnapshot.capture(raw, ids, series)
    }

    suspend fun deletePlanUndoable(id: Long, scope: SeriesScope): Pair<List<Long>, com.example.timeapp.data.db.PlanRowsSnapshot>? = db.withTransaction {
        val snap = snapshotPlanRows(id) ?: return@withTransaction null
        val removed = deletePlanInSeries(id, scope)
        if (removed.isEmpty()) null else removed to snap
    }

    suspend fun restorePlanRows(snap: com.example.timeapp.data.db.PlanRowsSnapshot) =
        db.withTransaction { snap.restore(db.openHelper.writableDatabase) }

    /** Series-aware delete; see [EntryRepository.deleteInSeries]. A later occurrence that has
     * any payment against it is kept — deleting it would orphan a real ledger row's plan — and
     * a plan that itself has payments is refused outright (cancel it instead). */
    suspend fun deletePlanInSeries(id: Long, scope: SeriesScope): List<Long> = db.withTransaction {
        val row = moneyDao.getById(id) ?: return@withTransaction emptyList()
        if (row.paidMinor > 0) return@withTransaction emptyList()
        val seriesId = row.seriesId
        if (seriesId == null || scope == SeriesScope.ONLY_THIS) {
            moneyDao.delete(id)
            if (seriesId != null) row.dueOn?.let { skipPlanInSeries(seriesId, it) }
            return@withTransaction listOf(id)
        }
        val split = planSeriesSplit(planOccurrences(seriesId), row.asOccurrence())
        val removed = split.delete + id
        removed.forEach { moneyDao.delete(it) }
        endPlanSeries(split.keepInOld + split.moveToNew, split.endOldOn)
        removed
    }

    /** Series-aware edit; see [EntryRepository.updateInSeries]. Never touches a payment:
     * an occurrence with money against it is kept as it is, only joined to the new series. */
    suspend fun updatePlanInSeries(edited: MoneyRecord, scope: SeriesScope): List<Long> = db.withTransaction {
        val original = moneyDao.getById(edited.id) ?: return@withTransaction emptyList()
        val seriesId = original.seriesId
        if (seriesId == null) {
            moneyDao.update(edited.toEntity().copy(seriesId = if (edited.recurrence != null) edited.id else null))
            return@withTransaction emptyList()
        }
        if (scope == SeriesScope.ONLY_THIS) {
            moneyDao.update(edited.toEntity().copy(recurrence = original.recurrence, seriesId = seriesId))
            val oldDate = original.dueOn
            if (oldDate != null && oldDate != edited.dueOn) skipPlanInSeries(seriesId, oldDate)
            return@withTransaction emptyList()
        }
        val split = planSeriesSplit(planOccurrences(seriesId), original.asOccurrence())
        split.delete.forEach { moneyDao.delete(it) }
        endPlanSeries(split.keepInOld, split.endOldOn)
        val newRule = edited.recurrence?.let { rule ->
            rule.copy(skipped = original.recurrence?.skipped.orEmpty().filter { d -> split.endOldOn == null || d.isAfter(split.endOldOn) }.toSet())
        }
        val newSeries = if (newRule != null) edited.id else null
        moneyDao.update(edited.toEntity().copy(recurrence = newRule, seriesId = newSeries))
        split.moveToNew.forEach { movedId ->
            moneyDao.getById(movedId)?.let { moneyDao.update(it.copy(recurrence = newRule, seriesId = newSeries)) }
        }
        split.delete
    }

    private suspend fun planOccurrences(seriesId: Long): List<SeriesOccurrence> =
        moneyDao.plansInSeries(seriesId).map { it.asOccurrence() }

    private fun com.example.timeapp.data.db.MoneyRecordEntity.asOccurrence() =
        SeriesOccurrence(id = id, date = dueOn, locked = paidMinor > 0 || status == PlanStatus.SETTLED)

    private suspend fun skipPlanInSeries(seriesId: Long, date: java.time.LocalDate) {
        moneyDao.plansInSeries(seriesId).forEach { r ->
            r.recurrence?.let { rule -> moneyDao.update(r.copy(recurrence = rule.copy(skipped = rule.skipped + date))) }
        }
    }

    private suspend fun endPlanSeries(ids: List<Long>, endOn: java.time.LocalDate?) {
        if (endOn == null) return
        ids.forEach { rowId ->
            moneyDao.getById(rowId)?.let { r ->
                r.recurrence?.let { rule ->
                    val end = rule.endOn?.takeIf { it.isBefore(endOn) } ?: endOn
                    moneyDao.update(r.copy(recurrence = rule.copy(endOn = end)))
                }
            }
        }
    }

    /** Posts one settled Direction.TRANSFER row — never counted as income or spending. */
    suspend fun transfer(fromWalletId: Long, toWalletId: Long, amountMinor: Long, feeMinor: Long = 0) = db.withTransaction {
        // The PDF: "transfer fees, if any, are separate expenses" — out of the source wallet, so
        // the transfer itself stays neutral and the fee shows up as spending.
        if (feeMinor > 0) {
            moneyDao.insert(
                MoneyRecord(
                    id = 0, direction = Direction.COST, title = L("Transfer fee"), amountMinor = feeMinor, paidMinor = feeMinor,
                    walletId = fromWalletId, settledAt = Instant.now(),
                ).toEntity(),
            )
        }
        moneyDao.insert(
            MoneyRecord(
                id = 0,
                direction = Direction.TRANSFER,
                title = L("Wallet transfer"),
                amountMinor = amountMinor,
                paidMinor = amountMinor,
                walletId = fromWalletId,
                transferToWalletId = toWalletId,
                settledAt = Instant.now(),
            ).toEntity(),
        )
    }

    /** B3b — new transaction: money that already moved, posted directly as a settled row with
     * no linked plan (distinct from a planned cost, which never touches a wallet until paid). */
    suspend fun addTransaction(direction: Direction, title: String, amountMinor: Long, walletId: Long, note: String? = null, links: com.example.timeapp.data.MoneyLinks = com.example.timeapp.data.MoneyLinks()) {
        moneyDao.insert(
            MoneyRecord(
                id = 0,
                direction = direction,
                title = title,
                amountMinor = amountMinor,
                paidMinor = amountMinor,
                walletId = walletId,
                settledAt = Instant.now(),
                note = note,
                categoryId = links.categoryId,
                taskId = links.taskId,
                projectId = links.projectId,
            ).toEntity(),
        )
    }

    /** Posts a correction as a settled COST (negative delta) or INCOME (positive delta). */
    suspend fun adjustBalance(walletId: Long, deltaMinor: Long) {
        moneyDao.insert(
            MoneyRecord(
                id = 0,
                direction = if (deltaMinor < 0) Direction.COST else Direction.INCOME,
                title = L("Balance adjustment"),
                amountMinor = abs(deltaMinor),
                paidMinor = abs(deltaMinor),
                walletId = walletId,
                settledAt = Instant.now(),
            ).toEntity(),
        )
    }

    /**
     * Deletes one settled ledger row. When it was a payment against a plan, the plan is
     * un-paid by the same amount and reopened if that leaves it unsettled — the PDF's
     * "editing or deleting a linked payment prompts a review of settlement status", enforced
     * here rather than left to the caller so a corrected typo cannot silently desync a plan.
     */
    /** What a ledger change replaced — enough to put it back (the PDF: "historical edits
     * preserve an audit record or undo state"). */
    data class LedgerSnapshot(
        val rows: List<com.example.timeapp.data.db.MoneyRecordEntity>,
        val plan: com.example.timeapp.data.db.MoneyRecordEntity?,
        val attachments: List<com.example.timeapp.data.db.AttachmentEntity> = emptyList(),
    )

    fun observeAttachments() = db.attachmentDao().observeAll()

    private suspend fun snapshotOf(id: Long, withRefunds: Boolean): LedgerSnapshot? {
        val row = moneyDao.getById(id) ?: return null
        val refunds = if (withRefunds && row.direction == Direction.COST) moneyDao.refundsOf(id) else emptyList()
        val rows = listOf(row) + refunds
        return LedgerSnapshot(rows, row.linkedPlanId?.let { moneyDao.getById(it) }, rows.flatMap { db.attachmentDao().forRecord(it.id) })
    }

    /** Deletes like [deleteHistoryRecord] and returns what's needed to undo it. */
    suspend fun deleteHistoryRecordUndoable(id: Long): LedgerSnapshot? = db.withTransaction {
        val snap = snapshotOf(id, withRefunds = true)
        deleteHistoryRecord(id)
        snap
    }

    /** Edits like [updateHistoryRecord] and returns what's needed to undo it. */
    suspend fun updateHistoryRecordUndoable(record: MoneyRecord): LedgerSnapshot? = db.withTransaction {
        val snap = snapshotOf(record.id, withRefunds = false)
        updateHistoryRecord(record)
        snap
    }

    /** Puts rows and their plan back exactly as they were, same ids. */
    suspend fun restore(snapshot: LedgerSnapshot) = db.withTransaction {
        snapshot.rows.forEach { moneyDao.upsert(it) }
        snapshot.plan?.let { moneyDao.upsert(it) }
        // Receipt files stay on disk until the next app start, so they come back too.
        snapshot.attachments.forEach { db.attachmentDao().upsert(it) }
    }

    suspend fun deleteHistoryRecord(id: Long) {
        db.withTransaction {
            val row = moneyDao.getById(id) ?: return@withTransaction
            // An expense takes its refunds with it: a refund of nothing would leave money in a
            // wallet with no reason. The plan gets back what those refunds had taken off.
            val refunds = if (row.direction == Direction.COST) moneyDao.refundsOf(id) else emptyList()
            refunds.forEach { moneyDao.delete(it.id) }
            moneyDao.delete(id)
            reconcileLinkedPlan(row.linkedPlanId, -planEffect(row) - refunds.sumOf { planEffect(it) })
        }
    }

    /** How much a ledger row counts toward its linked plan's net paid: a payment adds, a
     * refund of one takes back (the PDF: "net paid = linked payments minus linked refunds"). */
    private fun planEffect(row: com.example.timeapp.data.db.MoneyRecordEntity): Long =
        if (row.direction == Direction.REFUND) -row.paidMinor else row.paidMinor

    /** What can still be refunded on an expense: what was paid, less earlier refunds. */
    suspend fun refundableMinor(originalId: Long): Long {
        val original = moneyDao.getById(originalId) ?: return 0
        if (original.direction != Direction.COST || original.status != null) return 0
        return (original.paidMinor - moneyDao.refundsOf(originalId).sumOf { it.paidMinor }).coerceAtLeast(0)
    }

    /**
     * Records money coming back for [originalId] — a REFUND row, never income, into [walletId]
     * (the original's wallet by default). When the expense paid a plan, the plan's net paid
     * drops by the refund. A plan that was **settled** only reopens when [reopenPlan] says so:
     * the PDF's "refunds must not silently recreate a future obligation — ask". An open plan
     * simply has less paid against it. Returns false (and changes nothing) for an amount that
     * isn't positive or exceeds what's left to refund.
     */
    suspend fun refund(originalId: Long, amountMinor: Long, walletId: Long, reopenPlan: Boolean, note: String? = null): Boolean =
        db.withTransaction {
            val original = moneyDao.getById(originalId) ?: return@withTransaction false
            if (amountMinor <= 0 || amountMinor > refundableMinor(originalId)) return@withTransaction false
            moneyDao.insert(
                com.example.timeapp.data.db.MoneyRecordEntity(
                    direction = Direction.REFUND,
                    title = L("Refund: %1\$s", original.title),
                    categoryColor = original.categoryColor,
                    categoryId = original.categoryId,
                    amountMinor = amountMinor,
                    paidMinor = amountMinor,
                    walletId = walletId,
                    settledAt = Instant.now(),
                    linkedToPlan = original.linkedPlanId != null,
                    linkedPlanId = original.linkedPlanId,
                    refundOfId = originalId,
                    note = note,
                ),
            )
            val plan = original.linkedPlanId?.let { moneyDao.getById(it) }
            if (plan != null) {
                if (plan.status == PlanStatus.SETTLED && !reopenPlan) {
                    // Kept settled: less was actually spent, which the variance report shows.
                    moneyDao.update(plan.copy(paidMinor = (plan.paidMinor - amountMinor).coerceAtLeast(0)))
                } else {
                    reconcileLinkedPlan(plan.id, -amountMinor, forceOpen = reopenPlan)
                }
            }
            true
        }

    /**
     * Posts the chosen imported rows into [walletId] in **one transaction** — all or nothing,
     * so a failure part-way can never leave half a statement in the ledger. Each row is dated
     * at local noon on its day (a date-only CSV has no time; noon keeps it on that day in any
     * nearby timezone). Category names are matched to [categoryIdsByName] (lower-case keys);
     * unmatched ones import uncategorised. Returns how many were posted.
     */
    suspend fun importTransactions(
        rows: List<com.example.timeapp.data.CsvImport.ImportRow>,
        walletId: Long,
        categoryIdsByName: Map<String, Long>,
    ): Int = db.withTransaction {
        val zone = java.time.ZoneId.systemDefault()
        var count = 0
        rows.filter { it.importable }.forEach { row ->
            moneyDao.insert(
                com.example.timeapp.data.db.MoneyRecordEntity(
                    direction = row.direction!!,
                    title = row.title,
                    amountMinor = row.amountMinor!!,
                    paidMinor = row.amountMinor,
                    walletId = walletId,
                    settledAt = row.date!!.atTime(12, 0).atZone(zone).toInstant(),
                    categoryId = row.categoryName?.let { categoryIdsByName[it.trim().lowercase()] },
                    note = row.note,
                ),
            )
            count++
        }
        count
    }

    /** The plan a refund of [originalId] would touch, if any — for the "reopen?" question. */
    suspend fun linkedPlanOf(originalId: Long): MoneyRecord? =
        moneyDao.getById(originalId)?.linkedPlanId?.let { moneyDao.getById(it) }?.toDomain()

    /** Edits a settled ledger row, keeping any linked plan's paid total in step with it. */
    suspend fun updateHistoryRecord(record: MoneyRecord) {
        db.withTransaction {
            val previous = moneyDao.getById(record.id) ?: return@withTransaction
            val updated = record.toEntity()
            moneyDao.update(updated)
            reconcileLinkedPlan(record.linkedPlanId, planEffect(updated) - planEffect(previous))
        }
    }

    /** Applies [deltaMinor] to a plan's paid total and re-derives whether it is still settled. */
    private suspend fun reconcileLinkedPlan(planId: Long?, deltaMinor: Long, forceOpen: Boolean = false) {
        if (planId == null || deltaMinor == 0L) return
        val plan = moneyDao.getById(planId) ?: return
        val newPaid = (plan.paidMinor + deltaMinor).coerceAtLeast(0)
        // A cancelled plan stays cancelled: its payments changed, but nothing is owed again.
        if (plan.status == PlanStatus.CANCELLED) {
            moneyDao.update(plan.copy(paidMinor = newPaid))
            return
        }
        // forceOpen: the user chose to reopen, so even a still-covered total goes back to open.
        val stillCovered = newPaid >= plan.amountMinor && !forceOpen
        val status = when {
            stillCovered -> PlanStatus.SETTLED
            newPaid > 0 -> PlanStatus.PARTLY_PAID
            // Never Overdue: that's a badge derived from the date, not a stored status.
            else -> com.example.timeapp.data.storedPlanStatus(PlanStatus.PLANNED, plan.dueOn)
        }
        moneyDao.update(
            plan.copy(
                paidMinor = newPaid,
                status = status,
                settledAt = if (stillCovered) plan.settledAt ?: Instant.now() else null,
            ),
        )
    }

    /** E3 — record payment: settle in full or part-pay and keep open, never collapsed. Atomic.
     * Settling transitions the plan to SETTLED in place rather than deleting it, so the
     * original estimate (amountMinor) survives for variance reporting. */
    suspend fun recordPayment(planId: Long, amountMinor: Long, walletId: Long, settle: Boolean) {
        db.withTransaction {
            val plan = moneyDao.getById(planId) ?: return@withTransaction
            moneyDao.update(
                plan.copy(
                    paidMinor = plan.paidMinor + amountMinor,
                    walletId = walletId,
                    status = if (settle) PlanStatus.SETTLED else PlanStatus.PARTLY_PAID,
                    settledAt = if (settle) Instant.now() else plan.settledAt,
                ),
            )
            val historyRow = plan.copy(
                id = 0,
                paidMinor = amountMinor,
                walletId = walletId,
                settledAt = Instant.now(),
                status = null,
                linkedToPlan = true,
                linkedPlanId = planId,
            )
            moneyDao.insert(historyRow)
        }
    }
}
