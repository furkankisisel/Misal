package com.example.misal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.misal.domain.model.Chat
import com.example.misal.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupProfileViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _chat = MutableStateFlow<Chat?>(null)
    val chat: StateFlow<Chat?> = _chat.asStateFlow()

    fun loadGroupProfile(chatId: String) {
        if (chatId.isEmpty()) return
        viewModelScope.launch {
            chatRepository.observeChat(chatId).collect { loadedChat ->
                _chat.value = loadedChat
            }
        }
    }
}
