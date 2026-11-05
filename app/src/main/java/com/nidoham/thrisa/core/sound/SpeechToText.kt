package com.nidoham.thrisa.core.sound

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
 * The current implementation is [SystemSpeechToText], Android's speech recognizer.
 */
interface SpeechToText : Closeable {

    fun listen(): Flow<Event>

    /** Prepare expensive local engines before the first utterance. */
    suspend fun prepare() {}

    override fun close()

    // ---- Nested types ----

    sealed interface Event {
        data class Partial(val text: String) : Event
        data object Processing : Event
        data class Final(val text: String) : Event
    }

    /** Recognition failure. [code] is a `SpeechRecognizer.ERROR_*` constant. */
    class SttException(val code: Int) : RuntimeException("SpeechRecognizer error=$code")
}

/** Creates the currently supported speech-to-text engine. */
object SpeechToTextFactory {
    fun create(context: Context, language: String = "bn-BD"): SpeechToText =
        SystemSpeechToText(context.applicationContext, language)
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

    override fun listen(): Flow<SpeechToText.Event> = callbackFlow {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.bestText()?.let { trySend(SpeechToText.Event.Partial(it)) }
            }

            override fun onResults(results: Bundle?) {
                trySend(SpeechToText.Event.Final(results?.bestText().orEmpty()))
                close()
            }

            override fun onError(error: Int) {
                close(SpeechToText.SttException(error))
            }

            override fun onReadyForSpeech(params: Bundle?) {}
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
            // End the utterance shortly after the user stops talking instead of
            // the multi-second device default. Honored by most recognizers,
            // ignored (harmlessly) by the rest.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
        }
        recognizer.startListening(intent)

        awaitClose { runCatching { recognizer.cancel() } }
    }

    override fun close() = recognizer.destroy()

    private fun Bundle.bestText(): String? =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
}
