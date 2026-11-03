package com.okur.reader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
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
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
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

            val info = readBookInfo(format, target, id)
            info.title?.let { title = it }
            info.author?.let { author = it }
            language = info.language
            coverPath = info.coverPath
            units = info.units

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

    private class BookInfo(val title: String?, val author: String?, val language: String?, val coverPath: String?, val units: Int)

    /** Dosyadan başlık, yazar, dil, kapak ve sayfa sayısını çıkarır. */
    private suspend fun readBookInfo(format: BookFormat, file: File, id: String): BookInfo = when (format) {
        BookFormat.EPUB -> {
            val meta = EpubBook.readMetadata(file)
            BookInfo(
                meta.title?.takeIf { it.isNotBlank() },
                meta.author?.takeIf { it.isNotBlank() },
                meta.language,
                meta.cover?.let { bytes -> saveCover(id, bytes) },
                0,
            )
        }
        BookFormat.PDF -> PdfDocument(file).use { pdf ->
            BookInfo(null, null, null, pdf.render(0, 420)?.let { bmp -> saveCover(id, bmp) }, pdf.pageCount)
        }
        BookFormat.TXT -> BookInfo(null, null, null, null, 0)
    }

    suspend fun bookmarksOnce(bookId: String): List<BookmarkEntity> = db.bookmarks().list(bookId)

    /**
     * Yakındaki paylaşımla gelen kitabı kitaplığa ekler. Kitap zaten varsa dosya tekrar kopyalanmaz,
     * yalnızca yeni yer imleri ve notlar eklenir.
     */
    suspend fun importReceived(meta: JSONObject, file: ParcelFileDescriptor, cover: ParcelFileDescriptor?): ImportResult =
        withContext(Dispatchers.IO) {
            val name = meta.optString("originalName", "kitap")
            val size = meta.optLong("fileSize", -1)
            val format = BookFormat.entries.firstOrNull { it.name == meta.optString("format") }
                ?: return@withContext ImportResult.Failed(name, "Desteklenmeyen biçim")
            val existing = if (size > 0) db.books().findDuplicate(name, size) else null
            val book = existing ?: run {
                val id = UUID.randomUUID().toString()
                val target = File(booksDir, "$id.${format.name.lowercase()}")
                try {
                    FileInputStream(file.fileDescriptor).use { input -> FileOutputStream(target).use { input.copyTo(it, 64 * 1024) } }
                    var coverPath: String? = null
                    if (cover != null) {
                        val coverFile = File(coversDir, "$id.jpg")
                        FileInputStream(cover.fileDescriptor).use { input -> FileOutputStream(coverFile).use { input.copyTo(it) } }
                        if (coverFile.length() > 0) coverPath = coverFile.absolutePath
                    }
                    val info = readBookInfo(format, target, id)
                    if (coverPath == null) coverPath = info.coverPath
                    val entity = BookEntity(
                        id = id,
                        title = meta.optString("title").ifBlank { info.title ?: name.substringBeforeLast('.') },
                        author = meta.optString("author").ifBlank { info.author ?: "Bilinmeyen yazar" },
                        format = format.name,
                        filePath = target.absolutePath,
                        coverPath = coverPath,
                        fileSize = if (size > 0) size else target.length(),
                        originalName = name,
                        language = meta.optString("language").ifBlank { null } ?: info.language,
                        addedAt = System.currentTimeMillis(),
                        totalUnits = info.units,
                    )
                    db.books().insert(entity)
                    entity
                } catch (t: Throwable) {
                    target.delete()
                    return@withContext ImportResult.Failed(name, t.message ?: "Kopyalanamadı")
                }
            }
            val marks = meta.optJSONArray("bookmarks")
            if (marks != null) {
                val current = db.bookmarks().list(book.id)
                for (i in 0 until marks.length()) {
                    val m = marks.getJSONObject(i)
                    val index = m.optInt("positionIndex")
                    val fraction = m.optDouble("positionFraction").toFloat()
                    val note = m.optString("note")
                    val same = current.any { it.positionIndex == index && kotlin.math.abs(it.positionFraction - fraction) < 0.002f && it.note == note }
                    if (!same) {
                        db.bookmarks().insert(
                            BookmarkEntity(
                                bookId = book.id,
                                positionIndex = index,
                                positionFraction = fraction,
                                progress = m.optDouble("progress").toFloat(),
                                label = m.optString("label"),
                                snippet = m.optString("snippet"),
                                note = note,
                                createdAt = m.optLong("createdAt", System.currentTimeMillis()),
                            )
                        )
                    }
                }
            }
            if (existing != null) ImportResult.Duplicate(existing) else ImportResult.Added(book)
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
