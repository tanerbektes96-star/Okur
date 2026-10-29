package com.okur.reader.eyecare

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import com.okur.reader.data.EyePrefs
import com.okur.reader.data.FilterMode
import kotlinx.coroutines.delay
import java.time.LocalTime

/** Zamanlamaya göre mavi ışık filtresinin şu an etkin olup olmadığı. */
fun isNightNow(prefs: EyePrefs, hour: Int = LocalTime.now().hour): Boolean {
    val s = prefs.scheduleStart
    val e = prefs.scheduleEnd
    return if (s == e) false else if (s < e) hour in s until e else hour >= s || hour < e
}

/** Dakikada bir yeniden değerlendirilen saat (zamanlanmış filtre için). */
@Composable
fun rememberCurrentHour(): Int {
    var hour by remember { mutableIntStateOf(LocalTime.now().hour) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            hour = LocalTime.now().hour
        }
    }
    return hour
}

/**
 * Tüm uygulamanın üzerine çizilen, dokunmayı engellemeyen göz koruma katmanı:
 * sıcak renk (mavi ışık) filtresi + sistem minimumunun altına ek karartma.
 */
@Composable
fun EyeComfortOverlay(prefs: EyePrefs, modifier: Modifier = Modifier) {
    val hour = rememberCurrentHour()
    val filterOn = when (prefs.filterMode) {
        FilterMode.OFF -> false
        FilterMode.ON -> true
        FilterMode.SCHEDULE -> isNightNow(prefs, hour)
    }
    val warm by animateFloatAsState(if (filterOn) prefs.filterStrength else 0f, tween(600), label = "warm")
    val dim by animateFloatAsState(prefs.extraDim, tween(300), label = "dim")
    if (warm <= 0.001f && dim <= 0.001f) return
    Canvas(modifier.fillMaxSize()) {
        if (warm > 0f) {
            // Mavi bileşeni bastıran amber katman: çarpma kipi beyazı sıcak tona çevirir
            drawRect(Color(0xFFFFB061).copy(alpha = (warm * 0.85f).coerceAtMost(0.85f)), blendMode = BlendMode.Multiply)
            drawRect(Color(0xFFFF8A00).copy(alpha = warm * 0.12f))
        }
        if (dim > 0f) drawRect(Color.Black.copy(alpha = dim * 0.7f))
    }
}
