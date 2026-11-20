package com.naira.assistant.core.sound

import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import me.rerere.asr.ASRController
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.ASRStatus
import me.rerere.asr.providers.DashScopeASRController
import me.rerere.asr.providers.DeepSeekASRController
import me.rerere.asr.providers.MiMoASRController
import me.rerere.asr.providers.OpenAIRealtimeASRController
import me.rerere.asr.providers.StepASRController
import me.rerere.asr.providers.VolcengineASRController
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import com.naira.assistant.domain.settings.AsrProviderSettings

internal class ModuleSpeechToText(
    context: Context,
    private val settings: AsrProviderSettings,
) : SpeechToText {

    private val appContext = context.applicationContext
    private val provider = settings.toModuleProvider()
    private val httpClient = asrHttpClient

    @Volatile
    private var activeHandle: ControllerHandle? = null

    @Volatile
    private var finishRequested = false

    @Volatile
    private var closed = false

    override val requiresManualTurnEnd: Boolean =
        !provider.supportsServerVadVoiceMode

    override fun listen(): Flow<SpeechToText.Event> = callbackFlow {
        check(!closed) { "Speech recognition has already been closed." }
        val controller = createController()
        val handle = ControllerHandle(controller)
        activeHandle = handle
        finishRequested = false
        var finalSent = false
        var readySent = false
        var lastPartial = ""

        fun emitFinal(text: String) {
            if (finalSent) return
            finalSent = true
            trySend(SpeechToText.Event.Final(text.trim()))
            close()
        }

        val stateJob = launch {
            controller.state.collect { state ->
                when {
                    state.status == ASRStatus.Error || state.errorMessage != null -> {
                        close(
                            IllegalStateException(
                                "${settings.provider.displayName} speech recognition failed. " +
                                    "Check the provider credentials and network connection."
                            )
                        )
                    }
                    state.voiceTurn.isComplete -> {
                        emitFinal(state.voiceTurn.finalText ?: state.transcript)
                    }
                    state.status == ASRStatus.Connecting -> {
                        trySend(SpeechToText.Event.Preparing)
                    }
                    state.status == ASRStatus.Stopping -> {
                        trySend(SpeechToText.Event.Processing)
                    }
                    state.status == ASRStatus.Listening -> {
                        if (!readySent) {
                            readySent = true
                            trySend(SpeechToText.Event.Ready)
                        }
                        if (state.transcript.isNotBlank() && state.transcript != lastPartial) {
                            lastPartial = state.transcript
                            trySend(SpeechToText.Event.Partial(state.transcript))
                        }
                    }
                    state.status == ASRStatus.Idle && finishRequested -> {
                        emitFinal(state.transcript)
                    }
                }
            }
        }

        controller.start {}

        awaitClose {
            stateJob.cancel()
            handle.dispose()
            if (activeHandle === handle) activeHandle = null
        }
    }

    override fun finishListening() {
        if (requiresManualTurnEnd) {
            finishRequested = true
            activeHandle?.controller?.stop()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        activeHandle?.dispose()
        activeHandle = null
    }

    private fun createController(): ASRController = when (provider) {
        is ASRProviderSetting.OpenAIRealtime ->
            OpenAIRealtimeASRController(appContext, httpClient, provider)
        is ASRProviderSetting.DashScope ->
            DashScopeASRController(appContext, httpClient, provider)
        is ASRProviderSetting.Volcengine ->
            VolcengineASRController(appContext, httpClient, provider)
        is ASRProviderSetting.MiMo ->
            MiMoASRController(appContext, httpClient, provider)
        is ASRProviderSetting.Step ->
            StepASRController(appContext, httpClient, provider)
        is ASRProviderSetting.DeepSeek ->
            DeepSeekASRController(appContext, httpClient, provider)
    }

    private class ControllerHandle(val controller: ASRController) {
        private val disposed = AtomicBoolean(false)

        fun dispose() {
            if (disposed.compareAndSet(false, true)) controller.dispose()
        }
    }
}

internal fun AsrProviderSettings.toModuleProvider(): ASRProviderSetting {
    val apiKey = apiKey()
        ?: throw IllegalStateException(
            "Add an API key for ${provider.displayName} in Settings → Engine → Speech recognition."
        )
    return when (provider) {
        com.naira.assistant.domain.settings.AsrProvider.SYSTEM ->
            error("Android speech recognition does not use a speech-module provider.")
        com.naira.assistant.domain.settings.AsrProvider.OPENAI_REALTIME ->
            ASRProviderSetting.OpenAIRealtime(apiKey = apiKey)
        com.naira.assistant.domain.settings.AsrProvider.DASHSCOPE ->
            ASRProviderSetting.DashScope(apiKey = apiKey)
        com.naira.assistant.domain.settings.AsrProvider.VOLCENGINE ->
            ASRProviderSetting.Volcengine(apiKey = apiKey)
        com.naira.assistant.domain.settings.AsrProvider.MIMO ->
            ASRProviderSetting.MiMo(apiKey = apiKey)
        com.naira.assistant.domain.settings.AsrProvider.STEP ->
            ASRProviderSetting.Step(apiKey = apiKey)
        com.naira.assistant.domain.settings.AsrProvider.DEEPSEEK ->
            ASRProviderSetting.DeepSeek(apiKey = apiKey)
    }
}

private val asrHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()
}
