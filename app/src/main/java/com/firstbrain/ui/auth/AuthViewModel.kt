package com.firstbrain.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firstbrain.data.auth.AuthRepository
import com.firstbrain.data.auth.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repo: AuthRepository,
) : ViewModel() {

    sealed interface Event {
        data object Authenticated : Event
        data class Error(val message: String) : Event
    }

    val authState: StateFlow<AuthState> = repo.state

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            emit(Event.Error("All fields are required."))
            return
        }
        run { repo.signIn(email.trim(), password) }
    }

    fun signUp(name: String, email: String, password: String) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            emit(Event.Error("All fields are required."))
            return
        }
        if (password.length < 8) {
            emit(Event.Error("Password must be at least 8 characters."))
            return
        }
        run { repo.signUp(email.trim(), password, name.trim()) }
    }

    fun signOut() = run { repo.signOut() }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            runCatching { block() }
                .onSuccess { _events.send(Event.Authenticated) }
                .onFailure { _events.send(Event.Error(it.message ?: "Could not authenticate.")) }
            _loading.value = false
        }
    }

    private fun emit(event: Event) {
        viewModelScope.launch { _events.send(event) }
    }
}
