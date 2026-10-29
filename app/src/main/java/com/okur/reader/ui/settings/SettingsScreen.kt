@file:OptIn(ExperimentalMaterial3Api::class)

package com.okur.reader.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.okur.reader.AppContainer
import com.okur.reader.data.AppPrefs
import com.okur.reader.data.OrientationLock
import com.okur.reader.data.ReaderPrefs
import com.okur.reader.data.ThemeMode
import com.okur.reader.ui.common.SettingRow
import com.okur.reader.ui.common.SettingSwitch
import com.okur.reader.ui.common.SettingsGroup
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val app by container.settings.appPrefs.collectAsStateWithLifecycle(initialValue = AppPrefs())
    val reader by container.settings.readerPrefs.collectAsStateWithLifecycle(initialValue = ReaderPrefs())
    val updateApp: ((AppPrefs) -> AppPrefs) -> Unit = { t -> scope.launch { container.settings.updateApp(t) } }
    val updateReader: ((ReaderPrefs) -> ReaderPrefs) -> Unit = { t -> scope.launch { container.settings.updateReader(t) } }
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text("Ayarlar") }, scrollBehavior = scrollBehavior) },
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
            SettingsGroup("Uygulama görünümü") {
                SettingRow(Icons.Filled.WbSunny, "Tema")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    ThemeMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = app.themeMode == m,
                            onClick = { updateApp { it.copy(themeMode = m) } },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) { Text(m.label) }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingSwitch(Icons.Filled.Palette, "Dinamik renkler", "Duvar kağıdına uygun Material You renkleri", app.dynamicColor) { v -> updateApp { it.copy(dynamicColor = v) } }
                }
            }

            SettingsGroup("Okuma") {
                SettingSwitch(Icons.AutoMirrored.Filled.VolumeUp, "Ses tuşlarıyla sayfa çevir", null, reader.volumeKeys) { v -> updateReader { it.copy(volumeKeys = v) } }
                SettingSwitch(Icons.Filled.TouchApp, "Kenarlara dokunarak sayfa çevir", "Sol/sağ kenar: önceki/sonraki, orta: menü", reader.tapZones) { v -> updateReader { it.copy(tapZones = v) } }
                SettingSwitch(Icons.Filled.Lock, "Okurken ekranı açık tut", null, reader.keepScreenOn) { v -> updateReader { it.copy(keepScreenOn = v) } }
                SettingRow(Icons.Filled.ScreenRotation, "Ekran yönü (okuma)")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    OrientationLock.entries.forEachIndexed { i, o ->
                        SegmentedButton(
                            selected = reader.orientation == o,
                            onClick = { updateReader { it.copy(orientation = o) } },
                            shape = SegmentedButtonDefaults.itemShape(i, OrientationLock.entries.size),
                        ) { Text(o.label) }
                    }
                }
            }

            SettingsGroup("Hedefler") {
                SettingRow(Icons.Filled.Flag, "Günlük okuma hedefi", "${app.dailyGoalMin} dakika")
                Slider(
                    value = app.dailyGoalMin.toFloat(),
                    onValueChange = { v -> updateApp { it.copy(dailyGoalMin = (v / 5).roundToInt() * 5) } },
                    valueRange = 5f..180f,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            SettingsGroup("Hakkında") {
                SettingRow(Icons.Filled.Info, "Okur $version", "EPUB, PDF ve TXT okuyucu")
                Text(
                    "Okur tamamen çevrimdışı çalışır. Kitapların, notların ve istatistiklerin yalnızca bu cihazda saklanır; " +
                        "hesap gerekmez, reklam ve izleme yoktur.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
