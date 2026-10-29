package com.okur.reader.eyecare

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

data class SmartEyeState(
    val active: Boolean = false,
    val facePresent: Boolean = false,
    /** Kullanıcı ekrana bakıyor mu (baş açısına göre) */
    val looking: Boolean = false,
    /** Yüzün ekrandan uzaklaştığı/baktığı yönün değiştiği andan beri geçen süre (ms) */
    val awayForMs: Long = 0,
    /** Yüz genişliği / kare genişliği; büyüdükçe kullanıcı ekrana yaklaşır */
    val faceRatio: Float = 0f,
    val tooClose: Boolean = false,
    /** Son 60 saniyedeki göz kırpma sayısı (dakikalık hız) */
    val blinksPerMinute: Int = -1,
    val error: String? = null,
)

/**
 * Akıllı Göz: ön kamerayla cihaz üzerinde (internet olmadan) yüz ve göz algılar.
 * - Ekrana bakarken ekranı açık tutma (Smart Stay)
 * - Ekrana fazla yaklaşma uyarısı
 * - Göz kırpma hızını izleyip göz kuruluğuna karşı hatırlatma
 * Görüntüler hiçbir yere kaydedilmez veya gönderilmez.
 */
class SmartEyeMonitor(private val context: Context) {

    private val _state = MutableStateFlow(SmartEyeState())
    val state: StateFlow<SmartEyeState> = _state

    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var executor: ExecutorService? = null
    private var detector: FaceDetector? = null

    private var lastFrameAt = 0L
    private var lastSeenAt = 0L
    private var eyesClosed = false
    private val blinkTimes = ArrayDeque<Long>()
    private var startedAt = 0L
    private var closeSince = 0L

    fun start(owner: LifecycleOwner) {
        if (analysis != null) return
        startedAt = SystemClock.elapsedRealtime()
        lastSeenAt = startedAt
        val exec = Executors.newSingleThreadExecutor().also { executor = it }
        val det = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .setMinFaceSize(0.12f)
                .build()
        ).also { detector = it }

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val a = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                a.setAnalyzer(exec) { image -> analyze(image, det) }
                analysis = a
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, a)
                _state.value = _state.value.copy(active = true, error = null)
            } catch (t: Throwable) {
                _state.value = SmartEyeState(active = false, error = t.message ?: "Kamera başlatılamadı")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        analysis?.let { a ->
            a.clearAnalyzer()
            runCatching { provider?.unbind(a) }
        }
        analysis = null
        provider = null
        executor?.shutdown()
        executor = null
        detector?.close()
        detector = null
        blinkTimes.clear()
        _state.value = SmartEyeState()
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyze(image: ImageProxy, det: FaceDetector) {
        val now = SystemClock.elapsedRealtime()
        // ~8 kare/sn yeterli; pil tasarrufu için fazlasını atla
        val media = image.image
        if (now - lastFrameAt < 120 || media == null) {
            image.close()
            return
        }
        lastFrameAt = now
        val rotation = image.imageInfo.rotationDegrees
        val frameWidth = if (rotation == 90 || rotation == 270) image.height else image.width
        val input = InputImage.fromMediaImage(media, rotation)
        det.process(input)
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() }
                if (face == null) {
                    publish(now, present = false, looking = false, ratio = 0f)
                } else {
                    val looking = abs(face.headEulerAngleY) < 28f && abs(face.headEulerAngleX) < 24f
                    val ratio = face.boundingBox.width().toFloat() / frameWidth.coerceAtLeast(1)
                    trackBlink(now, face.leftEyeOpenProbability, face.rightEyeOpenProbability)
                    publish(now, present = true, looking = looking, ratio = ratio)
                }
            }
            .addOnFailureListener { publish(now, present = false, looking = false, ratio = 0f) }
            .addOnCompleteListener { image.close() }
    }

    private fun trackBlink(now: Long, left: Float?, right: Float?) {
        if (left == null || right == null) return
        val open = (left + right) / 2f
        if (!eyesClosed && open < 0.35f) {
            eyesClosed = true
        } else if (eyesClosed && open > 0.65f) {
            eyesClosed = false
            blinkTimes.addLast(now)
        }
        while (blinkTimes.isNotEmpty() && now - blinkTimes.first() > 60_000) blinkTimes.removeFirst()
    }

    private fun publish(now: Long, present: Boolean, looking: Boolean, ratio: Float) {
        if (present && looking) lastSeenAt = now
        // Yakınlık uyarısı için yüz 2 saniyeden uzun süre yakın kalmalı (anlık hareketleri yok say)
        val close = present && ratio > 0.58f
        closeSince = if (close) (if (closeSince == 0L) now else closeSince) else 0L
        val bpm = if (now - startedAt >= 60_000) blinkTimes.size else -1
        _state.value = SmartEyeState(
            active = true,
            facePresent = present,
            looking = present && looking,
            awayForMs = now - lastSeenAt,
            faceRatio = ratio,
            tooClose = closeSince != 0L && now - closeSince > 2_000,
            blinksPerMinute = bpm,
        )
    }
}
