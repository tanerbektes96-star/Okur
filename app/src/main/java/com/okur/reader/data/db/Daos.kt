package com.okur.reader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE originalName = :name AND fileSize = :size LIMIT 1")
    suspend fun findDuplicate(name: String, size: Long): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: BookEntity)

    @Update
    suspend fun update(book: BookEntity)

    @Query("UPDATE books SET progress = :progress, positionIndex = :index, positionFraction = :fraction, totalUnits = :total, lastOpenedAt = :now, isFinished = CASE WHEN :progress >= 0.995 THEN 1 ELSE isFinished END WHERE id = :id")
    suspend fun updatePosition(id: String, progress: Float, index: Int, fraction: Float, total: Int, now: Long)

    @Query("UPDATE books SET readingTimeMs = readingTimeMs + :delta WHERE id = :id")
    suspend fun addReadingTime(id: String, delta: Long)

    @Query("UPDATE books SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: String, fav: Boolean)

    @Query("UPDATE books SET isFinished = :finished WHERE id = :id")
    suspend fun setFinished(id: String, finished: Boolean)

    @Query("UPDATE books SET title = :title, author = :author WHERE id = :id")
    suspend fun rename(id: String, title: String, author: String)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY progress ASC")
    fun observeForBook(bookId: String): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Update
    suspend fun update(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: ReadingSessionEntity)

    @Query("SELECT day, SUM(durationMs) AS total FROM reading_sessions WHERE day >= :fromDay GROUP BY day ORDER BY day")
    fun observeDailyTotals(fromDay: Long): Flow<List<DayTotal>>

    @Query("SELECT DISTINCT day FROM reading_sessions ORDER BY day DESC")
    fun observeActiveDays(): Flow<List<Long>>

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM reading_sessions")
    fun observeTotalTime(): Flow<Long>

    @Query("DELETE FROM reading_sessions WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)
}
