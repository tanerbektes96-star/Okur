@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.okur.reader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.okur.reader.data.EyePrefs
import com.okur.reader.data.FontChoice
import com.okur.reader.data.FilterMode
import com.okur.reader.data.PageMode
import com.okur.reader.data.PageTurn
import com.okur.reader.tts.TtsVoice
import com.okur.reader.data.ReaderPrefs
import com.okur.reader.data.ReaderTheme
import com.okur.reader.data.db.BookmarkEntity
import com.okur.reader.format.TocEntry
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

// ---------------------------------------------------------------- Görünüm

@Composable
fun AppearanceSheet(
    prefs: ReaderPrefs,
    eye: EyePrefs,
    isPdf: Boolean,
    onPrefs: ((ReaderPrefs) -> ReaderPrefs) -> Unit,
    onEye: ((EyePrefs) -> EyePrefs) -> Unit,
    voices: List<TtsVoice>,
    onPreviewVoice: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionTitle("Tema")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReaderTheme.entries.forEach { t ->
                    ThemeSwatch(t, selected = prefs.theme == t) { onPrefs { it.copy(theme = t) } }
                }
            }

            // Parlaklık (okuma ekranına özel)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Brightness6, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Slider(
                    value = if (prefs.brightness < 0) 0.5f else prefs.brightness,
                    onValueChange = { v -> onPrefs { it.copy(brightness = v.coerceIn(0.01f, 1f)) } },
                    enabled = prefs.brightness >= 0,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = prefs.brightness < 0,
                    onClick = { onPrefs { it.copy(brightness = if (it.brightness < 0) 0.5f else -1f) } },
                    label = { Text("Sistem") },
                )
            }

            // Hızlı göz koruma: başlık üstte, kaydırıcı ve anahtar aynı satırda ortalı
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SelfImprovement, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text("Mavi ışık filtresi", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(36.dp))
                    Slider(
                        value = eye.filterStrength,
                        onValueChange = { v -> onEye { it.copy(filterStrength = v, filterMode = if (it.filterMode == FilterMode.OFF) FilterMode.ON else it.filterMode) } },
                        valueRange = 0f..0.8f,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = eye.filterMode != FilterMode.OFF,
                        onCheckedChange = { on -> onEye { it.copy(filterMode = if (on) FilterMode.ON else FilterMode.OFF) } },
                    )
                }
            }

            HorizontalDivider()

            if (!isPdf) {
                SectionTitle("Yazı")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { onPrefs { it.copy(fontSize = (it.fontSize - 1).coerceAtLeast(12)) } }) {
                        Icon(Icons.Filled.Remove, "Küçült")
                    }
                    Slider(
                        value = prefs.fontSize.toFloat(),
                        onValueChange = { v -> onPrefs { it.copy(fontSize = v.roundToInt()) } },
                        valueRange = 12f..40f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    FilledTonalIconButton(onClick = { onPrefs { it.copy(fontSize = (it.fontSize + 1).coerceAtMost(40)) } }) {
                        Icon(Icons.Filled.Add, "Büyüt")
                    }
                    Text("${prefs.fontSize}", modifier = Modifier.width(32.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelLarge)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FontChoice.entries.forEach { f ->
                        FilterChip(
                            selected = prefs.font == f,
                            onClick = { onPrefs { it.copy(font = f) } },
                            label = {
                                Text(
                                    f.label,
                                    fontFamily = when (f) {
                                        FontChoice.SERIF, FontChoice.SLAB -> FontFamily.Serif
                                        FontChoice.SANS -> FontFamily.SansSerif
                                        FontChoice.MONO -> FontFamily.Monospace
                                        FontChoice.ORIGINAL -> FontFamily.Default
                                    },
                                )
                            },
                        )
                    }
                }
                LabeledSlider(
                    icon = { Icon(Icons.Filled.FormatLineSpacing, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    label = "Satır aralığı",
                    valueText = String.format(Locale.US, "%.1f", prefs.lineHeight),
                    value = prefs.lineHeight,
                    range = 1.1f..2.4f,
                    onChange = { v -> onPrefs { it.copy(lineHeight = (v * 10).roundToInt() / 10f) } },
                )
                LabeledSlider(
                    icon = { Icon(Icons.Filled.FormatAlignJustify, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    label = "Kenar boşluğu",
                    valueText = "${prefs.margin}",
                    value = prefs.margin.toFloat(),
                    range = 4f..64f,
                    onChange = { v -> onPrefs { it.copy(margin = v.roundToInt()) } },
                )
                SwitchRow("İki yana yasla", prefs.justify) { v -> onPrefs { it.copy(justify = v) } }
                SwitchRow("Heceleme (satır sonu tirelemesi)", prefs.hyphenate) { v -> onPrefs { it.copy(hyphenate = v) } }
            }

            SectionTitle("Sayfa düzeni")
            val mode = if (isPdf) prefs.pdfPageMode else prefs.pageMode
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                PageMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { onPrefs { if (isPdf) it.copy(pdfPageMode = m) else it.copy(pageMode = m) } },
                        shape = SegmentedButtonDefaults.itemShape(i, PageMode.entries.size),
                    ) { Text(m.label) }
                }
            }
            AnimatedVisibility(mode == PageMode.PAGED) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sayfa çevirme animasyonu", style = MaterialTheme.typography.bodyMedium)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        PageTurn.entries.forEachIndexed { i, t ->
                            SegmentedButton(
                                selected = prefs.pageTurn == t,
                                onClick = { onPrefs { it.copy(pageTurn = t) } },
                                shape = SegmentedButtonDefaults.itemShape(i, PageTurn.entries.size),
                            ) { Text(t.label) }
                        }
                    }
                }
            }
            SwitchRow(if (isPdf) "Tablette yatayda çift sayfa" else "Geniş ekranda çift sütun", prefs.dualPage) { v -> onPrefs { it.copy(dualPage = v) } }
            SwitchRow("Kenarlara dokunarak sayfa çevir", prefs.tapZones) { v -> onPrefs { it.copy(tapZones = v) } }
            if (isPdf) SwitchRow("PDF renklerini temaya uyarla (gece modu)", prefs.pdfFollowTheme) { v -> onPrefs { it.copy(pdfFollowTheme = v) } }

            if (!isPdf) {
                HorizontalDivider()
                SectionTitle("Sesli okuma")
                Text("Ses tonu", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Bariton (kalın)" to 0.72f, "Normal" to 1f, "İnce" to 1.25f).forEach { (label, value) ->
                        FilterChip(
                            selected = kotlin.math.abs(prefs.ttsPitch - value) < 0.04f,
                            onClick = { onPrefs { it.copy(ttsPitch = value) } },
                            label = { Text(label) },
                        )
                    }
                }
                LabeledSlider(
                    icon = { Icon(Icons.Filled.RecordVoiceOver, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    label = "Ton ayarı",
                    valueText = String.format(Locale.US, "%.2f", prefs.ttsPitch),
                    value = prefs.ttsPitch,
                    range = 0.5f..1.6f,
                    onChange = { v -> onPrefs { it.copy(ttsPitch = (v * 20).roundToInt() / 20f) } },
                )
                Text("Ses", style = MaterialTheme.typography.bodyMedium)
                if (voices.isEmpty()) {
                    Text(
                        "Bu dil için ek ses bulunamadı. Android Ayarlar → Metin okuma (TTS) bölümünden Google ses verilerini indirerek erkek sesleri ekleyebilirsin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = prefs.ttsVoice.isEmpty(),
                            onClick = { onPrefs { it.copy(ttsVoice = "") } },
                            label = { Text("Varsayılan") },
                        )
                        voices.forEach { v ->
                            FilterChip(
                                selected = prefs.ttsVoice == v.name,
                                onClick = { onPrefs { it.copy(ttsVoice = v.name) } },
                                label = { Text(v.label) },
                            )
                        }
                    }
                    Text(
                        "Cinsiyet bilgisi her cihazda bulunmayabilir; sesleri dinleyerek seçebilirsin. Kalın bir erkek sesi için bir erkek ses + \"Bariton\" tonunu dene.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = onPreviewVoice) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sesi dinle")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ThemeSwatch(theme: ReaderTheme, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(theme.background)
                .border(
                    BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    CircleShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, null, tint = theme.text)
            else Text("Aa", color = theme.text, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Text(theme.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun LabeledSlider(
    icon: @Composable () -> Unit,
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(valueText, style = MaterialTheme.typography.labelLarge)
            }
            Slider(value = value, onValueChange = onChange, valueRange = range)
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---------------------------------------------------------------- İçindekiler + yer imleri

@Composable
fun ContentsSheet(
    toc: List<TocEntry>,
    currentChapter: Int,
    bookmarks: List<BookmarkEntity>,
    onToc: (TocEntry) -> Unit,
    onBookmark: (BookmarkEntity) -> Unit,
    onEditNote: (BookmarkEntity, String) -> Unit,
    onDeleteBookmark: (BookmarkEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(if (toc.isEmpty()) 1 else 0) }
    var editing by remember { mutableStateOf<BookmarkEntity?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("İçindekiler") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Yer imleri (${bookmarks.size})") })
        }
        if (tab == 0) {
            val currentIndex = toc.indexOfLast { it.chapterIndex <= currentChapter }.coerceAtLeast(0)
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 3).coerceAtLeast(0))
            if (toc.isEmpty()) {
                EmptyHint("Bu belgede içindekiler bilgisi yok.")
            } else {
                LazyColumn(state = listState, modifier = Modifier.heightIn(max = 560.dp).navigationBarsPadding()) {
                    itemsIndexed(toc) { i, entry ->
                        val active = i == currentIndex
                        ListItem(
                            headlineContent = {
                                Text(
                                    entry.title,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                            modifier = Modifier
                                .clickable { onToc(entry) }
                                .padding(start = (entry.depth * 16).dp),
                        )
                    }
                }
            }
        } else {
            if (bookmarks.isEmpty()) {
                EmptyHint("Henüz yer imi yok. Okurken üst çubuktaki yer imi simgesine dokun.")
            } else {
                LazyColumn(Modifier.heightIn(max = 560.dp).navigationBarsPadding()) {
                    items(bookmarks, key = { it.id }) { b ->
                        ListItem(
                            leadingContent = { Icon(Icons.Filled.Bookmark, null, tint = MaterialTheme.colorScheme.primary) },
                            headlineContent = { Text(b.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Column {
                                    if (b.snippet.isNotBlank()) Text("“${b.snippet}”", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                    if (b.note.isNotBlank()) Text("Not: ${b.note}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                                    Text("%${(b.progress * 100).roundToInt()}", style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { editing = b }) { Icon(Icons.Filled.Edit, "Not ekle") }
                                    IconButton(onClick = { onDeleteBookmark(b) }) { Icon(Icons.Filled.Delete, "Sil") }
                                }
                            },
                            modifier = Modifier.clickable { onBookmark(b) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    editing?.let { b ->
        var note by remember(b.id) { mutableStateOf(b.note) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Yer imi notu") },
            text = { OutlinedTextField(note, { note = it }, placeholder = { Text("Düşüncelerini yaz…") }, minLines = 3) },
            confirmButton = { TextButton(onClick = { onEditNote(b, note); editing = null }) { Text("Kaydet") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    )
}

// ---------------------------------------------------------------- Arama

@Composable
fun SearchSheet(
    state: SearchState,
    onSearch: (String) -> Unit,
    onHit: (SearchHit) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf(state.query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (state.query.isEmpty()) focus.requestFocus() }
    LaunchedEffect(query) {
        delay(350)
        if (query != state.query) onSearch(query)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Temizle") } },
            placeholder = { Text("Kitapta ara") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
        )
        if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), strokeCap = StrokeCap.Round)
        Text(
            when {
                state.query.trim().length < 2 -> "En az 2 karakter yaz"
                state.done && state.hits.isEmpty() -> "Sonuç bulunamadı"
                else -> "${state.hits.size}${if (state.hits.size >= 500) "+" else ""} sonuç"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.heightIn(min = 200.dp, max = 640.dp).navigationBarsPadding()) {
            items(state.hits) { hit ->
                ListItem(
                    overlineContent = { Text(hit.chapterTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    headlineContent = { Text(highlight(hit.snippet, state.query.trim()), style = MaterialTheme.typography.bodyMedium) },
                    modifier = Modifier.clickable { onHit(hit) },
                )
            }
        }
    }
}

@Composable
private fun highlight(text: String, query: String): androidx.compose.ui.text.AnnotatedString {
    val color = MaterialTheme.colorScheme.tertiaryContainer
    return remember(text, query, color) { highlightText(text, query, color) }
}

private fun highlightText(text: String, query: String, color: androidx.compose.ui.graphics.Color) = buildAnnotatedString {
    val tr = Locale("tr", "TR")
    val lower = text.lowercase(tr)
    val q = query.lowercase(tr)
    var start = 0
    var idx = if (q.isEmpty()) -1 else lower.indexOf(q)
    while (idx >= 0 && idx + q.length <= text.length) {
        append(text.substring(start, idx))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = color)) {
            append(text.substring(idx, idx + q.length))
        }
        start = idx + q.length
        idx = lower.indexOf(q, start)
    }
    append(text.substring(start.coerceAtMost(text.length)))
}

// ---------------------------------------------------------------- PDF sayfaya git

@Composable
fun GoToPageDialog(pageCount: Int, current: Int, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("${current + 1}") }
    val number = text.toIntOrNull()
    val valid = number != null && number in 1..pageCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sayfaya git") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() }.take(6) },
                label = { Text("Sayfa (1 – $pageCount)") },
                singleLine = true,
                isError = text.isNotEmpty() && !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (valid) onGo(number!! - 1) }),
            )
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onGo(number!! - 1) }) { Text("Git") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

// ---------------------------------------------------------------- Göz molası (20-20-20)

@Composable
fun EyeBreakDialog(onDone: () -> Unit, onSnooze: () -> Unit) {
    val progress = remember { Animatable(0f) }
    var finished by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(20_000, easing = LinearEasing))
        finished = true
    }
    val remaining = (20 - progress.value * 20).roundToInt()
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Filled.SelfImprovement, null) },
        title = { Text(if (finished) "Harika, gözlerin dinlendi!" else "Göz molası zamanı") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (finished) "Okumaya devam edebilirsin." else "20-20-20 kuralı: 20 saniye boyunca yaklaşık 6 metre uzaktaki bir noktaya bak ve birkaç kez göz kırp.",
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Box(contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(
                        progress = { progress.value },
                        modifier = Modifier.size(96.dp),
                        strokeWidth = 8.dp,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                    )
                    Text(if (finished) "✓" else "$remaining", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(if (finished) "Devam et" else "Atla") } },
        dismissButton = { if (!finished) TextButton(onClick = onSnooze) { Text("5 dk ertele") } },
    )
}
