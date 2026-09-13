package com.example.misal.domain.repository

import com.example.misal.domain.model.Chat
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getChats(): Flow<Result<List<Chat>>>
    suspend fun createChat(contactId: String, contactName: String): Result<String>
    suspend fun createGroup(groupName: String, participants: List<String>): Result<String>
    fun observeUserPresence(userId: String): Flow<Pair<Boolean, Long?>>
    suspend fun pinMessage(chatId: String, messageId: String): Result<Unit>
    suspend fun unpinMessage(chatId: String): Result<Unit>
    suspend fun setDisappearingTimer(chatId: String, timerMs: Long?): Result<Unit>
    fun observeChat(chatId: String): Flow<Chat?>
}
