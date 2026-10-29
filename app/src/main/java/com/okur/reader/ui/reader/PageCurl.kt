package com.okur.reader.ui.reader

import android.webkit.WebView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.core.view.drawToBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private val TurnEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

/**
 * Gerçekçi sayfa kıvrılması. WebView'in o anki görüntüsü alınır, içerik anında hedef sayfaya geçer ve
 * eski sayfa (ya da önceki sayfa) üstte kıvrılarak açılır/kapanır.
 *
 * İlerleme 0 = kıvrılan sayfa tamamen ekranda, 1 = tamamen çevrilmiş.
 * İleri: kıvrılan = mevcut sayfa, altında canlı WebView (sonraki sayfa).
 * Geri: kıvrılan = önceki sayfa (geçişten sonra yakalanır), altında mevcut sayfanın görüntüsü.
 */
@Stable
class PageCurlState(private val scope: CoroutineScope) {
    var turning by mutableStateOf<ImageBitmap?>(null)
        private set
    var under by mutableStateOf<ImageBitmap?>(null)
        private set
    var active by mutableStateOf(false)
        private set
    val progress = Animatable(0f)

    internal var webView: () -> WebView? = { null }
    internal var jumpBy: (Int) -> Unit = {}

    private var dir = 0
    private var finishing = false
    private var job: Job? = null

    private fun snapshot(): ImageBitmap? {
        val wv = webView() ?: return null
        if (wv.width == 0 || wv.height == 0) return null
        return runCatching { wv.drawToBitmap().asImageBitmap() }.getOrNull()
    }

    private suspend fun awaitVisualState() {
        val wv = webView() ?: return
        withTimeoutOrNull(350) {
            suspendCancellableCoroutine { cont ->
                wv.postVisualStateCallback(System.nanoTime(), object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        if (cont.isActive) cont.resume(Unit)
                    }
                })
            }
        }
    }

    /** Sürükleme ya da dokunmayla çevirme başlar. */
    fun start(direction: Int): Boolean {
        if (active) {
            if (!finishing) return false
            // Önceki otomatik çevirme bitmeden yenisi geldi: öncekini anında tamamla
            job?.cancel()
            clear()
        }
        val snap = snapshot() ?: run { jumpBy(direction); return false }
        dir = direction
        finishing = false
        active = true
        if (direction > 0) {
            turning = snap
            under = null
            scope.launch { progress.snapTo(0f) }
            jumpBy(1)
        } else {
            under = snap
            turning = null
            scope.launch { progress.snapTo(1f) }
            jumpBy(-1)
            scope.launch {
                awaitVisualState()
                turning = snapshot()
            }
        }
        return true
    }

    /** dx: parmağın yatay yer değiştirmesi / ekran genişliği */
    fun drag(dx: Float) {
        if (!active || finishing) return
        val p = if (dir > 0) -dx * 1.1f else 1f - dx * 1.1f
        scope.launch { progress.snapTo(p.coerceIn(0f, 1f)) }
    }

    fun finish(commit: Boolean) {
        if (!active || finishing) return
        finishing = true
        job = scope.launch {
            var waited = 0
            while (turning == null && waited < 400) {
                delay(16); waited += 16
            }
            val target = if (dir > 0) (if (commit) 1f else 0f) else (if (commit) 0f else 1f)
            val distance = kotlin.math.abs(target - progress.value)
            progress.animateTo(target, tween((140 + 300 * distance).toInt(), easing = TurnEasing))
            if (!commit) {
                jumpBy(-dir)
                awaitVisualState()
            }
            clear()
        }
    }

    fun auto(direction: Int) {
        if (start(direction)) finish(commit = true)
    }

    private fun clear() {
        active = false
        finishing = false
        turning = null
        under = null
    }
}

@Composable
fun PageCurlOverlay(state: PageCurlState, paper: Color, modifier: Modifier = Modifier) {
    if (!state.active) return
    val turning = state.turning
    val under = state.under
    Canvas(modifier.fillMaxSize()) {
        under?.let { drawImage(it) }
        if (turning == null) return@Canvas
        drawCurl(turning, state.progress.value, paper)
    }
}

private fun DrawScope.drawCurl(page: ImageBitmap, progress: Float, paper: Color) {
    val w = size.width
    val h = size.height
    val p = progress.coerceIn(0f, 1f)
    if (p >= 0.999f) return
    // Katlanma çizgisi; kıvrılan köşe parmağı takip eder (flapLeft = 2*fold - w)
    val fold = w * (1f - p)
    val flapLeft = 2f * fold - w
    val shadowW = 26f * density * (1f - p * 0.5f) + 6f * density

    // 1) Alttaki sayfaya düşen gölge (katlanma çizgisinin sağı)
    drawRect(
        brush = Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.32f * (1f - p * 0.6f)), Color.Transparent), startX = fold, endX = fold + shadowW),
        topLeft = Offset(fold, 0f),
        size = Size(shadowW, h),
    )

    // 2) Henüz çevrilmemiş kısım
    clipRect(0f, 0f, fold, h) {
        drawImage(page)
        // Katlanan sayfanın, altındaki kısma düşürdüğü yumuşak gölge
        val s = 18f * density
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f)), startX = flapLeft - s, endX = flapLeft),
            topLeft = Offset(flapLeft - s, 0f),
            size = Size(s, h),
        )
    }

    // 3) Sayfanın arka yüzü (katlanan kanat): aynalanmış içerik, kağıt rengiyle örtülü
    val left = flapLeft.coerceAtLeast(-w)
    clipRect(left, 0f, fold, h) {
        withTransform({ scale(-1f, 1f, pivot = Offset(fold, h / 2f)) }) {
            clipRect(fold, 0f, w, h) { drawImage(page) }
        }
        drawRect(paper.copy(alpha = 0.88f), topLeft = Offset(left, 0f), size = Size(fold - left, h))
        // Eğrilik hissi: kenarda parlak, katlanma çizgisine doğru koyulaşan geçiş
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.White.copy(alpha = 0.10f),
                0.65f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.16f),
                startX = left,
                endX = fold,
            ),
            topLeft = Offset(left, 0f),
            size = Size(fold - left, h),
        )
    }
}
