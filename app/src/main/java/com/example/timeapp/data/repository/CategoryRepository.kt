package com.example.timeapp.data.repository

import com.example.timeapp.data.Category
import com.example.timeapp.data.CategoryKind
import com.example.timeapp.data.DefaultMoneyCategories
import com.example.timeapp.data.DefaultWorkCategories
import androidx.room.withTransaction
import com.example.timeapp.data.db.CategoryEntity
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.toDomain
import com.example.timeapp.data.db.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The user's own work and money categories.
 *
 * Records store a `categoryId` and resolve the name/colour at read time, so renaming or
 * recolouring propagates everywhere at once — the PDF's "renaming/recoloring updates all
 * views without changing amounts or durations". Categories are archived rather than deleted
 * so a historical record never loses its label.
 */
class CategoryRepository(private val db: TimeDatabase) {
    private val dao get() = db.categoryDao()

    fun observeAll(): Flow<List<Category>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeActive(kind: CategoryKind): Flow<List<Category>> =
        dao.observeActive(kind).map { list -> list.map { it.toDomain() } }

    /** Creates the starter categories the first time the app runs. This is scaffolding, not
     * demo data: with an empty table nothing could be categorised at all. */
    suspend fun ensureDefaults() {
        if (dao.count() > 0) return
        DefaultWorkCategories.forEachIndexed { index, (name, color) ->
            dao.insert(CategoryEntity(name = name, colorArgb = color, kind = CategoryKind.WORK, sortOrder = index))
        }
        DefaultMoneyCategories.forEachIndexed { index, (name, color) ->
            dao.insert(CategoryEntity(name = name, colorArgb = color, kind = CategoryKind.MONEY, sortOrder = index))
        }
    }

    suspend fun add(name: String, colorArgb: Long, kind: CategoryKind, budgetMinor: Long? = null, icon: String? = null): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1
        val existing = dao.all().firstOrNull { it.kind == kind && it.name.equals(trimmed, ignoreCase = true) }
        // Re-adding an archived category brings it back rather than creating a duplicate.
        if (existing != null) {
            if (existing.archived) dao.update(existing.copy(archived = false))
            return existing.id
        }
        val order = dao.all().count { it.kind == kind }
        return dao.insert(CategoryEntity(name = trimmed, colorArgb = colorArgb, kind = kind, sortOrder = order, budgetMinor = budgetMinor, icon = icon))
    }

    suspend fun update(category: Category) {
        if (category.name.isBlank()) return
        dao.update(category.copy(name = category.name.trim()).toEntity())
    }

    /**
     * Merges [from] into [into] (the PDF: "archive a used category or remap its records
     * explicitly"): every task, bill, transaction and habit on [from] moves to [into], in one
     * transaction, and [from] is archived — never deleted. Same kind only.
     */
    suspend fun merge(from: Category, into: Category) {
        if (from.id == into.id || from.kind != into.kind) return
        db.withTransaction {
            if (from.kind == CategoryKind.PROJECT) {
                dao.remapProjects(from.id, into.id)
            } else {
                dao.remapEntries(from.id, into.id)
                dao.remapMoney(from.id, into.id)
                dao.remapHabits(from.id, into.id)
            }
            dao.update(from.copy(archived = true).toEntity())
        }
    }

    /** Archive, never delete — existing records keep resolving their label. */
    suspend fun setArchived(category: Category, archived: Boolean) {
        dao.update(category.copy(archived = archived).toEntity())
    }
}
