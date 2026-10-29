package com.okur.reader.data

import androidx.compose.ui.graphics.Color

enum class BookFormat(val label: String) {
    EPUB("EPUB"), PDF("PDF"), TXT("TXT");

    companion object {
        fun fromName(name: String?, mime: String?): BookFormat? {
            val lower = name?.lowercase().orEmpty()
            return when {
                lower.endsWith(".epub") || mime == "application/epub+zip" -> EPUB
                lower.endsWith(".pdf") || mime == "application/pdf" -> PDF
                lower.endsWith(".txt") || mime == "text/plain" -> TXT
                else -> null
            }
        }
    }
}

/** Okuma ekranı temaları. Göz yorgunluğunu azaltmak için düşük kontrastlı seçenekler de var. */
enum class ReaderTheme(val label: String, val background: Color, val text: Color, val link: Color, val isDark: Boolean) {
    LIGHT("Beyaz", Color(0xFFFFFFFF), Color(0xFF1F1F1F), Color(0xFF1565C0), false),
    PAPER("Kağıt", Color(0xFFF8F1E7), Color(0xFF2E2A25), Color(0xFF8C4A2F), false),
    SEPIA("Sepya", Color(0xFFF1E3C8), Color(0xFF4A3B2A), Color(0xFF7A4A1E), false),
    MINT("Nane", Color(0xFFE3F0E3), Color(0xFF253325), Color(0xFF2E6B3A), false),
    DUSK("Alacakaranlık", Color(0xFF3A3A40), Color(0xFFDADAD6), Color(0xFF9FC3FF), true),
    DARK("Koyu", Color(0xFF1C1B1A), Color(0xFFCFC8BE), Color(0xFFE0A458), true),
    BLACK("Siyah (AMOLED)", Color(0xFF000000), Color(0xFFB8B2A8), Color(0xFFE0A458), true);
}

enum class FontChoice(val label: String, val css: String?) {
    ORIGINAL("Yayıncı", null),
    SERIF("Serif", "'Noto Serif', Georgia, serif"),
    SANS("Sans", "Roboto, 'Noto Sans', sans-serif"),
    SLAB("Slab", "'Roboto Slab', 'Noto Serif', serif"),
    MONO("Mono", "'Roboto Mono', monospace");
}

enum class PageMode(val label: String) { PAGED("Sayfa çevirme"), SCROLL("Kaydırma") }

/** Sayfa çevirme animasyonu */
enum class PageTurn(val label: String) { CURL("Kıvrılma"), SLIDE("Kaydırma"), NONE("Animasyonsuz") }

enum class ThemeMode(val label: String) { SYSTEM("Sistem"), LIGHT("Açık"), DARK("Koyu") }

enum class FilterMode(val label: String) { OFF("Kapalı"), ON("Her zaman"), SCHEDULE("Zamanlanmış") }

enum class OrientationLock(val label: String) { AUTO("Otomatik"), PORTRAIT("Dikey"), LANDSCAPE("Yatay") }

enum class SortOrder(val label: String) {
    RECENT("Son okunan"), ADDED("Eklenme tarihi"), TITLE("Başlık"), AUTHOR("Yazar"), PROGRESS("İlerleme")
}

data class ReaderPrefs(
    val theme: ReaderTheme = ReaderTheme.PAPER,
    val fontSize: Int = 19,
    val font: FontChoice = FontChoice.SERIF,
    val lineHeight: Float = 1.6f,
    val margin: Int = 22,
    val justify: Boolean = true,
    val hyphenate: Boolean = true,
    val pageMode: PageMode = PageMode.PAGED,
    val pdfPageMode: PageMode = PageMode.SCROLL,
    val dualPage: Boolean = true,
    val brightness: Float = -1f,
    val volumeKeys: Boolean = true,
    val tapZones: Boolean = true,
    val keepScreenOn: Boolean = true,
    val orientation: OrientationLock = OrientationLock.AUTO,
    val pdfFollowTheme: Boolean = true,
    val ttsRate: Float = 1f,
    val ttsPitch: Float = 1f,
    val ttsVoice: String = "",
    val pageTurn: PageTurn = PageTurn.CURL,
    val tapGuideShown: Boolean = false,
)

data class EyePrefs(
    val filterMode: FilterMode = FilterMode.SCHEDULE,
    val filterStrength: Float = 0.35f,
    val scheduleStart: Int = 21,
    val scheduleEnd: Int = 7,
    val extraDim: Float = 0f,
    val breakReminder: Boolean = true,
    val breakIntervalMin: Int = 20,
    val smartEye: Boolean = false,
    val smartStay: Boolean = true,
    val distanceAlert: Boolean = true,
    val blinkReminder: Boolean = true,
    val pauseWhenAway: Boolean = true,
    val autoNightTheme: Boolean = false,
)

data class AppPrefs(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val gridView: Boolean = true,
    val sort: SortOrder = SortOrder.RECENT,
    val dailyGoalMin: Int = 30,
)
