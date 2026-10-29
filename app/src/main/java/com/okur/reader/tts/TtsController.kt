package com.okur.reader.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

enum class TtsState { IDLE, SPEAKING, PAUSED }

enum class VoiceGender { MALE, FEMALE, UNKNOWN }

data class TtsVoice(val name: String, val label: String, val gender: VoiceGender, val offline: Boolean)

/**
 * Sesli okuma. Metni paragraflara/cümlelere bölüp sıraya koyar.
 * Duraklatıldığında okunan parçayı hatırlar ve [resume] ile aynı yerden devam eder.
 */
class TtsController(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var pending: (() -> Unit)? = null
    private var chunks: List<String> = emptyList()
    private var currentChunk = 0
    private var session = 0
    private var rate = 1f
    private var pitch = 1f
    private var voiceName = ""
    private var locale: Locale = Locale("tr", "TR")

    private val _state = MutableStateFlow(TtsState.IDLE)
    val state: StateFlow<TtsState> = _state

    private val _voices = MutableStateFlow<List<TtsVoice>>(emptyList())
    val voices: StateFlow<List<TtsVoice>> = _voices

    var onChunkStart: ((String) -> Unit)? = null
    var onFinished: (() -> Unit)? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post {
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                refreshVoices()
                pending?.invoke()
                pending = null
            }
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                val (s, i) = parse(utteranceId) ?: return
                main.post {
                    if (s == session) {
                        currentChunk = i
                        chunks.getOrNull(i)?.let { onChunkStart?.invoke(it) }
                    }
                }
            }

            override fun onDone(utteranceId: String) {
                val (s, i) = parse(utteranceId) ?: return
                main.post {
                    if (s == session && i == chunks.lastIndex) {
                        _state.value = TtsState.IDLE
                        chunks = emptyList()
                        onFinished?.invoke()
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                main.post { if (_state.value == TtsState.SPEAKING) _state.value = TtsState.IDLE }
            }
        })
    }

    private fun parse(id: String): Pair<Int, Int>? {
        val parts = id.split(':')
        if (parts.size != 2) return null
        return (parts[0].toIntOrNull() ?: return null) to (parts[1].toIntOrNull() ?: return null)
    }

    /** Kitabın dilindeki sesleri listeler; erkek/kadın bilgisi ses adından tahmin edilir. */
    private fun refreshVoices() {
        val all = runCatching { tts.voices }.getOrNull().orEmpty()
        val list = all
            .filter { it.locale.language == locale.language }
            .filter { !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(compareBy<Voice>({ it.isNetworkConnectionRequired }, { it.name }))
        var n = 0
        _voices.value = list.map { v ->
            n++
            val g = genderOf(v.name)
            val genderLabel = when (g) { VoiceGender.MALE -> "Erkek"; VoiceGender.FEMALE -> "Kadın"; VoiceGender.UNKNOWN -> null }
            val label = listOfNotNull("Ses $n", genderLabel, if (v.isNetworkConnectionRequired) "çevrimiçi" else null).joinToString(" · ")
            TtsVoice(v.name, label, g, !v.isNetworkConnectionRequired)
        }
    }

    private fun genderOf(name: String): VoiceGender {
        val n = name.lowercase(Locale.ROOT)
        return when {
            "female" in n || Regex("(^|[-_])f\\d{2}").containsMatchIn(n) -> VoiceGender.FEMALE
            "male" in n || Regex("(^|[-_])m\\d{2}").containsMatchIn(n) -> VoiceGender.MALE
            // Google TTS Türkçe ses kodları (yaygın eşleşmeler)
            Regex("-x-(tmc|mfm|efs-?m|tmd)").containsMatchIn(n) -> VoiceGender.MALE
            Regex("-x-(cfs|efs|ama|tmf)").containsMatchIn(n) -> VoiceGender.FEMALE
            else -> VoiceGender.UNKNOWN
        }
    }

    fun configure(language: String?, rate: Float, pitch: Float, voice: String) {
        val newLocale = language?.let { Locale.forLanguageTag(it) }?.takeIf { it.language.isNotEmpty() } ?: Locale("tr", "TR")
        val localeChanged = newLocale.language != locale.language
        locale = newLocale
        this.rate = rate
        this.pitch = pitch
        this.voiceName = voice
        if (ready) {
            if (localeChanged) refreshVoices()
            applyVoice()
        }
    }

    private fun applyVoice() {
        if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) tts.language = locale
        if (voiceName.isNotEmpty()) {
            runCatching { tts.voices?.firstOrNull { it.name == voiceName } }.getOrNull()?.let { tts.voice = it }
        }
        tts.setSpeechRate(rate)
        tts.setPitch(pitch)
    }

    fun speak(text: String) {
        val action: () -> Unit = {
            applyVoice()
            chunks = split(text)
            currentChunk = 0
            if (chunks.isEmpty()) {
                _state.value = TtsState.IDLE
                onFinished?.invoke()
            } else {
                queueFrom(0)
            }
        }
        if (ready) action() else pending = action
    }

    private fun queueFrom(index: Int) {
        session++
        _state.value = TtsState.SPEAKING
        for (i in index until chunks.size) {
            tts.speak(chunks[i], if (i == index) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "$session:$i")
        }
    }

    /** Okumayı duraklatır; [resume] okunan parçanın başından devam eder. */
    fun pause() {
        if (_state.value != TtsState.SPEAKING) return
        session++
        tts.stop()
        _state.value = TtsState.PAUSED
    }

    fun resume() {
        if (_state.value != TtsState.PAUSED || chunks.isEmpty()) return
        applyVoice()
        queueFrom(currentChunk.coerceIn(0, chunks.lastIndex))
    }

    /** Ayar değişikliklerini (hız, ses, ton) okuma sürerken hemen uygular. */
    fun restartCurrent() {
        if (_state.value == TtsState.SPEAKING) {
            applyVoice()
            queueFrom(currentChunk.coerceIn(0, chunks.lastIndex))
        }
    }

    fun preview(text: String) {
        if (!ready) return
        applyVoice()
        session++
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "preview")
        if (_state.value == TtsState.SPEAKING) _state.value = TtsState.PAUSED
    }

    fun stop() {
        session++
        pending = null
        tts.stop()
        chunks = emptyList()
        _state.value = TtsState.IDLE
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    private fun split(text: String): List<String> {
        val max = minOf(TextToSpeech.getMaxSpeechInputLength() - 100, 600)
        val out = mutableListOf<String>()
        text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.forEach { para ->
            if (para.length <= max) {
                out += para
            } else {
                val sb = StringBuilder()
                para.split(Regex("(?<=[.!?…;:])\\s+")).forEach { sentence ->
                    if (sb.length + sentence.length > max && sb.isNotEmpty()) {
                        out += sb.toString().trim(); sb.clear()
                    }
                    if (sentence.length > max) sentence.chunked(max).forEach { out += it } else sb.append(sentence).append(' ')
                }
                if (sb.isNotBlank()) out += sb.toString().trim()
            }
        }
        return out
    }
}
