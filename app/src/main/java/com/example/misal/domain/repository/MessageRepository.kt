package com.example.misal.domain.repository

import com.example.misal.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getMessagesForChat(chatId: String): Flow<Result<List<Message>>>
    suspend fun sendMessage(chatId: String, text: String, replyToMessageId: String? = null): Result<Unit>
    suspend fun sendMedia(chatId: String, imageBytes: ByteArray, type: String = "IMAGE", replyToMessageId: String? = null): Result<Unit>
    suspend fun downloadMedia(messageId: String, mediaUrl: String, encryptedAesKey: String, iv: String): Result<ByteArray>
    suspend fun sendPoll(chatId: String, pollData: com.example.misal.domain.model.PollData, replyToMessageId: String? = null): Result<Unit>
    suspend fun votePoll(chatId: String, messageId: String, optionIndex: Int): Result<Unit>
    suspend fun sendLocation(chatId: String, latitude: Double, longitude: Double, replyToMessageId: String? = null): Result<Unit>
    suspend fun markMessagesAsDelivered(chatId: String, messageIds: List<String>): Result<Unit>
    suspend fun markMessagesAsRead(chatId: String, messageIds: List<String>): Result<Unit>
    
    suspend fun deleteMessage(chatId: String, messageId: String): Result<Unit>
    suspend fun deleteMessageForMe(chatId: String, messageId: String): Result<Unit>
    suspend fun editMessage(chatId: String, messageId: String, newText: String): Result<Unit>
    suspend fun reactToMessage(chatId: String, messageId: String, emoji: String): Result<Unit>

    suspend fun updateTypingStatus(chatId: String, isTyping: Boolean): Result<Unit>
    fun observeTypingStatus(chatId: String, contactId: String): Flow<Boolean>
}
