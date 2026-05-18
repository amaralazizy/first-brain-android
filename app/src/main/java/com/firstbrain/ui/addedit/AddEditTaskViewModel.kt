package com.firstbrain.ui.addedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firstbrain.data.local.TaskType
import com.firstbrain.data.local.Urgency
import com.firstbrain.data.repo.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class AddEditTaskViewModel @Inject constructor(
    private val repo: TaskRepository,
) : ViewModel() {

    sealed interface Event {
        data object Saved : Event
        data class Error(val message: String) : Event
    }

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun save(
        title: String,
        description: String?,
        urgency: Urgency,
        taskType: TaskType,
        estimatedEffort: Int,
        deadline: Instant?,
    ) {
        if (title.isBlank()) {
            viewModelScope.launch { _events.send(Event.Error("Title is required")) }
            return
        }
        viewModelScope.launch {
            runCatching {
                repo.createTask(
                    title = title.trim(),
                    description = description?.takeIf { it.isNotBlank() }?.trim(),
                    urgency = urgency,
                    taskType = taskType,
                    estimatedEffort = estimatedEffort.coerceAtLeast(1),
                    deadline = deadline,
                    hasDeadline = deadline != null,
                )
            }.fold(
                onSuccess = { _events.send(Event.Saved) },
                onFailure = { _events.send(Event.Error(it.message ?: "Could not save task")) },
            )
        }
    }
}
