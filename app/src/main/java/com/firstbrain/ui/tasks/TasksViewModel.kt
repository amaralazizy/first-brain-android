package com.firstbrain.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firstbrain.data.local.TaskEntity
import com.firstbrain.data.repo.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val repo: TaskRepository,
) : ViewModel() {

    val tasks: StateFlow<List<TaskEntity>> = repo.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun complete(id: String) = viewModelScope.launch { repo.complete(id) }
    fun skip(id: String) = viewModelScope.launch { repo.skip(id) }
    fun reopen(id: String) = viewModelScope.launch { repo.reopen(id) }
}
