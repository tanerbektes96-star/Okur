@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.okur.reader.ui.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.okur.reader.data.SortOrder
import com.okur.reader.data.db.BookEntity
import com.okur.reader.ui.common.BookCover
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

val ImportMimeTypes = arrayOf("application/epub+zip", "application/pdf", "text/plain", "application/octet-stream")

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenBook: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var searching by rememberSaveable { mutableStateOf(false) }
    var actionBook by remember { mutableStateOf<BookEntity?>(null) }
    var showSort by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.import(uris)
    }

    LaunchedEffect(Unit) {
        viewModel.messageFlow.collect { scope.launch { snackbar.showSnackbar(it) } }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val gridState = rememberLazyGridState()
    val fabExpanded by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 } }
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                if (searching) {
                    SearchTopBar(
                        query = state.query,
                        onQuery = viewModel::setQuery,
                        onClose = { searching = false; viewModel.setQuery("") },
                    )
                } else {
                    LargeTopAppBar(
                        title = { Text("Kitaplığım") },
                        actions = {
                            IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, "Ara") }
                            Box {
                                IconButton(onClick = { showSort = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sırala") }
                                DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                                    SortOrder.entries.forEach { order ->
                                        DropdownMenuItem(
                                            text = { Text(order.label) },
                                            leadingIcon = { RadioButton(selected = state.prefs.sort == order, onClick = null) },
                                            onClick = { viewModel.setSort(order); showSort = false },
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = viewModel::toggleGrid) {
                                Icon(
                                    if (state.prefs.gridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                                    "Görünüm",
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                }
                AnimatedVisibility(importing > 0, enter = fadeIn(), exit = fadeOut()) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch(ImportMimeTypes) },
                expanded = fabExpanded,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Kitap ekle") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!state.loading && state.totalCount == 0) {
            EmptyLibrary(Modifier.padding(padding)) { picker.launch(ImportMimeTypes) }
        } else {
            LibraryGrid(state, gridState, padding, isTablet, viewModel, onOpenBook) { actionBook = it }
        }
    }

    actionBook?.let { book ->
        BookActionsSheet(
            book = book,
            onDismiss = { actionBook = null },
            onOpen = { actionBook = null; onOpenBook(book.id) },
            onFavorite = { viewModel.toggleFavorite(book); actionBook = null },
            onFinished = { viewModel.toggleFinished(book); actionBook = null },
            onRename = { t, a -> viewModel.rename(book, t, a); actionBook = null },
            onDelete = { viewModel.delete(book); actionBook = null },
        )
    }
}

@Composable
private fun LibraryGrid(
    state: LibraryUiState,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    padding: PaddingValues,
    isTablet: Boolean,
    viewModel: LibraryViewModel,
    onOpenBook: (String) -> Unit,
    onActions: (BookEntity) -> Unit,
) {
    val grid = state.prefs.gridView
    LazyVerticalGrid(
        state = gridState,
        columns = if (grid) GridCells.Adaptive(if (isTablet) 150.dp else 108.dp) else GridCells.Adaptive(380.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 96.dp),
        horizontalArrangement = Arrangement.spacedBy(if (grid) 14.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (grid) 20.dp else 10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.query.isBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                HeaderSection(state, isTablet, onOpenBook)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "filters") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LibraryFilter.entries) { f ->
                    FilterChip(
                        selected = state.filter == f,
                        onClick = { viewModel.setFilter(f) },
                        label = { Text(f.label) },
                    )
                }
            }
        }
        if (state.books.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "nores") {
                Text(
                    "Bu filtreye uyan kitap yok.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 40.dp).fillMaxWidth(),
                )
            }
        }
        items(state.books, key = { it.id }) { book ->
            if (grid) {
                BookGridItem(
                    book = book,
                    modifier = Modifier.animateItem(),
                    onClick = { onOpenBook(book.id) },
                    onLongClick = { onActions(book) },
                )
            } else {
                BookListItem(
                    book = book,
                    modifier = Modifier.animateItem(),
                    onClick = { onOpenBook(book.id) },
                    onLongClick = { onActions(book) },
                )
            }
        }
    }
}

@Composable
private fun SearchTopBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kapat") } },
        title = {
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text("Başlık veya yazar ara") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        actions = {
            if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, "Temizle") }
        },
    )
}

@Composable
private fun HeaderSection(state: LibraryUiState, isTablet: Boolean, onOpenBook: (String) -> Unit) {
    if (isTablet && state.continueBook != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            ContinueReadingCard(state.continueBook, Modifier.weight(2f)) { onOpenBook(state.continueBook.id) }
            DailyGoalCard(state, Modifier.weight(1f))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            state.continueBook?.let { ContinueReadingCard(it, Modifier.fillMaxWidth()) { onOpenBook(it.id) } }
            DailyGoalCard(state, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ContinueReadingCard(book: BookEntity, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(book, Modifier.width(78.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Okumaya devam et", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(book.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.author, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { book.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("%${(book.progress * 100).roundToInt()} tamamlandı", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 14.dp)) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Devam")
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyGoalCard(state: LibraryUiState, modifier: Modifier) {
    val goal = state.prefs.dailyGoalMin.coerceAtLeast(1)
    val ratio = (state.todayMinutes.toFloat() / goal).coerceIn(0f, 1f)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier.size(56.dp),
                    strokeWidth = 6.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                )
                Text("${(ratio * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Günlük hedef", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (ratio >= 1f) "Bugünkü hedefe ulaştın! 🎉" else "Bugün ${state.todayMinutes} / $goal dk",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.streak > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.LocalFireDepartment, null, tint = Color(0xFFE8711A))
                    Text("${state.streak} gün", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun BookGridItem(book: BookEntity, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        modifier
            .clip16()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box {
            BookCover(book, Modifier.fillMaxWidth())
            if (book.isFavorite) {
                Icon(
                    Icons.Filled.Favorite, null,
                    tint = Color(0xFFE5484D),
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp),
                )
            }
            if (book.isFinished) {
                Icon(
                    Icons.Filled.CheckCircle, null,
                    tint = Color(0xFF3FA66B),
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (book.progress > 0f && !book.isFinished) {
            LinearProgressIndicator(
                progress = { book.progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            book.author,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BookListItem(book: BookEntity, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(book, Modifier.width(58.dp), corner = 6)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { book.progress },
                        modifier = Modifier.weight(1f).height(4.dp),
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (book.isFinished) "Bitti" else "%${(book.progress * 100).roundToInt()}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(book.format, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (book.isFavorite) Icon(Icons.Filled.Favorite, null, tint = Color(0xFFE5484D), modifier = Modifier.padding(start = 8.dp).size(18.dp))
        }
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier, onAdd: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.LocalLibrary, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
        Spacer(Modifier.height(20.dp))
        Text("Kitaplığın henüz boş", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "EPUB, PDF veya TXT dosyalarını ekleyerek okumaya başla. Dosya yöneticisinden \"Birlikte aç → Okur\" ile de ekleyebilirsin.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)) {
            Icon(Icons.Filled.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Kitap ekle")
        }
    }
}

@Composable
private fun BookActionsSheet(
    book: BookEntity,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onFinished: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(book, Modifier.width(64.dp), corner = 6)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    buildString {
                        append(book.format)
                        append(" · ").append(formatSize(book.fileSize))
                        if (book.readingTimeMs > 60_000) append(" · ").append(book.readingTimeMs / 60_000).append(" dk okundu")
                        append("\nEklendi: ").append(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(book.addedAt)))
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SheetAction(Icons.AutoMirrored.Filled.MenuBook, if (book.progress > 0f) "Okumaya devam et" else "Okumaya başla", onClick = onOpen)
        SheetAction(
            if (book.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            if (book.isFavorite) "Favorilerden çıkar" else "Favorilere ekle",
            onClick = onFavorite,
        )
        SheetAction(Icons.Filled.CheckCircle, if (book.isFinished) "Okunmadı olarak işaretle" else "Bitti olarak işaretle", onClick = onFinished)
        SheetAction(Icons.Filled.Edit, "Başlık ve yazarı düzenle") { renaming = true }
        SheetAction(Icons.Filled.Share, "Paylaş") {
            runCatching {
                val file = java.io.File(book.filePath)
                val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = when (book.format) { "EPUB" -> "application/epub+zip"; "PDF" -> "application/pdf"; else -> "text/plain" }
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TITLE, book.title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, book.title))
            }
        }
        SheetAction(Icons.Filled.Delete, "Kitaplıktan sil", tint = MaterialTheme.colorScheme.error) { confirmDelete = true }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Filled.Delete, null) },
            title = { Text("Kitap silinsin mi?") },
            text = { Text("\"${book.title}\" ve yer imleri cihazdan kalıcı olarak silinecek.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Sil", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } },
        )
    }
    if (renaming) {
        var title by remember { mutableStateOf(book.title) }
        var author by remember { mutableStateOf(book.author) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Kitap bilgileri") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Başlık") }, singleLine = true)
                    OutlinedTextField(author, { author = it }, label = { Text("Yazar") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { onRename(title, author) }) { Text("Kaydet") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label, color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface) },
        leadingContent = { Icon(icon, null, tint = tint) },
        modifier = Modifier.combinedClickable(onClick = onClick),
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576f)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

private fun Modifier.clip16(): Modifier = this.then(Modifier.clip(RoundedCornerShape(12.dp)))
