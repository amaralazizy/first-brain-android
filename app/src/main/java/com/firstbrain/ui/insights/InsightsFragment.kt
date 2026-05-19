package com.firstbrain.ui.insights

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
import com.firstbrain.databinding.FragmentInsightsBinding
import com.firstbrain.ui.common.formatScore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale

@AndroidEntryPoint
class InsightsFragment : Fragment() {

    private val vm: InsightsViewModel by viewModels()
    private var _binding: FragmentInsightsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentInsightsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.refreshBtn.setOnClickListener { vm.refresh() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { s ->
                    val top = s.topTask
                    if (top == null) {
                        binding.status.text = getString(R.string.insights_no_pending)
                        binding.message.text = ""
                        binding.explanations.text = getString(R.string.no_explanations)
                    } else {
                        binding.status.text = top.title
                        if (top.recScore == null) {
                            binding.message.text = getString(R.string.priority_pending_offline)
                            binding.explanations.text = getString(R.string.explanations_pending_offline)
                        } else {
                            binding.message.text = getString(
                                R.string.insights_top_score, top.recScore.formatScore(),
                            )
                            binding.explanations.text = if (s.contributions.isEmpty()) {
                                getString(R.string.explanations_pending_offline)
                            } else {
                                s.contributions
                                    .sortedByDescending { kotlin.math.abs(it.shap_value) }
                                    .joinToString("\n") {
                                        String.format(Locale.getDefault(), "%-22s %+.2f", it.feature, it.shap_value)
                                    }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
