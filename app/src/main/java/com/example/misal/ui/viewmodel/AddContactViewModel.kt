package com.example.misal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.misal.domain.model.User
import com.example.misal.domain.repository.AuthRepository
import com.example.misal.domain.repository.ChatRepository
import com.example.misal.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Success(val user: User) : SearchState()
    data class Error(val message: String) : SearchState()
}

sealed class CreateChatState {
    object Idle : CreateChatState()
    object Loading : CreateChatState()
    data class Success(val chatId: String, val contactId: String, val contactName: String) : CreateChatState()
    data class Error(val message: String) : CreateChatState()
}

@HiltViewModel
class AddContactViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState
    
    private val _createChatState = MutableStateFlow<CreateChatState>(CreateChatState.Idle)
    val createChatState: StateFlow<CreateChatState> = _createChatState

    fun searchUser(email: String) {
        if (email.isBlank()) {
            _searchState.value = SearchState.Error("Lütfen bir e-posta adresi girin.")
            return
        }

        viewModelScope.launch {
            _searchState.value = SearchState.Loading
            
            val currentUser = authRepository.currentUser.firstOrNull()
            if (currentUser != null && currentUser.email == email) {
                _searchState.value = SearchState.Error("Kendinizi arayamazsınız.")
                return@launch
            }

            val result = userRepository.searchUserByEmail(email)
            result.onSuccess { user ->
                if (user != null) {
                    _searchState.value = SearchState.Success(user)
                } else {
                    _searchState.value = SearchState.Error("Bu e-posta adresine sahip bir kullanıcı bulunamadı.")
                }
            }.onFailure {
                _searchState.value = SearchState.Error(it.message ?: "Bilinmeyen bir hata oluştu.")
            }
        }
    }

    fun startChat(user: User) {
        viewModelScope.launch {
            _createChatState.value = CreateChatState.Loading
            val result = chatRepository.createChat(user.id, "Kaydedilen Mesajlar")
            
            result.onSuccess { chatId ->
                _createChatState.value = CreateChatState.Success(chatId, user.id, "Kaydedilen Mesajlar")
            }.onFailure {
                _createChatState.value = CreateChatState.Error(it.message ?: "Sohbet oluşturulamadı.")
            }
        }
    }
    
    fun resetState() {
        _searchState.value = SearchState.Idle
        _createChatState.value = CreateChatState.Idle
    }

    fun startSelfChat() {
        viewModelScope.launch {
            val user = authRepository.currentUser.firstOrNull() ?: return@launch
            _createChatState.value = CreateChatState.Loading
            // Use contactId as currentUserId
            val result = chatRepository.createChat(user.id, "Kaydedilen Mesajlar")
            
            result.onSuccess { chatId ->
                _createChatState.value = CreateChatState.Success(chatId, user.id, "Kaydedilen Mesajlar")
            }.onFailure {
                _createChatState.value = CreateChatState.Error(it.message ?: "Sohbet oluşturulamadı.")
            }
        }
    }
}
