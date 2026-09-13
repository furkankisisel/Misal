package com.example.misal.data.repository

import com.example.misal.data.dto.ChatDto
import com.example.misal.data.dto.toDomain
import com.example.misal.domain.model.Chat
import com.example.misal.domain.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

import javax.inject.Inject

import com.example.misal.security.CryptoManager
import com.example.misal.data.local.dao.MessageDao

class FirebaseChatRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val chatDao: com.example.misal.data.local.dao.ChatDao,
    private val messageDao: MessageDao,
    private val cryptoManager: CryptoManager
) : ChatRepository {

    override fun getChats(): Flow<Result<List<Chat>>> = callbackFlow {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            trySend(Result.success(emptyList()))
            close()
            return@callbackFlow
        }
        
        // 1. Yerel veritabanından oku ve anında UI'a gönder
        val dbJob = launch {
            combine(chatDao.getAllChats(), messageDao.getMessagesUpdateTrigger()) { entities, _ ->
                val chats = entities.map { entity ->
                    val allMessages = messageDao.getMessagesForChatSync(entity.id)
                    val unreadCount = allMessages.count { msg ->
                        msg.senderId != currentUserId && 
                        (if (entity.isGroup) !msg.readBy.contains(currentUserId) else !msg.isRead)
                    }
                    val lastMessageEntity = allMessages.maxByOrNull { it.timestamp }
                    
                    var lastMessageText = ""
                    var lastMessageTimestamp = entity.lastMessageTimestamp
                        
                    if (lastMessageEntity != null) {
                        lastMessageTimestamp = lastMessageEntity.timestamp
                        val isFromMe = lastMessageEntity.senderId == currentUserId
                        val aesKeyToUse = lastMessageEntity.encryptedAesKeys?.get(currentUserId) 
                            ?: if (isFromMe) lastMessageEntity.aesKeyForSender else lastMessageEntity.aesKeyForRecipient
                            
                        val decryptedText = if (lastMessageEntity.messageType == "TEXT") {
                            try {
                                cryptoManager.decryptMessage(
                                    encryptedTextBase64 = lastMessageEntity.encryptedText,
                                    encryptedAesKeyBase64 = aesKeyToUse ?: "",
                                    ivBase64 = lastMessageEntity.iv
                                )
                            } catch (e: Exception) {
                                "Hata"
                            }
                        } else {
                            if (lastMessageEntity.messageType == "IMAGE" || lastMessageEntity.messageType == "VIEW_ONCE_IMAGE") "Fotoğraf" else if (lastMessageEntity.messageType == "AUDIO") "Sesli Mesaj" else if (lastMessageEntity.messageType == "LOCATION") "Konum" else if (lastMessageEntity.messageType == "POLL") "Anket" else ""
                        }
                        lastMessageText = if (lastMessageEntity.isDeleted) "🚫 Bu mesaj silindi" else decryptedText
                    }

                    Chat(
                        id = entity.id,
                        contactId = entity.contactId,
                        contactName = entity.contactName,
                        lastMessage = lastMessageText,
                        timestamp = lastMessageTimestamp,
                        unreadCount = unreadCount,
                        pinnedMessageId = entity.pinnedMessageId,
                        disappearingTimer = entity.disappearingTimer,
                        contactProfilePictureBase64 = entity.contactProfilePictureBase64,
                        isGroup = entity.isGroup,
                        groupName = entity.groupName,
                        groupIconBase64 = entity.groupIconBase64
                    )
                }.sortedByDescending { it.timestamp }
                Result.success(chats)
            }.flowOn(Dispatchers.IO).collect { result ->
                trySend(result)
            }
        }

        // 2. Arka planda Firestore'u dinle ve yeni verileri yerel veritabanına yaz
        val listener = firestore.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (auth.currentUser == null) return@addSnapshotListener
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val dtos = snapshot.documents.mapNotNull { doc -> doc.toObject(ChatDto::class.java) }
                    launch(Dispatchers.IO) {
                        val entities = dtos.map { dto ->
                            val isGroup = dto.isGroup
                            val contactId = if (isGroup) "" else dto.participants.firstOrNull { it != currentUserId } ?: currentUserId
                            
                            val resolvedName = if (isGroup) {
                                dto.groupName ?: "Bilinmeyen Grup"
                            } else {
                                // Karşı tarafın adını ve profil resmini users koleksiyonundan çek
                                val contactDoc = try {
                                    firestore.collection("users").document(contactId).get().await()
                                } catch (e: Exception) { null }
                                contactDoc?.getString("name") ?: dto.contactName
                            }
                            
                            val contactProfilePicture = if (isGroup) dto.groupIconBase64 else {
                                try {
                                    firestore.collection("users").document(contactId).get().await().getString("profilePictureBase64")
                                } catch (e: Exception) { null }
                            }
                            
                            com.example.misal.data.local.entity.ChatEntity(
                                id = dto.id,
                                contactId = contactId,
                                contactName = resolvedName,
                                lastMessageTimestamp = 0L,
                                pinnedMessageId = dto.pinnedMessageId,
                                disappearingTimer = dto.disappearingTimer,
                                contactProfilePictureBase64 = contactProfilePicture,
                                isGroup = isGroup,
                                groupName = dto.groupName,
                                groupIconBase64 = dto.groupIconBase64
                            )
                        }
                        chatDao.insertChats(entities)
                    }
                }
            }

        awaitClose {
            dbJob.cancel()
            listener.remove()
        }
    }

    override suspend fun createChat(contactId: String, contactName: String): Result<String> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
            val chatRef = firestore.collection("chats").document()
            val newChat = ChatDto(
                id = chatRef.id,
                participants = listOf(currentUserId, contactId),
                contactName = contactName
            )
            chatRef.set(newChat).await()
            Result.success(chatRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createGroup(groupName: String, participants: List<String>): Result<String> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
            val chatRef = firestore.collection("chats").document()
            
            // Kullanıcı kendisini katılımcılara eklememişse ekleyelim
            val allParticipants = if (participants.contains(currentUserId)) participants else participants + currentUserId
            
            val newChat = ChatDto(
                id = chatRef.id,
                participants = allParticipants,
                isGroup = true,
                groupName = groupName,
                contactName = "",
                lastMessage = "Grup oluşturuldu"
            )
            
            chatRef.set(newChat).await()
            Result.success(chatRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeUserPresence(userId: String): Flow<Pair<Boolean, Long?>> = callbackFlow {
        val listener = firestore.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Pair(false, null))
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val isOnline = snapshot.getBoolean("isOnline") ?: false
                    val lastSeen = snapshot.getTimestamp("lastSeen")?.toDate()?.time
                    trySend(Pair(isOnline, lastSeen))
                }
            }
        
        awaitClose {
            listener.remove()
        }
    }
    override suspend fun pinMessage(chatId: String, messageId: String): Result<Unit> {
        return try {
            firestore.collection("chats").document(chatId)
                .update("pinnedMessageId", messageId)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unpinMessage(chatId: String): Result<Unit> {
        return try {
            firestore.collection("chats").document(chatId)
                .update("pinnedMessageId", com.google.firebase.firestore.FieldValue.delete())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    override fun observeChat(chatId: String): Flow<Chat?> {
        return combine(chatDao.getChatById(chatId), messageDao.getMessagesUpdateTrigger()) { entity, _ ->
            entity?.let {
                val currentUserId = auth.currentUser?.uid ?: ""
                val allMessages = messageDao.getMessagesForChatSync(it.id)
                val unreadCount = allMessages.count { msg ->
                    msg.senderId != currentUserId && 
                    (if (it.isGroup) !msg.readBy.contains(currentUserId) else !msg.isRead)
                }
                val lastMessageEntity = allMessages.maxByOrNull { m -> m.timestamp }
                var lastMessageText = ""
                var lastMessageTimestamp = it.lastMessageTimestamp
                        
                if (lastMessageEntity != null) {
                    lastMessageTimestamp = lastMessageEntity.timestamp
                    val isFromMe = lastMessageEntity.senderId == currentUserId
                    val aesKeyToUse = lastMessageEntity.encryptedAesKeys?.get(currentUserId) 
                        ?: if (isFromMe) lastMessageEntity.aesKeyForSender else lastMessageEntity.aesKeyForRecipient
                            
                    val decryptedText = if (lastMessageEntity.messageType == "TEXT") {
                        try {
                            cryptoManager.decryptMessage(
                                encryptedTextBase64 = lastMessageEntity.encryptedText,
                                encryptedAesKeyBase64 = aesKeyToUse ?: "",
                                ivBase64 = lastMessageEntity.iv
                            )
                        } catch (e: Exception) {
                            "Hata"
                        }
                    } else {
                        if (lastMessageEntity.messageType == "IMAGE" || lastMessageEntity.messageType == "VIEW_ONCE_IMAGE") "Fotoğraf" else if (lastMessageEntity.messageType == "AUDIO") "Sesli Mesaj" else if (lastMessageEntity.messageType == "LOCATION") "Konum" else if (lastMessageEntity.messageType == "POLL") "Anket" else ""
                    }
                    lastMessageText = if (lastMessageEntity.isDeleted) "🚫 Bu mesaj silindi" else decryptedText
                }

                Chat(
                    id = it.id,
                    contactId = it.contactId,
                    contactName = it.contactName,
                    lastMessage = lastMessageText,
                    timestamp = lastMessageTimestamp,
                    unreadCount = unreadCount,
                    pinnedMessageId = it.pinnedMessageId,
                    disappearingTimer = it.disappearingTimer,
                    contactProfilePictureBase64 = it.contactProfilePictureBase64,
                    isGroup = it.isGroup,
                    groupName = it.groupName,
                    groupIconBase64 = it.groupIconBase64
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun setDisappearingTimer(chatId: String, timerMs: Long?): Result<Unit> {
        return try {
            val updateData = if (timerMs == null) {
                mapOf("disappearingTimer" to com.google.firebase.firestore.FieldValue.delete())
            } else {
                mapOf("disappearingTimer" to timerMs)
            }
            firestore.collection("chats").document(chatId).update(updateData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
