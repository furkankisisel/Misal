package com.example.misal.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.misal.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(id) + SUM(isRead) + SUM(LENGTH(readBy)) FROM messages")
    fun getMessagesUpdateTrigger(): Flow<Int?>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    fun getLastMessageForChatFlow(chatId: String): Flow<MessageEntity?>

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND isRead = 0 AND senderId != :currentUserId")
    fun getUnreadCountForChatFlow(chatId: String, currentUserId: String): Flow<Int>

    @Query("SELECT * FROM messages WHERE chatId = :chatId")
    fun getMessagesForChatSync(chatId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    fun getLastMessageForChatSync(chatId: String): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND isRead = 0 AND senderId != :currentUserId")
    fun getUnreadCountForChatSync(chatId: String, currentUserId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMessages(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET isRead = 1 WHERE chatId = :chatId AND id IN (:messageIds)")
    fun markMessagesAsRead(chatId: String, messageIds: List<String>)

    @Query("UPDATE messages SET localMediaPath = :path WHERE id = :messageId")
    fun updateLocalMediaPath(messageId: String, path: String)
    
    @Query("DELETE FROM messages")
    fun clearAll(): Unit
    
    @Query("DELETE FROM messages WHERE id = :messageId")
    fun deleteMessageById(messageId: String)
}
