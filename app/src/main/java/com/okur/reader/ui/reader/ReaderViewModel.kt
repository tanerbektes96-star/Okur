package com.okur.reader.ui.reader

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okur.reader.AppContainer
import com.okur.reader.data.BookFormat
import com.okur.reader.data.db.BookEntity
import com.okur.reader.data.db.BookmarkEntity
import com.okur.reader.data.db.bookFormat
import com.okur.reader.format.EpubBook
import com.okur.reader.format.PdfDocument
import com.okur.reader.format.ReflowableBook
import com.okur.reader.format.TxtBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs

sealed interface ReaderContent {
    data object Loading : ReaderContent
    data class Reflow(val book: ReflowableBook) : ReaderContent
    data class Pdf(val doc: PdfDocument) : ReaderContent
    data class Error(val message: String) : ReaderContent
}

data class SearchTarget(val query: String, val nth: Int)

data class ChapterRequest(
    val index: Int,
    val fraction: Float = 0f,
    val anchor: String? = null,
    val atEnd: Boolean = false,
    val search: SearchTarget? = null,
    val token: Long = SystemClock.uptimeMillis(),
)

data class SearchHit(val chapterIndex: Int, val nth: Int, val snippet: String, val chapterTitle: String)

data class ReflowPosition(val chapter: Int = 0, val page: Int = 0, val pages: Int = 1, val fraction: Float = 0f)

data class SearchState(val query: String = "", val running: Boolean = false, val hits: List<SearchHit> = emptyList(), val done: Boolean = false)

class ReaderViewModel(private val container: AppContainer, val bookId: String) : ViewModel() {
    private val repo = container.books

    private val _book = MutableStateFlow<BookEntity?>(null)
    val book: StateFlow<BookEntity?> = _book

    private val _content = MutableStateFlow<ReaderContent>(ReaderContent.Loading)
    val content: StateFlow<ReaderContent> = _content

    private val _request = MutableStateFlow<ChapterRequest?>(null)
    val request: StateFlow<ChapterRequest?> = _request

    private val _reflowPos = MutableStateFlow(ReflowPosition())
    val reflowPos: StateFlow<ReflowPosition> = _reflowPos

    private val _pdfPage = MutableStateFlow(0)
    val pdfPage: StateFlow<Int> = _pdfPage

    /** PDF'de sayfaya atlama isteği (kaydırıcı, içindekiler, yer imi) */
    private val _pdfJump = MutableStateFlow<Pair<Int, Long>?>(null)
    val pdfJump: StateFlow<Pair<Int, Long>?> = _pdfJump

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _chrome = MutableStateFlow(true)
    val chromeVisible: StateFlow<Boolean> = _chrome

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search

    val bookmarks: StateFlow<List<BookmarkEntity>> =
        repo.bookmarks(bookId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val events = Channel<String>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private var saveJob: Job? = null
    private var searchJob: Job? = null
    private var sessionStart = 0L
    private var sessionWall = 0L

    init {
        viewModelScope.launch {
            val entity = repo.getBook(bookId)
            if (entity == null) {
                _content.value = ReaderContent.Error("Kitap bulunamadı")
                return@launch
            }
            _book.value = entity
            _progress.value = entity.progress
            try {
                val file = File(entity.filePath)
                val content = withContext(Dispatchers.IO) {
                    when (entity.bookFormat) {
                        BookFormat.EPUB -> ReaderContent.Reflow(EpubBook.open(file))
                        BookFormat.TXT -> ReaderContent.Reflow(TxtBook.open(file, entity.title))
                        BookFormat.PDF -> ReaderContent.Pdf(PdfDocument(file))
                    }
                }
                when (content) {
                    is ReaderContent.Reflow -> {
                        val idx = entity.positionIndex.coerceIn(0, (content.book.chapters.size - 1).coerceAtLeast(0))
                        _reflowPos.value = ReflowPosition(chapter = idx, fraction = entity.positionFraction)
                        _request.value = ChapterRequest(idx, entity.positionFraction)
                    }
                    is ReaderContent.Pdf -> {
                        _pdfPage.value = entity.positionIndex.coerceIn(0, (content.doc.pageCount - 1).coerceAtLeast(0))
                    }
                    else -> Unit
                }
                _content.value = content
                // Kitaplıkta "son okunan" sıralaması için hemen işaretle
                repo.savePosition(bookId, entity.progress, entity.positionIndex, entity.positionFraction, entity.totalUnits)
            } catch (t: Throwable) {
                _content.value = ReaderContent.Error("Kitap açılamadı: ${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    // ---- Arayüz ----
    fun toggleChrome() { _chrome.value = !_chrome.value }
    fun setChrome(visible: Boolean) { _chrome.value = visible }

    private val reflow: ReflowableBook? get() = (_content.value as? ReaderContent.Reflow)?.book

    // ---- Yeniden akıtılabilir kitaplar ----
    fun onReflowPage(page: Int, pages: Int) {
        val book = reflow ?: return
        val chapter = _request.value?.index ?: return
        val safePages = pages.coerceAtLeast(1)
        _reflowPos.value = ReflowPosition(chapter, page, safePages, page.toFloat() / safePages)
        val readFraction = (page + 1).toFloat() / safePages
        updateProgress(book.overallProgress(chapter, readFraction), chapter, page.toFloat() / safePages, book.chapters.size)
    }

    fun onReflowScroll(fraction: Float) {
        val book = reflow ?: return
        val chapter = _request.value?.index ?: return
        _reflowPos.value = ReflowPosition(chapter, 0, 1, fraction)
        updateProgress(book.overallProgress(chapter, fraction), chapter, fraction, book.chapters.size)
    }

    fun nextChapter() {
        val book = reflow ?: return
        val current = _request.value?.index ?: return
        if (current < book.chapters.lastIndex) {
            _request.value = ChapterRequest(current + 1)
        } else {
            viewModelScope.launch {
                repo.setFinished(bookId, true)
                events.send("Kitabın sonuna ulaştın 🎉")
            }
        }
    }

    fun prevChapter() {
        val current = _request.value?.index ?: return
        if (current > 0) _request.value = ChapterRequest(current - 1, atEnd = true)
    }

    fun openChapter(index: Int, anchor: String? = null) {
        val book = reflow ?: return
        if (index in book.chapters.indices) _request.value = ChapterRequest(index, anchor = anchor)
    }

    fun jumpToProgress(progress: Float) {
        when (val c = _content.value) {
            is ReaderContent.Reflow -> {
                val (idx, fraction) = c.book.positionFor(progress)
                _request.value = ChapterRequest(idx, fraction)
            }
            is ReaderContent.Pdf -> jumpToPdfPage(((c.doc.pageCount - 1) * progress).toInt())
            else -> Unit
        }
    }

    // ---- PDF ----
    fun onPdfPage(page: Int) {
        val doc = (_content.value as? ReaderContent.Pdf)?.doc ?: return
        if (_pdfPage.value == page && _progress.value > 0f) return
        _pdfPage.value = page
        val count = doc.pageCount.coerceAtLeast(1)
        updateProgress((page + 1).toFloat() / count, page, 0f, count)
    }

    fun jumpToPdfPage(page: Int) {
        val doc = (_content.value as? ReaderContent.Pdf)?.doc ?: return
        val p = page.coerceIn(0, doc.pageCount - 1)
        _pdfJump.value = p to SystemClock.uptimeMillis()
    }

    // ---- Kaydetme ----
    private fun updateProgress(progress: Float, index: Int, fraction: Float, total: Int) {
        _progress.value = progress
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(700)
            repo.savePosition(bookId, progress, index, fraction, total)
        }
    }

    private fun saveNow() {
        val progress = _progress.value
        when (val c = _content.value) {
            is ReaderContent.Reflow -> {
                val pos = _reflowPos.value
                container.appScope.launch { repo.savePosition(bookId, progress, pos.chapter, pos.fraction, c.book.chapters.size) }
            }
            is ReaderContent.Pdf -> {
                val page = _pdfPage.value
                container.appScope.launch { repo.savePosition(bookId, progress, page, 0f, c.doc.pageCount) }
            }
            else -> Unit
        }
    }

    // ---- Okuma süresi istatistiği ----
    fun sessionResume() {
        sessionStart = SystemClock.elapsedRealtime()
        sessionWall = System.currentTimeMillis()
    }

    fun sessionPause() {
        if (sessionStart == 0L) return
        val duration = SystemClock.elapsedRealtime() - sessionStart
        val wall = sessionWall
        sessionStart = 0L
        container.appScope.launch { repo.recordSession(bookId, wall, duration) }
        saveNow()
    }

    // ---- Yer imleri ----
    fun currentBookmark(list: List<BookmarkEntity>): BookmarkEntity? = when (_content.value) {
        is ReaderContent.Reflow -> {
            val pos = _reflowPos.value
            val tolerance = 0.5f / pos.pages.coerceAtLeast(1)
            list.firstOrNull { it.positionIndex == pos.chapter && abs(it.positionFraction - pos.fraction) <= tolerance }
        }
        is ReaderContent.Pdf -> list.firstOrNull { it.positionIndex == _pdfPage.value }
        else -> null
    }

    fun toggleBookmark(snippet: String) {
        viewModelScope.launch {
            currentBookmark(bookmarks.value)?.let {
                repo.deleteBookmark(it.id)
                events.send("Yer imi kaldırıldı")
                return@launch
            }
            val bookmark = when (val c = _content.value) {
                is ReaderContent.Reflow -> {
                    val pos = _reflowPos.value
                    BookmarkEntity(
                        bookId = bookId,
                        positionIndex = pos.chapter,
                        positionFraction = pos.fraction,
                        progress = _progress.value,
                        label = c.book.titleForChapter(pos.chapter) ?: "Bölüm ${pos.chapter + 1}",
                        snippet = snippet,
                    )
                }
                is ReaderContent.Pdf -> BookmarkEntity(
                    bookId = bookId,
                    positionIndex = _pdfPage.value,
                    positionFraction = 0f,
                    progress = _progress.value,
                    label = "Sayfa ${_pdfPage.value + 1}",
                    snippet = "",
                )
                else -> return@launch
            }
            repo.addBookmark(bookmark)
            events.send("Yer imi eklendi")
        }
    }

    fun openBookmark(b: BookmarkEntity) {
        when (_content.value) {
            is ReaderContent.Reflow -> _request.value = ChapterRequest(b.positionIndex, b.positionFraction)
            is ReaderContent.Pdf -> jumpToPdfPage(b.positionIndex)
            else -> Unit
        }
    }

    fun updateBookmarkNote(b: BookmarkEntity, note: String) = viewModelScope.launch { repo.updateBookmark(b.copy(note = note)) }
    fun deleteBookmark(b: BookmarkEntity) = viewModelScope.launch { repo.deleteBookmark(b.id) }

    // ---- Kitap içi arama ----
    fun search(query: String) {
        val book = reflow ?: return
        searchJob?.cancel()
        val q = query.trim()
        if (q.length < 2) {
            _search.value = SearchState(query = query)
            return
        }
        _search.value = SearchState(query = query, running = true)
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            val tr = Locale("tr", "TR")
            val needle = q.lowercase(tr)
            val hits = mutableListOf<SearchHit>()
            for (i in book.chapters.indices) {
                val text = withContext(Dispatchers.IO) { book.chapterPlainText(i) }
                val lower = text.lowercase(tr)
                var idx = lower.indexOf(needle)
                var nth = 0
                while (idx >= 0 && hits.size < 500) {
                    val start = (idx - 50).coerceAtLeast(0)
                    val end = (idx + needle.length + 70).coerceAtMost(text.length)
                    val snippet = (if (start > 0) "…" else "") + text.substring(start, end).replace(Regex("\\s+"), " ") + (if (end < text.length) "…" else "")
                    hits += SearchHit(i, nth, snippet, book.titleForChapter(i) ?: "Bölüm ${i + 1}")
                    nth++
                    idx = lower.indexOf(needle, idx + needle.length)
                }
                _search.value = SearchState(query = query, running = true, hits = hits.toList())
            }
            _search.value = SearchState(query = query, running = false, hits = hits.toList(), done = true)
        }
    }

    fun openSearchHit(hit: SearchHit) {
        _request.value = ChapterRequest(hit.chapterIndex, search = SearchTarget(_search.value.query.trim(), hit.nth))
    }

    override fun onCleared() {
        sessionPause()
        saveNow()
        when (val c = _content.value) {
            is ReaderContent.Reflow -> runCatching { c.book.close() }
            is ReaderContent.Pdf -> c.doc.close()
            else -> Unit
        }
        super.onCleared()
    }
}
