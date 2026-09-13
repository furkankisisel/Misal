package com.example.misal.domain.repository

import com.example.misal.domain.model.User

interface UserRepository {
    suspend fun searchUserByEmail(email: String): Result<User?>
    suspend fun updateProfile(userId: String, name: String, bio: String, profilePictureBase64: String?): Result<Unit>
    suspend fun getUserById(userId: String): Result<User?>
    fun getUserFlow(userId: String): kotlinx.coroutines.flow.Flow<Result<User>>
}
