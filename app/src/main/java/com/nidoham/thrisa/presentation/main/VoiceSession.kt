package com.nidoham.thrisa.presentation.main

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nidoham.thrisa.core.sound.AssistantReply
import com.nidoham.thrisa.core.sound.isActive
import com.nidoham.thrisa.core.sound.VoiceChatController
import com.nidoham.thrisa.core.sound.VoiceConversation
import com.nidoham.thrisa.core.sound.VoiceState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** One mic-driven session exposed to the UI: the current voice state + the mic tap handler. */
class VoiceSession(val state: VoiceState, val onMicClick: () -> Unit)

/** Adapts the chat ViewModel to the [VoiceChatController] seam the sound layer depends on. */
private fun AssistantViewModel.asVoiceChatController(): VoiceChatController =
    object : VoiceChatController {
        override val replies: Flow<AssistantReply?> =
            messages.map { list ->
                list.lastOrNull { !it.isUser }?.let { AssistantReply(it.text, it.isError) }
            }
        override val busyStatus = this@asVoiceChatController.busyStatus
        override fun send(text: String) = this@asVoiceChatController.sendMessageStream(text)
        override fun cancelCurrent() = this@asVoiceChatController.cancelCurrent()
    }

/** Creates the controller, handles RECORD_AUDIO permission, and stops everything when the app leaves the foreground. */
@Composable
fun rememberVoiceSession(assistant: AssistantViewModel, language: String): VoiceSession {
    val context = LocalContext.current
    val scope = rememberCoroutineScope() // Main dispatcher
    val conversation = remember(assistant, language) {
        VoiceConversation(context, assistant.asVoiceChatController(), scope, language)
    }
    val state by conversation.state.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) conversation.start()
        else conversation.reportError("Microphone permission is required for voice chat.")
    }

    DisposableEffect(conversation) { onDispose { conversation.close() } }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, conversation) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) conversation.stop()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    return VoiceSession(state) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted && !state.isActive) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        else conversation.onMicTap()
    }
}
