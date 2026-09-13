package com.example.misal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.misal.domain.model.User
import com.example.misal.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    val currentUser: StateFlow<User?> = authRepository.currentUser
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("E-posta ve şifre boş bırakılamaz.")
            return
        }
        
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = authRepository.signInWithEmailAndPassword(email.trim(), password)
            if (result.isSuccess) {
                _authState.value = AuthState.Success
            } else {
                _authState.value = AuthState.Error(result.exceptionOrNull()?.localizedMessage ?: "Giriş başarısız.")
            }
        }
    }

    fun register(name: String, email: String, password: String) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("Tüm alanları doldurunuz.")
            return
        }

        if (password.length < 6) {
            _authState.value = AuthState.Error("Şifre en az 6 karakter olmalıdır.")
            return
        }

        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = authRepository.signUpWithEmailAndPassword(email.trim(), password, name.trim())
            if (result.isSuccess) {
                _authState.value = AuthState.Success
            } else {
                _authState.value = AuthState.Error(result.exceptionOrNull()?.localizedMessage ?: "Kayıt başarısız.")
            }
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }

    fun updatePresence(isOnline: Boolean) {
        viewModelScope.launch {
            authRepository.updateUserPresence(isOnline)
        }
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}
