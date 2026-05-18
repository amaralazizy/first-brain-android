package com.firstbrain.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firstbrain.data.local.TaskEntity
import com.firstbrain.data.repo.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    repo: TaskRepository,
) : ViewModel() {

    val history: StateFlow<List<TaskEntity>> = repo.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
