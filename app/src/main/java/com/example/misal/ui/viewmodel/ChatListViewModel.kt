package com.example.misal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.misal.domain.model.Chat
import com.example.misal.domain.repository.AuthRepository
import com.example.misal.domain.repository.ChatRepository
import com.example.misal.domain.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val messageRepository: MessageRepository,
    private val database: com.example.misal.data.local.MisalDatabase
) : ViewModel() {

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    // Asıl liste yerine arama sonuçlarını (filteredChats) UI'a sunacağız

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    val filteredChats: StateFlow<List<Chat>> = combine(_chats, _searchQuery) { chatList, query ->
        if (query.isBlank()) {
            chatList
        } else {
            chatList.filter { it.contactName.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                if (user != null) {
                    loadChats()
                } else {
                    _chats.value = emptyList()
                    _error.value = null
                }
            }
        }
    }

    private var currentChatJob: Job? = null
    private val messageSyncJobs = mutableMapOf<String, Job>()

    private fun syncMessagesForActiveChats(chatList: List<Chat>) {
        val currentChatIds = chatList.map { it.id }.toSet()
        
        val it = messageSyncJobs.iterator()
        while (it.hasNext()) {
            val (id, job) = it.next()
            if (id !in currentChatIds) {
                job.cancel()
                it.remove()
            }
        }
        
        for (chatId in currentChatIds) {
            if (!messageSyncJobs.containsKey(chatId)) {
                messageSyncJobs[chatId] = viewModelScope.launch(Dispatchers.IO) {
                    try {
                        messageRepository.getMessagesForChat(chatId).collect()
                    } catch (e: Exception) {
                        // Ignore exceptions to prevent crashing the viewmodel
                    }
                }
            }
        }
    }

    private fun loadChats() {
        currentChatJob?.cancel()
        currentChatJob = viewModelScope.launch {
            chatRepository.getChats().collect { result ->
                result.fold(
                    onSuccess = { chatList ->
                        _chats.value = chatList
                        syncMessagesForActiveChats(chatList)
                        _error.value = null
                    },
                    onFailure = { throwable ->
                        _error.value = "Sohbetler yüklenemedi: ${throwable.message}"
                    }
                )
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    // Test amaçlı sohbet oluşturma
    fun createTestChat(contactName: String) {
        viewModelScope.launch {
            chatRepository.createChat("test-contact-id", contactName)
        }
    }

    fun signOut() {
        viewModelScope.launch(Dispatchers.IO) {
            database.clearAllTables()
            authRepository.signOut()
        }
    }
}
