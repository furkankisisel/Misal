package com.example.misal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.misal.domain.model.User
import com.example.misal.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactProfileViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {


    private val _contact = MutableStateFlow<User?>(null)
    val contact: StateFlow<User?> = _contact.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadContactProfile(contactId: String) {
        if (contactId.isEmpty()) return
        viewModelScope.launch {
            userRepository.getUserFlow(contactId).collect { result ->
                result.onSuccess {
                    _contact.value = it
                }.onFailure {
                    _error.value = "Kullanıcı bilgileri yüklenemedi: ${it.message}"
                }
            }
        }
    }
}

