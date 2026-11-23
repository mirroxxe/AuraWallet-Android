package com.aurawallet.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Локальна SQLite-база застосунку: файл `aura.db` у приватній пам'яті застосунку. */
@Database(entities = [HistoryEntity::class], version = 1, exportSchema = true)
abstract class AuraDatabase : RoomDatabase() {
    abstract fun history(): HistoryDao

    companion object {
        @Volatile private var instance: AuraDatabase? = null

        fun get(context: Context): AuraDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AuraDatabase::class.java, "aura.db")
                .build().also { instance = it }
        }
    }
}
