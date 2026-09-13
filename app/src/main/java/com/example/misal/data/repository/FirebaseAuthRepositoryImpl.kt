package com.example.misal.data.repository

import com.example.misal.domain.model.User
import com.example.misal.domain.repository.AuthRepository
import com.example.misal.security.CryptoManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseAuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val cryptoManager: CryptoManager
) : AuthRepository {

    override val currentUser: Flow<User?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            if (firebaseUser == null) {
                trySend(null)
            } else {
                firestore.collection("users").document(firebaseUser.uid).get()
                    .addOnSuccessListener { document ->
                        val name = document.getString("name") ?: ""
                        val firestorePublicKey = document.getString("publicKey") ?: ""
                        
                        cryptoManager.generateKeyPairIfNeeded()
                        val localPublicKey = cryptoManager.getPublicKeyBase64() ?: ""
                        
                        if (localPublicKey.isNotEmpty() && firestorePublicKey != localPublicKey) {
                            firestore.collection("users").document(firebaseUser.uid).update("publicKey", localPublicKey)
                        }
                        
                        trySend(User(id = firebaseUser.uid, email = firebaseUser.email ?: "", name = name, publicKey = localPublicKey))
                    }
                    .addOnFailureListener {
                        trySend(User(id = firebaseUser.uid, email = firebaseUser.email ?: "", name = ""))
                    }
            }
        }
        
        auth.addAuthStateListener(listener)
        
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signInWithEmailAndPassword(email: String, password: String): Result<Unit> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val userId = result.user?.uid ?: throw Exception("User is null")
            
            // Eğer cihazda anahtar yoksa yeni bir tane üret
            cryptoManager.generateKeyPairIfNeeded()
            val publicKey = cryptoManager.getPublicKeyBase64() ?: ""
            
            // Üretilen açık anahtarı Firestore'a kaydet (Güncelle)
            firestore.collection("users").document(userId).update("publicKey", publicKey).await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signUpWithEmailAndPassword(email: String, password: String, name: String): Result<Unit> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val userId = result.user?.uid ?: throw Exception("User is null")
            
            // Cihazda anahtar oluştur
            cryptoManager.generateKeyPairIfNeeded()
            val publicKey = cryptoManager.getPublicKeyBase64() ?: ""
            
            // Save user profile to Firestore
            val user = User(id = userId, email = email, name = name, publicKey = publicKey)
            firestore.collection("users").document(userId).set(user).await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return try {
            auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    override suspend fun updateUserPresence(isOnline: Boolean): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.success(Unit)
            val updates = mutableMapOf<String, Any>(
                "isOnline" to isOnline
            )
            if (!isOnline) {
                updates["lastSeen"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
            }
            firestore.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
