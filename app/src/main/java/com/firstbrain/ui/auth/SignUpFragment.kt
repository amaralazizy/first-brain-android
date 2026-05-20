// AI-assisted: drafted with Claude (Anthropic), reviewed and adapted by the team.
// See README §12 for the team's originality statement.

package com.firstbrain.ui.auth

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
import com.firstbrain.databinding.FragmentSignupBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SignUpFragment : Fragment() {

    private val vm: AuthViewModel by viewModels()
    private var _binding: FragmentSignupBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSignupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.signUpBtn.setOnClickListener {
            vm.signUp(
                name = binding.nameInput.text?.toString().orEmpty(),
                email = binding.emailInput.text?.toString().orEmpty(),
                password = binding.passwordInput.text?.toString().orEmpty(),
            )
        }

        binding.goLoginBtn.setOnClickListener {
            findNavController().popBackStack()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.loading.collect { isLoading ->
                        binding.progress.visibility = if (isLoading) View.VISIBLE else View.GONE
                        binding.signUpBtn.isEnabled = !isLoading
                    }
                }
                launch {
                    vm.events.collect { event ->
                        when (event) {
                            AuthViewModel.Event.Authenticated ->
                                findNavController().navigate(R.id.action_auth_to_home)
                            is AuthViewModel.Event.Error ->
                                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
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