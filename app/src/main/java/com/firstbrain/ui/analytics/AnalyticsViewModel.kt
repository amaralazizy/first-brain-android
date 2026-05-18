package com.firstbrain.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firstbrain.data.local.ActionCount
import com.firstbrain.data.local.InteractionDao
import com.firstbrain.data.local.TaskDao
import com.firstbrain.data.local.TaskEntity
import com.firstbrain.data.local.TaskStatus
import com.firstbrain.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

data class AnalyticsState(
    val total: Int = 0,
    val completed: Int = 0,
    val skipped: Int = 0,
    val pending: Int = 0,
    val completionRate: Double = 0.0,
    val skipRate: Double = 0.0,
    val avgEffort: Double = 0.0,
    val interactionsLast7Days: List<ActionCount> = emptyList(),
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    taskDao: TaskDao,
    private val interactionDao: InteractionDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val interactionsWindow = MutableStateFlow<List<ActionCount>>(emptyList())

    val state: StateFlow<AnalyticsState> = combine(taskDao.observeAll(), interactionsWindow) {
        tasks, counts -> aggregate(tasks, counts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsState())

    init {
        // Recompute the 7-day interaction window whenever tasks change (cheap signal).
        taskDao.observeAll()
            .onEach { refreshWindow() }
            .launchIn(viewModelScope)
        refreshWindow()
    }

    private fun refreshWindow() = viewModelScope.launch {
        val since = Instant.now().toEpochMilli() - 7L * 86_400_000L
        interactionsWindow.value = withContext(io) { interactionDao.actionCountsSince(since) }
    }

    private fun aggregate(tasks: List<TaskEntity>, counts: List<ActionCount>): AnalyticsState {
        val completed = tasks.count { it.status == TaskStatus.completed }
        val skipped = tasks.count { it.status == TaskStatus.skipped }
        val pending = tasks.count { it.status == TaskStatus.pending }
        val total = tasks.size
        val avg = if (tasks.isEmpty()) 0.0
                  else tasks.sumOf { it.estimatedEffort.toDouble() } / tasks.size
        return AnalyticsState(
            total = total,
            completed = completed,
            skipped = skipped,
            pending = pending,
            completionRate = if (total == 0) 0.0 else completed.toDouble() / total,
            skipRate = if (total == 0) 0.0 else skipped.toDouble() / total,
            avgEffort = avg,
            interactionsLast7Days = counts,
        )
    }
}
