package com.example.misal.data.dto

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class MessageDto(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val encryptedText: String = "",
    val encryptedAesKeys: Map<String, String> = emptyMap(),
    val aesKeyForSender: String = "",
    val aesKeyForRecipient: String = "",
    val iv: String = "",
    @ServerTimestamp val timestamp: Date? = null,
    @get:PropertyName("isRead")
    @set:PropertyName("isRead")
    var isRead: Boolean = false,
    @get:PropertyName("isDelivered")
    @set:PropertyName("isDelivered")
    var isDelivered: Boolean = false,
    @get:PropertyName("isDeleted")
    @set:PropertyName("isDeleted")
    var isDeleted: Boolean = false,
    val expiresAt: Long? = null,
    @get:PropertyName("isEdited")
    @set:PropertyName("isEdited")
    var isEdited: Boolean = false,
    val messageType: String = "TEXT",
    val mediaUrl: String? = null,
    val deletedFor: List<String> = emptyList(),
    val reactions: Map<String, String> = emptyMap(),
    val replyToMessageId: String? = null,
    val readBy: List<String> = emptyList(),
    val encryptedPollVotes: Map<String, String> = emptyMap()
)
