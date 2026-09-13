package com.example.misal.data.dto

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class ChatDto(
    @DocumentId val id: String = "",
    val participants: List<String> = emptyList(),
    val contactName: String = "", // Basitlik için eklendi, gerçekte participants'dan resolve edilebilir
    val lastMessage: String = "",
    @ServerTimestamp val timestamp: Date? = null,
    val unreadCount: Int = 0,
    val disappearingTimer: Long? = null,
    val pinnedMessageId: String? = null,
    @get:com.google.firebase.firestore.PropertyName("isGroup")
    val isGroup: Boolean = false,
    val groupName: String? = null,
    val groupIconBase64: String? = null
)
