package com.naira.assistant.presentation.main

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.naira.assistant.MainActivity
import com.naira.assistant.ThrisaApp
import com.naira.assistant.core.sound.AssistantReply
import com.naira.assistant.core.sound.VoiceChatController
import com.naira.assistant.core.sound.VoiceConversation
import com.naira.assistant.core.sound.VoiceState
import com.naira.assistant.core.sound.WakeWordDetector
import com.naira.assistant.core.sound.isActive
import com.naira.assistant.domain.AssistantSettingsStorage
import com.naira.assistant.domain.sttLanguageTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WakeWordForegroundService : Service() {

    inner class LocalBinder : Binder() {
        val service: WakeWordForegroundService
            get() = this@WakeWordForegroundService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private var assistant: AssistantViewModel? = null
    private var conversation: VoiceConversation? = null
    private var wakeWordDetector: WakeWordDetector? = null
    private var monitorJob: Job? = null
    private var voiceStateJob: Job? = null
    private var approvalJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var conversationActive = false
    private var foreground = false

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            setMonitoringRequested(false)
            releaseVoiceResources(resetState = true)
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val shouldStart = when (intent?.action) {
            ACTION_START -> true
            null -> isMonitoringRequested()
            else -> false
        }
        if (!shouldStart) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (monitorJob?.isActive == true) return START_STICKY
        if (!hasRequiredPermissions()) {
            setMonitoringRequested(false)
            publishState(VoiceState.Error(
                "Microphone and notification permissions are needed for background voice."
            ))
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!notificationsAreVisible()) {
            setMonitoringRequested(false)
            publishState(VoiceState.Error(
                "Enable notifications to keep Hey Assistant available in the background."
            ))
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            publishState(VoiceState.Preparing)
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(VoiceState.Preparing, awaitingApproval = false),
                serviceType,
            )
            foreground = true
            setMonitoringRequested(true)
            startMonitoring()
            return START_STICKY
        } catch (error: Exception) {
            setMonitoringRequested(false)
            val errorState = VoiceState.Error(
                error.message ?: "Hey Assistant couldn't start background voice."
            )
            publishState(errorState)
            releaseVoiceResources(resetState = false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
            postNotification(
                buildNotification(errorState, awaitingApproval = false, ongoing = false),
            )
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseVoiceResources(resetState = _state.value !is VoiceState.Error)
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startMonitoring() {
        if (monitorJob?.isActive == true) return

        val assistantViewModel = (application as ThrisaApp).assistantViewModel()
        val voiceConversation = VoiceConversation(
            context = this,
            chat = assistantViewModel.asVoiceChatController(),
            scope = serviceScope,
            language = AssistantSettingsStorage(this).getLanguage().sttLanguageTag(),
        )
        assistant = assistantViewModel
        conversation = voiceConversation
        wakeWordDetector = WakeWordDetector(this)
        acquireWakeLock()
        _isMonitoring.value = true

        voiceStateJob = serviceScope.launch {
            voiceConversation.state.collect { voiceState ->
                if (conversationActive && voiceState != VoiceState.Idle) {
                    publishState(voiceState)
                }
            }
        }
        approvalJob = serviceScope.launch {
            assistantViewModel.awaitingApproval.collect {
                updateNotification()
            }
        }
        monitorJob = serviceScope.launch {
            try {
                while (_isMonitoring.value && currentCoroutineContext().isActive) {
                    conversationActive = false
                    publishState(VoiceState.Preparing)
                    checkNotNull(wakeWordDetector).awaitWakeWord {
                        if (_isMonitoring.value) publishState(VoiceState.WakeWordListening)
                    }
                    if (!_isMonitoring.value) break

                    delay(HANDOVER_SETTLE_MILLIS)
                    conversationActive = true
                    voiceConversation.start()
                    val started = voiceConversation.state.first {
                        it.isActive || it is VoiceState.Error
                    }
                    if (started is VoiceState.Error) {
                        conversationActive = false
                        publishState(started)
                        delay(ERROR_DISPLAY_MILLIS)
                        continue
                    }

                    val finished = voiceConversation.state.first {
                        it == VoiceState.Idle || it is VoiceState.Error
                    }
                    conversationActive = false
                    if (finished is VoiceState.Error) {
                        publishState(finished)
                        delay(ERROR_DISPLAY_MILLIS)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                failMonitoring(error)
            }
        }
    }

    private fun failMonitoring(error: Exception) {
        setMonitoringRequested(false)
        _isMonitoring.value = false
        conversationActive = false
        val errorState = VoiceState.Error(
            error.message ?: "Hey Assistant wake-word detection failed."
        )
        publishState(errorState)
        releaseVoiceResources(resetState = false)
        if (foreground) stopForeground(STOP_FOREGROUND_DETACH)
        foreground = false
        postNotification(
            buildNotification(errorState, awaitingApproval = false, ongoing = false),
        )
        stopSelf()
    }

    private fun isMonitoringRequested(): Boolean =
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .getBoolean(PREFERENCE_MONITORING_REQUESTED, false)

    private fun setMonitoringRequested(requested: Boolean) {
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(PREFERENCE_MONITORING_REQUESTED, requested)
            .apply()
    }

    private fun releaseVoiceResources(resetState: Boolean) {
        _isMonitoring.value = false
        conversationActive = false
        monitorJob?.cancel()
        monitorJob = null
        voiceStateJob?.cancel()
        voiceStateJob = null
        approvalJob?.cancel()
        approvalJob = null
        conversation?.close()
        conversation = null
        wakeWordDetector = null
        assistant = null
        wakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wakeLock = null
        if (resetState) publishState(VoiceState.Idle)
    }

    private fun acquireWakeLock() {
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:WakeWord")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun publishState(newState: VoiceState) {
        _state.value = newState
        updateNotification()
    }

    fun reportUiError(message: String) {
        publishState(VoiceState.Error(message))
    }

    private fun updateNotification() {
        if (!foreground) return
        postNotification(
            buildNotification(
                state = _state.value,
                awaitingApproval = assistant?.awaitingApproval?.value == true,
            ),
        )
    }

    private fun postNotification(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(this)
        if (manager.areNotificationsEnabled()) manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        state: VoiceState,
        awaitingApproval: Boolean,
        ongoing: Boolean = true,
    ): Notification {
        val status = when {
            awaitingApproval -> "An action needs your approval. Open Naira to review it."
            state is VoiceState.Error -> state.message
            state is VoiceState.WakeWordListening ->
                "Listening for Hey Assistant. Wake-word detection runs on-device."
            state is VoiceState.Listening -> "Listening for your voice command."
            state is VoiceState.Transcribing -> "Transcribing your voice command."
            state is VoiceState.Thinking -> "Naira is working on your request."
            state is VoiceState.Speaking -> "Naira is speaking."
            else -> "Preparing on-device wake-word listening."
        }
        val openAppIntent = PendingIntent.getActivity(
            this,
            OPEN_APP_REQUEST_CODE,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            Intent(this, WakeWordForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(
                if (ongoing) "Hey Assistant background voice is on" else "Hey Assistant voice stopped"
            )
            .setContentText(status)
            .setStyle(NotificationCompat.BigTextStyle().bigText(status))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .apply {
                if (ongoing) addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            }
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Hey Assistant background voice",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shown while on-device wake-word listening is enabled."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun hasRequiredPermissions(): Boolean {
        val microphoneGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        return microphoneGranted && notificationsGranted
    }

    private fun notificationsAreVisible(): Boolean {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false
        return getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL_ID)
            ?.importance != NotificationManager.IMPORTANCE_NONE
    }

    companion object {
        const val ACTION_START = "com.naira.assistant.action.START_WAKE_WORD"
        const val ACTION_STOP = "com.naira.assistant.action.STOP_WAKE_WORD"
        const val CHANNEL_ID = "hey_naira_background_voice"

        private const val PREFERENCES_NAME = "background_voice"
        private const val PREFERENCE_MONITORING_REQUESTED = "monitoring_requested"
        private const val NOTIFICATION_ID = 4217
        private const val OPEN_APP_REQUEST_CODE = 4218
        private const val STOP_REQUEST_CODE = 4219
        private const val ERROR_DISPLAY_MILLIS = 2_500L
        private const val HANDOVER_SETTLE_MILLIS = 150L

        fun start(context: android.content.Context) {
            val intent = Intent(context, WakeWordForegroundService::class.java)
                .setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: android.content.Context) {
            val intent = Intent(context, WakeWordForegroundService::class.java)
                .setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}

private fun AssistantViewModel.asVoiceChatController(): VoiceChatController =
    object : VoiceChatController {
        override val replies: Flow<AssistantReply?> = messages.map { list ->
            list.lastOrNull { !it.isUser }?.let { AssistantReply(it.text, it.isError) }
        }
        override val busyStatus = this@asVoiceChatController.busyStatus
        override val awaitingApproval = this@asVoiceChatController.awaitingApproval
        override fun send(text: String) = this@asVoiceChatController.sendMessageStream(text)
        override fun cancelCurrent() = this@asVoiceChatController.cancelCurrent()
    }
