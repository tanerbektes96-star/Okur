package com.okur.reader.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okur.reader.AppContainer
import com.okur.reader.data.AppPrefs
import com.okur.reader.data.BookFormat
import com.okur.reader.data.ImportResult
import com.okur.reader.data.SortOrder
import com.okur.reader.data.db.BookEntity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.time.LocalDate
import java.util.Locale

enum class LibraryFilter(val label: String) {
    ALL("Tümü"), READING("Okunuyor"), UNREAD("Başlanmadı"), FINISHED("Bitti"), FAVORITES("Favoriler"),
    EPUB("EPUB"), PDF("PDF"), TXT("TXT")
}

data class LibraryUiState(
    val loading: Boolean = true,
    val books: List<BookEntity> = emptyList(),
    val totalCount: Int = 0,
    val continueBook: BookEntity? = null,
    val query: String = "",
    val filter: LibraryFilter = LibraryFilter.ALL,
    val prefs: AppPrefs = AppPrefs(),
    val todayMinutes: Int = 0,
    val streak: Int = 0,
)

class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.books
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(LibraryFilter.ALL)
    private val collator = Collator.getInstance(Locale("tr", "TR"))

    private val _importing = MutableStateFlow(0)
    val importing: StateFlow<Int> = _importing

    private val messages = Channel<String>(Channel.BUFFERED)
    val messageFlow = messages.receiveAsFlow()

    /** Dosya yöneticisinden açılan kitabın içe aktarıldıktan sonra açılması için */
    private val openRequests = Channel<String>(Channel.BUFFERED)
    val openFlow = openRequests.receiveAsFlow()

    private val today = LocalDate.now().toEpochDay()

    private val stats = combine(
        repo.dailyTotals(today).map { list -> (list.firstOrNull { it.day == today }?.total ?: 0L) / 60_000 },
        repo.activeDays,
    ) { todayMin, days -> todayMin.toInt() to streakFrom(days) }

    val state: StateFlow<LibraryUiState> = combine(
        repo.books, query, filter, container.settings.appPrefs, stats,
    ) { books, q, f, prefs, (todayMin, streak) ->
        val filtered = books.filter { b ->
            val matchesQuery = q.isBlank() || b.title.contains(q, true) || b.author.contains(q, true)
            val matchesFilter = when (f) {
                LibraryFilter.ALL -> true
                LibraryFilter.READING -> b.progress > 0f && !b.isFinished
                LibraryFilter.UNREAD -> b.progress == 0f && !b.isFinished
                LibraryFilter.FINISHED -> b.isFinished
                LibraryFilter.FAVORITES -> b.isFavorite
                LibraryFilter.EPUB -> b.format == BookFormat.EPUB.name
                LibraryFilter.PDF -> b.format == BookFormat.PDF.name
                LibraryFilter.TXT -> b.format == BookFormat.TXT.name
            }
            matchesQuery && matchesFilter
        }
        val sorted = when (prefs.sort) {
            SortOrder.RECENT -> filtered.sortedWith(compareByDescending<BookEntity> { it.lastOpenedAt ?: 0L }.thenByDescending { it.addedAt })
            SortOrder.ADDED -> filtered.sortedByDescending { it.addedAt }
            SortOrder.TITLE -> filtered.sortedWith { a, b -> collator.compare(a.title, b.title) }
            SortOrder.AUTHOR -> filtered.sortedWith { a, b -> collator.compare(a.author, b.author) }
            SortOrder.PROGRESS -> filtered.sortedByDescending { it.progress }
        }
        LibraryUiState(
            loading = false,
            books = sorted,
            totalCount = books.size,
            continueBook = books.filter { it.lastOpenedAt != null && !it.isFinished }.maxByOrNull { it.lastOpenedAt ?: 0L },
            query = q,
            filter = f,
            prefs = prefs,
            todayMinutes = todayMin,
            streak = streak,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setQuery(q: String) { query.value = q }
    fun setFilter(f: LibraryFilter) { filter.value = f }
    fun setSort(s: SortOrder) = viewModelScope.launch { container.settings.updateApp { it.copy(sort = s) } }
    fun toggleGrid() = viewModelScope.launch { container.settings.updateApp { it.copy(gridView = !it.gridView) } }

    fun import(uris: List<Uri>, openAfter: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _importing.value += uris.size
            var added = 0
            var dup = 0
            val failed = mutableListOf<String>()
            var lastId: String? = null
            for (uri in uris) {
                when (val r = repo.import(uri)) {
                    is ImportResult.Added -> { added++; lastId = r.book.id }
                    is ImportResult.Duplicate -> { dup++; lastId = r.book.id }
                    is ImportResult.Failed -> failed += "${r.name}: ${r.reason}"
                }
                _importing.value -= 1
            }
            if (openAfter && lastId != null) {
                openRequests.send(lastId)
            } else {
                val parts = buildList {
                    if (added > 0) add("$added kitap eklendi")
                    if (dup > 0) add("$dup kitap zaten kitaplıkta")
                    if (failed.isNotEmpty()) add("${failed.size} dosya eklenemedi (${failed.first()})")
                }
                if (parts.isNotEmpty()) messages.send(parts.joinToString(" · "))
            }
        }
    }

    fun toggleFavorite(book: BookEntity) = viewModelScope.launch { repo.setFavorite(book.id, !book.isFavorite) }
    fun toggleFinished(book: BookEntity) = viewModelScope.launch { repo.setFinished(book.id, !book.isFinished) }
    fun rename(book: BookEntity, title: String, author: String) = viewModelScope.launch {
        repo.rename(book.id, title.ifBlank { book.title }, author.ifBlank { book.author })
    }

    fun delete(book: BookEntity) = viewModelScope.launch {
        repo.delete(book)
        messages.send("\"${book.title}\" silindi")
    }

    companion object {
        fun streakFrom(daysDesc: List<Long>): Int {
            if (daysDesc.isEmpty()) return 0
            val today = LocalDate.now().toEpochDay()
            var expected = if (daysDesc.first() == today) today else today - 1
            var streak = 0
            for (d in daysDesc) {
                if (d == expected) { streak++; expected-- } else if (d < expected) break
            }
            return streak
        }
    }
}
