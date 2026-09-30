package com.example.timeapp.ui

import kotlinx.coroutines.flow.flowOn
import com.example.timeapp.data.L

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timeapp.data.Achievement
import com.example.timeapp.data.Bookmark
import com.example.timeapp.data.repository.MiscRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Achievements (G1) stay read-only. Bookmarks (Links) get add/delete below. */
class MiscViewModel(
    private val repository: MiscRepository,
    achievementsRepository: com.example.timeapp.data.repository.AchievementsRepository,
) : ViewModel() {
    /** Derived from live records — see AchievementsRepository for why this is not a table. */
    val achievements: StateFlow<List<Achievement>> = achievementsRepository.observe()
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val bookmarks: StateFlow<List<Bookmark>> = repository.observeBookmarks()
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addBookmark(title: String, url: String, folder: String, note: String? = null, favourite: Boolean = false) {
        if (title.isBlank() || url.isBlank()) return
        viewModelScope.launch {
            repository.addBookmark(Bookmark(id = 0, title = title, url = url, folder = folder, favourite = favourite, note = note, addedAt = java.time.Instant.now()))
        }
    }

    /** A quiet change (a favourite star, a ticked checklist line) — no Undo offered. */
    fun updateBookmark(bookmark: Bookmark) {
        viewModelScope.launch { repository.updateBookmark(bookmark) }
    }

    /** An edit saved from the Add link form, with Undo. */
    fun saveBookmarkEdit(bookmark: Bookmark) {
        viewModelScope.launch {
            val snap = repository.snapshotBookmark(bookmark.id)
            repository.updateBookmark(bookmark)
            offerRestore(L("“%1\$s” updated", bookmark.title), snap)
        }
    }

    private fun offerRestore(message: String, snap: com.example.timeapp.data.db.BookmarkRowsSnapshot) {
        com.example.timeapp.ui.plan.UndoBus.offer(com.example.timeapp.ui.plan.UndoAction(message) {
            viewModelScope.launch { repository.restoreBookmark(snap) }
        })
    }

    fun importBookmarks(rows: List<com.example.timeapp.data.BookmarkHtml.Row>, onDone: (Int) -> Unit) {
        val bookmarks = rows.filter { it.importable }.map { r ->
            Bookmark(0, r.parsed.title, r.url!!, r.parsed.folder, r.parsed.favourite, r.parsed.note, addedAt = r.parsed.addedAt ?: java.time.Instant.now())
        }
        viewModelScope.launch {
            repository.importBookmarks(bookmarks)
            onDone(bookmarks.size)
        }
    }

    /** Hides a link from the list, keeping its task links; Undo or *Unarchive* bring it back. */
    fun archiveBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            repository.setBookmarkArchived(bookmark.id, java.time.Instant.now())
            com.example.timeapp.ui.plan.UndoBus.offer(com.example.timeapp.ui.plan.UndoAction(L("“%1\$s” archived", bookmark.title)) {
                viewModelScope.launch { repository.setBookmarkArchived(bookmark.id, null) }
            })
        }
    }

    fun unarchiveBookmark(id: Long) {
        viewModelScope.launch { repository.setBookmarkArchived(id, null) }
    }

    fun moveBookmark(bookmark: Bookmark, folder: String) {
        val target = folder.trim()
        if (target.isEmpty() || target == bookmark.folder) return
        viewModelScope.launch {
            repository.setBookmarkFolder(bookmark.id, target)
            com.example.timeapp.ui.plan.UndoBus.offer(com.example.timeapp.ui.plan.UndoAction(L("Moved to %1\$s", target)) {
                viewModelScope.launch { repository.setBookmarkFolder(bookmark.id, bookmark.folder) }
            })
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            val snap = repository.snapshotBookmark(id)
            repository.deleteBookmark(id)
            offerRestore(L("“%1\$s” deleted", snap.title), snap)
        }
    }

    class Factory(
        private val repository: MiscRepository,
        private val achievementsRepository: com.example.timeapp.data.repository.AchievementsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MiscViewModel(repository, achievementsRepository) as T
    }
}
