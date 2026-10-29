package com.okur.reader.ui.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.okur.reader.data.PageMode
import com.okur.reader.data.ReaderTheme
import com.okur.reader.format.PdfDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@Stable
class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var size: IntSize = IntSize.Zero
    var allowVerticalPan = true

    val zoomed: Boolean get() = scale > 1.01f

    fun zoomBy(factor: Float, centroid: Offset) {
        val newScale = (scale * factor).coerceIn(1f, 5f)
        val c = centroid - Offset(size.width / 2f, size.height / 2f)
        offset = (offset - c) * (newScale / scale) + c
        scale = newScale
        clamp()
    }

    fun panBy(delta: Offset) {
        offset += if (allowVerticalPan) delta else Offset(delta.x, 0f)
        clamp()
    }

    fun toggle(at: Offset) {
        if (zoomed) reset() else {
            val c = at - Offset(size.width / 2f, size.height / 2f)
            scale = 2.5f
            offset = -c * 1.5f
            clamp()
        }
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    private fun clamp() {
        val maxX = (scale - 1f) * size.width / 2f
        val maxY = if (allowVerticalPan) (scale - 1f) * size.height / 2f else 0f
        offset = Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
        if (scale <= 1f) offset = Offset.Zero
    }
}

/** Beyazı tema arka planına, siyahı tema metin rengine eşleyen renk matrisi. */
fun themeColorFilter(theme: ReaderTheme): ColorFilter? {
    if (theme == ReaderTheme.LIGHT) return null
    val bg = theme.background
    val fg = theme.text
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                bg.red - fg.red, 0f, 0f, 0f, fg.red * 255f,
                0f, bg.green - fg.green, 0f, 0f, fg.green * 255f,
                0f, 0f, bg.blue - fg.blue, 0f, fg.blue * 255f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
}

private fun Modifier.zoomGestures(zoom: ZoomState, singleFingerPan: Boolean, onTap: (Offset) -> Unit): Modifier =
    this
        .onSizeChanged { zoom.size = it }
        .pointerInput(zoom, singleFingerPan) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.count { it.pressed }
                    if (pressed >= 2) {
                        zoom.zoomBy(event.calculateZoom(), event.calculateCentroid())
                        zoom.panBy(event.calculatePan())
                        event.changes.forEach { it.consume() }
                    } else if (zoom.zoomed) {
                        val pan = event.calculatePan()
                        if (singleFingerPan) {
                            if (pan != Offset.Zero) {
                                zoom.panBy(pan)
                                event.changes.forEach { it.consume() }
                            }
                        } else {
                            // Kaydırma modunda dikey hareket listeye kalır, yatay hareket yakınlaştırılmış sayfayı kaydırır
                            zoom.panBy(Offset(pan.x, 0f))
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
        .pointerInput(zoom) {
            detectTapGestures(onDoubleTap = { zoom.toggle(it) }, onTap = onTap)
        }

@Composable
fun PdfReader(
    doc: PdfDocument,
    initialPage: Int,
    jump: Pair<Int, Long>?,
    commands: Flow<Int>,
    mode: PageMode,
    dualPage: Boolean,
    tapZones: Boolean,
    theme: ReaderTheme,
    followTheme: Boolean,
    onPage: (Int) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filter = remember(theme, followTheme) { if (followTheme) themeColorFilter(theme) else null }
    val pageBackdrop = if (theme.isDark && followTheme) Color(0xFF0E0E0E) else Color(0xFFE7E3DC)
    val currentOnPage by rememberUpdatedState(onPage)

    BoxWithConstraints(modifier.fillMaxSize().background(pageBackdrop)) {
        val density = LocalDensity.current
        val wide = maxWidth > maxHeight && maxWidth >= 840.dp
        val zoom = remember { ZoomState() }
        val scope = rememberCoroutineScope()

        if (mode == PageMode.SCROLL) {
            zoom.allowVerticalPan = false
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage.coerceIn(0, max(0, doc.pageCount - 1)))
            val contentWidth = min(maxWidth.value, 1000f).dp
            val renderWidth = with(density) { contentWidth.roundToPx() } * (if (zoom.scale > 1.8f) 2 else 1)

            val current by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
                    info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index
                        ?: listState.firstVisibleItemIndex
                }
            }
            LaunchedEffect(Unit) {
                snapshotFlow { current }.distinctUntilChanged().collect { currentOnPage(it) }
            }
            LaunchedEffect(current, renderWidth) {
                for (i in current + 1..min(current + 2, doc.pageCount - 1)) doc.render(i, renderWidth)
            }
            LaunchedEffect(jump) { jump?.let { listState.scrollToItem(it.first) } }
            LaunchedEffect(Unit) {
                commands.collect { dir ->
                    val viewport = listState.layoutInfo.viewportSize.height
                    listState.animateScrollBy(dir * viewport * 0.85f)
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .zoomGestures(zoom, singleFingerPan = false) { pos ->
                        // Kaydırma modunda kenarlara dokunmak ekran ekran ilerletir
                        val w = zoom.size.width.toFloat()
                        val h = zoom.size.height.toFloat()
                        when {
                            tapZones && !zoom.zoomed && pos.x < w * 0.25f -> scope.launch { listState.animateScrollBy(-h * 0.85f) }
                            tapZones && !zoom.zoomed && pos.x > w * 0.75f -> scope.launch { listState.animateScrollBy(h * 0.85f) }
                            else -> onTap()
                        }
                    }
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom.scale
                            scaleY = zoom.scale
                            translationX = zoom.offset.x
                        },
                ) {
                    items(doc.pageCount, key = { it }) { index ->
                        PdfPageImage(
                            doc = doc,
                            index = index,
                            widthPx = renderWidth,
                            filter = filter,
                            placeholder = theme.background,
                            modifier = Modifier.widthIn(max = contentWidth).fillMaxWidth(),
                        )
                    }
                }
            }
        } else {
            zoom.allowVerticalPan = true
            val perSpread = if (dualPage && wide) 2 else 1
            val spreads = ceil(doc.pageCount / perSpread.toFloat()).toInt().coerceAtLeast(1)
            val pagerState = rememberPagerState(initialPage = (initialPage / perSpread).coerceIn(0, spreads - 1)) { spreads }
            val renderWidth = with(density) { (maxWidth / perSpread).roundToPx() } * (if (zoom.scale > 1.8f) 2 else 1)

            LaunchedEffect(pagerState, perSpread) {
                snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect {
                    zoom.reset()
                    currentOnPage(it * perSpread)
                }
            }
            LaunchedEffect(pagerState.currentPage, renderWidth) {
                val first = (pagerState.currentPage + 1) * perSpread
                for (i in first until min(first + perSpread * 2, doc.pageCount)) doc.render(i, renderWidth)
            }
            LaunchedEffect(jump) { jump?.let { pagerState.scrollToPage(it.first / perSpread) } }
            LaunchedEffect(pagerState) {
                commands.collect { dir ->
                    val target = (pagerState.currentPage + dir).coerceIn(0, spreads - 1)
                    pagerState.animateScrollToPage(target)
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .zoomGestures(zoom, singleFingerPan = true) { pos ->
                        val w = zoom.size.width.toFloat()
                        when {
                            tapZones && !zoom.zoomed && pos.x < w * 0.28f ->
                                scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) }
                            tapZones && !zoom.zoomed && pos.x > w * 0.72f ->
                                scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(spreads - 1)) }
                            else -> onTap()
                        }
                    }
            ) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !zoom.zoomed,
                    beyondViewportPageCount = 1,
                    key = { it },
                    modifier = Modifier.fillMaxSize(),
                ) { spread ->
                    val isCurrent = spread == pagerState.currentPage
                    Row(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (isCurrent) {
                                    scaleX = zoom.scale
                                    scaleY = zoom.scale
                                    translationX = zoom.offset.x
                                    translationY = zoom.offset.y
                                }
                            }
                            .padding(horizontal = if (perSpread == 2) 12.dp else 0.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (k in 0 until perSpread) {
                            val index = spread * perSpread + k
                            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = if (perSpread == 2) (if (k == 0) Alignment.CenterEnd else Alignment.CenterStart) else Alignment.Center) {
                                if (index < doc.pageCount) {
                                    PdfPageImage(
                                        doc = doc,
                                        index = index,
                                        widthPx = renderWidth,
                                        filter = filter,
                                        placeholder = theme.background,
                                        fitHeight = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageImage(
    doc: PdfDocument,
    index: Int,
    widthPx: Int,
    filter: ColorFilter?,
    placeholder: Color,
    modifier: Modifier = Modifier,
    fitHeight: Boolean = false,
) {
    var bitmap by remember(index, widthPx) { mutableStateOf<Bitmap?>(doc.cached(index, widthPx)) }
    var aspect by remember(index) { mutableFloatStateOf(doc.knownAspect(index) ?: 1.414f) }
    LaunchedEffect(index, widthPx) {
        if (bitmap == null) {
            aspect = doc.aspect(index)
            bitmap = doc.render(index, widthPx)
        }
        bitmap?.let { aspect = it.height.toFloat() / it.width }
    }
    Box(
        modifier
            .aspectRatio(1f / aspect, matchHeightConstraintsFirst = fitHeight)
            .background(placeholder),
        contentAlignment = Alignment.Center,
    ) {
        val image = remember(bitmap) { bitmap?.asImageBitmap() }
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "Sayfa ${index + 1}",
                contentScale = ContentScale.FillBounds,
                colorFilter = filter,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
        }
    }
}
