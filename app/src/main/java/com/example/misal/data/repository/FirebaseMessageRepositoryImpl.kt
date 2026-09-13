package com.example.misal.data.repository

import kotlinx.coroutines.withContext

import com.example.misal.data.dto.MessageDto
import com.example.misal.domain.model.Message
import com.example.misal.domain.repository.AuthRepository
import com.example.misal.domain.repository.MessageRepository
import com.example.misal.security.CryptoManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class FirebaseMessageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val storage: com.google.firebase.storage.FirebaseStorage,
    private val authRepository: AuthRepository,
    private val cryptoManager: CryptoManager,
    private val messageDao: com.example.misal.data.local.dao.MessageDao
) : MessageRepository {

    override fun getMessagesForChat(chatId: String): Flow<Result<List<Message>>> = callbackFlow {
        // 1. Yerel veritabanından oku ve UI'a aktar (Şifreyi çözerek)
        val dbJob = launch {
            messageDao.getMessagesForChat(chatId).map { entities ->
                try {
                    val currentUserId = authRepository.getCurrentUserId()
                    val messages = entities.map { entity ->
                        val isFromMe = entity.senderId == currentUserId
                        val aesKeyToUse = entity.encryptedAesKeys[currentUserId] ?: if (isFromMe) entity.aesKeyForSender else entity.aesKeyForRecipient
                        
                        val decryptedText = if (entity.messageType == "TEXT" || entity.messageType == "POLL" || entity.messageType == "LOCATION") {
                            try {
                                cryptoManager.decryptMessage(
                                    encryptedTextBase64 = entity.encryptedText,
                                    encryptedAesKeyBase64 = aesKeyToUse,
                                    ivBase64 = entity.iv
                                )
                            } catch (e: Exception) {
                                "Hata: ${e.message}"
                            }
                        } else {
                            if (entity.messageType == "IMAGE" || entity.messageType == "VIEW_ONCE_IMAGE") "Fotoğraf" else if (entity.messageType == "AUDIO") "Sesli Mesaj" else ""
                        }

                        val pollVotes = entity.encryptedPollVotes.mapValues { (_, encryptedVote) ->
                            try {
                                val voteStr = cryptoManager.decryptMessage(
                                    encryptedTextBase64 = encryptedVote,
                                    encryptedAesKeyBase64 = aesKeyToUse,
                                    ivBase64 = entity.iv // Using same IV is fine if random padding, but standard decrypt uses it
                                )
                                voteStr.toIntOrNull() ?: -1
                            } catch (e: Exception) {
                                -1
                            }
                        }.filterValues { it != -1 }

                        Message(
                            id = entity.id,
                            senderId = entity.senderId,
                            text = if (entity.isDeleted) "🚫 Bu mesaj silindi" else decryptedText,
                            timestamp = entity.timestamp,
                            isFromMe = isFromMe,
                            isRead = entity.isRead,
                            isDelivered = entity.isDelivered,
                            isDeleted = entity.isDeleted,
                            isEdited = entity.isEdited,
                            messageType = entity.messageType,
                            mediaUrl = entity.mediaUrl,
                            localMediaPath = entity.localMediaPath,
                            reactions = entity.reactions,
                            replyToMessageId = entity.replyToMessageId,
                            readBy = entity.readBy,
                            pollVotes = pollVotes
                        )
                    }
                    Result.success(messages)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }.flowOn(Dispatchers.IO).collect { result ->
                trySend(result)
            }
        }

        // 2. Firestore'u dinle ve yerel veritabanını güncelle
        val listener = firestore.collection("chats").document(chatId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (authRepository.getCurrentUserId() == null) return@addSnapshotListener
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val dtos = snapshot.documents.mapNotNull { doc -> doc.toObject(MessageDto::class.java) }
                    launch(Dispatchers.IO) {
                        val existingEntities = messageDao.getMessagesForChatSync(chatId) // TODO: Implement this method
                        val existingMap = existingEntities.associateBy { it.id }
                        
                        val currentTime = System.currentTimeMillis()
                        val expiredDtos = dtos.filter { it.expiresAt != null && it.expiresAt!! <= currentTime }
                        val unexpiredDtos = dtos.filter { it.expiresAt == null || it.expiresAt!! > currentTime }
                        
                        val currentUserId = authRepository.getCurrentUserId()
                        val validDtos = unexpiredDtos.filter { currentUserId == null || !it.deletedFor.contains(currentUserId) }
                        val deletedDtos = unexpiredDtos.filter { currentUserId != null && it.deletedFor.contains(currentUserId) }
                        
                        deletedDtos.forEach { messageDao.deleteMessageById(it.id) }
                        expiredDtos.forEach { 
                            messageDao.deleteMessageById(it.id)
                            firestore.collection("chats").document(chatId).collection("messages").document(it.id).delete()
                        }
                        
                        val entities = validDtos.map { dto ->
                            val existing = existingMap[dto.id]
                            com.example.misal.data.local.entity.MessageEntity(
                                id = dto.id,
                                chatId = chatId,
                                senderId = dto.senderId,
                                encryptedText = dto.encryptedText,
                                encryptedAesKeys = dto.encryptedAesKeys,
                                aesKeyForSender = dto.aesKeyForSender,
                                aesKeyForRecipient = dto.aesKeyForRecipient,
                                iv = dto.iv,
                                timestamp = dto.timestamp?.time ?: 0L,
                                isRead = dto.isRead,
                                isDelivered = dto.isDelivered,
                                isDeleted = dto.isDeleted,
                                expiresAt = dto.expiresAt,
                                isEdited = dto.isEdited,
                                messageType = dto.messageType,
                                mediaUrl = dto.mediaUrl,
                                localMediaPath = existing?.localMediaPath,
                                reactions = dto.reactions,
                                replyToMessageId = dto.replyToMessageId,
                                readBy = dto.readBy,
                                encryptedPollVotes = dto.encryptedPollVotes
                            )
                        }
                        messageDao.insertMessages(entities)

                        // Okunmamış veya teslim edilmemiş (bize gelen) mesajlar için Firestore'u güncelle
                        val unDeliveredIds = entities.filter { it.senderId != currentUserId && !it.isDelivered }.map { it.id }
                        if (unDeliveredIds.isNotEmpty()) {
                            launch(kotlinx.coroutines.Dispatchers.IO) {
                                markMessagesAsDelivered(chatId, unDeliveredIds)
                            }
                        }

                        // Medyaları indir (eğer yoksa)
                                                entities.forEach { entity ->
                            if ((entity.messageType == "IMAGE" || entity.messageType == "VIEW_ONCE_IMAGE" || entity.messageType == "AUDIO") && entity.localMediaPath == null && entity.mediaUrl != null) {
                                if (entity.mediaUrl == "base64" && entity.encryptedText.isNotEmpty()) {
                                    // Firebase Storage KULLANMADAN doğrudan Firestore'dan Base64 olarak okuyoruz!
                                    launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            val encryptedBytes = android.util.Base64.decode(entity.encryptedText, android.util.Base64.NO_WRAP)
                                            val aesKeyToUse = entity.encryptedAesKeys[currentUserId] ?: if (entity.senderId == currentUserId) entity.aesKeyForSender else entity.aesKeyForRecipient
                                            val decryptedBytes = cryptoManager.decryptMedia(encryptedBytes, aesKeyToUse, entity.iv)
                                            
                                            val mediaDir = java.io.File(context.cacheDir, "misal_media").apply { mkdirs() }
                                            val extension = if (entity.messageType == "AUDIO") "m4a" else "jpg"
                                            val file = java.io.File(mediaDir, "media_${entity.id}.$extension")
                                            file.writeBytes(decryptedBytes)
                                            messageDao.updateLocalMediaPath(entity.id, file.absolutePath)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                } else if (entity.mediaUrl != "base64") {
                                    // Eski (Storage kullanan) mesajlar için geriye dönük uyumluluk
                                    launch(kotlinx.coroutines.Dispatchers.IO) {
                                        val aesKeyToUse = entity.encryptedAesKeys[currentUserId] ?: if (entity.senderId == currentUserId) entity.aesKeyForSender else entity.aesKeyForRecipient
                                        val result = downloadMedia(entity.id, entity.mediaUrl, aesKeyToUse, entity.iv)
                                        result.onSuccess { bytes ->
                                            val mediaDir = java.io.File(context.cacheDir, "misal_media").apply { mkdirs() }
                                            val file = java.io.File(mediaDir, "media_${entity.id}.jpg")
                                            file.writeBytes(bytes)
                                            messageDao.updateLocalMediaPath(entity.id, file.absolutePath)
                                        }.onFailure { e ->
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        awaitClose {
            dbJob.cancel()
            listener.remove()
        }
    }

    override suspend fun sendMessage(chatId: String, text: String, replyToMessageId: String?): Result<Unit> {
        return try {
            val currentUserId = authRepository.getCurrentUserId() ?: throw Exception("Not logged in")
            
            // 1. Fetch chat document to get participants
            val chatDoc = firestore.collection("chats").document(chatId).get().await()
            val participants = chatDoc.get("participants") as? List<String> ?: throw Exception("Chat not found or invalid")
            val disappearingTimer = chatDoc.getLong("disappearingTimer")
            
            // 2. Fetch public keys for ALL participants
            val publicKeysMap = mutableMapOf<String, String>()
            for (userId in participants) {
                if (userId == currentUserId) {
                    val myKey = cryptoManager.getPublicKeyBase64()
                    if (myKey != null) publicKeysMap[userId] = myKey
                } else {
                    val userDoc = firestore.collection("users").document(userId).get().await()
                    val pk = userDoc.getString("publicKey")
                    if (!pk.isNullOrEmpty()) {
                        publicKeysMap[userId] = pk
                    }
                }
            }
            if (publicKeysMap.isEmpty()) {
                throw Exception("Grupta (veya sohbette) şifreleme anahtarı olan kimse yok")
            }
            
            // 3. Encrypt message for group
            val encryptedPayload = cryptoManager.encryptMessageForGroup(text, publicKeysMap)
            
            // 4. Save to firestore
            val messageRef = firestore.collection("chats").document(chatId).collection("messages").document()
            val newMessage = MessageDto(
                id = messageRef.id,
                senderId = currentUserId,
                encryptedText = encryptedPayload.encryptedText,
                encryptedAesKeys = encryptedPayload.encryptedAesKeys,
                iv = encryptedPayload.iv,
                expiresAt = if (disappearingTimer != null) System.currentTimeMillis() + disappearingTimer else null,
                replyToMessageId = replyToMessageId
            )
            messageRef.set(newMessage).await()
            firestore.collection("chats").document(chatId).update("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendPoll(chatId: String, pollData: com.example.misal.domain.model.PollData, replyToMessageId: String?): Result<Unit> {
        return try {
            val currentUserId = authRepository.getCurrentUserId() ?: throw Exception("Not logged in")
            val chatDoc = firestore.collection("chats").document(chatId).get().await()
            val participants = chatDoc.get("participants") as? List<String> ?: throw Exception("Chat not found")
            val disappearingTimer = chatDoc.getLong("disappearingTimer")
            
            val publicKeysMap = mutableMapOf<String, String>()
            for (userId in participants) {
                if (userId == currentUserId) {
                    val myKey = cryptoManager.getPublicKeyBase64()
                    if (myKey != null) publicKeysMap[userId] = myKey
                } else {
                    val userDoc = firestore.collection("users").document(userId).get().await()
                    val pk = userDoc.getString("publicKey")
                    if (!pk.isNullOrEmpty()) publicKeysMap[userId] = pk
                }
            }
            if (publicKeysMap.isEmpty()) throw Exception("Grupta şifreleme anahtarı olan kimse yok")

            val jsonPoll = org.json.JSONObject().apply {
                put("question", pollData.question)
                put("options", org.json.JSONArray(pollData.options))
            }.toString()

            val encryptedPayload = cryptoManager.encryptMessageForGroup(jsonPoll, publicKeysMap)

            val messageRef = firestore.collection("chats").document(chatId).collection("messages").document()
            val newMessage = MessageDto(
                id = messageRef.id,
                senderId = currentUserId,
                encryptedText = encryptedPayload.encryptedText,
                encryptedAesKeys = encryptedPayload.encryptedAesKeys,
                iv = encryptedPayload.iv,
                expiresAt = if (disappearingTimer != null) System.currentTimeMillis() + disappearingTimer else null,
                replyToMessageId = replyToMessageId,
                messageType = "POLL"
            )
            messageRef.set(newMessage).await()
            firestore.collection("chats").document(chatId).update("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendLocation(chatId: String, latitude: Double, longitude: Double, replyToMessageId: String?): Result<Unit> {
        return try {
            val currentUserId = authRepository.getCurrentUserId() ?: throw Exception("Not logged in")
            val chatDoc = firestore.collection("chats").document(chatId).get().await()
            val participants = chatDoc.get("participants") as? List<String> ?: throw Exception("Chat not found")
            val disappearingTimer = chatDoc.getLong("disappearingTimer")
            
            val publicKeysMap = mutableMapOf<String, String>()
            for (userId in participants) {
                if (userId == currentUserId) {
                    val myKey = cryptoManager.getPublicKeyBase64()
                    if (myKey != null) publicKeysMap[userId] = myKey
                } else {
                    val userDoc = firestore.collection("users").document(userId).get().await()
                    val pk = userDoc.getString("publicKey")
                    if (!pk.isNullOrEmpty()) publicKeysMap[userId] = pk
                }
            }
            if (publicKeysMap.isEmpty()) throw Exception("Grupta şifreleme anahtarı olan kimse yok")

            val locationText = "$latitude,$longitude"
            val encryptedPayload = cryptoManager.encryptMessageForGroup(locationText, publicKeysMap)

            val messageRef = firestore.collection("chats").document(chatId).collection("messages").document()
            val newMessage = MessageDto(
                id = messageRef.id,
                senderId = currentUserId,
                encryptedText = encryptedPayload.encryptedText,
                encryptedAesKeys = encryptedPayload.encryptedAesKeys,
                iv = encryptedPayload.iv,
                expiresAt = if (disappearingTimer != null) System.currentTimeMillis() + disappearingTimer else null,
                replyToMessageId = replyToMessageId,
                messageType = "LOCATION"
            )
            messageRef.set(newMessage).await()
            firestore.collection("chats").document(chatId).update("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun votePoll(chatId: String, messageId: String, optionIndex: Int): Result<Unit> {
        return try {
            val currentUserId = authRepository.getCurrentUserId() ?: throw Exception("Not logged in")
            
            // 1. Orijinal mesajı çek (AES anahtarını alabilmek için)
            val messageDoc = firestore.collection("chats").document(chatId).collection("messages").document(messageId).get().await()
            val messageDto = messageDoc.toObject(MessageDto::class.java) ?: throw Exception("Mesaj bulunamadı")
            
            // 2. AES anahtarını bul
            val encryptedAesKey = messageDto.encryptedAesKeys[currentUserId] ?: throw Exception("Bu mesaj için şifreleme anahtarınız yok")
            
            // 3. Oy bilgisini (optionIndex) AES anahtarıyla şifrele
            val encryptedVote = cryptoManager.encryptMessage(
                optionIndex.toString(),
                encryptedAesKey,
                messageDto.iv
            )
            
            // 4. Firestore'da güncelle
            firestore.collection("chats").document(chatId).collection("messages").document(messageId)
                .update("encryptedPollVotes.$currentUserId", encryptedVote)
                .await()
                
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun compressImageToFitFirestore(originalBytes: ByteArray): ByteArray {
        val maxSizeBytes = 700 * 1024 // 700 KB (Firestore belgesinin maksimum limiti 1MB'dir)
        if (originalBytes.size <= maxSizeBytes) return originalBytes
        
        var quality = 80
        var compressedBytes = originalBytes
        var bitmap = android.graphics.BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size)
        if (bitmap == null) return originalBytes
        
        if (bitmap.width > 1200 || bitmap.height > 1200) {
            val ratio = 1200f / maxOf(bitmap.width, bitmap.height)
            val newWidth = (bitmap.width * ratio).toInt()
            val newHeight = (bitmap.height * ratio).toInt()
            bitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        }

        while (quality > 10) {
            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, outputStream)
            compressedBytes = outputStream.toByteArray()
            if (compressedBytes.size <= maxSizeBytes) {
                break
            }
            quality -= 10
        }
        return compressedBytes
    }

    override suspend fun sendMedia(chatId: String, imageBytes: ByteArray, type: String, replyToMessageId: String?): Result<Unit> {
        return try {
            val currentUserId = authRepository.getCurrentUserId() ?: throw Exception("Not logged in")
            
            // 1. Fetch chat document to get participants
            val chatDoc = firestore.collection("chats").document(chatId).get().await()
            val participants = chatDoc.get("participants") as? List<String> ?: throw Exception("Chat not found or invalid")
            val disappearingTimer = chatDoc.getLong("disappearingTimer")
            
            // 2. Fetch public keys for ALL participants
            val publicKeysMap = mutableMapOf<String, String>()
            for (userId in participants) {
                if (userId == currentUserId) {
                    val myKey = cryptoManager.getPublicKeyBase64()
                    if (myKey != null) publicKeysMap[userId] = myKey
                } else {
                    val userDoc = firestore.collection("users").document(userId).get().await()
                    val pk = userDoc.getString("publicKey")
                    if (!pk.isNullOrEmpty()) {
                        publicKeysMap[userId] = pk
                    }
                }
            }
            if (publicKeysMap.isEmpty()) {
                throw Exception("Grupta (veya sohbette) şifreleme anahtarı olan kimse yok")
            }
            
            // 3. Resmi boyutlandır (Firestore'un 1MB belge limitini aşmaması için)
            val compressedBytes = compressImageToFitFirestore(imageBytes)
            
            // 4. Encrypt media for group
            val encryptedPayload = cryptoManager.encryptMediaForGroup(compressedBytes, publicKeysMap)
            
            // 5. Firebase Storage kullanmak yerine (limit veya plan hatalarını önlemek için) Base64'e çevirip direkt Firestore'a kaydet
            val base64EncryptedBytes = android.util.Base64.encodeToString(encryptedPayload.encryptedBytes, android.util.Base64.NO_WRAP)
            
            val messageRef = firestore.collection("chats").document(chatId).collection("messages").document()
            val newMessage = MessageDto(
                id = messageRef.id,
                senderId = currentUserId,
                encryptedText = base64EncryptedBytes,
                encryptedAesKeys = encryptedPayload.encryptedAesKeys,
                iv = encryptedPayload.iv,
                messageType = type,
                mediaUrl = "base64", // Indicates that media is inside encryptedText
                expiresAt = if (disappearingTimer != null) System.currentTimeMillis() + disappearingTimer else null,
                replyToMessageId = replyToMessageId
            )
            messageRef.set(newMessage).await()
            firestore.collection("chats").document(chatId).update("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun downloadMedia(messageId: String, mediaUrl: String, encryptedAesKey: String, iv: String): Result<ByteArray> {
        return try {
            // Sadece geriye dönük uyumluluk (Eski yöntem)
            val storageRef = if (mediaUrl.startsWith("http") || mediaUrl.startsWith("gs://")) {
                storage.getReferenceFromUrl(mediaUrl)
            } else {
                storage.reference.child(mediaUrl)
            }
            
            val maxDownloadSize: Long = 10 * 1024 * 1024
            val encryptedBytes = storageRef.getBytes(maxDownloadSize).await()
            
            val decryptedBytes = cryptoManager.decryptMedia(encryptedBytes, encryptedAesKey, iv)
            Result.success(decryptedBytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun markMessagesAsDelivered(chatId: String, messageIds: List<String>): Result<Unit> {
        if (messageIds.isEmpty()) return Result.success(Unit)
        
        return try {
            val batch = firestore.batch()
            val messagesRef = firestore.collection("chats").document(chatId).collection("messages")
            
            for (id in messageIds) {
                val docRef = messagesRef.document(id)
                batch.update(docRef, "isDelivered", true)
            }
            
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun markMessagesAsRead(chatId: String, messageIds: List<String>): Result<Unit> {
        if (messageIds.isEmpty()) return Result.success(Unit)
        
        return try {
            val currentUserId = authRepository.getCurrentUserId() ?: return Result.success(Unit)
            val batch = firestore.batch()
            val messagesRef = firestore.collection("chats").document(chatId).collection("messages")
            
            for (id in messageIds) {
                val docRef = messagesRef.document(id)
                batch.update(
                    docRef,
                    "isRead", true,
                    "readBy", com.google.firebase.firestore.FieldValue.arrayUnion(currentUserId)
                )
            }
            
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateTypingStatus(chatId: String, isTyping: Boolean): Result<Unit> {
        return try {
            val currentUserId = authRepository.getCurrentUserId() ?: return Result.success(Unit)
            firestore.collection("chats").document(chatId)
                .set(mapOf("typingMap" to mapOf(currentUserId to isTyping)), com.google.firebase.firestore.SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeTypingStatus(chatId: String, contactId: String): Flow<Boolean> = callbackFlow {
        val listener = firestore.collection("chats").document(chatId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(false)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val typingMap = snapshot.get("typingMap") as? Map<String, Any>
                    val isContactTyping = typingMap?.get(contactId) as? Boolean ?: false
                    trySend(isContactTyping)
                }
            }
        
        awaitClose { listener.remove() }
    }

    override suspend fun deleteMessage(chatId: String, messageId: String): Result<Unit> {
        return try {
            firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update("isDeleted", true)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteMessageForMe(chatId: String, messageId: String): Result<Unit> {
        return try {
            val currentUserId = authRepository.getCurrentUserId() ?: throw Exception("Not logged in")
            // Update Firestore
            firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update("deletedFor", com.google.firebase.firestore.FieldValue.arrayUnion(currentUserId))
                .await()
            // Delete from local DB immediately for fast UI feedback
            withContext(Dispatchers.IO) {
                messageDao.deleteMessageById(messageId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    override suspend fun editMessage(chatId: String, messageId: String, newText: String): Result<Unit> {
        return try {
            val currentUserId = authRepository.getCurrentUserId() ?: throw Exception("Not logged in")
            
            // 1. Fetch chat document to get participants
            val chatDoc = firestore.collection("chats").document(chatId).get().await()
            val participants = chatDoc.get("participants") as? List<String> ?: throw Exception("Chat not found or invalid")
            val contactId = participants.firstOrNull { it != currentUserId } ?: currentUserId
            
            // 2. Fetch contact's public key
            val contactDoc = firestore.collection("users").document(contactId).get().await()
            val recipientPublicKey = contactDoc.getString("publicKey")
            if (recipientPublicKey.isNullOrEmpty()) {
                throw Exception("Kullanıcı güvenli mesajlaşmayı henüz etkinleştirmedi")
            }
            
            // 3. Encrypt new message
            val encryptedPayload = cryptoManager.encryptMessage(newText, recipientPublicKey)

            // 4. Update in firestore
            firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update(
                    mapOf(
                        "encryptedText" to encryptedPayload.encryptedText,
                        "aesKeyForSender" to encryptedPayload.aesKeyForSender,
                        "aesKeyForRecipient" to encryptedPayload.aesKeyForRecipient,
                        "iv" to encryptedPayload.iv,
                        "isEdited" to true
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    override suspend fun reactToMessage(chatId: String, messageId: String, emoji: String): Result<Unit> {
        return try {
            val currentUserId = authRepository.getCurrentUserId() ?: throw Exception("Not logged in")
            val updateData = if (emoji.isEmpty()) {
                mapOf("reactions.$currentUserId" to com.google.firebase.firestore.FieldValue.delete())
            } else {
                mapOf("reactions.$currentUserId" to emoji)
            }
            
            firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update(updateData)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
