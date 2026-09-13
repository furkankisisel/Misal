package com.example.misal.data.repository

import com.example.misal.domain.model.User
import com.example.misal.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseUserRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : UserRepository {

    override suspend fun searchUserByEmail(email: String): Result<User?> {
        return try {
            val querySnapshot = firestore.collection("users")
                .whereEqualTo("email", email)
                .get()
                .await()

            if (querySnapshot.isEmpty) {
                Result.success(null)
            } else {
                val document = querySnapshot.documents.first()
                val id = document.id
                val name = document.getString("name") ?: ""
                val userEmail = document.getString("email") ?: ""
                val bio = document.getString("bio") ?: "Merhaba, ben Misal kullanıyorum!"
                val profilePictureBase64 = document.getString("profilePictureBase64")
                
                Result.success(User(id = id, name = name, email = userEmail, bio = bio, profilePictureBase64 = profilePictureBase64))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserById(userId: String): Result<User?> {
        return try {
            val document = firestore.collection("users").document(userId).get().await()
            if (document.exists()) {
                val name = document.getString("name") ?: ""
                val userEmail = document.getString("email") ?: ""
                val bio = document.getString("bio") ?: "Merhaba, ben Misal kullanıyorum!"
                val profilePictureBase64 = document.getString("profilePictureBase64")
                Result.success(User(id = userId, name = name, email = userEmail, bio = bio, profilePictureBase64 = profilePictureBase64))
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateProfile(userId: String, name: String, bio: String, profilePictureBase64: String?): Result<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>(
                "name" to name,
                "bio" to bio
            )
            if (profilePictureBase64 != null) {
                updates["profilePictureBase64"] = profilePictureBase64
            } else {
                updates["profilePictureBase64"] = com.google.firebase.firestore.FieldValue.delete()
            }
            firestore.collection("users").document(userId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getUserFlow(userId: String): Flow<Result<User>> = callbackFlow {
        val listener = firestore.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val name = snapshot.getString("name") ?: ""
                    val email = snapshot.getString("email") ?: ""
                    val bio = snapshot.getString("bio") ?: "Merhaba, ben Misal kullanıyorum!"
                    val profilePictureBase64 = snapshot.getString("profilePictureBase64")
                    trySend(Result.success(User(id = userId, name = name, email = email, bio = bio, profilePictureBase64 = profilePictureBase64)))
                }
            }
        awaitClose { listener.remove() }
    }
}
