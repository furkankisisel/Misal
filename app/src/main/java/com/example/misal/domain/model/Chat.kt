package com.example.misal.domain.model

data class Chat(
    val id: String,
    val contactId: String,
    val contactName: String,
    val lastMessage: String,
    val timestamp: Long,
    val unreadCount: Int = 0,
    val disappearingTimer: Long? = null,
    val pinnedMessageId: String? = null,
    val contactProfilePictureBase64: String? = null,
    val isGroup: Boolean = false,
    val groupName: String? = null,
    val groupIconBase64: String? = null
)
