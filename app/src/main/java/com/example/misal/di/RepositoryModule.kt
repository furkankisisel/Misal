package com.example.misal.di

import com.example.misal.data.repository.FirebaseAuthRepositoryImpl
import com.example.misal.data.repository.FirebaseChatRepositoryImpl
import com.example.misal.data.repository.FirebaseMessageRepositoryImpl
import com.example.misal.domain.repository.AuthRepository
import com.example.misal.domain.repository.CallRepository
import com.example.misal.data.repository.FirebaseCallRepositoryImpl
import com.example.misal.domain.repository.ChatRepository
import com.example.misal.domain.repository.MessageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindCallRepository(
        impl: FirebaseCallRepositoryImpl
    ): CallRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: FirebaseAuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(
        chatRepositoryImpl: FirebaseChatRepositoryImpl
    ): ChatRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(
        messageRepositoryImpl: FirebaseMessageRepositoryImpl
    ): MessageRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        userRepositoryImpl: com.example.misal.data.repository.FirebaseUserRepositoryImpl
    ): com.example.misal.domain.repository.UserRepository
}

