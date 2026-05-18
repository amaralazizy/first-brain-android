package com.firstbrain.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.firstbrain.R
import com.firstbrain.data.local.TaskEntity
import com.firstbrain.data.local.TaskStatus
import com.firstbrain.databinding.ItemTaskBinding

class TaskAdapter(
    private val showScore: Boolean = false,
    private val onClick: (TaskEntity) -> Unit,
    private val onComplete: ((TaskEntity) -> Unit)? = null,
    private val onSkip: ((TaskEntity) -> Unit)? = null,
) : ListAdapter<TaskEntity, TaskAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(task: TaskEntity) {
            binding.title.text = task.title
            binding.subtitle.text = binding.root.context.getString(
                R.string.task_subtitle_fmt,
                task.urgency.name,
                task.taskType.name,
                task.estimatedEffort,
            )

            val deadline = task.deadline?.takeIf { task.hasDeadline }?.formatDate()
            binding.deadline.text = deadline
            binding.deadline.visibility = if (deadline == null) android.view.View.GONE
                                         else android.view.View.VISIBLE

            val score = task.recScore
            if (showScore && score != null) {
                binding.score.visibility = android.view.View.VISIBLE
                binding.score.text = score.formatScore()
            } else {
                binding.score.visibility = android.view.View.GONE
            }

            val statusVisible = task.status != TaskStatus.pending
            binding.statusChip.visibility = if (statusVisible) android.view.View.VISIBLE
                                            else android.view.View.GONE
            binding.statusChip.text = task.status.name

            val actionable = task.status == TaskStatus.pending && (onComplete != null || onSkip != null)
            binding.actionRow.visibility = if (actionable) android.view.View.VISIBLE
                                           else android.view.View.GONE
            binding.completeBtn.setOnClickListener { onComplete?.invoke(task) }
            binding.skipBtn.setOnClickListener { onSkip?.invoke(task) }

            binding.root.setOnClickListener { onClick(task) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<TaskEntity>() {
            override fun areItemsTheSame(a: TaskEntity, b: TaskEntity) = a.id == b.id
            override fun areContentsTheSame(a: TaskEntity, b: TaskEntity) = a == b
        }
    }
}
