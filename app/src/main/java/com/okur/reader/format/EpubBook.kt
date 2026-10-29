package com.okur.reader.format

import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

data class EpubMetadata(
    val title: String?,
    val author: String?,
    val language: String?,
    val cover: ByteArray?,
)

/**
 * Hafif, bağımlılıksız EPUB 2/3 ayrıştırıcısı.
 * container.xml -> OPF (metadata, manifest, spine) -> içindekiler (nav.xhtml ya da toc.ncx)
 */
class EpubBook private constructor(
    private val zip: ZipFile,
    override val title: String,
    override val author: String,
    override val language: String?,
    override val chapters: List<Chapter>,
    override val toc: List<TocEntry>,
    val coverPath: String?,
) : ReflowableBook {

    override fun resource(path: String): BookResource? {
        val entry = zip.getEntry(path) ?: return null
        val bytes = zip.getInputStream(entry).use { it.readBytes() }
        return BookResource(mimeFor(path), bytes)
    }

    override fun close() = zip.close()

    private data class Item(val id: String, val path: String, val mediaType: String, val properties: String)

    private class Opf(
        val title: String?,
        val authors: List<String>,
        val language: String?,
        val items: Map<String, Item>,
        val spine: List<String>,
        val ncxId: String?,
        val coverId: String?,
        val baseDir: String,
    ) {
        val coverPath: String?
            get() {
                coverId?.let { id -> items[id]?.let { return it.path } }
                items.values.firstOrNull { "cover-image" in it.properties }?.let { return it.path }
                return items.values.firstOrNull {
                    it.mediaType.startsWith("image/") && (it.id.contains("cover", true) || it.path.contains("cover", true))
                }?.path
            }
    }

    companion object {
        fun open(file: File): EpubBook {
            val zip = ZipFile(file)
            try {
                val opf = readOpf(zip)
                val spineItems = opf.spine.mapNotNull { opf.items[it] }
                    .filter { it.mediaType.contains("html") || it.path.endsWith("html") || it.path.endsWith("htm") }
                val paths = spineItems.map { it.path }

                val navItem = opf.items.values.firstOrNull { it.properties.split(' ').contains("nav") }
                val ncxItem = opf.ncxId?.let { opf.items[it] }
                    ?: opf.items.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }

                val rawToc = runCatching { navItem?.let { parseNav(zip, it.path) } }.getOrNull()?.takeIf { it.isNotEmpty() }
                    ?: runCatching { ncxItem?.let { parseNcx(zip, it.path) } }.getOrNull()
                    ?: emptyList()

                val toc = rawToc.mapNotNull { (title, href, depth) ->
                    val path = href.substringBefore('#')
                    val idx = paths.indexOf(path)
                    if (idx < 0) null else TocEntry(title.trim().ifEmpty { "Bölüm ${idx + 1}" }, idx, href.substringAfter('#', "").ifEmpty { null }, depth)
                }

                val chapters = spineItems.mapIndexed { i, item ->
                    val title = toc.firstOrNull { it.chapterIndex == i }?.title
                    val size = zip.getEntry(item.path)?.size?.takeIf { it > 0 } ?: 1000L
                    Chapter(item.path, title, size)
                }

                return EpubBook(
                    zip = zip,
                    title = opf.title?.trim().takeUnless { it.isNullOrEmpty() } ?: file.nameWithoutExtension,
                    author = opf.authors.joinToString(", ").ifEmpty { "Bilinmeyen yazar" },
                    language = opf.language,
                    chapters = chapters,
                    toc = toc.ifEmpty {
                        chapters.mapIndexed { i, c -> TocEntry(c.title ?: "Bölüm ${i + 1}", i, null, 0) }
                    },
                    coverPath = opf.coverPath,
                )
            } catch (t: Throwable) {
                zip.close()
                throw t
            }
        }

        fun readMetadata(file: File): EpubMetadata = ZipFile(file).use { zip ->
            val opf = readOpf(zip)
            val cover = opf.coverPath?.let { p -> zip.getEntry(p)?.let { e -> zip.getInputStream(e).use { it.readBytes() } } }
            EpubMetadata(opf.title?.trim(), opf.authors.joinToString(", ").ifEmpty { null }, opf.language, cover)
        }

        private fun parser(input: InputStream): XmlPullParser {
            val p = XmlPullParserFactory.newInstance().newPullParser()
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            runCatching { p.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
            p.setInput(input, null)
            return p
        }

        private fun XmlPullParser.local(): String = name.substringAfter(':').lowercase()

        private fun XmlPullParser.attr(name: String): String? {
            for (i in 0 until attributeCount) {
                val n = getAttributeName(i)
                if (n == name || n.substringAfter(':') == name) return getAttributeValue(i)
            }
            return null
        }

        /** Bir elemanın tüm metin içeriğini (iç içe etiketler dahil) okur. */
        private fun XmlPullParser.readAllText(): String {
            val sb = StringBuilder()
            var depth = 1
            while (depth > 0) {
                when (next()) {
                    XmlPullParser.START_TAG -> depth++
                    XmlPullParser.END_TAG -> depth--
                    XmlPullParser.TEXT, XmlPullParser.ENTITY_REF -> sb.append(text ?: "")
                    XmlPullParser.END_DOCUMENT -> return sb.toString()
                }
            }
            return decodeEntities(sb.toString()).replace(Regex("\\s+"), " ")
        }

        private val entityRegex = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")
        private val namedEntities = mapOf(
            "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
            "mdash" to "—", "ndash" to "–", "hellip" to "…", "laquo" to "«", "raquo" to "»",
            "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”",
        )

        /** Gevşek ayrıştırma modunda çözülmeyen HTML varlıklarını metne çevirir. */
        private fun decodeEntities(text: String): String = entityRegex.replace(text) { m ->
            val v = m.groupValues[1]
            when {
                v.startsWith("#x") -> v.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                v.startsWith("#") -> v.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                else -> namedEntities[v]
            } ?: m.value
        }

        fun resolve(baseDir: String, href: String): String {
            val decoded = Uri.decode(href.substringBefore('#'))
            val fragment = href.substringAfter('#', "")
            val parts = ArrayDeque<String>()
            val combined = if (decoded.startsWith("/")) decoded.drop(1) else baseDir + decoded
            for (seg in combined.split('/')) {
                when (seg) {
                    "", "." -> Unit
                    ".." -> parts.removeLastOrNull()
                    else -> parts.addLast(seg)
                }
            }
            val path = parts.joinToString("/")
            return if (fragment.isNotEmpty()) "$path#$fragment" else path
        }

        private fun readOpf(zip: ZipFile): Opf {
            val containerEntry = zip.getEntry("META-INF/container.xml") ?: error("Geçersiz EPUB: container.xml yok")
            var opfPath: String? = null
            zip.getInputStream(containerEntry).use { input ->
                val p = parser(input)
                while (p.next() != XmlPullParser.END_DOCUMENT) {
                    if (p.eventType == XmlPullParser.START_TAG && p.local() == "rootfile") {
                        opfPath = p.attr("full-path")
                        break
                    }
                }
            }
            val path = opfPath ?: zip.entries().asSequence().firstOrNull { it.name.endsWith(".opf") }?.name
                ?: error("Geçersiz EPUB: OPF bulunamadı")
            val baseDir = if (path.contains('/')) path.substringBeforeLast('/') + "/" else ""

            var title: String? = null
            val authors = mutableListOf<String>()
            var language: String? = null
            var coverId: String? = null
            var ncxId: String? = null
            val items = LinkedHashMap<String, Item>()
            val spine = mutableListOf<String>()

            zip.getInputStream(zip.getEntry(path) ?: error("OPF okunamadı")).use { input ->
                val p = parser(input)
                while (p.next() != XmlPullParser.END_DOCUMENT) {
                    if (p.eventType != XmlPullParser.START_TAG) continue
                    when (p.local()) {
                        "title" -> if (title == null) title = runCatching { p.readAllText() }.getOrNull()
                        "creator" -> runCatching { p.readAllText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { authors += it }
                        "language" -> if (language == null) language = runCatching { p.readAllText().trim() }.getOrNull()
                        "meta" -> if (p.attr("name") == "cover") coverId = p.attr("content")
                        "item" -> {
                            val id = p.attr("id") ?: continue
                            val href = p.attr("href") ?: continue
                            items[id] = Item(id, resolve(baseDir, href), p.attr("media-type").orEmpty(), p.attr("properties").orEmpty())
                        }
                        "spine" -> ncxId = p.attr("toc")
                        "itemref" -> p.attr("idref")?.let { spine += it }
                    }
                }
            }
            return Opf(title, authors, language, items, spine, ncxId, coverId, baseDir)
        }

        private fun parseNcx(zip: ZipFile, path: String): List<Triple<String, String, Int>> {
            val baseDir = if (path.contains('/')) path.substringBeforeLast('/') + "/" else ""
            val result = mutableListOf<Triple<String, String, Int>>()
            zip.getInputStream(zip.getEntry(path) ?: return result).use { input ->
                val p = parser(input)
                var depth = -1
                var pendingTitle = ""
                var inLabel = false
                while (p.next() != XmlPullParser.END_DOCUMENT) {
                    when (p.eventType) {
                        XmlPullParser.START_TAG -> when (p.local()) {
                            "navpoint" -> depth++
                            "navlabel" -> inLabel = true
                            "text" -> if (inLabel) pendingTitle = p.readAllText()
                            "content" -> p.attr("src")?.let { result += Triple(pendingTitle, resolve(baseDir, it), depth.coerceAtLeast(0)) }
                        }
                        XmlPullParser.END_TAG -> when (p.local()) {
                            "navpoint" -> depth--
                            "navlabel" -> inLabel = false
                        }
                    }
                }
            }
            return result
        }

        private fun parseNav(zip: ZipFile, path: String): List<Triple<String, String, Int>> {
            val baseDir = if (path.contains('/')) path.substringBeforeLast('/') + "/" else ""
            val result = mutableListOf<Triple<String, String, Int>>()
            zip.getInputStream(zip.getEntry(path) ?: return result).use { input ->
                val p = parser(input)
                var inToc = false
                var olDepth = 0
                while (p.next() != XmlPullParser.END_DOCUMENT) {
                    when (p.eventType) {
                        XmlPullParser.START_TAG -> when (p.local()) {
                            "nav" -> {
                                val type = p.attr("type").orEmpty()
                                inToc = type.contains("toc") || (type.isEmpty() && result.isEmpty())
                                olDepth = 0
                            }
                            "ol" -> if (inToc) olDepth++
                            "a" -> if (inToc) {
                                val href = p.attr("href")
                                val text = p.readAllText()
                                if (href != null) result += Triple(text, resolve(baseDir, href), (olDepth - 1).coerceAtLeast(0))
                            }
                        }
                        XmlPullParser.END_TAG -> when (p.local()) {
                            "ol" -> if (inToc) olDepth--
                            "nav" -> {
                                if (inToc && result.isNotEmpty()) return result
                                inToc = false
                            }
                        }
                    }
                }
            }
            return result
        }
    }
}
