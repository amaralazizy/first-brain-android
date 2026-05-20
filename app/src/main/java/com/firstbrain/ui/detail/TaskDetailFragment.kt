package com.firstbrain.ui.detail

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
import com.firstbrain.R
import com.firstbrain.data.local.TaskStatus
import com.firstbrain.databinding.FragmentTaskDetailBinding
import com.firstbrain.ui.common.formatDate
import com.firstbrain.ui.common.formatDateTime
import com.firstbrain.ui.common.formatScore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TaskDetailFragment : Fragment() {

    private val vm: TaskDetailViewModel by viewModels()
    private var _binding: FragmentTaskDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTaskDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.completeBtn.setOnClickListener { vm.complete() }
        binding.skipBtn.setOnClickListener { vm.skip() }
        binding.reopenBtn.setOnClickListener { vm.reopen() }
        binding.deleteBtn.setOnClickListener {
            vm.delete()
            findNavController().popBackStack()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.task.collect { task ->
                        if (task == null) return@collect
                        binding.title.text = task.title
                        binding.description.text =
                            task.description?.takeIf { it.isNotBlank() }
                                ?: getString(R.string.no_description)
                        binding.deadline.text = task.deadline?.takeIf { task.hasDeadline }
                            ?.formatDate() ?: getString(R.string.no_deadline)
                        binding.urgencyLabel.text = task.urgency.name
                        binding.typeLabel.text = task.taskType.name
                        binding.effortLabel.text = getString(R.string.hours_fmt, task.estimatedEffort.toFloat())
                        binding.created.text = getString(
                            R.string.detail_created_fmt, task.createdAt.formatDateTime(),
                        )
                        binding.score.text = task.recScore?.formatScore()
                            ?: getString(R.string.no_score)
                        binding.status.text = task.status.name

                        val pending = task.status == TaskStatus.pending
                        binding.completeBtn.isEnabled = pending
                        binding.skipBtn.isEnabled = pending
                        binding.reopenBtn.isEnabled = !pending
                    }
                }
                launch {
                    vm.interactions.collect { events ->
                        binding.interactions.text = events
                            .joinToString("\n") { "• ${it.action} — ${it.occurredAt.formatDateTime()}" }
                            .ifBlank { getString(R.string.no_interactions) }
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
