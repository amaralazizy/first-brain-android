package com.firstbrain.ui.addedit

import android.app.DatePickerDialog
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
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

@AndroidEntryPoint
class AddEditTaskFragment : Fragment() {

    private val vm: AddEditTaskViewModel by viewModels()
    private var _binding: FragmentAddEditTaskBinding? = null
    private val binding get() = _binding!!

    private var deadline: Instant? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAddEditTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.deadlineBtn.setOnClickListener { showDatePicker() }
        binding.clearDeadlineBtn.setOnClickListener {
            deadline = null
            binding.deadlineLabel.text = getString(R.string.no_deadline)
        }

        binding.saveBtn.setOnClickListener {
            val urgency = when (binding.urgencyGroup.checkedRadioButtonId) {
                R.id.urgencyLow -> Urgency.Low
                R.id.urgencyHigh -> Urgency.High
                R.id.urgencyCritical -> Urgency.Critical
                else -> Urgency.Medium
            }
            val type = when (binding.typeGroup.checkedRadioButtonId) {
                R.id.typeWork -> TaskType.work
                R.id.typeLearning -> TaskType.learning
                R.id.typeHealth -> TaskType.health
                R.id.typePersonal -> TaskType.personal
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

    private fun showDatePicker() {
        val today = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val picked = LocalDate.of(year, month + 1, day)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                deadline = picked
                binding.deadlineLabel.text = picked.atZone(ZoneId.systemDefault()).toLocalDate().toString()
            },
            today.get(Calendar.YEAR),
            today.get(Calendar.MONTH),
            today.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
