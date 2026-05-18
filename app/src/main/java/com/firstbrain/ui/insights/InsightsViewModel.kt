package com.firstbrain.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firstbrain.data.local.TaskDao
import com.firstbrain.data.local.TaskEntity
import com.firstbrain.data.repo.FeatureContribution
import com.firstbrain.data.repo.RankingHeuristic
import com.firstbrain.data.repo.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class InsightsState(
    val topTask: TaskEntity? = null,
    val contributions: List<FeatureContribution> = emptyList(),
)

/**
 * Insights surface the heuristic that picks today's recommendations.
 * For the top-scoring pending task we break the score down by component so
 * the user can see *why* it was chosen — the local analogue of the SHAP
 * panel in the web version.
 */
@HiltViewModel
class InsightsViewModel @Inject constructor(
    taskDao: TaskDao,
    private val repo: TaskRepository,
) : ViewModel() {

    val state: StateFlow<InsightsState> = taskDao.observePicks()
        .map { picks ->
            val top = picks.firstOrNull() ?: return@map InsightsState()
            InsightsState(topTask = top, contributions = RankingHeuristic.breakdown(top, Instant.now()))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsState())

    fun refresh() = viewModelScope.launch { repo.rescoreAll() }
}
