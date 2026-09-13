package com.example.misal.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.misal.data.local.dao.ChatDao
import com.example.misal.data.local.dao.MessageDao
import com.example.misal.data.local.entity.ChatEntity
import com.example.misal.data.local.entity.MessageEntity

@Database(entities = [ChatEntity::class, MessageEntity::class], version = 9, exportSchema = false)
@androidx.room.TypeConverters(Converters::class)
abstract class MisalDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
}
