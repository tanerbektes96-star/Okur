package com.okur.reader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BookEntity::class, BookmarkEntity::class, ReadingSessionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun books(): BookDao
    abstract fun bookmarks(): BookmarkDao
    abstract fun sessions(): SessionDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "okur.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
