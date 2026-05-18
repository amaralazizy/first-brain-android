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
                    binding.completionRate.text = s.completionRate.formatPercent()
                    binding.skipRate.text = s.skipRate.formatPercent()
                    binding.avgEffort.text = getString(R.string.hours_fmt, s.avgEffort)
                    binding.interactions.text = s.interactionsLast7Days
                        .joinToString("\n") { "${it.action}: ${it.count}" }
                        .ifBlank { getString(R.string.empty_interactions) }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
