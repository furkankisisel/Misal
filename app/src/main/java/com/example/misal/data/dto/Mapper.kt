package com.example.misal.data.dto

import com.example.misal.domain.model.Chat
import com.example.misal.domain.model.Message
import com.example.misal.security.CryptoManager

fun ChatDto.toDomain(currentUserId: String?): Chat {
    return Chat(
        id = id,
        contactId = participants.firstOrNull { it != currentUserId } ?: currentUserId ?: "",
        contactName = contactName,
        lastMessage = lastMessage,
        timestamp = timestamp?.time ?: 0L,
        unreadCount = unreadCount,
        disappearingTimer = disappearingTimer
    )
}

fun MessageDto.toDomain(currentUserId: String?, cryptoManager: CryptoManager): Message {
    val isFromMe = senderId == currentUserId
    
    // Hangi AES anahtarını çözeceğimizi belirliyoruz (Önce yeni sistem, yoksa eski sistem)
    val aesKeyToUse = encryptedAesKeys[currentUserId] ?: if (isFromMe) aesKeyForSender else aesKeyForRecipient
    
    if (encryptedText.isEmpty()) {
        return Message(
            id = id,
            senderId = senderId,
            text = "Eski Şifresiz Mesaj (Gizlendi)",
            timestamp = timestamp?.time ?: 0L,
            isFromMe = isFromMe,
            isRead = isRead
        )
    }
    
    val decryptedText = try {
        cryptoManager.decryptMessage(
            encryptedTextBase64 = encryptedText,
            encryptedAesKeyBase64 = aesKeyToUse,
            ivBase64 = iv
        )
    } catch (e: Exception) {
        "Hata: ${e.toString()}"
    }

    return Message(
        id = id,
        senderId = senderId,
        text = decryptedText,
        timestamp = timestamp?.time ?: 0L,
        isFromMe = isFromMe,
        isRead = isRead,
        isDelivered = isDelivered,
        isDeleted = isDeleted,
        expiresAt = expiresAt,
        isEdited = isEdited,
        messageType = messageType,
        mediaUrl = mediaUrl,
        reactions = reactions,
        replyToMessageId = replyToMessageId,
        readBy = readBy
    )
}
