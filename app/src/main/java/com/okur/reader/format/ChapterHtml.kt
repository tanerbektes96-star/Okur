package com.okur.reader.format

private val voidTags = setOf(
    "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr",
)
private val selfClosing = Regex("<([a-zA-Z][a-zA-Z0-9:-]*)(\\s[^<>]*?)?\\s*/>")
private val headClose = Regex("</head\\s*>", RegexOption.IGNORE_CASE)
private val htmlOpen = Regex("<html[^>]*>", RegexOption.IGNORE_CASE)
private val xmlProlog = Regex("^\\uFEFF?\\s*<\\?xml[^>]*\\?>")

/**
 * XHTML bölümlerini HTML ayrıştırıcısına uygun hale getirir ve okuma motorunu enjekte eder.
 * (<title/> gibi kendiliğinden kapanan etiketler HTML'de belgenin geri kalanını yutabilir.)
 */
fun prepareChapterHtml(raw: String, scriptUrl: String): String {
    var html = raw.replaceFirst(xmlProlog, "")
    html = selfClosing.replace(html) { m ->
        val tag = m.groupValues[1]
        if (tag.lowercase() in voidTags) m.value else "<$tag${m.groupValues[2]}></$tag>"
    }
    val tags = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no\">" +
        "<script src=\"$scriptUrl\"></script>"
    headClose.find(html)?.let { return html.substring(0, it.range.first) + tags + html.substring(it.range.first) }
    htmlOpen.find(html)?.let { return html.substring(0, it.range.last + 1) + "<head>$tags</head>" + html.substring(it.range.last + 1) }
    return "<!DOCTYPE html><html><head>$tags</head><body>$html</body></html>"
}
