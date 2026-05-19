package com.firstbrain.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firstbrain.data.local.TaskDao
import com.firstbrain.data.local.TaskEntity
import com.firstbrain.data.remote.FeatureContribution
import com.firstbrain.data.repo.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class InsightsState(
    val topTask: TaskEntity? = null,
    val contributions: List<FeatureContribution> = emptyList(),
)

/**
 * Surfaces the SHAP-style explanation for today's top pick. The contributions
 * come straight from the ML server's `/recommend` response — persisted on the
 * task row in `explanation_json` and rendered as-is.
 */
@HiltViewModel
class InsightsViewModel @Inject constructor(
    taskDao: TaskDao,
    private val repo: TaskRepository,
    private val json: Json,
) : ViewModel() {

    val state: StateFlow<InsightsState> = taskDao.observePicks()
        .map { picks ->
            val top = picks.firstOrNull() ?: return@map InsightsState()
            InsightsState(topTask = top, contributions = parseContributions(top.explanationJson))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsState())

    fun refresh() = viewModelScope.launch { repo.rescoreAll() }

    private fun parseContributions(jsonStr: String?): List<FeatureContribution> {
        if (jsonStr.isNullOrEmpty()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(FeatureContribution.serializer()), jsonStr)
        }.getOrDefault(emptyList())
    }
}
