package com.okur.reader.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Sesli okuma. Metni paragraflara/cümlelere bölüp sıraya koyar;
 * her parça başladığında [onChunkStart], son parça bitince [onFinished] çağrılır.
 */
class TtsController(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var pending: (() -> Unit)? = null
    private var chunks: List<String> = emptyList()
    private var session = 0

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking

    var onChunkStart: ((String) -> Unit)? = null
    var onFinished: (() -> Unit)? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post {
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                pending?.invoke()
                pending = null
            }
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                val (s, i) = parse(utteranceId) ?: return
                main.post { if (s == session) chunks.getOrNull(i)?.let { onChunkStart?.invoke(it) } }
            }

            override fun onDone(utteranceId: String) {
                val (s, i) = parse(utteranceId) ?: return
                main.post {
                    if (s == session && i == chunks.lastIndex) {
                        _speaking.value = false
                        onFinished?.invoke()
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                main.post { _speaking.value = false }
            }
        })
    }

    private fun parse(id: String): Pair<Int, Int>? {
        val parts = id.split(':')
        if (parts.size != 2) return null
        return (parts[0].toIntOrNull() ?: return null) to (parts[1].toIntOrNull() ?: return null)
    }

    val isAvailable: Boolean get() = ready

    fun speak(text: String, language: String?, rate: Float) {
        val action: () -> Unit = {
            session++
            val locale = language?.let { Locale.forLanguageTag(it) }?.takeIf { it.language.isNotEmpty() } ?: Locale("tr", "TR")
            if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) tts.language = locale
            tts.setSpeechRate(rate)
            chunks = split(text)
            if (chunks.isEmpty()) {
                _speaking.value = false
                onFinished?.invoke()
            } else {
                _speaking.value = true
                chunks.forEachIndexed { i, chunk ->
                    tts.speak(chunk, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "$session:$i")
                }
            }
        }
        if (ready) action() else pending = action
    }

    fun stop() {
        session++
        pending = null
        tts.stop()
        _speaking.value = false
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    private fun split(text: String): List<String> {
        val max = minOf(TextToSpeech.getMaxSpeechInputLength() - 100, 1200)
        val out = mutableListOf<String>()
        text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.forEach { para ->
            if (para.length <= max) {
                out += para
            } else {
                val sb = StringBuilder()
                para.split(Regex("(?<=[.!?…;:])\\s+")).forEach { sentence ->
                    if (sb.length + sentence.length > max && sb.isNotEmpty()) {
                        out += sb.toString(); sb.clear()
                    }
                    if (sentence.length > max) sentence.chunked(max).forEach { out += it } else sb.append(sentence).append(' ')
                }
                if (sb.isNotBlank()) out += sb.toString().trim()
            }
        }
        return out
    }
}
