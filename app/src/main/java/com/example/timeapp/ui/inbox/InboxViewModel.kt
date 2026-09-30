package com.example.timeapp.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timeapp.data.InboxItem
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.repository.InboxRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The bell: its unread count (shown on every tab's bar) and the list behind it. */
class InboxViewModel(private val repository: InboxRepository, private val database: TimeDatabase) : ViewModel() {
    val items: StateFlow<List<InboxItem>?> = repository.observeRecent()
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val unreadCount: StateFlow<Int> = repository.observeUnreadCount()
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun markRead(id: Long) = viewModelScope.launch { repository.markRead(id) }
    fun markAllRead() = viewModelScope.launch { repository.markAllRead() }
    fun clear() = viewModelScope.launch { repository.clear() }

    /**
     * Whether what an item points at is still there. A deleted task's page would open blank, so
     * the list says so instead. Routes to whole screens (Money, Focus, the calendar) always open.
     */
    suspend fun stillThere(route: String): Boolean {
        fun idAfter(prefix: String) = route.removePrefix(prefix).toLongOrNull()
        return when {
            route.startsWith("plan/task/") -> idAfter("plan/task/")?.let { database.entryDao().getById(it) } != null
            route.startsWith("habits/") -> idAfter("habits/")?.let { database.habitDao().getById(it) } != null
            route.startsWith("money/loans/") -> idAfter("money/loans/")?.let { database.loanDao().getById(it) } != null
            else -> true
        }
    }

    class Factory(private val database: TimeDatabase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = InboxViewModel(InboxRepository(database), database) as T
    }
}
