package com.example.misal.domain.repository

import kotlinx.coroutines.flow.Flow
import com.example.misal.domain.model.User

interface AuthRepository {
    val currentUser: Flow<User?>
    
    suspend fun signInWithEmailAndPassword(email: String, password: String): Result<Unit>
    suspend fun signUpWithEmailAndPassword(email: String, password: String, name: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
    
    fun getCurrentUserId(): String?
    suspend fun updateUserPresence(isOnline: Boolean): Result<Unit>
}
