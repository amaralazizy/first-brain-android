package com.firstbrain.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firstbrain.data.local.InteractionEntity
import com.firstbrain.data.local.TaskEntity
import com.firstbrain.data.repo.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    private val repo: TaskRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val taskId: String = checkNotNull(savedState["taskId"])

    val task: StateFlow<TaskEntity?> = repo.observeTask(taskId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val interactions: StateFlow<List<InteractionEntity>> = repo.observeInteractions(taskId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { repo.logViewed(taskId) }
    }

    fun complete() = viewModelScope.launch { repo.complete(taskId) }
    fun skip() = viewModelScope.launch { repo.skip(taskId) }
    fun reopen() = viewModelScope.launch { repo.reopen(taskId) }
    fun delete() = viewModelScope.launch { repo.delete(taskId) }
}
