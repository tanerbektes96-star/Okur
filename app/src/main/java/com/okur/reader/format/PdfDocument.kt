package com.okur.reader.format

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors

/**
 * PdfRenderer iş parçacığı güvenli değildir; tüm işlemler tek bir arka plan iş parçacığında yürütülür.
 * Oluşturulan sayfalar bellek boyutuna göre sınırlı bir LRU önbellekte tutulur.
 */
class PdfDocument(file: File) : Closeable {
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    private val aspects = HashMap<Int, Float>()

    @Volatile
    private var closed = false

    val pageCount: Int = renderer.pageCount

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 5).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cached(index: Int, width: Int): Bitmap? = cache.get("$index@$width")

    /** Sayfanın yükseklik/genişlik oranı (bilinmiyorsa null). */
    fun knownAspect(index: Int): Float? = synchronized(aspects) { aspects[index] }

    suspend fun aspect(index: Int): Float = knownAspect(index) ?: withContext(dispatcher) {
        if (closed) return@withContext 1.414f
        renderer.openPage(index).use { page ->
            val a = page.height.toFloat() / page.width.toFloat()
            synchronized(aspects) { aspects[index] = a }
            a
        }
    }

    suspend fun render(index: Int, width: Int): Bitmap? {
        val key = "$index@$width"
        cache.get(key)?.let { return it }
        return withContext(dispatcher) {
            if (closed || index !in 0 until pageCount) return@withContext null
            cache.get(key)?.let { return@withContext it }
            renderer.openPage(index).use { page ->
                val aspect = page.height.toFloat() / page.width.toFloat()
                synchronized(aspects) { aspects[index] = aspect }
                val w = width.coerceIn(64, 2600)
                val h = (w * aspect).toInt().coerceIn(64, 4000)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                cache.put(key, bitmap)
                bitmap
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        executor.execute {
            runCatching { renderer.close() }
            runCatching { fd.close() }
            cache.evictAll()
        }
        executor.shutdown()
    }
}
