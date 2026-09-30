package com.example.timeapp.ui

import kotlinx.coroutines.flow.flowOn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timeapp.data.Category
import com.example.timeapp.data.CategoryKind
import com.example.timeapp.data.repository.CategoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs every category picker plus the Categories settings screen. */
class CategoryViewModel(private val repository: CategoryRepository) : ViewModel() {

    val all: StateFlow<List<Category>> = repository.observeAll()
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val work: StateFlow<List<Category>> = all
        .map { list -> list.filter { it.kind == CategoryKind.WORK && !it.archived } }
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val money: StateFlow<List<Category>> = all
        .map { list -> list.filter { it.kind == CategoryKind.MONEY && !it.archived } }
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Projects are PROJECT-kind categories — see [CategoryKind]. */
    val projects: StateFlow<List<Category>> = all
        .map { list -> list.filter { it.kind == CategoryKind.PROJECT && !it.archived } }
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun add(name: String, colorArgb: Long, kind: CategoryKind, budgetMinor: Long? = null, onCreated: (Long) -> Unit = {}, icon: String? = null) {
        viewModelScope.launch { onCreated(repository.add(name, colorArgb, kind, budgetMinor, icon)) }
    }

    fun update(category: Category) {
        viewModelScope.launch { repository.update(category) }
    }

    fun merge(from: Category, into: Category) {
        viewModelScope.launch { repository.merge(from, into) }
    }

    fun setArchived(category: Category, archived: Boolean) {
        viewModelScope.launch { repository.setArchived(category, archived) }
    }

    class Factory(private val repository: CategoryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CategoryViewModel(repository) as T
    }
}
