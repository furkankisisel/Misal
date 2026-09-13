package com.example.misal.di

import android.content.Context
import androidx.room.Room
import com.example.misal.data.local.MisalDatabase
import com.example.misal.data.local.dao.ChatDao
import com.example.misal.data.local.dao.MessageDao
import com.example.misal.security.CryptoManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return Firebase.auth
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return Firebase.firestore
    }

    @Provides
    @Singleton
    fun provideFirebaseStorage(): com.google.firebase.storage.FirebaseStorage {
        return com.google.firebase.storage.FirebaseStorage.getInstance()
    }

    @Provides
    @Singleton
    fun provideCryptoManager(): CryptoManager {
        return CryptoManager()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MisalDatabase {
        return Room.databaseBuilder(
            context,
            MisalDatabase::class.java,
            "misal_database"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideChatDao(database: MisalDatabase): ChatDao {
        return database.chatDao()
    }

    @Provides
    fun provideMessageDao(database: MisalDatabase): MessageDao {
        return database.messageDao()
    }
}
