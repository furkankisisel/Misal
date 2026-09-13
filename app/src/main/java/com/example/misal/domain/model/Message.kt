package com.example.misal.domain.model

data class Message(
    val id: String,
    val senderId: String,
    val text: String,
    val timestamp: Long,
    val isFromMe: Boolean,
    val isRead: Boolean = false,
    val isDelivered: Boolean = false,
    val isDeleted: Boolean = false,
    val expiresAt: Long? = null,
    val isEdited: Boolean = false,
    val messageType: String = "TEXT",
    val mediaUrl: String? = null,
    val localMediaPath: String? = null,
    val reactions: Map<String, String> = emptyMap(),
    val replyToMessageId: String? = null,
    val replyToMessageText: String? = null,
    val readBy: List<String> = emptyList(),
    val pollVotes: Map<String, Int> = emptyMap()
)
