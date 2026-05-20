package com.firstbrain.ui.addedit

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import com.firstbrain.data.local.TaskType
import com.firstbrain.data.local.Urgency
import com.firstbrain.databinding.FragmentAddEditTaskBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Calendar

@AndroidEntryPoint
class AddEditTaskFragment : Fragment() {

    private val vm: AddEditTaskViewModel by viewModels()
    private var _binding: FragmentAddEditTaskBinding? = null
    private val binding get() = _binding!!

    private var deadline: Instant? = null
    private val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAddEditTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.deadlineBtn.setOnClickListener { showDateTimePicker() }
        binding.clearDeadlineBtn.setOnClickListener {
            deadline = null
            updateDeadlineLabel()
        }

        binding.saveBtn.setOnClickListener {
            val urgency = when (binding.urgencyChipGroup.checkedChipId) {
                R.id.chipLow -> Urgency.Low
                R.id.chipHigh -> Urgency.High
                R.id.chipCritical -> Urgency.Critical
                else -> Urgency.Medium
            }
            val type = when (binding.typeChipGroup.checkedChipId) {
                R.id.chipWork -> TaskType.work
                R.id.chipLearning -> TaskType.learning
                R.id.chipHealth -> TaskType.health
                R.id.chipPersonal -> TaskType.personal
                else -> TaskType.other
            }
            vm.save(
                title = binding.titleInput.text?.toString().orEmpty(),
                description = binding.descInput.text?.toString(),
                urgency = urgency,
                taskType = type,
                estimatedEffort = binding.effortInput.text?.toString()?.toIntOrNull() ?: 1,
                deadline = deadline,
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.events.collect { event ->
                    when (event) {
                        AddEditTaskViewModel.Event.Saved -> findNavController().popBackStack()
                        is AddEditTaskViewModel.Event.Error ->
                            Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun showDateTimePicker() {
        val now = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                TimePickerDialog(
                    requireContext(),
                    { _, hour, minute ->
                        val picked = LocalDateTime.of(year, month + 1, day, hour, minute)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                        deadline = picked
                        updateDeadlineLabel()
                    },
                    now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.MINUTE),
                    false // Use 24-hour format or system default
                ).show()
            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun updateDeadlineLabel() {
        val d = deadline
        if (d == null) {
            binding.deadlineLabel.text = getString(R.string.no_deadline)
        } else {
            binding.deadlineLabel.text = d.atZone(ZoneId.systemDefault()).format(dateTimeFormatter)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
