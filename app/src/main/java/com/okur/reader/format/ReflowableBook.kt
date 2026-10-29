package com.okur.reader.format

import android.text.Html
import java.io.Closeable

data class Chapter(val path: String, val title: String?, val weight: Long)

data class TocEntry(val title: String, val chapterIndex: Int, val anchor: String?, val depth: Int)

class BookResource(val mime: String, val bytes: ByteArray)

/** EPUB ve TXT gibi yeniden akıtılabilir (reflowable) kitaplar için ortak arayüz. */
interface ReflowableBook : Closeable {
    val title: String
    val author: String
    val language: String?
    val chapters: List<Chapter>
    val toc: List<TocEntry>

    /** Kitap kökünden göreli yol için içeriği döndürür. */
    fun resource(path: String): BookResource?

    fun chapterIndexOf(path: String): Int = chapters.indexOfFirst { it.path == path }

    fun chapterPlainText(index: Int): String {
        val res = resource(chapters[index].path) ?: return ""
        return htmlToPlainText(String(res.bytes, Charsets.UTF_8))
    }

    /** Bölüm ağırlıklarına göre (bölüm, bölüm-içi oran) -> toplam ilerleme. */
    fun overallProgress(index: Int, fraction: Float): Float {
        val total = chapters.sumOf { it.weight }.coerceAtLeast(1)
        val before = chapters.take(index).sumOf { it.weight }
        val current = chapters.getOrNull(index)?.weight ?: 0
        return ((before + current * fraction.coerceIn(0f, 1f)) / total.toFloat()).coerceIn(0f, 1f)
    }

    /** Toplam ilerleme -> (bölüm, bölüm-içi oran). */
    fun positionFor(progress: Float): Pair<Int, Float> {
        val total = chapters.sumOf { it.weight }.coerceAtLeast(1)
        val target = (progress.coerceIn(0f, 1f) * total).toLong()
        var acc = 0L
        chapters.forEachIndexed { i, c ->
            if (target < acc + c.weight || i == chapters.lastIndex) {
                val f = if (c.weight == 0L) 0f else (target - acc).toFloat() / c.weight
                return i to f.coerceIn(0f, 1f)
            }
            acc += c.weight
        }
        return 0 to 0f
    }

    fun titleForChapter(index: Int): String? =
        chapters.getOrNull(index)?.title ?: toc.lastOrNull { it.chapterIndex <= index }?.title
}

private val headRegex = Regex("(?is)<head.*?</head>|<script.*?</script>|<style.*?</style>")

fun htmlToPlainText(html: String): String {
    val cleaned = html.replace(headRegex, " ")
    @Suppress("DEPRECATION")
    val text = if (android.os.Build.VERSION.SDK_INT >= 24) {
        Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY).toString()
    } else {
        Html.fromHtml(cleaned).toString()
    }
    return text.replace('￼', ' ')
}

fun mimeFor(path: String): String {
    val ext = path.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "html", "htm", "xhtml", "xht" -> "text/html"
        "css" -> "text/css"
        "js" -> "application/javascript"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "ttf" -> "font/ttf"
        "otf" -> "font/otf"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "mp3" -> "audio/mpeg"
        "mp4" -> "video/mp4"
        "xml", "ncx", "opf" -> "application/xml"
        else -> "application/octet-stream"
    }
}
