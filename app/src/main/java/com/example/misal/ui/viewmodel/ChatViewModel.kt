package com.example.misal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.misal.domain.model.Message
import com.example.misal.domain.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val chatRepository: com.example.misal.domain.repository.ChatRepository,
    private val authRepository: com.example.misal.domain.repository.AuthRepository,
    private val userRepository: com.example.misal.domain.repository.UserRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _isContactOnline = MutableStateFlow(false)
    val isContactOnline: StateFlow<Boolean> = _isContactOnline.asStateFlow()

    private val _contactLastSeen = MutableStateFlow<Long?>(null)
    val contactLastSeen: StateFlow<Long?> = _contactLastSeen.asStateFlow()

    private val _isContactTyping = MutableStateFlow(false)
    val isContactTyping: StateFlow<Boolean> = _isContactTyping.asStateFlow()
    private val _currentChat = MutableStateFlow<com.example.misal.domain.model.Chat?>(null)
    val currentChat: StateFlow<com.example.misal.domain.model.Chat?> = _currentChat.asStateFlow()
    
    private val _replyingToMessage = MutableStateFlow<Message?>(null)
    val replyingToMessage: StateFlow<Message?> = _replyingToMessage.asStateFlow()
    
    fun setReplyingToMessage(message: Message?) {
        _replyingToMessage.value = message
    }
    
    private val _readByNames = MutableStateFlow<List<String>>(emptyList())
    val readByNames: StateFlow<List<String>> = _readByNames.asStateFlow()
    
    fun fetchReadByNames(userIds: List<String>) {
        _readByNames.value = emptyList()
        viewModelScope.launch {
            val names = mutableListOf<String>()
            for (id in userIds) {
                try {
                    val userDoc = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(id).get().await()
                    val name = userDoc.getString("name") ?: "Bilinmeyen Kullanıcı"
                    names.add(name)
                } catch (e: Exception) {
                    names.add("Hata")
                }
            }
            _readByNames.value = names
        }
    }
    
    private val _allChats = MutableStateFlow<List<com.example.misal.domain.model.Chat>>(emptyList())
    val allChats: StateFlow<List<com.example.misal.domain.model.Chat>> = _allChats.asStateFlow()

    init {
        viewModelScope.launch {
            chatRepository.getChats().collect { result ->
                result.onSuccess {
                    _allChats.value = it
                }
            }
        }
    }
    
    private var chatJob: Job? = null

    private var currentChatId: String? = null
    val currentUserId: String? = authRepository.getCurrentUserId()
    private var messagesJob: Job? = null
    private var presenceJob: Job? = null
    private var typingJob: Job? = null
    
    private var typingTimerJob: Job? = null

    fun loadMessages(chatId: String, contactId: String) {
        if (currentChatId == chatId) return
        currentChatId = chatId
        
        messagesJob?.cancel()
        chatJob?.cancel()
        messagesJob = viewModelScope.launch {
            messageRepository.getMessagesForChat(chatId).collect { result ->
                result.fold(
                    onSuccess = { messageList ->
                        _messages.value = messageList
                    },
                    onFailure = { throwable ->
                        _error.value = "Mesajlar yüklenemedi: ${throwable.message}"
                    }
                )
            }
        }
        
        if (contactId.isNotEmpty()) {
            presenceJob?.cancel()
            presenceJob = viewModelScope.launch {
                chatRepository.observeUserPresence(contactId).collect { (isOnline, lastSeen) ->
                    _isContactOnline.value = isOnline
                    _contactLastSeen.value = lastSeen
                }
            }
            
            typingJob?.cancel()
            typingJob = viewModelScope.launch {
                messageRepository.observeTypingStatus(chatId, contactId).collect { isTyping ->
                    _isContactTyping.value = isTyping
                }
            }
        }
        
        chatJob?.cancel()
        chatJob = viewModelScope.launch {
            chatRepository.observeChat(chatId).collect { chat ->
                _currentChat.value = chat
            }
        }
    }

    fun clearChat() {
        val chatId = currentChatId
        if (chatId != null) {
            viewModelScope.launch {
                messageRepository.updateTypingStatus(chatId, false)
            }
        }
        typingTimerJob?.cancel()
        isTypingActive = false
        
        messagesJob?.cancel()
        chatJob?.cancel()
        presenceJob?.cancel()
        typingJob?.cancel()
        messagesJob = null
        presenceJob = null
        typingJob = null
        currentChatId = null
        _messages.value = emptyList()
        _isContactOnline.value = false
        _contactLastSeen.value = null
        _isContactTyping.value = false
    }

    fun updateTypingStatus(isTyping: Boolean) {
        val chatId = currentChatId ?: return
        viewModelScope.launch {
            val res = messageRepository.updateTypingStatus(chatId, isTyping)
            if (res.isFailure) {
                android.util.Log.e("ChatViewModel", "Typing error: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    private var isTypingActive = false

    fun onUserTyping() {
        if (!isTypingActive) {
            isTypingActive = true
            updateTypingStatus(true)
        }
        typingTimerJob?.cancel()
        typingTimerJob = viewModelScope.launch {
            kotlinx.coroutines.delay(2000) // 2 saniye yazmazsa false
            isTypingActive = false
            updateTypingStatus(false)
        }
    }

    fun sendMessage(text: String) {
        val chatId = currentChatId ?: return
        val replyId = _replyingToMessage.value?.id
        _replyingToMessage.value = null
        typingTimerJob?.cancel()
        isTypingActive = false
        updateTypingStatus(false)
        viewModelScope.launch {
            val result = messageRepository.sendMessage(chatId, text, replyId)
            if (result.isFailure) {
                _error.value = "Mesaj gönderilemedi: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    
    fun sendAudio(audioBytes: ByteArray) {
        val chatId = currentChatId ?: return
        val replyId = _replyingToMessage.value?.id
        _replyingToMessage.value = null
        viewModelScope.launch {
            val result = messageRepository.sendMedia(chatId, audioBytes, "AUDIO", replyId)
            if (result.isFailure) {
                _error.value = "Ses gönderilemedi: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun sendMedia(imageBytes: ByteArray, type: String = "IMAGE") {
        val chatId = currentChatId ?: return
        val replyId = _replyingToMessage.value?.id
        _replyingToMessage.value = null
        typingTimerJob?.cancel()
        isTypingActive = false
        updateTypingStatus(false)
        viewModelScope.launch {
            val result = messageRepository.sendMedia(chatId, imageBytes, type, replyId)
            if (result.isFailure) {
                _error.value = "Medya gönderilemedi: ${result.exceptionOrNull()?.message}"
            }
        }
    }
    
    fun sendPoll(pollData: com.example.misal.domain.model.PollData) {
        val chatId = currentChatId ?: return
        val replyId = _replyingToMessage.value?.id
        _replyingToMessage.value = null
        viewModelScope.launch {
            val result = messageRepository.sendPoll(chatId, pollData, replyId)
            if (result.isFailure) {
                _error.value = "Anket gönderilemedi: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun votePoll(messageId: String, optionIndex: Int) {
        val chatId = currentChatId ?: return
        viewModelScope.launch {
            val result = messageRepository.votePoll(chatId, messageId, optionIndex)
            if (result.isFailure) {
                _error.value = "Oy verilemedi: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun sendLocation(latitude: Double, longitude: Double) {
        val chatId = currentChatId ?: return
        val replyId = _replyingToMessage.value?.id
        _replyingToMessage.value = null
        viewModelScope.launch {
            val result = messageRepository.sendLocation(chatId, latitude, longitude, replyId)
            if (result.isFailure) {
                _error.value = "Konum gönderilemedi: ${result.exceptionOrNull()?.message}"
            }
        }
    }
    
    fun deleteMessage(messageId: String) {
        val chatId = currentChatId ?: return
        viewModelScope.launch {
            val result = messageRepository.deleteMessage(chatId, messageId)
            if (result.isFailure) {
                _error.value = "Mesaj silinemedi: ${result.exceptionOrNull()?.message}"
            }
        }
    }
    
    fun deleteMessageForMe(messageId: String) {
        val chatId = currentChatId ?: return
        viewModelScope.launch {
            val result = messageRepository.deleteMessageForMe(chatId, messageId)
            if (result.isFailure) {
                _error.value = "Mesaj silinemedi: ${result.exceptionOrNull()?.message}"
            }
        }
    }
    
    fun editMessage(messageId: String, newText: String) {
        val chatId = currentChatId ?: return
        typingTimerJob?.cancel()
        isTypingActive = false
        updateTypingStatus(false)
        viewModelScope.launch {
            val result = messageRepository.editMessage(chatId, messageId, newText)
            if (result.isFailure) {
                _error.value = "Mesaj düzenlenemedi: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun forwardMessage(message: Message, targetChatIds: List<String>) {
        viewModelScope.launch {
            targetChatIds.forEach { targetChatId ->
                if (message.messageType == "TEXT") {
                    messageRepository.sendMessage(targetChatId, message.text, null)
                } else if (message.messageType == "IMAGE" || message.messageType == "AUDIO") {
                    if (message.localMediaPath != null) {
                        val file = java.io.File(message.localMediaPath)
                        if (file.exists()) {
                            messageRepository.sendMedia(targetChatId, file.readBytes(), message.messageType, null)
                        }
                    }
                }
            }
        }
    }


    fun markMessagesAsRead() {
        val chatId = currentChatId ?: return
        val currentUserId = authRepository.getCurrentUserId() ?: return
        val currentMessages = _messages.value
        val unreadMessageIds = currentMessages
            .filter { !it.isFromMe && (!it.isRead || !it.readBy.contains(currentUserId)) }
            .map { it.id }
        
        if (unreadMessageIds.isNotEmpty()) {
            viewModelScope.launch {
                messageRepository.markMessagesAsRead(chatId, unreadMessageIds)
            }
        }
    }
    
    fun reactToMessage(messageId: String, emoji: String) {
        val chatId = currentChatId ?: return
        viewModelScope.launch {
            messageRepository.reactToMessage(chatId, messageId, emoji)
        }
    }

    fun pinMessage(messageId: String) {
        val chatId = currentChatId ?: return
        viewModelScope.launch {
            chatRepository.pinMessage(chatId, messageId)
        }
    }

    fun unpinMessage() {
        val chatId = currentChatId ?: return
        viewModelScope.launch {
            chatRepository.unpinMessage(chatId)
        }
    }

    fun setDisappearingTimer(timerMs: Long?) {
        val chatId = currentChatId ?: return
        viewModelScope.launch {
            chatRepository.setDisappearingTimer(chatId, timerMs)
        }
    }
}
