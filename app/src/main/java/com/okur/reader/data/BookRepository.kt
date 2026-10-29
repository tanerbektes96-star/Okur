package com.okur.reader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.okur.reader.data.db.AppDatabase
import com.okur.reader.data.db.BookEntity
import com.okur.reader.data.db.BookmarkEntity
import com.okur.reader.data.db.DayTotal
import com.okur.reader.data.db.ReadingSessionEntity
import com.okur.reader.format.EpubBook
import com.okur.reader.format.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.UUID

sealed interface ImportResult {
    data class Added(val book: BookEntity) : ImportResult
    data class Duplicate(val book: BookEntity) : ImportResult
    data class Failed(val name: String, val reason: String) : ImportResult
}

class BookRepository(private val context: Context, private val db: AppDatabase) {

    private val booksDir = File(context.filesDir, "books").apply { mkdirs() }
    private val coversDir = File(context.filesDir, "covers").apply { mkdirs() }

    val books: Flow<List<BookEntity>> = db.books().observeAll()
    fun book(id: String): Flow<BookEntity?> = db.books().observe(id)
    suspend fun getBook(id: String): BookEntity? = db.books().get(id)
    fun bookmarks(bookId: String): Flow<List<BookmarkEntity>> = db.bookmarks().observeForBook(bookId)

    fun dailyTotals(fromDay: Long): Flow<List<DayTotal>> = db.sessions().observeDailyTotals(fromDay)
    val activeDays: Flow<List<Long>> = db.sessions().observeActiveDays()
    val totalReadingTime: Flow<Long> = db.sessions().observeTotalTime()

    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "kitap"
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        val format = BookFormat.fromName(name, resolver.getType(uri))
            ?: return@withContext ImportResult.Failed(name, "Desteklenmeyen dosya türü")

        if (size > 0) db.books().findDuplicate(name, size)?.let { return@withContext ImportResult.Duplicate(it) }

        val id = UUID.randomUUID().toString()
        val target = File(booksDir, "$id.${format.name.lowercase()}")
        try {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output, 64 * 1024) }
            } ?: return@withContext ImportResult.Failed(name, "Dosya açılamadı")
            val actualSize = target.length()

            val baseTitle = name.substringBeforeLast('.').replace('_', ' ').trim()
            var title = baseTitle
            var author = "Bilinmeyen yazar"
            var language: String? = null
            var coverPath: String? = null
            var units = 0

            when (format) {
                BookFormat.EPUB -> {
                    val meta = EpubBook.readMetadata(target)
                    meta.title?.takeIf { it.isNotBlank() }?.let { title = it }
                    meta.author?.takeIf { it.isNotBlank() }?.let { author = it }
                    language = meta.language
                    meta.cover?.let { bytes -> coverPath = saveCover(id, bytes) }
                }
                BookFormat.PDF -> {
                    PdfDocument(target).use { pdf ->
                        units = pdf.pageCount
                        pdf.render(0, 420)?.let { bmp -> coverPath = saveCover(id, bmp) }
                    }
                }
                BookFormat.TXT -> Unit
            }

            val book = BookEntity(
                id = id,
                title = title,
                author = author,
                format = format.name,
                filePath = target.absolutePath,
                coverPath = coverPath,
                fileSize = if (size > 0) size else actualSize,
                originalName = name,
                language = language,
                addedAt = System.currentTimeMillis(),
                totalUnits = units,
            )
            db.books().insert(book)
            ImportResult.Added(book)
        } catch (t: Throwable) {
            target.delete()
            ImportResult.Failed(name, t.message ?: "Bilinmeyen hata")
        }
    }

    private fun saveCover(id: String, bytes: ByteArray): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 420) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        return saveCover(id, bmp)
    }

    private fun saveCover(id: String, bitmap: Bitmap): String {
        val file = File(coversDir, "$id.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return file.absolutePath
    }

    suspend fun delete(book: BookEntity) = withContext(Dispatchers.IO) {
        File(book.filePath).delete()
        book.coverPath?.let { File(it).delete() }
        db.bookmarks().deleteForBook(book.id)
        db.books().delete(book.id)
    }

    suspend fun setFavorite(id: String, fav: Boolean) = db.books().setFavorite(id, fav)
    suspend fun setFinished(id: String, finished: Boolean) = db.books().setFinished(id, finished)
    suspend fun rename(id: String, title: String, author: String) = db.books().rename(id, title, author)

    suspend fun savePosition(id: String, progress: Float, index: Int, fraction: Float, total: Int) =
        db.books().updatePosition(id, progress, index, fraction, total, System.currentTimeMillis())

    suspend fun recordSession(bookId: String, startedAt: Long, durationMs: Long) {
        if (durationMs < 5_000) return
        db.sessions().insert(ReadingSessionEntity(bookId = bookId, day = LocalDate.now().toEpochDay(), startedAt = startedAt, durationMs = durationMs))
        db.books().addReadingTime(bookId, durationMs)
    }

    suspend fun addBookmark(bookmark: BookmarkEntity) = db.bookmarks().insert(bookmark)
    suspend fun updateBookmark(bookmark: BookmarkEntity) = db.bookmarks().update(bookmark)
    suspend fun deleteBookmark(id: Long) = db.bookmarks().delete(id)
}
