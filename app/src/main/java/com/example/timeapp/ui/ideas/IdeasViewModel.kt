package com.example.timeapp.ui.ideas

import kotlinx.coroutines.flow.flowOn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timeapp.data.Idea
import com.example.timeapp.data.IdeaCollection
import com.example.timeapp.data.repository.IdeaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class IdeasUiState(
    val collections: List<IdeaCollection> = emptyList(),
    val recentlyTouched: List<Idea> = emptyList(),
    val bucketOpen: List<Idea> = emptyList(),
    val bucketDone: List<Idea> = emptyList(),
)

class IdeasViewModel(private val repository: IdeaRepository) : ViewModel() {
    val uiState: StateFlow<IdeasUiState> = combine(
        repository.observeCollections(),
        repository.observeRecentlyTouched(),
        repository.observeBucketOpen(),
        repository.observeBucketDone(),
    ) { collections, recentlyTouched, bucketOpen, bucketDone ->
        IdeasUiState(collections, recentlyTouched, bucketOpen, bucketDone)
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, IdeasUiState())

    /** For NotebookPageScreen — every idea, so it can look one up by id reactively. */
    val allIdeas: StateFlow<List<Idea>> = repository.observeIdeas()
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addCollection(name: String) {
        viewModelScope.launch { repository.addCollection(name) }
    }

    /** [onDone] gets why it was refused, or null once renamed. */
    fun renameCollection(id: Long, name: String, onDone: (String?) -> Unit) {
        viewModelScope.launch { onDone(repository.renameCollection(id, name)) }
    }

    fun deleteCollection(id: Long, onDone: (String?) -> Unit) {
        viewModelScope.launch { onDone(repository.deleteCollection(id)) }
    }

    /** I4 quick capture. */
    fun addIdea(title: String, collectionId: Long?) {
        if (title.isBlank()) return
        viewModelScope.launch { repository.addIdea(title, collectionId) }
    }

    fun toggleDone(id: Long) {
        viewModelScope.launch { repository.toggleDone(id) }
    }

    fun updateIdea(idea: Idea) {
        viewModelScope.launch { repository.updateIdea(idea) }
    }

    fun deleteIdea(id: Long) {
        viewModelScope.launch { repository.deleteIdea(id) }
    }

    fun markPromoted(id: Long) {
        viewModelScope.launch { repository.markPromoted(id) }
    }

    class Factory(private val repository: IdeaRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = IdeasViewModel(repository) as T
    }
}
