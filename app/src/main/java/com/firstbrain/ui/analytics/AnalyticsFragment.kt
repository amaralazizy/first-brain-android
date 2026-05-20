package com.firstbrain.ui.analytics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.firstbrain.R
import com.firstbrain.databinding.FragmentAnalyticsBinding
import com.firstbrain.ui.common.formatPercent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AnalyticsFragment : Fragment() {

    private val vm: AnalyticsViewModel by viewModels()
    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { s ->
                    binding.totalValue.text = s.total.toString()
                    binding.completedValue.text = s.completed.toString()
                    binding.skippedValue.text = s.skipped.toString()
                    binding.pendingValue.text = s.pending.toString()
                    binding.completionRate.text = getString(R.string.completion_rate_fmt, s.completionRate.formatPercent())
                    binding.avgEffort.text = getString(R.string.hours_fmt, s.avgEffort)
                    binding.totalHours.text = "${s.totalHoursBacklog}h"

                    setupTaskTypeChart(s.tasksByType)
                    setupUrgencyChart(s.tasksByUrgency)
                }
            }
        }
    }

    private fun setupTaskTypeChart(data: Map<com.firstbrain.data.local.TaskType, CategorySummary>) {
        binding.taskTypeChartContainer.removeAllViews()
        val maxVal = data.values.maxOfOrNull { it.total } ?: 1
        
        data.entries.sortedByDescending { it.value.total }.forEach { (type, stats) ->
            addChartRow(
                container = binding.taskTypeChartContainer,
                label = type.name,
                value = stats.total,
                maxValue = maxVal,
                color = when(type) {
                    com.firstbrain.data.local.TaskType.work -> R.color.chart_work
                    com.firstbrain.data.local.TaskType.personal -> R.color.chart_personal
                    com.firstbrain.data.local.TaskType.learning -> R.color.chart_learning
                    com.firstbrain.data.local.TaskType.health -> R.color.chart_health
                    else -> R.color.chart_other
                },
                subtitle = "${stats.completed} done · ${stats.skipped} skipped"
            )
        }
    }

    private fun setupUrgencyChart(data: Map<com.firstbrain.data.local.Urgency, Int>) {
        binding.urgencyChartContainer.removeAllViews()
        val maxVal = data.values.maxOfOrNull { it } ?: 1
        
        com.firstbrain.data.local.Urgency.values().forEach { urgency ->
            val count = data[urgency] ?: 0
            addChartRow(
                container = binding.urgencyChartContainer,
                label = urgency.name,
                value = count,
                maxValue = maxVal,
                color = when(urgency) {
                    com.firstbrain.data.local.Urgency.Low -> R.color.urgency_low
                    com.firstbrain.data.local.Urgency.Medium -> R.color.urgency_medium
                    com.firstbrain.data.local.Urgency.High -> R.color.urgency_high
                    com.firstbrain.data.local.Urgency.Critical -> R.color.urgency_critical
                }
            )
        }
    }

    private fun addChartRow(
        container: ViewGroup,
        label: String,
        value: Int,
        maxValue: Int,
        color: Int,
        subtitle: String? = null
    ) {
        val rowBinding = com.firstbrain.databinding.ItemChartRowBinding.inflate(
            LayoutInflater.from(requireContext()), container, false
        )

        rowBinding.label.text = label
        rowBinding.count.text = value.toString()
        rowBinding.bar.setBackgroundColor(
            androidx.core.content.ContextCompat.getColor(requireContext(), color)
        )

        if (subtitle != null) {
            rowBinding.subtitle.text = subtitle
            rowBinding.subtitle.visibility = View.VISIBLE
        } else {
            rowBinding.subtitle.visibility = View.GONE
        }

        rowBinding.root.post {
            val totalWidth = rowBinding.root.width - rowBinding.label.width - rowBinding.count.width - 100
            val barWidth = (totalWidth * (value.toDouble() / maxValue)).toInt().coerceAtLeast(10)
            val params = rowBinding.bar.layoutParams
            params.width = barWidth
            rowBinding.bar.layoutParams = params
        }

        container.addView(rowBinding.root)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
