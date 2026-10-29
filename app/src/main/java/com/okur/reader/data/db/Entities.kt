package com.okur.reader.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.okur.reader.data.BookFormat

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val format: String,
    val filePath: String,
    val coverPath: String?,
    val fileSize: Long,
    val originalName: String,
    val language: String?,
    val addedAt: Long,
    val lastOpenedAt: Long? = null,
    /** 0..1 arası toplam ilerleme */
    val progress: Float = 0f,
    /** EPUB/TXT: bölüm indeksi, PDF: sayfa indeksi */
    val positionIndex: Int = 0,
    /** Bölüm içindeki konum (0..1) */
    val positionFraction: Float = 0f,
    /** PDF sayfa sayısı ya da bölüm sayısı */
    val totalUnits: Int = 0,
    val isFavorite: Boolean = false,
    val isFinished: Boolean = false,
    val readingTimeMs: Long = 0,
)

val BookEntity.bookFormat: BookFormat get() = BookFormat.valueOf(format)

@Entity(
    tableName = "bookmarks",
    indices = [Index("bookId")],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val positionIndex: Int,
    val positionFraction: Float,
    val progress: Float,
    val label: String,
    val snippet: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "reading_sessions",
    indices = [Index("day"), Index("bookId")],
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    /** LocalDate.toEpochDay() */
    val day: Long,
    val startedAt: Long,
    val durationMs: Long,
)

data class DayTotal(
    @ColumnInfo(name = "day") val day: Long,
    @ColumnInfo(name = "total") val total: Long,
)
