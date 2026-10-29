@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.okur.reader.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.okur.reader.AppContainer
import com.okur.reader.data.db.BookEntity
import com.okur.reader.ui.library.LibraryViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

data class StatsUi(
    val days: List<Pair<LocalDate, Int>> = emptyList(),
    val totalMinutes: Long = 0,
    val streak: Int = 0,
    val activeDays: Int = 0,
    val finished: Int = 0,
    val reading: Int = 0,
    val goal: Int = 30,
    val topBooks: List<BookEntity> = emptyList(),
)

class StatsViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.books
    private val today = LocalDate.now()
    private val from = today.minusDays(13)

    val state: StateFlow<StatsUi> = combine(
        repo.dailyTotals(from.toEpochDay()),
        repo.activeDays,
        repo.totalReadingTime,
        repo.books,
        container.settings.appPrefs,
    ) { totals, days, total, books, prefs ->
        val map = totals.associate { it.day to (it.total / 60_000).toInt() }
        StatsUi(
            days = (0..13).map { i -> from.plusDays(i.toLong()).let { d -> d to (map[d.toEpochDay()] ?: 0) } },
            totalMinutes = total / 60_000,
            streak = LibraryViewModel.streakFrom(days),
            activeDays = days.size,
            finished = books.count { it.isFinished },
            reading = books.count { it.progress > 0f && !it.isFinished },
            goal = prefs.dailyGoalMin,
            topBooks = books.filter { it.readingTimeMs > 60_000 }.sortedByDescending { it.readingTimeMs }.take(5),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUi())
}

@Composable
fun StatsScreen(viewModel: StatsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text("İstatistikler") }, scrollBehavior = scrollBehavior) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 4) {
                StatTile(Icons.Filled.Schedule, formatMinutes(state.totalMinutes), "Toplam okuma", Modifier.weight(1f).widthIn(min = 150.dp))
                StatTile(Icons.Filled.LocalFireDepartment, "${state.streak} gün", "Okuma serisi", Modifier.weight(1f).widthIn(min = 150.dp))
                StatTile(Icons.Filled.EmojiEvents, "${state.finished}", "Bitirilen kitap", Modifier.weight(1f).widthIn(min = 150.dp))
                StatTile(Icons.Filled.AutoStories, "${state.reading}", "Okunan kitap", Modifier.weight(1f).widthIn(min = 150.dp))
            }

            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Son 14 gün · günlük okuma (dk)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Kesikli çizgi günlük hedefin (${state.goal} dk). Bir çubuğa dokunarak değeri gör.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    DailyBarChart(state.days, state.goal)
                }
            }

            if (state.topBooks.isNotEmpty()) {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("En çok okunan kitaplar", style = MaterialTheme.typography.titleMedium)
                        val maxMs = state.topBooks.first().readingTimeMs.coerceAtLeast(1)
                        state.topBooks.forEach { b ->
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(b.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(formatMinutes(b.readingTimeMs / 60_000), style = MaterialTheme.typography.labelLarge)
                                }
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { b.readingTimeMs.toFloat() / maxMs },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    strokeCap = StrokeCap.Round,
                                    gapSize = 0.dp,
                                    drawStopIndicator = {},
                                )
                            }
                        }
                    }
                }
            }
            Text(
                "Toplam ${state.activeDays} gün okuma yaptın. Okuma süresi yalnızca okuma ekranı açıkken sayılır.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun DailyBarChart(days: List<Pair<LocalDate, Int>>, goal: Int) {
    if (days.isEmpty()) return
    val bar = MaterialTheme.colorScheme.primary
    val barMuted = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    var selected by remember(days) { mutableIntStateOf(days.lastIndex) }
    val maxValue = max(days.maxOf { it.second }, goal).coerceAtLeast(10)
    val tr = Locale("tr", "TR")
    val description = days.joinToString { "${it.first.dayOfMonth} ${it.first.month.getDisplayName(TextStyle.SHORT, tr)}: ${it.second} dk" }

    val sel = days[selected]
    Text(
        "${sel.first.dayOfMonth} ${sel.first.month.getDisplayName(TextStyle.FULL, tr)} ${sel.first.dayOfWeek.getDisplayName(TextStyle.FULL, tr)} · ${sel.second} dk",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(8.dp))
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .semantics { contentDescription = "Günlük okuma süreleri: $description" }
            .pointerInput(days) {
                detectTapGestures { pos ->
                    val slot = size.width / days.size.toFloat()
                    selected = (pos.x / slot).toInt().coerceIn(0, days.lastIndex)
                }
            }
    ) {
        val slot = size.width / days.size
        val barWidth = (slot * 0.5f).coerceAtMost(18.dp.toPx())
        val chartH = size.height
        // Hedef çizgisi (ızgara kadar sakin)
        val goalY = chartH - chartH * goal / maxValue
        drawLine(grid, Offset(0f, chartH), Offset(size.width, chartH), strokeWidth = 1.dp.toPx())
        drawLine(
            ink.copy(alpha = 0.6f), Offset(0f, goalY), Offset(size.width, goalY),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        )
        days.forEachIndexed { i, (_, minutes) ->
            val h = if (minutes == 0) 0f else (chartH * minutes / maxValue).coerceAtLeast(4.dp.toPx())
            val x = slot * i + (slot - barWidth) / 2
            if (h > 0f) {
                drawRoundRect(
                    color = if (i == selected) bar else barMuted,
                    topLeft = Offset(x, chartH - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
            if (i == selected) {
                drawCircle(bar, radius = 3.dp.toPx(), center = Offset(x + barWidth / 2, chartH + 0.5f))
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth()) {
        days.forEachIndexed { i, (date, _) ->
            Text(
                if (i % 2 == days.lastIndex % 2) date.dayOfWeek.getDisplayName(TextStyle.SHORT, tr).take(3) else "",
                style = MaterialTheme.typography.labelSmall,
                color = ink,
                maxLines = 1,
                modifier = Modifier.weight(1f).width(0.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

private fun formatMinutes(min: Long): String = when {
    min >= 60 -> "${min / 60} sa ${min % 60} dk"
    else -> "$min dk"
}
