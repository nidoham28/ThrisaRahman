package com.naira.assistant.presentation.main

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naira.assistant.core.sound.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow

/** One mic-driven session exposed to the UI: the current voice state + the mic tap handler. */
class VoiceSession(val state: VoiceState, val onMicClick: () -> Unit)

/** Binds the UI to the user-started microphone foreground service. */
@Composable
fun rememberVoiceSession(): VoiceSession {
    val context = LocalContext.current
    var service by remember { mutableStateOf<WakeWordForegroundService?>(null) }
    val fallbackState = remember { MutableStateFlow<VoiceState>(VoiceState.Idle) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (hasRequiredVoicePermissions(context)) {
            try { WakeWordForegroundService.start(context) }
            catch (_: Exception) { fallbackState.value = VoiceState.Error("Couldn't start background voice.") }
        } else if (granted.isNotEmpty()) {
            fallbackState.value = VoiceState.Error("Microphone and notification permissions are needed for background voice.")
        }
    }

    LaunchedEffect(context) {
        val preferences = context.getSharedPreferences("background_voice", android.content.Context.MODE_PRIVATE)
        if (!preferences.getBoolean("first_open_prompted", false)) {
            preferences.edit().putBoolean("first_open_prompted", true).apply()
            val missing = missingVoicePermissions(context)
            if (missing.isEmpty()) WakeWordForegroundService.start(context)
            else permissionLauncher.launch(missing)
        }
    }

    DisposableEffect(context) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as? WakeWordForegroundService.LocalBinder)?.service
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
        val bound = context.bindService(
            Intent(context, WakeWordForegroundService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        if (!bound) {
            fallbackState.value = VoiceState.Error("Couldn't connect to background voice.")
        }
        onDispose {
            if (bound) context.unbindService(connection)
            service = null
        }
    }

    val stateFlow = service?.state ?: fallbackState
    val state by stateFlow.collectAsStateWithLifecycle()

    fun reportError(message: String) {
        val errorState = VoiceState.Error(message)
        val activeService = service
        if (activeService != null) activeService.reportUiError(message)
        else fallbackState.value = errorState
    }

    return VoiceSession(state) {
        val activeService = service
        if (activeService != null) activeService.onMicButtonTap()
        else reportError("Background voice isn't running. Enable it in Settings first.")
    }
}

private fun hasRequiredVoicePermissions(context: Context): Boolean =
    missingVoicePermissions(context).isEmpty()

private fun missingVoicePermissions(context: Context): Array<String> = buildList {
    if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        add(Manifest.permission.RECORD_AUDIO)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()
