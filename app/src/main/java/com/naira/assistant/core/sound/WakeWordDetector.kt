package com.naira.assistant.core.sound

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class WakeWordDetector(context: Context) {

    private val appContext = context.applicationContext

    suspend fun awaitWakeWord(onListening: suspend () -> Unit) = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECORD_AUDIO,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("Microphone permission is required for wake-word listening.")
        }
        val keywordSpotter = createKeywordSpotter()
        var recorder: AudioRecord? = null
        var stream: OnlineStream? = null
        try {
            val activeStream = keywordSpotter.createStream()
            stream = activeStream
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            check(minBufferSize > 0) { "Microphone audio format is unavailable." }

            val activeRecorder = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                )
                .setBufferSizeInBytes(
                    maxOf(minBufferSize, SAMPLES_PER_READ * BYTES_PER_SAMPLE * 2),
                )
                .build()
            recorder = activeRecorder
            check(activeRecorder.state == AudioRecord.STATE_INITIALIZED) {
                "Microphone could not be initialized for wake-word detection."
            }

            activeRecorder.startRecording()
            check(activeRecorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "Microphone recording could not be started for wake-word detection."
            }
            onListening()

            val coroutineContext = currentCoroutineContext()
            val pcmBuffer = ShortArray(SAMPLES_PER_READ)
            while (true) {
                coroutineContext.ensureActive()
                val count = activeRecorder.read(
                    pcmBuffer,
                    0,
                    pcmBuffer.size,
                    AudioRecord.READ_BLOCKING,
                )
                coroutineContext.ensureActive()
                check(count >= 0) { "Microphone audio capture failed ($count)." }
                if (count == 0) continue

                val samples = FloatArray(count) { index ->
                    pcmBuffer[index] / PCM_SCALE
                }
                activeStream.acceptWaveform(samples, SAMPLE_RATE)
                while (keywordSpotter.isReady(activeStream)) {
                    keywordSpotter.decode(activeStream)
                    if (keywordSpotter.getResult(activeStream).keyword.isNotBlank()) {
                        return@withContext
                    }
                }
            }
        } finally {
            try {
                recorder?.let { activeRecorder ->
                    try {
                        if (activeRecorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            activeRecorder.stop()
                        }
                    } finally {
                        activeRecorder.release()
                    }
                }
            } finally {
                try {
                    stream?.release()
                } finally {
                    keywordSpotter.release()
                }
            }
        }
    }

    internal fun createKeywordSpotter(): KeywordSpotter = KeywordSpotter(
        assetManager = appContext.assets,
        config = KeywordSpotterConfig(
            featConfig = FeatureConfig(
                sampleRate = SAMPLE_RATE,
                featureDim = FEATURE_DIM,
            ),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = MODEL_ENCODER,
                    decoder = MODEL_DECODER,
                    joiner = MODEL_JOINER,
                ),
                tokens = MODEL_TOKENS,
                numThreads = MODEL_THREADS,
                provider = "cpu",
                modelType = MODEL_TYPE,
            ),
            keywordsFile = KEYWORDS_FILE,
            keywordsScore = KEYWORD_SCORE,
            keywordsThreshold = KEYWORD_THRESHOLD,
            numTrailingBlanks = NUM_TRAILING_BLANKS,
        ),
    )

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val FEATURE_DIM = 80
        const val MODEL_THREADS = 1
        const val MODEL_TYPE = "zipformer2"
        const val MODEL_ENCODER =
            "wake-word/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
        const val MODEL_DECODER =
            "wake-word/decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
        const val MODEL_JOINER =
            "wake-word/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
        const val MODEL_TOKENS = "wake-word/tokens.txt"
        const val KEYWORDS_FILE = "wake-word/keywords.txt"
        const val KEYWORD_SCORE = 1.5f
        const val KEYWORD_THRESHOLD = 0.25f
        const val NUM_TRAILING_BLANKS = 2
        const val SAMPLES_PER_READ = SAMPLE_RATE / 10
        const val BYTES_PER_SAMPLE = 2
        const val PCM_SCALE = 32768.0f
    }
}
