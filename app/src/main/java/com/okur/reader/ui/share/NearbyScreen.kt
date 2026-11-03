@file:OptIn(ExperimentalMaterial3Api::class)

package com.okur.reader.ui.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.okur.reader.AppContainer
import com.okur.reader.data.AppPrefs
import com.okur.reader.data.db.BookEntity
import com.okur.reader.share.NearbyDiagnostics
import com.okur.reader.share.NearbyPermissions
import com.okur.reader.share.NearbyPhase
import com.okur.reader.share.NearbyUiState
import com.okur.reader.share.TransferItem
import com.okur.reader.ui.common.BookCover
import kotlinx.coroutines.delay

fun deviceDisplayName(prefs: AppPrefs): String =
    prefs.deviceName.ifBlank { "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim() }

/**
 * Yakındaki Okur kullanıcılarıyla kitap alışverişi.
 * [sendIds] boşsa alma modunda açılır; doluysa bu kitaplar seçili olarak gönderme modunda açılır.
 */
@Composable
fun NearbyScreen(container: AppContainer, sendIds: List<String>, receive: Boolean, onBack: () -> Unit, onOpenBook: (String) -> Unit) {
    val context = LocalContext.current
    val manager = container.nearby
    val state by manager.state.collectAsStateWithLifecycle()
    val app by container.settings.appPrefs.collectAsStateWithLifecycle(initialValue = AppPrefs())
    val books by container.books.books.collectAsStateWithLifecycle(initialValue = emptyList())

    var granted by remember { mutableStateOf(NearbyPermissions.granted(context)) }
    var denied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.all { it }
        denied = !granted
    }
    LaunchedEffect(Unit) { if (!granted) permissionLauncher.launch(NearbyPermissions.required) }

    var selected by rememberSaveable { mutableStateOf(sendIds.toSet()) }
    var withNotes by rememberSaveable { mutableStateOf(true) }
    var started by rememberSaveable { mutableStateOf(false) }

    // Alma modu: izin varsa hemen görünür ol
    LaunchedEffect(granted, receive) {
        if (receive && granted) manager.startReceiving(deviceDisplayName(app))
    }
    DisposableEffect(Unit) { onDispose { manager.stop() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (receive) "Kitap al" else "Yakındaki Okur'a gönder") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                when {
                    !granted -> PermissionNeeded(denied) {
                        if (denied) {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                        } else {
                            permissionLauncher.launch(NearbyPermissions.required)
                        }
                    }
                    receive -> ReceiveContent(state, deviceDisplayName(app), onOpenBook, onRetry = { manager.startReceiving(deviceDisplayName(app)) })
                    !started -> SelectBooks(
                        books = books,
                        selected = selected,
                        withNotes = withNotes,
                        onToggle = { id -> selected = if (id in selected) selected - id else selected + id },
                        onNotes = { withNotes = it },
                        onStart = {
                            val chosen = books.filter { it.id in selected }
                            if (chosen.isNotEmpty()) {
                                manager.startSending(chosen, withNotes, deviceDisplayName(app))
                                started = true
                            }
                        },
                    )
                    else -> SendContent(state, onConnect = manager::connect, onRetry = { started = false; manager.stop() })
                }
            }
        }
    }

    state.incoming?.let { req ->
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Filled.PhoneAndroid, null) },
            title = { Text("${req.name} sana kitap göndermek istiyor") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Gönderen cihazda da aynı kodun göründüğünden emin ol:", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(req.code, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
                }
            },
            confirmButton = { Button(onClick = manager::acceptIncoming) { Text("Kabul et") } },
            dismissButton = { TextButton(onClick = manager::rejectIncoming) { Text("Reddet") } },
        )
    }
}

@Composable
private fun PermissionNeeded(denied: Boolean, onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Wifi, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Yakındaki cihazlar izni gerekli", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Okur, kitapları internet kullanmadan Bluetooth ve Wi-Fi ile yakındaki telefonlara gönderir. " +
                "Bunun için yakındaki cihazları bulma izni gerekiyor. Konumun kaydedilmez veya paylaşılmaz.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequest) { Text(if (denied) "Ayarlardan izin ver" else "İzin ver") }
    }
}

@Composable
private fun SelectBooks(
    books: List<BookEntity>,
    selected: Set<String>,
    withNotes: Boolean,
    onToggle: (String) -> Unit,
    onNotes: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text("Gönderilecek kitapları seç", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(books, key = { it.id }) { b ->
                ListItem(
                    leadingContent = { BookCover(b, Modifier.width(40.dp), corner = 4) },
                    headlineContent = { Text(b.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${b.author} · ${b.format}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = { Checkbox(checked = b.id in selected, onCheckedChange = { onToggle(b.id) }) },
                    modifier = Modifier.clickable { onToggle(b.id) },
                )
            }
        }
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().clickable { onNotes(!withNotes) }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Yer imleri ve notları da gönder", style = MaterialTheme.typography.bodyLarge)
                Text("Okuma ilerlemen gönderilmez", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = withNotes, onCheckedChange = onNotes)
        }
        Text(
            "Yalnızca paylaşma hakkın olan kitapları gönder (telifsiz kitaplar, kendi belgelerin). Satın alınan kitaplar genellikle lisans gereği paylaşılamaz.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onStart,
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) { Text(if (selected.isEmpty()) "Kitap seç" else "${selected.size} kitabı göndermek için cihaz ara") }
    }
}

@Composable
private fun SendContent(state: NearbyUiState, onConnect: (com.okur.reader.share.NearbyDevice) -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state.phase) {
            NearbyPhase.SEARCHING -> {
                Diagnostics()
                Radar()
                Text(
                    "Yakındaki Okur kullanıcıları aranıyor…\nKarşı taraf Okur'da Kitaplık → ⇄ → \"Kitap al\" ekranını açmalı.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.devices.isEmpty()) SlowHints(state.startedAt, sending = true)
                state.devices.forEach { d ->
                    Card(
                        onClick = { onConnect(d) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PhoneAndroid, null)
                            Spacer(Modifier.width(12.dp))
                            Text(d.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("Gönder", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            NearbyPhase.CONNECTING -> {
                Spacer(Modifier.height(24.dp))
                Text("${state.peerName ?: "Cihaz"} onayı bekleniyor", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                state.code?.let {
                    Text("Karşı cihazda bu kod görünmeli:", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text(it, fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            else -> TransferList(state, onOpenBook = null)
        }
        if (state.phase == NearbyPhase.FAILED || state.phase == NearbyPhase.DONE) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.phase == NearbyPhase.DONE) "Başka kitap gönder" else "Tekrar dene")
            }
        }
    }
}

@Composable
private fun ReceiveContent(state: NearbyUiState, myName: String, onOpenBook: (String) -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state.phase) {
            NearbyPhase.VISIBLE, NearbyPhase.AWAITING_ACCEPT, NearbyPhase.IDLE -> {
                Diagnostics()
                Radar()
                Text("Görünürsün: $myName", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(
                    "Gönderen kişi Okur'da bir kitaba uzun basıp \"Yakındaki Okur'a gönder\"i seçmeli. Bu ekran açık kaldığı sürece kitap alabilirsin.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                SlowHints(state.startedAt, sending = false)
            }
            NearbyPhase.CONNECTING -> {
                Text("${state.peerName ?: "Cihaz"} ile bağlanılıyor…", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            else -> TransferList(state, onOpenBook)
        }
        if (state.phase == NearbyPhase.FAILED || state.phase == NearbyPhase.DONE) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Başka kitap al") }
        }
    }
}

@Composable
private fun TransferList(state: NearbyUiState, onOpenBook: ((String) -> Unit)?) {
    val title = when (state.phase) {
        NearbyPhase.DONE -> if (state.items.any { it.failed }) "Aktarım tamamlandı (bazı kitaplar gönderilemedi)" else "Aktarım tamamlandı"
        NearbyPhase.FAILED -> "Aktarım başarısız"
        else -> "${state.peerName ?: "Cihaz"} ile aktarılıyor…"
    }
    Text(title, style = MaterialTheme.typography.titleLarge)
    state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (state.phase == NearbyPhase.FAILED) Diagnostics()
    state.items.forEach { item -> TransferRow(item, onOpenBook) }
}

@Composable
private fun TransferRow(item: TransferItem, onOpenBook: ((String) -> Unit)?) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                when {
                    item.failed -> Icon(Icons.Filled.Error, "Başarısız", tint = MaterialTheme.colorScheme.error)
                    item.done -> Icon(Icons.Filled.CheckCircle, "Tamamlandı", tint = Color(0xFF3FA66B))
                }
            }
            Spacer(Modifier.height(8.dp))
            val ratio = if (item.total > 0) (item.transferred.toFloat() / item.total).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { if (item.done) 1f else ratio },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.note ?: if (item.done) "Kitaplığa eklendi" else "%${(ratio * 100).toInt()} · ${formatBytes(item.transferred)} / ${formatBytes(item.total)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (onOpenBook != null && item.bookId != null) {
                    TextButton(onClick = { onOpenBook(item.bookId) }) { Text("Aç") }
                }
            }
        }
    }
}

/** Bluetooth, Wi-Fi, konum vb. durumunu gösterir; sorun varsa düzeltme düğmesi sunar. Ekran açıkken yenilenir. */
@Composable
private fun Diagnostics() {
    val context = LocalContext.current
    var items by remember { mutableStateOf(NearbyDiagnostics.check(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_500)
            items = NearbyDiagnostics.check(context)
        }
    }
    val problems = items.filter { !it.ok }
    if (problems.isEmpty()) {
        Text(
            "✓ " + items.joinToString(" · ") { it.label },
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF3FA66B),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        return
    }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cihazların birbirini bulması için:", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
            problems.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (item.required) Icons.Filled.Error else Icons.Filled.Warning, null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    if (item.fix != null) {
                        TextButton(onClick = {
                            runCatching { context.startActivity(item.fix.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }) { Text(item.fixLabel ?: "Düzelt") }
                    }
                }
            }
        }
    }
}

/** Uzun süre kimse bulunamazsa olası çözümleri gösterir. */
@Composable
private fun SlowHints(startedAt: Long, sending: Boolean) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    if (startedAt == 0L || now - startedAt < 15_000) return
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (sending) "Henüz kimse bulunamadı" else "Henüz kimse bağlanmadı", style = MaterialTheme.typography.titleSmall)
            listOf(
                if (sending) "Karşı cihazda Okur açık ve \"Kitap al\" ekranında olmalı." else "Karşı cihaz \"Yakındaki Okur'a gönder\" ekranında olmalı.",
                "İki cihazda da Bluetooth, Wi-Fi ve konum servisi açık olsun; ekranlar açık kalsın.",
                "Cihazları birbirine yaklaştır (1–2 metre).",
                "Olmazsa rolleri değiştirip dene: tablet gönderen, telefon alan olsun.",
                "İki cihazı aynı Wi-Fi ağına bağlamak bulmayı kolaylaştırır.",
                "İki cihazda da Okur'un son sürümü kurulu olmalı.",
            ).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun Radar() {
    val color = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "radar")
    val pulse by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Restart), label = "pulse")
    Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(150.dp)) {
            for (k in 0..2) {
                val p = (pulse + k / 3f) % 1f
                drawCircle(color.copy(alpha = (1f - p) * 0.5f), radius = size.minDimension / 2 * p, style = Stroke(width = 3.dp.toPx()))
            }
            drawCircle(color, radius = 10.dp.toPx())
        }
    }
}

private fun formatBytes(b: Long): String = when {
    b >= 1_048_576 -> String.format("%.1f MB", b / 1_048_576f)
    b >= 1024 -> "${b / 1024} KB"
    else -> "$b B"
}
