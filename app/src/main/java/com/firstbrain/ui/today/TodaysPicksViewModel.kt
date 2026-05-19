package com.firstbrain.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firstbrain.data.local.TaskEntity
import com.firstbrain.data.repo.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TodaysPicksViewModel @Inject constructor(
    private val repo: TaskRepository,
) : ViewModel() {

    val picks: StateFlow<List<TaskEntity>> = repo.observePicks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init { refresh() }

    /** Rescore everything from scratch so picks reflect current time/deadlines. */
    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            repo.rescoreAll()
            _refreshing.value = false
        }
    }

    fun complete(id: String) = viewModelScope.launch { repo.complete(id) }
    fun skip(id: String) = viewModelScope.launch { repo.skip(id) }
}
