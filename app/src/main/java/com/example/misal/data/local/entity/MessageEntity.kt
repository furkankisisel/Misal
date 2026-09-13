package com.example.misal.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val encryptedText: String,
    val encryptedAesKeys: Map<String, String>,
    val aesKeyForSender: String,
    val aesKeyForRecipient: String,
    val iv: String,
    val timestamp: Long,
    val isRead: Boolean,
    val isDelivered: Boolean,
    val isDeleted: Boolean = false,
    val expiresAt: Long? = null,
    val isEdited: Boolean = false,
    val messageType: String = "TEXT",
    val mediaUrl: String?,
    val localMediaPath: String?,
    val reactions: Map<String, String> = emptyMap(),
    val replyToMessageId: String? = null,
    val readBy: List<String> = emptyList(),
    val encryptedPollVotes: Map<String, String> = emptyMap()
)
