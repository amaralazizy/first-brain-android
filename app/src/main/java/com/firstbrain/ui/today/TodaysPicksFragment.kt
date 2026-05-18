package com.firstbrain.ui.today

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.firstbrain.R
import com.firstbrain.databinding.FragmentTodaysPicksBinding
import com.firstbrain.ui.common.TaskAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TodaysPicksFragment : Fragment() {

    private val vm: TodaysPicksViewModel by viewModels()
    private var _binding: FragmentTodaysPicksBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTodaysPicksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = TaskAdapter(
            showScore = true,
            onClick = { task ->
                findNavController().navigate(
                    R.id.action_today_to_detail,
                    Bundle().apply { putInt("taskId", task.id) },
                )
            },
            onComplete = { vm.complete(it.id) },
            onSkip = { vm.skip(it.id) },
        )

        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { vm.refresh() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.picks.collect { picks ->
                        adapter.submitList(picks)
                        binding.empty.visibility =
                            if (picks.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch { vm.refreshing.collect { binding.swipeRefresh.isRefreshing = it } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.list.adapter = null
        _binding = null
    }
}
