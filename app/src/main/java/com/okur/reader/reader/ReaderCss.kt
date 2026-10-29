package com.okur.reader.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.okur.reader.data.ReaderPrefs
import com.okur.reader.data.ReaderTheme

/** Okuyucu teması ve tipografisi için WebView'e enjekte edilen CSS. */
object ReaderCss {

    fun hex(color: Color): String = String.format("#%06X", 0xFFFFFF and color.toArgb())

    fun build(p: ReaderPrefs, theme: ReaderTheme): String {
        val bg = hex(theme.background)
        val fg = hex(theme.text)
        val link = hex(theme.link)
        return buildString {
            append("html,body{background:$bg !important;color:$fg !important;-webkit-text-size-adjust:100% !important;}")
            append("html{font-size:${p.fontSize}px !important;}")
            append("body{font-size:${p.fontSize}px !important;line-height:${p.lineHeight} !important;overflow-wrap:break-word;word-wrap:break-word;-webkit-tap-highlight-color:transparent;}")
            append("body *{color:inherit !important;background-color:transparent !important;border-color:${fg}33 !important;}")
            append("p,li,blockquote,dd,dt,td,th,div,span{line-height:${p.lineHeight} !important;}")
            p.font.css?.let {
                append("body,p,div,span,li,blockquote,td,th,a,em,i,b,strong,small,h1,h2,h3,h4,h5,h6{font-family:$it !important;}")
            }
            if (p.justify) append("p,li,blockquote{text-align:justify !important;}")
            if (p.hyphenate) {
                append("p,li,blockquote{-webkit-hyphens:auto !important;hyphens:auto !important;}")
            } else {
                append("p,li,blockquote{-webkit-hyphens:manual !important;hyphens:manual !important;}")
            }
            append("a,a *{color:$link !important;text-decoration:none !important;}")
            append("img,svg,video{max-width:100% !important;height:auto;object-fit:contain;break-inside:avoid;-webkit-column-break-inside:avoid;}")
            append("h1,h2,h3,h4,h5,h6{break-after:avoid;-webkit-column-break-after:avoid;line-height:1.3 !important;}")
            append("p{orphans:2;widows:2;}")
            append("pre,code{white-space:pre-wrap !important;}")
            append("table{max-width:100% !important;}")
            if (theme.isDark) append("img{filter:brightness(0.82) contrast(1.06);}")
            append("mark.okur-hl{background-color:#FFC94D99 !important;color:inherit !important;border-radius:3px;}")
            append("::selection{background:${link}55;}")
            append("html.okur-loading body{opacity:0 !important;}")
            append("body{transition:opacity .12s ease-out;}")
            append(".okur-chapter-nav{display:flex;justify-content:center;margin:28px 0 !important;}")
            append(".okur-chapter-nav button{font:inherit;font-size:0.9em;color:$link !important;border:1.5px solid ${link}88 !important;border-radius:24px;padding:10px 22px;background:transparent !important;}")
        }
    }
}
