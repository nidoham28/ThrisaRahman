package com.naira.assistant.core.sound

import android.content.Context
import android.speech.SpeechRecognizer
import com.naira.assistant.domain.settings.AsrProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.naira.assistant.domain.settings.AsrProviderSettingsStorage
import java.io.Closeable

// ───────────────────────── State ─────────────────────────

sealed interface VoiceState {
    data object Idle : VoiceState
    data object Preparing : VoiceState
    data object WakeWordListening : VoiceState
    data class Listening(
        val partial: String = "",
        val requiresManualTurnEnd: Boolean = false,
    ) : VoiceState
    data object Transcribing : VoiceState
    data object Thinking : VoiceState
    data object Speaking : VoiceState
    data class Error(val message: String) : VoiceState
}

val VoiceState.isActive: Boolean
    get() = this is VoiceState.Preparing || this is VoiceState.WakeWordListening ||
            this is VoiceState.Listening || this is VoiceState.Transcribing ||
            this is VoiceState.Thinking || this is VoiceState.Speaking

// ───────────────────────── Controller ─────────────────────────

/**
 * Hands-free loop:  listen → send to Gemini (streaming) → speak reply → listen …
 *
 * - Speech starts as soon as the first sentence of the streamed answer is complete; text that
 *   arrives while a chunk is being spoken is batched into the next chunk, so playback stays
 *   continuous without one TTS request per word.
 * - Tapping the mic ends the session for Android/server-VAD recognition, or finishes a batch-ASR utterance.
 * - During Thinking/Speaking it interrupts the reply; while approval is pending it stops voice capture.
 * - Two silent rounds in a row end the conversation.
 *
 * [scope] must run on the Main dispatcher (SpeechRecognizer requires the main thread).
 */
class VoiceConversation(
    context: Context,
    private val chat: VoiceChatController,
    private val scope: CoroutineScope,
    private val language: String = "bn-BD",
) : Closeable {

    private val appContext = context.applicationContext
    private val tts = TextToSpeech(appContext, language)
    private var stt: SpeechToText? = null

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
            is VoiceState.Listening -> {
                if (stt?.requiresManualTurnEnd == true) {
                    _state.value = VoiceState.Transcribing
                    stt?.finishListening()
                } else {
                    stop()
                }
            }
            is VoiceState.Preparing, is VoiceState.Transcribing -> stop()
            is VoiceState.WakeWordListening -> stop()
            is VoiceState.Thinking -> interrupt()
            is VoiceState.Speaking -> interrupt()
        }
    }

    suspend fun prepareForWakeWord() = prepareSpeechToText(prewarmSystemOnly = true)

    private suspend fun prepareSpeechToText(prewarmSystemOnly: Boolean) {
        if (stt != null) return

        val settings = withContext(Dispatchers.IO) {
            AsrProviderSettingsStorage(appContext).load()
        }
        if (prewarmSystemOnly && settings.provider != AsrProvider.SYSTEM) return
        if (settings.provider == AsrProvider.SYSTEM &&
            !SpeechRecognizer.isRecognitionAvailable(appContext)
        ) {
            error("Speech recognition isn't available on this device.")
        }

        val recognizer = SpeechToTextFactory.create(appContext, language, settings)
        try {
            recognizer.prepare()
            stt = recognizer
        } catch (error: Exception) {
            recognizer.close()
            throw error
        }
    }

    fun start(afterWakeWord: Boolean = false) {
        if (running) return
        val gen = ++generation
        loop = scope.launch {
            var sessionStt: SpeechToText? = null
            try {
                prepareSpeechToText(prewarmSystemOnly = false)
                sessionStt = stt
                converse(afterWakeWord)
            } catch (e: SpeechToText.SttException) {
                if (gen == generation) reportError(sttErrorMessage(e.code))
            } catch (e: Exception) {
                if (gen == generation && e !is CancellationException) {
                    reportError(e.message ?: "Voice chat couldn't start.")
                }
            } finally {
                sessionStt?.close()
                if (stt === sessionStt) stt = null
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
        stt?.close()
        stt = null
    }

    // ---- the loop ----

    private suspend fun converse(afterWakeWord: Boolean) {
        var silentRounds = 0
        var retries = 0
        var wakeAcknowledgementPending = afterWakeWord
        while (true) {
            interrupted = false

            val heard: String? = try {
                listenOnce(
                    firstSpeechTimeoutMillis = WAKE_QUESTION_START_TIMEOUT_MILLIS
                        .takeIf { wakeAcknowledgementPending },
                ).also { retries = 0 }
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

            val question = heard.orEmpty().removeLeadingWakePhrase()
            if (question.isBlank()) {
                if (wakeAcknowledgementPending) {
                    wakeAcknowledgementPending = false
                    silentRounds = 0
                    if (!speak(wakeWordAcknowledgement())) return
                    continue
                }
                if (++silentRounds >= MAX_SILENT_ROUNDS) return // quietly end → Idle
                delay(200)
                continue
            }
            wakeAcknowledgementPending = false
            silentRounds = 0

            if (question.isSleepAssistantCommand()) {
                speak(sleepCommandAcknowledgement())
                // Returning ends this conversation; the foreground service resumes wake-word detection.
                return
            }

            chat.busyStatus.first { it == null } // one request at a time
            _state.value = VoiceState.Thinking
            chat.send(question)

            if (!speakReply()) return
            delay(200) // let audio focus / recognizer settle
        }
    }

    private suspend fun listenOnce(firstSpeechTimeoutMillis: Long? = null): String? = coroutineScope {
        var final: String? = null
        val recognizer = checkNotNull(stt) { "Speech recognition has not been prepared." }
        _state.value = VoiceState.Preparing
        val speechReady = CompletableDeferred<Unit>()
        val firstSpeech = CompletableDeferred<Unit>()
        val listenJob = launch {
            recognizer.listen().collect { event ->
                when (event) {
                    SpeechToText.Event.Preparing -> _state.value = VoiceState.Preparing
                    SpeechToText.Event.Ready -> {
                        speechReady.complete(Unit)
                        _state.value = VoiceState.Listening(
                            requiresManualTurnEnd = recognizer.requiresManualTurnEnd,
                        )
                    }
                    is SpeechToText.Event.Partial -> {
                        speechReady.complete(Unit)
                        firstSpeech.complete(Unit)
                        _state.value = VoiceState.Listening(
                            partial = event.text,
                            requiresManualTurnEnd = recognizer.requiresManualTurnEnd,
                        )
                    }
                    SpeechToText.Event.Processing -> {
                        speechReady.complete(Unit)
                        firstSpeech.complete(Unit)
                        _state.value = VoiceState.Transcribing
                    }
                    is SpeechToText.Event.Final -> {
                        speechReady.complete(Unit)
                        firstSpeech.complete(Unit)
                        final = event.text
                    }
                }
            }
        }
        try {
            if (firstSpeechTimeoutMillis != null) {
                speechReady.await()
                val startedSpeaking = withTimeoutOrNull(firstSpeechTimeoutMillis) {
                    firstSpeech.await()
                    true
                } ?: false
                if (!startedSpeaking) {
                    listenJob.cancelAndJoin()
                    return@coroutineScope null
                }
            }
            listenJob.join()
            final
        } finally {
            listenJob.cancelAndJoin()
        }
    }

    private data class Snapshot(
        val text: String,
        val isError: Boolean,
        val done: Boolean,
    )

    /** Speaks the streaming answer sentence by sentence. Returns false when the conversation should end. */
    private suspend fun speakReply(): Boolean {
        val snapshots = combine(chat.replies, chat.busyStatus) { reply, busy ->
            Snapshot(
                text = reply?.text.orEmpty(),
                isError = reply?.isError == true,
                done = busy == null,
            )
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
        val playbackStarted = CompletableDeferred<Unit>()
        val job = launch {
            result = tts.speak(text) {
                playbackStarted.complete(Unit)
            }
        }
        val speakingStatusJob = launch {
            playbackStarted.await()
            delay(SPEAKING_STATUS_DELAY_MILLIS)
            if (job.isActive && !interrupted) _state.value = VoiceState.Speaking
        }
        job.join()
        speakingStatusJob.cancel()
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

    private fun wakeWordAcknowledgement(): String =
        if (language.startsWith("bn", ignoreCase = true)) {
            BANGLA_WAKE_WORD_ACKNOWLEDGEMENT
        } else {
            ENGLISH_WAKE_WORD_ACKNOWLEDGEMENT
        }

    private fun sleepCommandAcknowledgement(): String =
        if (language.startsWith("bn", ignoreCase = true)) {
            BANGLA_SLEEP_COMMAND_ACKNOWLEDGEMENT
        } else {
            ENGLISH_SLEEP_COMMAND_ACKNOWLEDGEMENT
        }

    private companion object {
        const val MAX_SILENT_ROUNDS = 2
        const val MAX_RETRIES = 3
        const val WAKE_QUESTION_START_TIMEOUT_MILLIS = 5_000L
        const val ENGLISH_WAKE_WORD_ACKNOWLEDGEMENT = "I'm listening. What would you like to ask?"
        const val BANGLA_WAKE_WORD_ACKNOWLEDGEMENT = "আমি শুনছি। কী জানতে চান?"
        const val ENGLISH_SLEEP_COMMAND_ACKNOWLEDGEMENT =
            "Going to sleep now. Say Hey Assistant, Hello Assistant, or Wakeup Assistant to wake me."
        const val BANGLA_SLEEP_COMMAND_ACKNOWLEDGEMENT =
            "আমি এখন ঘুমিয়ে থাকছি। আবার ডাকতে Hey Assistant, Hello Assistant, বা Wakeup Assistant বলুন।"
        const val MIN_CHUNK = 24
        const val SPEAKING_STATUS_DELAY_MILLIS = 250L
        const val SENTENCE_ENDS = ".!?।…"
        val CODE_FENCE = Regex("```[\\s\\S]*?```")
        val MD_LINK = Regex("\\[([^\\]]*)]\\([^)]*\\)")
        val MD_SYMBOLS = Regex("[*_`#>~|]+")
        val WHITESPACE = Regex("\\s+")
    }
}

private val LEADING_WAKE_PHRASE = Regex(
    "^\\s*(?:hey\\s+assistant|hello\\s+assistant|wake\\s*up\\s+assistant)\\b[\\s,.:;!?-]*",
    RegexOption.IGNORE_CASE,
)

private fun String.removeLeadingWakePhrase(): String =
    replaceFirst(LEADING_WAKE_PHRASE, "").trim()

private val VOICE_COMMAND_SEPARATORS = Regex("[^\\p{L}\\p{N}]+")
private val VOICE_COMMAND_WHITESPACE = Regex("\\s+")
private val SLEEP_ASSISTANT_COMMANDS = setOf(
    "sleep assistant",
    "assistant sleep",
    "go to sleep assistant",
    "go to sleep",
    "stop listening",
    "অ্যাসিস্ট্যান্ট ঘুমাও",
    "অ্যাসিস্ট্যান্ট ঘুমাতে যাও",
    "অ্যাসিস্ট্যান্ট ঘুমিয়ে পড়ো",
    "সহকারী ঘুমাও",
)

private fun String.isSleepAssistantCommand(): Boolean =
    lowercase()
        .replace(VOICE_COMMAND_SEPARATORS, " ")
        .trim()
        .replace(VOICE_COMMAND_WHITESPACE, " ") in SLEEP_ASSISTANT_COMMANDS

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
