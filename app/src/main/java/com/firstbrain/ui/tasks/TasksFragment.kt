package com.firstbrain.ui.tasks

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
import com.firstbrain.databinding.FragmentTasksBinding
import com.firstbrain.ui.common.TaskAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TasksFragment : Fragment() {

    private val vm: TasksViewModel by viewModels()
    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = TaskAdapter(
            onClick = { task ->
                findNavController().navigate(
                    R.id.action_tasks_to_detail,
                    Bundle().apply { putInt("taskId", task.id) },
                )
            },
            onComplete = { vm.complete(it.id) },
            onSkip = { vm.skip(it.id) },
        )

        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter

        binding.swipeRefresh.isEnabled = false

        binding.addFab.setOnClickListener {
            findNavController().navigate(R.id.action_tasks_to_add)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.tasks.collect { tasks ->
                    adapter.submitList(tasks)
                    binding.empty.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.list.adapter = null
        _binding = null
    }
}
