package com.example.misal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.misal.domain.model.User
import com.example.misal.domain.repository.AuthRepository
import com.example.misal.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        val currentUserId = authRepository.getCurrentUserId()
        if (currentUserId != null) {
            viewModelScope.launch {
                userRepository.getUserFlow(currentUserId).collect { result ->
                    result.onSuccess {
                        _user.value = it
                    }.onFailure {
                        _error.value = it.message
                    }
                }
            }
        }
    }

    fun updateProfile(name: String, bio: String, profilePictureBase64: String?) {
        val currentUserId = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val result = userRepository.updateProfile(currentUserId, name, bio, profilePictureBase64)
            result.onSuccess {
                _isLoading.value = false
                // Flow will automatically update _user
            }.onFailure {
                _isLoading.value = false
                _error.value = "Güncelleme başarısız: ${it.message}"
            }
        }
    }
}
