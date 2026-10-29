@file:OptIn(ExperimentalMaterial3Api::class)

package com.okur.reader.ui.reader

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.okur.reader.AppContainer
import com.okur.reader.data.EyePrefs
import com.okur.reader.data.OrientationLock
import com.okur.reader.data.PageMode
import com.okur.reader.data.ReaderPrefs
import com.okur.reader.data.ReaderTheme
import com.okur.reader.eyecare.SmartEyeMonitor
import com.okur.reader.eyecare.isNightNow
import com.okur.reader.eyecare.rememberCurrentHour
import com.okur.reader.reader.ReaderKeys
import com.okur.reader.tts.TtsController
import com.okur.reader.tts.TtsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun ReaderScreen(bookId: String, container: AppContainer, onBack: () -> Unit) {
    val vm: ReaderViewModel = viewModel(
        key = "reader-$bookId",
        factory = viewModelFactory { initializer { ReaderViewModel(container, bookId) } },
    )
    val prefsOrNull by container.settings.readerPrefs.collectAsStateWithLifecycle(initialValue = null)
    val eye by container.settings.eyePrefs.collectAsStateWithLifecycle(initialValue = EyePrefs())
    val prefs = prefsOrNull
    val content by vm.content.collectAsStateWithLifecycle()

    if (prefs == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    ReaderScaffold(vm, container, prefs, eye, content, onBack)
}

@Composable
private fun ReaderScaffold(
    vm: ReaderViewModel,
    container: AppContainer,
    prefs: ReaderPrefs,
    eye: EyePrefs,
    content: ReaderContent,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val book by vm.book.collectAsStateWithLifecycle()
    val request by vm.request.collectAsStateWithLifecycle()
    val reflowPos by vm.reflowPos.collectAsStateWithLifecycle()
    val pdfPage by vm.pdfPage.collectAsStateWithLifecycle()
    val pdfJump by vm.pdfJump.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val chromeVisible by vm.chromeVisible.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val searchState by vm.search.collectAsStateWithLifecycle()
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    val updatePrefs: ((ReaderPrefs) -> ReaderPrefs) -> Unit = { t -> scope.launch { container.settings.updateReader(t) } }
    val updateEye: ((EyePrefs) -> EyePrefs) -> Unit = { t -> scope.launch { container.settings.updateEye(t) } }

    // Gece saatlerinde otomatik koyu tema (Akıllı Göz ayarı)
    val hour = rememberCurrentHour()
    val theme = if (eye.autoNightTheme && isNightNow(eye, hour) && !prefs.theme.isDark) ReaderTheme.DARK else prefs.theme
    val chromeBg = lerp(theme.background, theme.text, 0.07f)

    val webController = remember { WebReaderController() }
    val pdfCommands = remember { MutableSharedFlow<Int>(extraBufferCapacity = 8) }
    val snackbar = remember { SnackbarHostState() }

    var showAppearance by remember { mutableStateOf(false) }
    var showContents by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showGoTo by remember { mutableStateOf(false) }
    var sliderDrag by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(Unit) { vm.eventFlow.collect { snackbar.showSnackbar(it) } }

    // ---- Okuma süresi istatistiği ----
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> vm.sessionResume()
                Lifecycle.Event.ON_PAUSE -> vm.sessionPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.sessionPause()
        }
    }

    // ---- Akıllı Göz ----
    val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val smartEyeOn = eye.smartEye && cameraGranted
    val monitor = remember { SmartEyeMonitor(context.applicationContext) }
    DisposableEffect(smartEyeOn, lifecycleOwner) {
        if (smartEyeOn) monitor.start(lifecycleOwner)
        onDispose { monitor.stop() }
    }
    val eyeState by monitor.state.collectAsStateWithLifecycle()
    val userAway = smartEyeOn && eyeState.active && eyeState.awayForMs > 8_000
    val currentAway by rememberUpdatedState(userAway)

    var blinkHintAt by remember { mutableLongStateOf(0L) }
    var showBlinkHint by remember { mutableStateOf(false) }
    LaunchedEffect(eyeState.blinksPerMinute, eye.blinkReminder, smartEyeOn) {
        val bpm = eyeState.blinksPerMinute
        val now = System.currentTimeMillis()
        if (smartEyeOn && eye.blinkReminder && bpm in 0..5 && eyeState.facePresent && now - blinkHintAt > 180_000) {
            blinkHintAt = now
            showBlinkHint = true
            delay(6_000)
            showBlinkHint = false
        }
    }

    // ---- Göz molası (20-20-20) ----
    var breakDue by remember { mutableStateOf(false) }
    var readMs by rememberSaveable { mutableLongStateOf(0L) }
    val currentResumed by rememberUpdatedState(resumed)
    LaunchedEffect(eye.breakReminder, eye.breakIntervalMin) {
        if (!eye.breakReminder) return@LaunchedEffect
        while (true) {
            delay(1_000)
            if (currentResumed && !breakDue && !currentAway) {
                readMs += 1_000
                if (readMs >= eye.breakIntervalMin * 60_000L) {
                    readMs = 0
                    breakDue = true
                }
            }
        }
    }

    // ---- Pencere: tam ekran, parlaklık, ekranı açık tutma, yön ----
    val window = activity?.window
    val insetsController = remember(window, view) { window?.let { WindowCompat.getInsetsController(it, view) } }
    LaunchedEffect(chromeVisible, insetsController) {
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chromeVisible) insetsController?.show(WindowInsetsCompat.Type.systemBars())
        else insetsController?.hide(WindowInsetsCompat.Type.systemBars())
    }
    LaunchedEffect(theme, insetsController) {
        insetsController?.isAppearanceLightStatusBars = !theme.isDark
        insetsController?.isAppearanceLightNavigationBars = !theme.isDark
    }
    val keepOn = prefs.keepScreenOn && !(smartEyeOn && eye.smartStay && eyeState.awayForMs > 30_000)
    LaunchedEffect(keepOn, window) {
        if (keepOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    LaunchedEffect(prefs.brightness, window) {
        window?.let { w ->
            w.attributes = w.attributes.apply {
                screenBrightness = if (prefs.brightness < 0) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else prefs.brightness
            }
        }
    }
    LaunchedEffect(prefs.orientation, activity) {
        activity?.requestedOrientation = when (prefs.orientation) {
            OrientationLock.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            OrientationLock.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            OrientationLock.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
    DisposableEffect(window) {
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // ---- Sesli okuma ----
    val tts = remember { TtsController(context) }
    val ttsState by tts.state.collectAsStateWithLifecycle()
    val ttsVoices by tts.voices.collectAsStateWithLifecycle()
    val speaking = ttsState == TtsState.SPEAKING
    val ttsActive = ttsState != TtsState.IDLE
    var ttsContinue by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    val reflowBook = (content as? ReaderContent.Reflow)?.book
    val ttsLanguage = reflowBook?.language ?: book?.language
    LaunchedEffect(ttsLanguage, prefs.ttsRate, prefs.ttsPitch, prefs.ttsVoice) {
        tts.configure(ttsLanguage, prefs.ttsRate, prefs.ttsPitch, prefs.ttsVoice)
        tts.restartCurrent()
    }
    val startTts: () -> Unit = {
        webController.textFromHere { text ->
            if (text.isBlank()) vm.nextChapter().also { ttsContinue = true }
            else tts.speak(text)
        }
    }
    // Üst çubuktaki düğme: oku → duraklat → kaldığın yerden devam
    val toggleTts: () -> Unit = {
        when (ttsState) {
            TtsState.SPEAKING -> tts.pause()
            TtsState.PAUSED -> tts.resume()
            TtsState.IDLE -> startTts()
        }
    }
    tts.onChunkStart = { webController.ensureVisible(it) }
    tts.onFinished = {
        val idx = request?.index ?: 0
        if (reflowBook != null && idx < reflowBook.chapters.lastIndex) {
            ttsContinue = true
            vm.nextChapter()
        }
    }
    LaunchedEffect(request?.token) {
        if (!ttsContinue) return@LaunchedEffect
        ttsContinue = false
        // Bölüm yüklenene kadar bekle
        repeat(15) {
            delay(400)
            webController.textFromHere { text ->
                if (text.isNotBlank() && tts.state.value == TtsState.IDLE) tts.speak(text)
            }
            delay(200)
            if (tts.state.value != TtsState.IDLE) return@LaunchedEffect
        }
    }

    // ---- Ses tuşları ----
    val turnPage: (Int) -> Unit = { dir ->
        when (content) {
            is ReaderContent.Reflow -> if (dir > 0) webController.next() else webController.prev()
            is ReaderContent.Pdf -> pdfCommands.tryEmit(dir)
            else -> Unit
        }
    }
    val currentTurn by rememberUpdatedState(turnPage)
    val currentSpeaking by rememberUpdatedState(speaking)
    DisposableEffect(prefs.volumeKeys) {
        val handler: (Int) -> Boolean = { code ->
            when {
                currentSpeaking -> false
                code == KeyEvent.KEYCODE_VOLUME_DOWN -> { currentTurn(1); true }
                code == KeyEvent.KEYCODE_VOLUME_UP -> { currentTurn(-1); true }
                else -> false
            }
        }
        ReaderKeys.handler = if (prefs.volumeKeys) handler else null
        onDispose { ReaderKeys.handler = null }
    }

    val awayDim by animateFloatAsState(if (userAway && eye.pauseWhenAway && !speaking) 0.55f else 0f, tween(800), label = "away")

    CompositionLocalProvider(LocalContentColor provides theme.text) {
        Box(Modifier.fillMaxSize().background(theme.background)) {
            when (val c = content) {
                is ReaderContent.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = theme.link)
                is ReaderContent.Error -> ErrorState(c.message, onBack)
                is ReaderContent.Reflow -> EpubWebReader(
                    book = c.book,
                    request = request,
                    prefs = prefs,
                    theme = theme,
                    controller = webController,
                    onPage = vm::onReflowPage,
                    onScroll = vm::onReflowScroll,
                    onTap = vm::toggleChrome,
                    onNextChapter = vm::nextChapter,
                    onPrevChapter = vm::prevChapter,
                    onLink = { uri ->
                        val target = internalLinkTarget(uri)
                        if (target != null) {
                            val index = c.book.chapterIndexOf(target.first)
                            if (index == request?.index && target.second != null) webController.goToAnchor(target.second!!)
                            else if (index >= 0) vm.openChapter(index, target.second)
                        } else if (uri.scheme == "http" || uri.scheme == "https" || uri.scheme == "mailto") {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        }
                    },
                    chromeVisible = chromeVisible,
                    modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout),
                )
                is ReaderContent.Pdf -> PdfReader(
                    doc = c.doc,
                    initialPage = pdfPage,
                    jump = pdfJump,
                    commands = pdfCommands,
                    mode = prefs.pdfPageMode,
                    dualPage = prefs.dualPage,
                    tapZones = prefs.tapZones,
                    theme = theme,
                    followTheme = prefs.pdfFollowTheme,
                    chromeVisible = chromeVisible,
                    pageTurn = prefs.pageTurn,
                    onPage = vm::onPdfPage,
                    onTap = vm::toggleChrome,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Sayfa bilgisi (arayüz gizliyken)
            AnimatedVisibility(
                visible = !chromeVisible && content !is ReaderContent.Loading,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                FooterStatus(content, reflowPos, pdfPage, progress, prefs, theme)
            }

            // Kullanıcı uzaklaşınca ekranı karart (Akıllı Göz)
            if (awayDim > 0f) Canvas(Modifier.fillMaxSize()) { drawRect(Color.Black.copy(alpha = awayDim)) }

            // Üst çubuk
            AnimatedVisibility(
                visible = chromeVisible,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                TopChrome(
                    title = book?.title.orEmpty(),
                    subtitle = when (content) {
                        is ReaderContent.Reflow -> (content as ReaderContent.Reflow).book.titleForChapter(reflowPos.chapter)
                        is ReaderContent.Pdf -> "Sayfa ${pdfPage + 1} / ${(content as ReaderContent.Pdf).doc.pageCount}"
                        else -> null
                    },
                    background = chromeBg,
                    isReflow = content is ReaderContent.Reflow,
                    bookmarked = vm.currentBookmark(bookmarks) != null,
                    ttsState = ttsState,
                    smartEyeOn = smartEyeOn,
                    looking = eyeState.looking,
                    onBack = onBack,
                    onSearch = { showSearch = true },
                    onBookmark = {
                        if (content is ReaderContent.Reflow) webController.visibleSnippet { vm.toggleBookmark(it) }
                        else vm.toggleBookmark("")
                    },
                    onTts = toggleTts,
                    onAppearance = { showAppearance = true },
                )
            }

            // Uyarılar
            Column(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = if (chromeVisible) 72.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(smartEyeOn && eye.distanceAlert && eyeState.tooClose, enter = fadeIn(), exit = fadeOut()) {
                    Hint("Ekrana çok yakınsın — gözlerini korumak için biraz uzaklaş 👀")
                }
                AnimatedVisibility(showBlinkHint, enter = fadeIn(), exit = fadeOut()) {
                    Hint("Göz kırpmayı unutma 😌 Birkaç kez yavaşça kırp")
                }
            }

            // İlk açılışta dokunma bölgeleri rehberi
            if (!prefs.tapGuideShown && (content is ReaderContent.Reflow || content is ReaderContent.Pdf)) {
                TapGuide(tapZones = prefs.tapZones) {
                    vm.setChrome(false)
                    updatePrefs { it.copy(tapGuideShown = true) }
                }
            }

            // Alt çubuk
            Column(Modifier.align(Alignment.BottomCenter)) {
                SnackbarHost(snackbar, Modifier.align(Alignment.CenterHorizontally))
                AnimatedVisibility(ttsActive, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    TtsPill(
                        background = chromeBg,
                        rate = prefs.ttsRate,
                        paused = ttsState == TtsState.PAUSED,
                        onPlayPause = toggleTts,
                        onStop = { tts.stop() },
                        onRate = { r -> updatePrefs { it.copy(ttsRate = r) } },
                    )
                }
                AnimatedVisibility(
                    visible = chromeVisible,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    BottomChrome(
                        background = chromeBg,
                        theme = theme,
                        progress = sliderDrag ?: progress,
                        label = bottomLabel(content, reflowPos, pdfPage, sliderDrag ?: progress, sliderDrag != null),
                        isPdf = content is ReaderContent.Pdf,
                        isDark = theme.isDark,
                        onSlide = { sliderDrag = it },
                        onSlideEnd = { sliderDrag?.let { vm.jumpToProgress(it) }; sliderDrag = null },
                        onContents = { showContents = true },
                        onAppearance = { showAppearance = true },
                        onLabelClick = { if (content is ReaderContent.Pdf) showGoTo = true },
                        onToggleNight = {
                            updatePrefs { p -> p.copy(theme = if (p.theme.isDark) ReaderTheme.PAPER else ReaderTheme.DARK) }
                        },
                    )
                }
            }
        }
    }

    // ---- Sayfalar ----
    if (showAppearance) {
        AppearanceSheet(
            prefs = prefs,
            eye = eye,
            isPdf = content is ReaderContent.Pdf,
            onPrefs = updatePrefs,
            onEye = updateEye,
            voices = ttsVoices,
            onPreviewVoice = { tts.preview("Merhaba, bu ses ile kitabını okuyabilirim.") },
            onDismiss = { showAppearance = false },
        )
    }
    if (showContents) {
        val toc = (content as? ReaderContent.Reflow)?.book?.toc.orEmpty()
        ContentsSheet(
            toc = toc,
            currentChapter = if (content is ReaderContent.Pdf) pdfPage else reflowPos.chapter,
            bookmarks = bookmarks,
            onToc = { entry -> showContents = false; vm.openChapter(entry.chapterIndex, entry.anchor) },
            onBookmark = { b -> showContents = false; vm.openBookmark(b) },
            onEditNote = { b, note -> vm.updateBookmarkNote(b, note) },
            onDeleteBookmark = { vm.deleteBookmark(it) },
            onDismiss = { showContents = false },
        )
    }
    if (showSearch && reflowBook != null) {
        SearchSheet(
            state = searchState,
            onSearch = vm::search,
            onHit = { hit -> showSearch = false; vm.setChrome(false); vm.openSearchHit(hit) },
            onDismiss = { showSearch = false; webController.clearSearch() },
        )
    }
    if (showGoTo && content is ReaderContent.Pdf) {
        GoToPageDialog(
            pageCount = (content as ReaderContent.Pdf).doc.pageCount,
            current = pdfPage,
            onGo = { p -> showGoTo = false; vm.jumpToPdfPage(p) },
            onDismiss = { showGoTo = false },
        )
    }
    if (breakDue) {
        EyeBreakDialog(
            onDone = { breakDue = false },
            onSnooze = {
                breakDue = false
                readMs = (eye.breakIntervalMin * 60_000L - 5 * 60_000L).coerceAtLeast(0)
            },
        )
    }
}

private fun bottomLabel(content: ReaderContent, pos: ReflowPosition, pdfPage: Int, progress: Float, dragging: Boolean): String {
    val percent = "%${(progress * 100).roundToInt()}"
    return when (content) {
        is ReaderContent.Pdf -> {
            val count = content.doc.pageCount
            if (dragging) "Sayfa ${((count - 1) * progress).toInt() + 1} / $count" else "Sayfa ${pdfPage + 1} / $count · $percent"
        }
        is ReaderContent.Reflow -> {
            if (dragging) {
                val (idx, _) = content.book.positionFor(progress)
                "$percent · ${content.book.titleForChapter(idx) ?: "Bölüm ${idx + 1}"}"
            } else {
                val left = (pos.pages - pos.page - 1).coerceAtLeast(0)
                if (pos.pages > 1) "$percent · Bölümde $left sayfa kaldı" else percent
            }
        }
        else -> ""
    }
}

@Composable
private fun TopChrome(
    title: String,
    subtitle: String?,
    background: Color,
    isReflow: Boolean,
    bookmarked: Boolean,
    ttsState: TtsState,
    smartEyeOn: Boolean,
    looking: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onBookmark: () -> Unit,
    onTts: () -> Unit,
    onAppearance: () -> Unit,
) {
    Surface(color = background, contentColor = LocalContentColor.current, shadowElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = LocalContentColor.current.copy(alpha = 0.7f))
                }
            }
            if (smartEyeOn) {
                Icon(
                    if (looking) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = if (looking) "Akıllı Göz: ekrana bakıyorsun" else "Akıllı Göz: yüz algılanmadı",
                    tint = if (looking) Color(0xFF3FA66B) else LocalContentColor.current.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 6.dp).size(20.dp),
                )
            }
            if (isReflow) {
                IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Kitapta ara") }
                IconButton(onClick = onTts) {
                    when (ttsState) {
                        TtsState.SPEAKING -> Icon(Icons.Filled.Pause, "Sesli okumayı duraklat")
                        TtsState.PAUSED -> Icon(Icons.Filled.PlayArrow, "Sesli okumaya devam et")
                        TtsState.IDLE -> Icon(Icons.Filled.RecordVoiceOver, "Sesli oku")
                    }
                }
            }
            IconButton(onClick = onBookmark) {
                Icon(
                    if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    if (bookmarked) "Yer imini kaldır" else "Yer imi ekle",
                    tint = if (bookmarked) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
            IconButton(onClick = onAppearance) { Icon(Icons.Filled.TextFields, "Görünüm") }
        }
    }
}

@Composable
private fun BottomChrome(
    background: Color,
    theme: ReaderTheme,
    progress: Float,
    label: String,
    isPdf: Boolean,
    isDark: Boolean,
    onSlide: (Float) -> Unit,
    onSlideEnd: () -> Unit,
    onContents: () -> Unit,
    onAppearance: () -> Unit,
    onLabelClick: () -> Unit,
    onToggleNight: () -> Unit,
) {
    Surface(color = background, contentColor = LocalContentColor.current, shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable(enabled = isPdf, onClick = onLabelClick)
                    .padding(vertical = 4.dp, horizontal = 8.dp),
            )
            Slider(
                value = progress.coerceIn(0f, 1f),
                onValueChange = onSlide,
                onValueChangeFinished = onSlideEnd,
                colors = SliderDefaults.colors(
                    thumbColor = theme.link,
                    activeTrackColor = theme.link,
                    inactiveTrackColor = theme.text.copy(alpha = 0.18f),
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ChromeAction(Icons.AutoMirrored.Filled.FormatListBulleted, "İçindekiler", onContents)
                ChromeAction(Icons.Filled.TextFields, "Görünüm", onAppearance)
                ChromeAction(if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode, if (isDark) "Gündüz" else "Gece", onToggleNight)
            }
        }
    }
}

@Composable
private fun ChromeAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .widthIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TtsPill(
    background: Color,
    rate: Float,
    paused: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onRate: (Float) -> Unit,
) {
    val rates = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
    Surface(color = background, shape = RoundedCornerShape(28.dp), shadowElevation = 4.dp, modifier = Modifier.padding(bottom = 10.dp)) {
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(onClick = onPlayPause) {
                Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, if (paused) "Devam et" else "Duraklat")
            }
            Text(
                if (paused) "Duraklatıldı" else "Sesli okunuyor",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            TextButton(onClick = { onRate(rates[(rates.indexOf(rate).coerceAtLeast(0) + 1) % rates.size]) }) {
                Icon(Icons.Filled.Speed, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("${rate}x")
            }
            IconButton(onClick = onStop) { Icon(Icons.Filled.Stop, "Sesli okumayı bitir") }
        }
    }
}

@Composable
private fun FooterStatus(content: ReaderContent, pos: ReflowPosition, pdfPage: Int, progress: Float, prefs: ReaderPrefs, theme: ReaderTheme) {
    val color = theme.text.copy(alpha = 0.45f)
    val text = when (content) {
        is ReaderContent.Reflow -> {
            val chapter = content.book.titleForChapter(pos.chapter)
            val pages = if (prefs.pageMode == PageMode.PAGED && pos.pages > 1) "${pos.page + 1}/${pos.pages}  ·  " else ""
            listOfNotNull(chapter?.take(40), "$pages%${(progress * 100).roundToInt()}").joinToString("    ")
        }
        is ReaderContent.Pdf -> "${pdfPage + 1} / ${content.doc.pageCount}"
        else -> ""
    }
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.navigationBarsPadding().padding(bottom = 6.dp, start = 24.dp, end = 24.dp),
    )
}

@Composable
private fun TapGuide(tapZones: Boolean, onDismiss: () -> Unit) {
    val scrim = Color.Black.copy(alpha = 0.62f)
    Box(
        Modifier
            .zIndex(10f)
            .fillMaxSize()
            .background(scrim)
            .clickable(onClick = onDismiss),
    ) {
        Row(Modifier.fillMaxSize()) {
            if (tapZones) GuideZone("‹", "Önceki sayfa", Modifier.weight(1f))
            GuideZone("☰", "Menüyü aç / kapat", Modifier.weight(2f), highlighted = true)
            if (tapZones) GuideZone("›", "Sonraki sayfa", Modifier.weight(1f))
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Sayfayı parmağınla sağa-sola kaydırarak da çevirebilirsin. Menü açıkken herhangi bir yere dokunmak menüyü kapatır.",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.size(16.dp))
            Button(onClick = onDismiss) { Text("Anladım") }
        }
    }
}

@Composable
private fun GuideZone(symbol: String, label: String, modifier: Modifier, highlighted: Boolean = false) {
    Column(
        modifier
            .fillMaxSize()
            .padding(6.dp)
            .background(Color.White.copy(alpha = if (highlighted) 0.10f else 0.05f), RoundedCornerShape(20.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(symbol, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.size(8.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.titleSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun Hint(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.RemoveRedEye, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ErrorState(message: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("😕", fontSize = 48.sp)
        Spacer(Modifier.size(12.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.size(20.dp))
        Button(onClick = onBack) { Text("Kitaplığa dön") }
    }
}
