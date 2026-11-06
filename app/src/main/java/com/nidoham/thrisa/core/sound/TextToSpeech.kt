package com.nidoham.thrisa.core.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.speech.tts.TextToSpeech as AndroidTextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.nidoham.thrisa.ai.AssistantApiService
import com.nidoham.thrisa.core.config.API_KEYS
import com.nidoham.thrisa.domain.AssistantVoice
import com.nidoham.thrisa.domain.SpeechSettingsStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.UUID
import java.io.Closeable
import kotlin.coroutines.coroutineContext

enum class SpeechEngine(val label: String, val description: String) {
    SYSTEM("System", "Local Device TTS"),
    ADVANCED("Advanced", "Gemini TTS")
}

/**
 * Speaks with the user's selected local or Gemini TTS engine.
 *
 * The system engine is created once and reused for every utterance — engine
 * init costs 150-500 ms, which used to be paid per spoken sentence. For the
 * Gemini engine, [prefetch] starts generating the next sentence's audio while
 * the current one plays, so playback flows back-to-back, and the raw PCM is
 * streamed straight into an [AudioTrack] (no WAV container, no MediaPlayer
 * prepare step) so the first sentence starts within ~20 ms of arriving.
 */
class TextToSpeech(
    context: Context,
    /** BCP-47 tag the local engine should speak (e.g. "bn-BD", "en-US"). */
    private val language: String = "en-US",
) : Closeable {

    private val appContext = context.applicationContext
    private val speechSettings = SpeechSettingsStorage(appContext)
    private val assistantApi = AssistantApiService(appContext)

    private val playbackLock = Any()
    private var activePlayback: PcmPlayback? = null
    private var activeSpeechJob: Job? = null

    // Reused system engine (null = not created / init failed and was dropped).
    private var systemEngine: AndroidTextToSpeech? = null
    private var systemEngineReady: CompletableDeferred<Result<AndroidTextToSpeech>>? = null

    /** Generates speech off the caller, so [prefetch] works while playback runs. */
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Single-slot cache for the Gemini TTS prefetch (see [prefetch]). */
    private var prefetched: Prefetch? = null

    @Volatile private var closed = false

    /** Generates speech remotely with Gemini, then plays it through Android audio playback. */
    suspend fun speak(text: String): Result<Unit> {
        var requestJob: Job? = null
        return try {
            require(text.isNotBlank()) { "Text cannot be blank" }
            check(!closed) { "TTS has already been closed" }

            val job = currentCoroutineContext()[Job]
            requestJob = job
            synchronized(playbackLock) {
                activeSpeechJob?.takeIf { it !== job }?.cancel()
                stopPlaybackLocked()
                activeSpeechJob = job
            }

            val voice = speechSettings.getVoice()
            when (runCatching { SpeechEngine.valueOf(speechSettings.getEngine()) }
                .getOrDefault(SpeechEngine.SYSTEM)) {
                SpeechEngine.SYSTEM -> speakOnDevice(text.trim(), voice)
                SpeechEngine.ADVANCED -> {
                    val apiKey = API_KEYS(appContext).trim()
                    check(apiKey.isNotEmpty()) {
                        "Add a Gemini API key in Settings → AI Providers → Credentials to use Gemini TTS."
                    }
                    val pcm = takePrefetch(text, voice)
                        ?: generateGeminiSpeech(apiKey, text.trim(), voice)
                    coroutineContext.ensureActive()
                    check(!closed) { "TTS has already been closed" }
                    playPcm(pcm)
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            synchronized(playbackLock) {
                if (activeSpeechJob === requestJob) activeSpeechJob = null
            }
        }
    }

    /**
     * Starts generating audio for an upcoming utterance in the background so
     * [speak] can start playing it the moment the previous sentence ends.
     * A no-op for the system engine (it has no per-utterance setup cost) and
     * when the slot already holds the same text. Cancelled by [stop]/[close].
     */
    fun prefetch(text: String) {
        if (closed) return
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        val engine = runCatching { SpeechEngine.valueOf(speechSettings.getEngine()) }
            .getOrDefault(SpeechEngine.SYSTEM)
        if (engine != SpeechEngine.ADVANCED) return

        synchronized(playbackLock) {
            if (closed) return
            val voice = speechSettings.getVoice()
            if (prefetched?.text == trimmed && prefetched?.voice == voice) return
            prefetched?.pcm?.cancel()
            val apiKey = API_KEYS(appContext).trim()
            if (apiKey.isEmpty()) return
            prefetched = Prefetch(
                text = trimmed,
                voice = voice,
                pcm = prefetchScope.async { generateGeminiSpeech(apiKey, trimmed, voice) },
            )
        }
    }

    /** Stops any current Gemini request or audio playback, and drops the prefetch. */
    fun stop() {
        synchronized(playbackLock) {
            activeSpeechJob?.cancel(CancellationException("Speech stopped"))
            activeSpeechJob = null
            stopPlaybackLocked()
            prefetched?.pcm?.cancel()
            prefetched = null
        }
        systemEngine?.stop()
    }

    override fun close() {
        if (closed) return
        closed = true
        stop()
        prefetchScope.cancel()
        synchronized(playbackLock) {
            systemEngine?.shutdown()
            systemEngine = null
            systemEngineReady = null
        }
        assistantApi.close()
    }

    // ------------------------------------------------------------------ system engine

    /** Returns the shared engine, creating it on first use. */
    private fun systemEngineHandle(): Pair<AndroidTextToSpeech, CompletableDeferred<Result<AndroidTextToSpeech>>> =
        synchronized(playbackLock) {
            val engine = systemEngine
            val ready = systemEngineReady
            if (engine != null && ready != null) {
                engine to ready
            } else {
                val newReady = CompletableDeferred<Result<AndroidTextToSpeech>>()
                var engineRef: AndroidTextToSpeech? = null
                val newEngine = AndroidTextToSpeech(appContext) { status ->
                    // AndroidTextToSpeech dispatches the init callback through the
                    // Looper, so it always runs after engineRef is assigned below.
                    val created = engineRef
                    if (status == AndroidTextToSpeech.SUCCESS && created != null) {
                        newReady.complete(Result.success(created))
                    } else {
                        newReady.complete(
                            Result.failure(IllegalStateException("Local device speech is unavailable."))
                        )
                    }
                }
                engineRef = newEngine
                systemEngine = newEngine
                systemEngineReady = newReady
                newEngine to newReady
            }
        }

    private suspend fun speakOnDevice(text: String, voice: AssistantVoice) {
        val (engine, ready) = systemEngineHandle()
        val initialised = ready.await().getOrNull()
        if (initialised == null) {
            // Init failed — drop the broken engine so a later speak retries from scratch.
            synchronized(playbackLock) {
                systemEngine?.shutdown()
                systemEngine = null
                systemEngineReady = null
            }
            check(!closed) { "TTS has already been closed" }
            error("Local device speech is unavailable.")
        }
        check(!closed) { "TTS has already been closed" }

        suspendCancellableCoroutine<Unit> { continuation ->
            val utteranceId = UUID.randomUUID().toString()
            // Speak the assistant's configured language, not the device default -
            // a Bangla reply read by an English voice is unintelligible. Fall back
            // to the engine default when the voice data isn't installed.
            when (initialised.setLanguage(Locale.forLanguageTag(language))) {
                AndroidTextToSpeech.LANG_MISSING_DATA,
                AndroidTextToSpeech.LANG_NOT_SUPPORTED -> initialised.language = Locale.getDefault()
            }
            selectLocalVoice(initialised, voice)
            initialised.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) {
                    if (id == utteranceId && continuation.isActive) {
                        continuation.resumeWith(Result.success(Unit))
                    }
                }

                private fun handleSpeechError(id: String?) {
                    if (id == utteranceId && continuation.isActive) continuation.resumeWith(
                        Result.failure(IllegalStateException("Local device speech failed."))
                    )
                }

                @Deprecated("Deprecated in Android")
                @Suppress("DEPRECATION")
                override fun onError(id: String?) = handleSpeechError(id)

                override fun onError(id: String?, errorCode: Int) = handleSpeechError(id)

                override fun onStop(id: String?, interrupted: Boolean) = Unit
            })
            val result = initialised.speak(text, AndroidTextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
            if (result == AndroidTextToSpeech.ERROR && continuation.isActive) {
                continuation.resumeWith(Result.failure(IllegalStateException("Local device speech could not start.")))
            }
            continuation.invokeOnCancellation { initialised.stop() }
        }
    }

    // ------------------------------------------------------------------ Gemini engine

    private suspend fun generateGeminiSpeech(
        apiKey: String,
        text: String,
        voice: AssistantVoice,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            val output = ByteArrayOutputStream()
            assistantApi.generateSpeech(
                apiKey = apiKey,
                text = text,
                voiceName = voice.geminiVoiceName,
            ).collect { chunk ->
                require(chunk.format == me.rerere.tts.model.AudioFormat.PCM) {
                    "Gemini TTS returned unsupported audio format: ${chunk.format}"
                }
                output.write(chunk.data)
            }
            output.toByteArray()
        }

    /** Returns the prefetched audio for [text] if one matches, consuming the slot. */
    private suspend fun takePrefetch(text: String, voice: AssistantVoice): ByteArray? {
        val candidate = synchronized(playbackLock) {
            val p = prefetched
            prefetched = null
            p
        }
        if (candidate == null || candidate.text != text.trim() || candidate.voice != voice) {
            candidate?.pcm?.cancel()
            return null
        }
        return try {
            candidate.pcm.await()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null // the prefetch failed; generate fresh so [speak] reports the real error
        }
    }

    private class Prefetch(
        val text: String,
        val voice: AssistantVoice,
        val pcm: Deferred<ByteArray>,
    )

    private fun selectLocalVoice(engine: AndroidTextToSpeech, selected: AssistantVoice) {
        val requestedLocale = Locale.forLanguageTag(language)
        val availableVoices = engine.voices
        val requestedLanguageVoices = availableVoices.filter {
            it.locale.language.equals(requestedLocale.language, ignoreCase = true)
        }
        val activeLocale = requestedLocale.takeIf { requestedLanguageVoices.isNotEmpty() }
            ?: Locale.getDefault()
        val languageVoices = requestedLanguageVoices.ifEmpty {
            availableVoices.filter {
                it.locale.language.equals(activeLocale.language, ignoreCase = true)
            }
        }
        val matchingVoice = languageVoices
            .filter { it.matchesGender(selected) }
            .sortedByDescending { it.locale.country.equals(activeLocale.country, ignoreCase = true) }
            .firstOrNull()
        val defaultVoice = languageVoices
            .sortedByDescending { it.locale.country.equals(activeLocale.country, ignoreCase = true) }
            .firstOrNull()
        (matchingVoice ?: defaultVoice)?.let { engine.voice = it }
    }

    private fun android.speech.tts.Voice.matchesGender(selected: AssistantVoice): Boolean {
        val tokens = name.lowercase(Locale.ROOT).split(Regex("[^a-z]+"))
        return when (selected) {
            AssistantVoice.FEMALE -> "female" in tokens || "woman" in tokens
            AssistantVoice.MALE -> "male" in tokens || "man" in tokens
        }
    }

    // ------------------------------------------------------------------ audio playback

    /** Gemini 2.5+ TTS returns 24 kHz mono 16-bit PCM. */
    private companion object {
        const val SAMPLE_RATE = 24_000
        const val WRITE_CHUNK_BYTES = 8_192
    }

    private suspend fun playPcm(pcm: ByteArray) {
        val playback = PcmPlayback(pcm)
        synchronized(playbackLock) {
            check(!closed) { "TTS has already been closed" }
            activePlayback = playback
        }
        try {
            playback.start()
            playback.await()
        } catch (e: CancellationException) {
            synchronized(playbackLock) {
                if (activePlayback === playback) stopPlaybackLocked()
            }
            throw e
        } finally {
            synchronized(playbackLock) {
                if (activePlayback === playback) activePlayback = null
            }
            playback.release()
        }
    }

    private fun stopPlaybackLocked() {
        val playback = activePlayback ?: return
        activePlayback = null
        playback.release()
        playback.done.complete(Unit)
    }

    /**
     * Streams raw PCM into an [AudioTrack] instead of wrapping it in a WAV and
     * handing it to MediaPlayer. [AudioTrack] in MODE_STREAM starts playing the
     * moment the first chunk is written - no prepareAsync round trip - so the
     * gap between two prefetched sentences drops to a few milliseconds.
     *
     * Writes are non-blocking and interleaved with [ensureActive] checks, so
     * [release] from any thread cancels playback immediately without waiting
     * for a blocking write to return.
     */
    private inner class PcmPlayback(private val pcm: ByteArray) {
        val done = CompletableDeferred<Unit>()
        private var track: AudioTrack? = null

        fun start() {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            check(minBuf > 0) { "Audio playback is unavailable on this device." }
            track = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                maxOf(minBuf, WRITE_CHUNK_BYTES) * 2,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
            ).apply { play() } // starts at once; plays silence until the first write lands
        }

        suspend fun await() = withContext(Dispatchers.IO) {
            val t = track ?: error("Playback was not started.")
            var offset = 0
            while (offset < pcm.size) {
                currentCoroutineContext().ensureActive()
                val written = t.write(
                    pcm, offset, minOf(WRITE_CHUNK_BYTES, pcm.size - offset), AudioTrack.WRITE_NON_BLOCKING
                )
                when {
                    written < 0 -> error("Audio playback failed (code $written).")
                    written == 0 -> delay(10) // buffer full - let it drain a little
                    else -> offset += written
                }
            }
            // stop() drains the already-written buffer before stopping; the head
            // position then reaches the final frame once playback is done. The
            // runCatching guards only fire when stop/release raced this coroutine -
            // cancellation handles the exit in that case.
            runCatching { t.stop() }
            val totalFrames = pcm.size / 2
            // Deadline = full playback duration + slack, so a device whose head
            // position never advances can't hang the voice loop in Speaking.
            val deadlineMs = System.currentTimeMillis() + (totalFrames * 1000L / SAMPLE_RATE) + 2_000
            while (System.currentTimeMillis() < deadlineMs) {
                currentCoroutineContext().ensureActive()
                val head = runCatching { t.playbackHeadPosition }.getOrDefault(totalFrames)
                if (head >= totalFrames) break
                delay(20)
            }
            done.complete(Unit)
        }

        fun release() {
            track?.runCatching {
                pause()
                flush()
                release()
            }
            track = null
        }
    }
}
