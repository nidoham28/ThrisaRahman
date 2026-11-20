package com.naira.assistant.core.sound

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.naira.assistant.domain.AsrProviderSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.Closeable

/**
 * A speech-to-text engine wrapped as a cold [Flow].
 *
 * - Emits [Event.Partial] while the user speaks when supported.
 * - Emits [Event.Processing] while an engine processes captured audio.
 * - Emits [Event.Final] then closes on success.
 * - Closes exceptionally with [SttException] on error.
 * - Cancelling the collector stops the underlying engine.
 *
 * The implementations are [SystemSpeechToText] and the provider-backed
 * adapter for the app's selected speech-module ASR provider.
 */
interface SpeechToText : Closeable {

    fun listen(): Flow<Event>

    /** True when the caller must explicitly stop capture to finish a transcript. */
    val requiresManualTurnEnd: Boolean
        get() = false

    /** Requests the current utterance to finish for providers without server-side turn detection. */
    fun finishListening() = Unit

    /** Prepare expensive local engines before the first utterance. */
    suspend fun prepare() {}

    override fun close()

    // ---- Nested types ----

    sealed interface Event {
        data object Preparing : Event
        data object Ready : Event
        data class Partial(val text: String) : Event
        data object Processing : Event
        data class Final(val text: String) : Event
    }

    /** Recognition failure. [code] is a `SpeechRecognizer.ERROR_*` constant. */
    class SttException(val code: Int) : RuntimeException("SpeechRecognizer error=$code")
}

/** Creates the selected ASR engine, using Android recognition by default. */
object SpeechToTextFactory {
    fun create(
        context: Context,
        language: String = "bn-BD",
        settings: AsrProviderSettings = AsrProviderSettings(),
    ): SpeechToText =
        if (settings.provider == com.naira.assistant.domain.AsrProvider.SYSTEM) {
            SystemSpeechToText(context.applicationContext, language)
        } else {
            ModuleSpeechToText(context.applicationContext, settings)
        }
}

/**
 * Android's [SpeechRecognizer] wrapped as a cold [Flow]. Requires the main
 * thread to collect — the recognizer must be created and driven from there.
 */
class SystemSpeechToText(
    context: Context,
    private val language: String = "bn-BD",
) : SpeechToText {

    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    @Volatile
    private var closed = false

    override fun listen(): Flow<SpeechToText.Event> = callbackFlow {
        var latestPartial = ""
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.bestText()?.takeIf(String::isNotBlank)?.let {
                    latestPartial = it
                    trySend(SpeechToText.Event.Partial(it))
                }
            }

            override fun onResults(results: Bundle?) {
                val finalText = results?.bestText().orEmpty().ifBlank { latestPartial }
                trySend(SpeechToText.Event.Final(finalText))
                close()
            }

            override fun onError(error: Int) {
                close(SpeechToText.SttException(error))
            }

            override fun onReadyForSpeech(params: Bundle?) {
                trySend(SpeechToText.Event.Ready)
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer.startListening(intent)

        awaitClose { runCatching { recognizer.cancel() } }
    }

    override fun close() {
        if (closed) return
        closed = true
        recognizer.destroy()
    }

    private fun Bundle.bestText(): String? =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
}
