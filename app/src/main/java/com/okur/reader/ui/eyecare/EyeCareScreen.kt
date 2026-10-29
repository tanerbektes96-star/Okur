@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.okur.reader.ui.eyecare

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.okur.reader.AppContainer
import com.okur.reader.data.EyePrefs
import com.okur.reader.data.FilterMode
import com.okur.reader.eyecare.SmartEyeMonitor
import com.okur.reader.eyecare.SmartEyeState
import com.okur.reader.ui.common.SettingSwitch
import com.okur.reader.ui.common.SettingsGroup
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun EyeCareScreen(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val eye by container.settings.eyePrefs.collectAsStateWithLifecycle(initialValue = EyePrefs())
    val update: ((EyePrefs) -> EyePrefs) -> Unit = { t -> scope.launch { container.settings.updateEye(t) } }

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
        if (ok) update { it.copy(smartEye = true) }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text("Göz Bakımı") }, scrollBehavior = scrollBehavior) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Akıllı Göz ----
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.RemoveRedEye, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Akıllı Göz", style = MaterialTheme.typography.titleLarge)
                            Text("Ön kamera ile cihaz üzerinde yüz ve göz algılama", style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(
                            checked = eye.smartEye && granted,
                            onCheckedChange = { on ->
                                if (on && !granted) permission.launch(Manifest.permission.CAMERA)
                                else update { it.copy(smartEye = on) }
                            },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "🔒 Görüntüler yalnızca cihazında, anlık olarak işlenir; hiçbir zaman kaydedilmez veya internete gönderilmez. " +
                            "Okuma ekranı dışında kamera kullanılmaz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                    if (denied) {
                        Spacer(Modifier.height(8.dp))
                        Text("Kamera izni verilmedi.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                        }) { Text("Uygulama ayarlarını aç") }
                    }
                    AnimatedVisibility(eye.smartEye && granted) {
                        LiveSmartEye(container)
                    }
                }
            }

            SettingsGroup("Akıllı Göz özellikleri") {
                val enabled = eye.smartEye && granted
                SettingSwitch(Icons.Filled.ScreenLockPortrait, "Bakarken ekranı açık tut", "Ekrana bakmadığında 30 sn sonra ekranın normal şekilde kapanmasına izin verir", eye.smartStay, enabled) { v -> update { it.copy(smartStay = v) } }
                SettingSwitch(Icons.Filled.Straighten, "Mesafe uyarısı", "Ekrana fazla yaklaştığında uyarır", eye.distanceAlert, enabled) { v -> update { it.copy(distanceAlert = v) } }
                SettingSwitch(Icons.Filled.Visibility, "Göz kırpma hatırlatıcısı", "Göz kırpma hızın düşünce (göz kuruluğu riski) nazikçe hatırlatır", eye.blinkReminder, enabled) { v -> update { it.copy(blinkReminder = v) } }
                SettingSwitch(Icons.Filled.Face, "Uzaklaşınca ekranı karart", "Yüzün algılanmadığında ekranı karartır, molayı duraklatır", eye.pauseWhenAway, enabled) { v -> update { it.copy(pauseWhenAway = v) } }
            }

            // ---- Mavi ışık filtresi ----
            SettingsGroup("Mavi ışık filtresi") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        FilterMode.entries.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = eye.filterMode == m,
                                onClick = { update { it.copy(filterMode = m) } },
                                shape = SegmentedButtonDefaults.itemShape(i, FilterMode.entries.size),
                            ) { Text(m.label) }
                        }
                    }
                    LabeledSlider("Yoğunluk", "%${(eye.filterStrength / 0.8f * 100).roundToInt()}", eye.filterStrength, 0.05f..0.8f) { v -> update { it.copy(filterStrength = v) } }
                    if (eye.filterMode == FilterMode.SCHEDULE) {
                        LabeledSlider("Başlangıç", "%02d:00".format(eye.scheduleStart), eye.scheduleStart.toFloat(), 0f..23f, steps = 22, icon = Icons.Filled.Bedtime) { v -> update { it.copy(scheduleStart = v.roundToInt()) } }
                        LabeledSlider("Bitiş", "%02d:00".format(eye.scheduleEnd), eye.scheduleEnd.toFloat(), 0f..23f, steps = 22, icon = Icons.Filled.LightMode) { v -> update { it.copy(scheduleEnd = v.roundToInt()) } }
                    }
                    LabeledSlider("Ekstra karartma", "%${(eye.extraDim * 100).roundToInt()}", eye.extraDim, 0f..0.8f, icon = Icons.Filled.BrightnessLow) { v -> update { it.copy(extraDim = v) } }
                    Text(
                        "Ekstra karartma, sistemin en düşük parlaklığından daha karanlık bir ekran sağlar — karanlıkta okurken idealdir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SettingSwitch(Icons.Filled.DarkMode, "Gece otomatik koyu okuma teması", "Filtre saatlerinde okuyucu koyu temaya geçer", eye.autoNightTheme) { v -> update { it.copy(autoNightTheme = v) } }
            }

            // ---- Göz molası ----
            SettingsGroup("Göz molası (20-20-20)") {
                SettingSwitch(Icons.Filled.SelfImprovement, "Mola hatırlatıcısı", "Her ${eye.breakIntervalMin} dakikada bir 20 saniyelik uzağa bakma molası", eye.breakReminder) { v -> update { it.copy(breakReminder = v) } }
                FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 15, 20, 30, 45, 60).forEach { m ->
                        FilterChip(
                            selected = eye.breakIntervalMin == m,
                            onClick = { update { it.copy(breakIntervalMin = m, breakReminder = true) } },
                            label = { Text("$m dk") },
                            leadingIcon = if (eye.breakIntervalMin == m) ({ Icon(Icons.Filled.Timer, null, Modifier.size(16.dp)) }) else null,
                        )
                    }
                }
            }

            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Göz sağlığı ipuçları", style = MaterialTheme.typography.titleMedium)
                    listOf(
                        "Ekranı göz hizasının biraz altında ve 40–60 cm uzakta tut.",
                        "Karanlıkta okurken Koyu veya Siyah temayı ve mavi ışık filtresini kullan.",
                        "Yazı boyutunu kısmak yerine büyüt; gözlerini kısmak yorgunluğu artırır.",
                        "Sepya ve Kağıt temaları uzun okumalarda kontrastı yumuşatır.",
                    ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
private fun LiveSmartEye(container: AppContainer) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val monitor = remember { SmartEyeMonitor(context.applicationContext) }
    DisposableEffect(owner) {
        monitor.start(owner)
        onDispose { monitor.stop() }
    }
    val state by monitor.state.collectAsStateWithLifecycle()
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Canlı durum", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(Icons.Filled.CameraFront, if (state.active) "Kamera açık" else "Başlatılıyor…", state.active)
            StatusChip(Icons.Filled.Face, if (state.facePresent) "Yüz algılandı" else "Yüz yok", state.facePresent)
            StatusChip(Icons.Filled.Visibility, if (state.looking) "Ekrana bakıyorsun" else "Bakmıyorsun", state.looking)
        }
        DistanceMeter(state)
        Text(
            if (state.blinksPerMinute < 0) "Göz kırpma hızı ölçülüyor (1 dk)…" else "Göz kırpma: dakikada ${state.blinksPerMinute} (okurken 10+ önerilir)",
            style = MaterialTheme.typography.bodySmall,
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun DistanceMeter(state: SmartEyeState) {
    val ratio = (state.faceRatio / 0.7f).coerceIn(0f, 1f)
    val label = when {
        !state.facePresent -> "Mesafe: —"
        state.faceRatio > 0.58f -> "Mesafe: çok yakın"
        state.faceRatio > 0.42f -> "Mesafe: biraz yakın"
        else -> "Mesafe: iyi"
    }
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(
            progress = { ratio },
            color = if (state.faceRatio > 0.58f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().height(6.dp),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun StatusChip(icon: ImageVector, label: String, ok: Boolean) {
    FilterChip(
        selected = ok,
        onClick = {},
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(16.dp), tint = if (ok) Color(0xFF2E7D4F) else MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    icon: ImageVector? = null,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.widthIn(max = 720.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(8.dp)) }
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge)
        }
        Slider(value = current, onValueChange = onChange, valueRange = range, steps = steps)
    }
}
