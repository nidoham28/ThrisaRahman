package com.naira.assistant.core.sound

import android.content.Context
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.Closeable

// ───────────────────────── State ─────────────────────────

sealed interface VoiceState {
    data object Idle : VoiceState
    data object Preparing : VoiceState
    data class Listening(val partial: String = "") : VoiceState
    data object Transcribing : VoiceState
    data object Thinking : VoiceState
    data object Speaking : VoiceState
    data class Error(val message: String) : VoiceState
}

val VoiceState.isActive: Boolean
    get() = this is VoiceState.Preparing || this is VoiceState.Listening || this is VoiceState.Transcribing ||
            this is VoiceState.Thinking || this is VoiceState.Speaking

// ───────────────────────── Controller ─────────────────────────

/**
 * Hands-free loop:  listen → send to Gemini (streaming) → speak reply → listen …
 *
 * - Speech starts as soon as the first sentence of the streamed answer is complete; text that
 *   arrives while a chunk is being spoken is batched into the next chunk, so playback stays
 *   continuous without one TTS request per word.
 * - Tapping the mic while Listening/Transcribing ends the session; during Thinking/Speaking it interrupts the reply.
 * - Two silent rounds in a row end the conversation.
 *
 * [scope] must run on the Main dispatcher (SpeechRecognizer requires the main thread).
 */
class VoiceConversation(
    context: Context,
    private val chat: VoiceChatController,
    private val scope: CoroutineScope,
    language: String = "bn-BD",
) : Closeable {

    private val appContext = context.applicationContext
    private val stt = SpeechToTextFactory.create(appContext, language)
    private val tts = TextToSpeech(appContext, language)

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var loop: Job? = null
    private var generation = 0
    @Volatile private var interrupted = false

    private val running get() = loop?.isActive == true

    // ---- public API ----

    fun onMicTap() {
        when (_state.value) {
            is VoiceState.Idle, is VoiceState.Error -> start()
            is VoiceState.Preparing, is VoiceState.Listening, is VoiceState.Transcribing -> stop()
            is VoiceState.Thinking, is VoiceState.Speaking -> interrupt()
        }
    }

    fun start() {
        if (running) return
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            reportError("Speech recognition isn't available on this device.")
            return
        }
        val gen = ++generation
        loop = scope.launch {
            try {
                stt.prepare()
                converse()
            } catch (e: SpeechToText.SttException) {
                if (gen == generation) reportError(sttErrorMessage(e.code))
            } catch (e: Exception) {
                if (gen == generation && e !is CancellationException) {
                    reportError(e.message ?: "Voice chat couldn't start.")
                }
            } finally {
                if (gen == generation) {
                    tts.stop()
                    if (_state.value !is VoiceState.Error) _state.value = VoiceState.Idle
                }
            }
        }
    }

    /** Ends the conversation entirely. */
    fun stop() {
        generation++
        loop?.cancel()
        loop = null
        tts.stop()
        chat.cancelCurrent()
        _state.value = VoiceState.Idle
    }

    /** Cuts off the assistant (request and speech) and goes back to listening. */
    fun interrupt() {
        interrupted = true
        tts.stop()
        chat.cancelCurrent()
    }

    fun reportError(message: String) {
        _state.value = VoiceState.Error(message)
    }

    override fun close() {
        stop()
        tts.close()
        stt.close()
    }

    // ---- the loop ----

    private suspend fun converse() {
        var silentRounds = 0
        var retries = 0
        while (true) {
            interrupted = false

            val heard: String? = try {
                listenOnce().also { retries = 0 }
            } catch (e: SpeechToText.SttException) {
                when {
                    e.code == SpeechRecognizer.ERROR_NO_MATCH ||
                            e.code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> null
                    isRetryableSttError(e.code) -> {
                        if (++retries > MAX_RETRIES) {
                            reportError(sttErrorMessage(e.code))
                            return
                        }
                        delay(500)
                        continue
                    }
                    else -> {
                        reportError(sttErrorMessage(e.code))
                        return
                    }
                }
            }

            if (heard.isNullOrBlank()) {
                if (++silentRounds >= MAX_SILENT_ROUNDS) return // quietly end → Idle
                delay(200)
                continue
            }
            silentRounds = 0

            chat.busyStatus.first { it == null } // one request at a time
            _state.value = VoiceState.Thinking
            chat.send(heard)

            if (!speakReply()) return
            delay(200) // let audio focus / recognizer settle
        }
    }

    private suspend fun listenOnce(): String? {
        _state.value = VoiceState.Listening()
        var final: String? = null
        stt.listen().collect { event ->
            when (event) {
                is SpeechToText.Event.Partial -> _state.value = VoiceState.Listening(event.text)
                SpeechToText.Event.Processing -> _state.value = VoiceState.Transcribing
                is SpeechToText.Event.Final -> final = event.text
            }
        }
        return final
    }

    private data class Snapshot(val text: String, val isError: Boolean, val done: Boolean)

    /** Speaks the streaming answer sentence by sentence. Returns false when the conversation should end. */
    private suspend fun speakReply(): Boolean {
        val snapshots = combine(chat.replies, chat.busyStatus) { reply, busy ->
            Snapshot(reply?.text.orEmpty(), reply?.isError == true, busy == null)
        }
        var spoken = 0
        while (true) {
            val s = snapshots.first { it.done || sentenceEnd(it.text, spoken) > spoken }
            if (interrupted) return true

            if (s.isError) {
                reportError(s.text.trim().ifBlank { "Something went wrong." })
                return false
            }

            val from = minOf(spoken, s.text.length)
            val end = if (s.done) s.text.length else sentenceEnd(s.text, from)
            spoken = end

            val chunk = cleanForSpeech(s.text.substring(from, end))
            if (chunk.isNotBlank()) {
                // While this chunk plays, pre-generate the next sentence's audio
                // so Advanced (Gemini) TTS starts the moment this one ends.
                if (!s.done) {
                    val nextEnd = sentenceEnd(s.text, end)
                    if (nextEnd > end) tts.prefetch(cleanForSpeech(s.text.substring(end, nextEnd)))
                }
                _state.value = VoiceState.Speaking
                if (!speak(chunk)) return false
                if (interrupted) return true
            }
            if (s.done) return true
        }
    }

    /**
     * Runs TTS in a child job: [TextToSpeech.stop] cancels the *calling* job, and we only want
     * that to cancel the speech, not the whole conversation loop.
     */
    private suspend fun speak(text: String): Boolean = coroutineScope {
        var result: Result<Unit>? = null
        val job = launch { result = tts.speak(text) }
        job.join()
        val r = result
        if (r != null && r.isFailure && !interrupted) {
            reportError("Couldn't play the reply: ${r.exceptionOrNull()?.message}")
            false
        } else true
    }

    // ---- helpers ----

    /** Index just past the last complete sentence in [text] at or after [from]; [from] if none. */
    private fun sentenceEnd(text: String, from: Int): Int {
        var i = text.length - 1
        while (i >= from) {
            val c = text[i]
            val terminal = c == '\n' || (c in SENTENCE_ENDS && i + 1 < text.length && text[i + 1].isWhitespace())
            if (terminal && i + 1 - from >= MIN_CHUNK) return i + 1
            i--
        }
        return from
    }

    private fun cleanForSpeech(raw: String): String = raw
        .replace(CODE_FENCE, " ")
        .replace(MD_LINK, "$1")
        .replace(MD_SYMBOLS, "")
        .replace(WHITESPACE, " ")
        .trim()

    private companion object {
        const val MAX_SILENT_ROUNDS = 2
        const val MAX_RETRIES = 3
        const val MIN_CHUNK = 24
        const val SENTENCE_ENDS = ".!?।…"
        val CODE_FENCE = Regex("```[\\s\\S]*?```")
        val MD_LINK = Regex("\\[([^\\]]*)]\\([^)]*\\)")
        val MD_SYMBOLS = Regex("[*_`#>~|]+")
        val WHITESPACE = Regex("\\s+")
    }
}

internal fun isRetryableSttError(code: Int): Boolean = when (code) {
    SpeechRecognizer.ERROR_CLIENT,
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
    SpeechRecognizer.ERROR_SERVER,
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> true
    else -> false
}

internal fun sttErrorMessage(code: Int): String = when (code) {
    SpeechRecognizer.ERROR_AUDIO -> "Couldn't record audio."
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition needs a network connection."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
        "This language isn't available for speech recognition on this device."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Speech recognition is busy. Try again."
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "The speech service disconnected. Try again."
    SpeechRecognizer.ERROR_SERVER -> "Speech service error. Try again."
    else -> "Speech recognition failed (code $code)."
}
