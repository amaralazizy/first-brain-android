package com.firstbrain.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.firstbrain.R
import com.firstbrain.data.local.TaskEntity
import com.firstbrain.data.local.TaskStatus
import com.firstbrain.data.remote.FeatureContribution
import com.firstbrain.databinding.ItemTaskBinding
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val explanationJson = Json { ignoreUnknownKeys = true }
private val explanationSerializer = ListSerializer(FeatureContribution.serializer())

class TaskAdapter(
    private val showScore: Boolean = false,
    private val onClick: (TaskEntity) -> Unit,
    private val onComplete: ((TaskEntity) -> Unit)? = null,
    private val onSkip: ((TaskEntity) -> Unit)? = null,
    private val onReopen: ((TaskEntity) -> Unit)? = null,
) : ListAdapter<TaskEntity, TaskAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class VH(private val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(task: TaskEntity, position: Int) {
            binding.indexLabel.text = (position + 1).toString()
            binding.title.text = task.title
            binding.description.text = task.description ?: ""
            binding.description.visibility = if (task.description.isNullOrBlank()) android.view.View.GONE
                                             else android.view.View.VISIBLE

            val score = task.recScore
            if (score == null) {
                binding.priorityBar.progress = 0
                binding.priorityBar.visibility = android.view.View.GONE
                binding.score.text = binding.root.context.getString(R.string.priority_pending_offline)
            } else {
                val priorityPercent = (score * 100).toInt().coerceIn(0, 100)
                binding.priorityBar.visibility = android.view.View.VISIBLE
                binding.priorityBar.progress = priorityPercent
                binding.score.text = "$priorityPercent% priority"
            }

            binding.urgencyTag.text = task.urgency.name
            binding.typeTag.text = task.taskType.name
            binding.effortTag.text = "${task.estimatedEffort}h"

            val deadline = task.deadline?.takeIf { task.hasDeadline }?.formatDate()
            binding.deadline.text = if (deadline != null) "Due $deadline" else ""
            binding.deadline.visibility = if (deadline == null) android.view.View.GONE
                                         else android.view.View.VISIBLE

            val contributions = task.explanationJson?.let { raw ->
                runCatching { explanationJson.decodeFromString(explanationSerializer, raw) }.getOrNull()
            } ?: emptyList()
            val reasonsText = when {
                contributions.isNotEmpty() -> contributions
                    .sortedByDescending { kotlin.math.abs(it.shap_value) }
                    .take(3)
                    .joinToString("\n") {
                        val symbol = if (it.shap_value > 0) "▲" else "▼"
                        "$symbol ${it.feature}"
                    }
                task.recScore == null -> binding.root.context.getString(R.string.explanations_pending_offline)
                else -> ""
            }
            binding.explanationText.text = reasonsText
            binding.explanationHeader.visibility = if (reasonsText.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
            binding.explanationText.visibility = if (reasonsText.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
            binding.divider.visibility = if (reasonsText.isBlank()) android.view.View.GONE else android.view.View.VISIBLE

            val isPending = task.status == com.firstbrain.data.local.TaskStatus.pending
            binding.completeBtn.visibility = if (isPending) android.view.View.VISIBLE else android.view.View.GONE
            binding.skipBtn.visibility = if (isPending) android.view.View.VISIBLE else android.view.View.GONE
            binding.reopenBtn.visibility = if (!isPending && onReopen != null) android.view.View.VISIBLE else android.view.View.GONE
            
            if (task.status == com.firstbrain.data.local.TaskStatus.completed) {
                binding.statusLabel.visibility = android.view.View.VISIBLE
                binding.statusLabel.text = "✓ Done"
                binding.statusLabel.setBackgroundResource(R.drawable.status_bg_done)
            } else if (task.status == com.firstbrain.data.local.TaskStatus.skipped) {
                binding.statusLabel.visibility = android.view.View.VISIBLE
                binding.statusLabel.text = "— Skipped"
                binding.statusLabel.setBackgroundResource(R.drawable.status_bg_skipped)
            } else {
                binding.statusLabel.visibility = android.view.View.GONE
            }

            binding.completeBtn.setOnClickListener { onComplete?.invoke(task) }
            binding.skipBtn.setOnClickListener { onSkip?.invoke(task) }
            binding.reopenBtn.setOnClickListener { onReopen?.invoke(task) }

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
