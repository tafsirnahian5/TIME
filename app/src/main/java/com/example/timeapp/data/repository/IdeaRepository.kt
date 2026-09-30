package com.example.timeapp.data.repository

import com.example.timeapp.data.CollectionKind
import com.example.timeapp.data.Idea
import com.example.timeapp.data.IdeaCollection
import com.example.timeapp.data.L
import com.example.timeapp.data.db.IdeaCollectionDao
import com.example.timeapp.data.db.IdeaCollectionEntity
import com.example.timeapp.data.db.IdeaDao
import com.example.timeapp.data.db.IdeaEntity
import com.example.timeapp.data.db.toDomain
import com.example.timeapp.data.db.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class IdeaRepository(
    private val collectionDao: IdeaCollectionDao,
    private val ideaDao: IdeaDao,
) {
    /** count/doneCount are recomputed live from real idea rows, not the stored seed values —
     * a collection's tile always agrees with what BucketListScreen (or any future collection
     * detail screen) shows for the same collection. */
    fun observeCollections(): Flow<List<IdeaCollection>> =
        combine(collectionDao.observeAll(), ideaDao.observeAll()) { collections, ideas ->
            collections.map { c ->
                val inCollection = ideas.filter { it.collectionId == c.id }
                c.toDomain().copy(count = inCollection.size, doneCount = inCollection.count { it.isDone })
            }
        }
    fun observeIdeas(): Flow<List<Idea>> = ideaDao.observeAll().map { list -> list.map { it.toDomain() } }

    /** The newest ideas. This used to keep only ideas carrying the old demo data's fake "2d"
     * label, so nothing a person added ever appeared here. (No edit time is stored, so it's
     * honestly "recently added".) */
    fun observeRecentlyTouched(): Flow<List<Idea>> = observeIdeas().map { ideas -> ideas.sortedByDescending { it.id }.take(8) }

    fun observeBucketOpen(): Flow<List<Idea>> = bucketIdeas().map { ideas -> ideas.filter { !it.isDone } }
    fun observeBucketDone(): Flow<List<Idea>> = bucketIdeas().map { ideas -> ideas.filter { it.isDone } }

    private fun bucketIdeas(): Flow<List<Idea>> =
        combine(observeCollections(), observeIdeas()) { collections, ideas ->
            val bucketId = collections.firstOrNull { it.kind == CollectionKind.BUCKET }?.id
            ideas.filter { it.collectionId == bucketId }
        }

    /** New user-created collection — not in the real design canvas (Ideas has no PDF spec at
     * all); a plain LIST kind with a generic icon, same precedent as "Reading"/"Places". */
    suspend fun addCollection(name: String) {
        if (name.isBlank()) return
        collectionDao.insert(
            IdeaCollectionEntity(name = name, kind = CollectionKind.LIST, icon = "menu_book", iconColorArgb = 0xFF6C4ED9, count = 0),
        )
    }

    /**
     * Renames one of the user's own collections. Sparks and the Bucket list keep their names:
     * quick capture finds them by name. Returns why it was refused, or null.
     */
    suspend fun renameCollection(id: Long, name: String): String? {
        val trimmed = name.trim()
        val c = collectionDao.getById(id) ?: return L("That collection no longer exists.")
        if (c.kind != CollectionKind.LIST) return L("Sparks and the Bucket list keep their names.")
        if (trimmed.isEmpty()) return L("Give it a name.")
        val clash = collectionDao.getByName(trimmed)
        if (clash != null && clash.id != id) return L("There's already a collection called “%1\$s”.", trimmed)
        collectionDao.rename(id, trimmed)
        return null
    }

    /**
     * Deletes one of the user's own collections. Its ideas move to Sparks first, in the same
     * transaction — deleting a folder never deletes what's in it. Returns why it was refused, or null.
     */
    suspend fun deleteCollection(id: Long): String? {
        val c = collectionDao.getById(id) ?: return null
        if (c.kind != CollectionKind.LIST) return L("Sparks and the Bucket list can't be deleted.")
        val sparks = collectionDao.firstOfKind(CollectionKind.SPARKS) ?: return L("There's no Sparks collection to move its ideas to.")
        collectionDao.deleteMovingIdeas(id, sparks.id)
        return null
    }

    /**
     * Sparks and the Bucket list always exist: quick capture saves into them. Like the starter
     * categories they're scaffolding, not demo data — on a fresh install they used to be missing
     * (only the demo data made them), and every idea saved was silently thrown away.
     */
    suspend fun ensureDefaults() {
        if (collectionDao.firstOfKind(CollectionKind.SPARKS) == null) {
            collectionDao.insert(IdeaCollectionEntity(name = "Sparks", kind = CollectionKind.SPARKS, icon = "bolt", iconColorArgb = 0xFF5B41AE, count = 0))
        }
        if (collectionDao.firstOfKind(CollectionKind.BUCKET) == null) {
            collectionDao.insert(IdeaCollectionEntity(name = "Bucket list", kind = CollectionKind.BUCKET, icon = "menu_book", iconColorArgb = 0xFFFFFFFF, count = 0))
        }
    }

    /**
     * I4 quick capture into [collectionId] — or Sparks when it's null or gone. It used to look the
     * collection up by its *name*, which failed whenever that name didn't exist (a fresh install,
     * the Bangla UI) and dropped the idea without a word. An idea is never dropped now.
     */
    suspend fun addIdea(title: String, collectionId: Long?): Long {
        val target = collectionId?.let { collectionDao.getById(it) }
            ?: collectionDao.firstOfKind(CollectionKind.SPARKS)
            ?: run { ensureDefaults(); collectionDao.firstOfKind(CollectionKind.SPARKS)!! }
        return ideaDao.insert(IdeaEntity(collectionId = target.id, title = title.trim()))
    }

    suspend fun updateIdea(idea: Idea) = ideaDao.update(idea.toEntity())

    suspend fun deleteIdea(id: Long) = ideaDao.delete(id)

    suspend fun toggleDone(id: Long) {
        val idea = ideaDao.getById(id) ?: return
        ideaDao.update(idea.copy(isDone = !idea.isDone))
    }

    suspend fun markPromoted(id: Long) {
        val idea = ideaDao.getById(id) ?: return
        ideaDao.update(idea.copy(promotedToPlan = true))
    }
}
