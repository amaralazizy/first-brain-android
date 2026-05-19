package com.firstbrain.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firstbrain.data.local.TaskEntity
import com.firstbrain.data.repo.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HistoryFilter { ALL, COMPLETED, SKIPPED }

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repo: TaskRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.ALL)

    val history: StateFlow<List<TaskEntity>> = combine(repo.observeHistory(), filter) { list, f ->
        when (f) {
            HistoryFilter.ALL -> list
            HistoryFilter.COMPLETED -> list.filter { it.status == com.firstbrain.data.local.TaskStatus.completed }
            HistoryFilter.SKIPPED -> list.filter { it.status == com.firstbrain.data.local.TaskStatus.skipped }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val doneCount: StateFlow<Int> = repo.observeHistory()
        .map { list -> list.count { it.status == com.firstbrain.data.local.TaskStatus.completed } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val skippedCount: StateFlow<Int> = repo.observeHistory()
        .map { list -> list.count { it.status == com.firstbrain.data.local.TaskStatus.skipped } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun setFilter(f: HistoryFilter) { filter.value = f }

    fun reopen(id: String) = viewModelScope.launch { repo.reopen(id) }
}
