package com.okur.reader.format

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Düz metin dosyalarını bölümlere ayırarak EPUB ile aynı okuyucuda gösterir.
 * "Bölüm 1", "Chapter II" gibi başlıklar otomatik olarak içindekilere dönüşür.
 */
class TxtBook private constructor(
    override val title: String,
    private val sections: List<Pair<String?, List<String>>>,
) : ReflowableBook {

    override val author: String = "Bilinmeyen yazar"
    override val language: String? = null

    override val chapters: List<Chapter> = sections.mapIndexed { i, (heading, paragraphs) ->
        Chapter("txt/ch$i.html", heading, paragraphs.sumOf { it.length.toLong() }.coerceAtLeast(1))
    }

    override val toc: List<TocEntry> = chapters.mapIndexed { i, c -> TocEntry(c.title ?: "Bölüm ${i + 1}", i, null, 0) }

    private val htmlCache = HashMap<Int, ByteArray>()

    override fun resource(path: String): BookResource? {
        val index = chapterIndexOf(path)
        if (index < 0) return null
        val bytes = synchronized(htmlCache) { htmlCache.getOrPut(index) { buildHtml(index).toByteArray() } }
        return BookResource("text/html", bytes)
    }

    override fun chapterPlainText(index: Int): String = sections[index].second.joinToString("\n")

    private fun buildHtml(index: Int): String {
        val (heading, paragraphs) = sections[index]
        return buildString {
            append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>")
            append(escape(heading ?: title))
            append("</title></head><body>")
            if (heading != null) append("<h2>").append(escape(heading)).append("</h2>")
            paragraphs.forEach { append("<p>").append(escape(it)).append("</p>") }
            append("</body></html>")
        }
    }

    override fun close() = Unit

    companion object {
        private const val TARGET_CHUNK = 30_000
        private val headingRegex = Regex(
            "^(bölüm|bolum|kısım|kisim|kitap|chapter|part|book|prologue|epilogue|önsöz|sonsöz|giriş)\\b.*|^[IVXLCDM]+\\.?$|^\\d{1,3}\\.?$",
            RegexOption.IGNORE_CASE,
        )

        fun open(file: File, title: String): TxtBook {
            val text = decode(file.readBytes()).replace("\r\n", "\n").replace('\r', '\n')
            val lines = text.split('\n')

            // Paragrafları oluştur: boş satırlar paragraf ayırıcıdır; hiç boş satır yoksa her satır bir paragraftır.
            val hasBlankLines = lines.count { it.isBlank() } > lines.size / 20
            val paragraphs = mutableListOf<String>()
            val current = StringBuilder()
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || !hasBlankLines) {
                    if (!hasBlankLines && trimmed.isNotEmpty()) current.append(trimmed)
                    if (current.isNotEmpty()) {
                        paragraphs += current.toString(); current.clear()
                    }
                } else {
                    if (current.isNotEmpty()) current.append(' ')
                    current.append(trimmed)
                }
            }
            if (current.isNotEmpty()) paragraphs += current.toString()

            val sections = mutableListOf<Pair<String?, MutableList<String>>>()
            var heading: String? = null
            var body = mutableListOf<String>()
            var size = 0
            fun flush() {
                if (body.isNotEmpty() || heading != null) sections += heading to body
                heading = null; body = mutableListOf(); size = 0
            }
            for (p in paragraphs) {
                if (p.length <= 80 && headingRegex.matches(p)) {
                    flush()
                    heading = p
                    continue
                }
                // Çok uzun bölümleri akıcılık için parçalara böl
                if (size > TARGET_CHUNK) flush()
                body += p
                size += p.length
            }
            flush()
            if (sections.isEmpty()) sections += null to mutableListOf("")
            return TxtBook(title, sections)
        }

        /** UTF-8 dener, olmazsa Türkçe Windows kodlamasına (windows-1254) düşer. */
        fun decode(bytes: ByteArray): String {
            val utf8 = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return try {
                utf8.decode(ByteBuffer.wrap(bytes)).toString().removePrefix("﻿")
            } catch (e: CharacterCodingException) {
                val fallback = runCatching { Charset.forName("windows-1254") }.getOrDefault(Charsets.ISO_8859_1)
                String(bytes, fallback)
            }
        }

        private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }
}
