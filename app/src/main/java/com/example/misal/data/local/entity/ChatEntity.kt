package com.example.misal.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val contactId: String,
    val contactName: String,
    val lastMessageTimestamp: Long,
    val pinnedMessageId: String? = null,
    val disappearingTimer: Long? = null,
    val contactProfilePictureBase64: String? = null,
    val isGroup: Boolean = false,
    val groupName: String? = null,
    val groupIconBase64: String? = null
)
