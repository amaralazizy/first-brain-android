package com.firstbrain.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.firstbrain.databinding.FragmentHistoryBinding
import com.firstbrain.ui.common.TaskAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HistoryFragment : Fragment() {

    private val vm: HistoryViewModel by viewModels()
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = TaskAdapter(
            onClick = { /* no-op */ },
            onReopen = { vm.reopen(it.id) }
        )
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter

        // Reset filter to ALL when fragment is created/restored to match TabLayout's default state
        vm.setFilter(HistoryFilter.ALL)

        binding.tabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> vm.setFilter(HistoryFilter.ALL)
                    1 -> vm.setFilter(HistoryFilter.COMPLETED)
                    2 -> vm.setFilter(HistoryFilter.SKIPPED)
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })

        val mainActivity = activity as? com.firstbrain.ui.MainActivity
        mainActivity?.setToolbarStats(done = null, skipped = null, visible = true)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.history.collect {
                        adapter.submitList(it) {
                            // Explicitly notify the adapter that the whole set has changed
                            // to force re-indexing of all visible items, even if the objects
                            // are the same (which happens when filtering the same list).
                            adapter.notifyDataSetChanged()
                        }
                        binding.empty.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    vm.doneCount.collect { mainActivity?.setToolbarStats(done = it, skipped = null, visible = true) }
                }
                launch {
                    vm.skippedCount.collect { mainActivity?.setToolbarStats(done = null, skipped = it, visible = true) }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? com.firstbrain.ui.MainActivity)?.setToolbarStats(
            done = null, skipped = null, visible = false,
        )
        binding.list.adapter = null
        _binding = null
    }
}
