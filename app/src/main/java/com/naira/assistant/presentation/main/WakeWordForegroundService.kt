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
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
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
import com.naira.assistant.domain.settings.AssistantSettingsStorage
import com.naira.assistant.domain.settings.sttLanguageTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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
    private var alertStopJob: Job? = null
    private var alertTone: ToneGenerator? = null
    private var alertAudioFocusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var conversationActive = false
    private var foreground = false
    private var alertActive = false

    override fun onBind(intent: Intent?): IBinder = binder

    /** Starts/stops a user-requested conversation without changing service monitoring. */
    fun onMicButtonTap() {
        val voiceConversation = conversation ?: return
        monitorJob?.cancel()
        monitorJob = null
        val conversationWasActive = voiceConversation.state.value.isActive
        conversationActive = true
        voiceConversation.onMicTap()
        serviceScope.launch {
            if (!conversationWasActive) {
                voiceConversation.state.first { it.isActive || it is VoiceState.Error }
            }
            voiceConversation.state.first { it == VoiceState.Idle || it is VoiceState.Error }
            conversationActive = false
            if (_isMonitoring.value) {
                voiceConversation.close()
                if (conversation === voiceConversation) conversation = null
                startMonitoring()
            }
        }
    }

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
        if (intent?.action == ACTION_PLAY_ALERT) return startPhoneAlert(intent, startId)
        if (intent?.action == ACTION_STOP_ALERT) return stopPhoneAlert(intent, startId)

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
                buildNotification(VoiceState.Preparing),
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
                buildNotification(errorState, ongoing = false),
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

    private fun startPhoneAlert(intent: Intent, startId: Int): Int {
        val requestId = intent.getStringExtra(EXTRA_ALERT_REQUEST_ID)
        var alertStartAttempted = false
        try {
            check(notificationsAreVisible()) {
                "Enable notifications to play a phone-finder alert in the background."
            }
            val durationSeconds = intent.getIntExtra(
                EXTRA_ALERT_DURATION_SECONDS,
                DEFAULT_ALERT_DURATION_SECONDS,
            )
            check(durationSeconds in MIN_ALERT_DURATION_SECONDS..MAX_ALERT_DURATION_SECONDS) {
                "Phone alert duration must be between $MIN_ALERT_DURATION_SECONDS and " +
                    "$MAX_ALERT_DURATION_SECONDS seconds."
            }
            val sound = intent.getStringExtra(EXTRA_ALERT_SOUND) ?: DEFAULT_ALERT_SOUND
            val toneType = when (sound) {
                "beep" -> ToneGenerator.TONE_PROP_BEEP
                "ring" -> ToneGenerator.TONE_SUP_RINGTONE
                "urgent" -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
                else -> error("Unsupported phone alert sound '$sound'.")
            }

            val serviceType = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && _isMonitoring.value ->
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                else -> 0
            }
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(_state.value),
                serviceType,
            )
            foreground = true
            alertStartAttempted = true
            startAlertSound(toneType, durationSeconds)
            completePhoneAlertRequest(
                requestId,
                Result.success(
                    "Phone alert is sounding for up to $durationSeconds seconds. " +
                        "It follows the device alarm volume and Do Not Disturb settings. " +
                        "Say 'stop phone alert' to stop it sooner."
                ),
            )
        } catch (error: Exception) {
            completePhoneAlertRequest(requestId, Result.failure(error))
            if (alertStartAttempted || !alertActive) {
                stopPhoneAlertSound(updateNotification = _isMonitoring.value)
            }
            if (!_isMonitoring.value && !alertActive) {
                if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
                foreground = false
                stopSelf(startId)
            }
        }
        return if (_isMonitoring.value) START_STICKY else START_NOT_STICKY
    }

    private fun stopPhoneAlert(intent: Intent, startId: Int): Int {
        val wasActive = alertActive
        stopPhoneAlertSound(updateNotification = _isMonitoring.value)
        if (!_isMonitoring.value) {
            if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
            stopSelf(startId)
        }
        completePhoneAlertRequest(
            intent.getStringExtra(EXTRA_ALERT_REQUEST_ID),
            Result.success(
                if (wasActive) "Phone alert stopped." else "No phone alert was playing."
            ),
        )
        return if (_isMonitoring.value) START_STICKY else START_NOT_STICKY
    }

    private fun startAlertSound(toneType: Int, durationSeconds: Int) {
        stopPhoneAlertSound(updateNotification = _isMonitoring.value)
        if (!_isMonitoring.value) {
            acquireWakeLock(
                tag = "PhoneAlert",
                timeoutMillis = durationSeconds * 1_000L + ALERT_WAKE_LOCK_GRACE_MILLIS,
            )
        }
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(audioAttributes)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                if (alertActive &&
                    (change == AudioManager.AUDIOFOCUS_LOSS ||
                        change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                        change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
                ) {
                    stopPhoneAlertSound(updateNotification = _isMonitoring.value)
                    if (!_isMonitoring.value) {
                        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
                        foreground = false
                        stopSelf()
                    }
                }
            }, Handler(Looper.getMainLooper()))
            .build()
        val audioManager = getSystemService(AudioManager::class.java)
        check(audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            "Audio focus is unavailable for the phone alert."
        }
        alertAudioFocusRequest = focusRequest

        val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
        if (!toneGenerator.startTone(toneType, ALERT_TONE_DURATION_MILLIS)) {
            toneGenerator.release()
            error("The device couldn't start the phone alert sound.")
        }

        alertTone = toneGenerator
        alertActive = true
        updateNotification()
        alertStopJob = serviceScope.launch {
            val stopAt = SystemClock.elapsedRealtime() + durationSeconds * 1_000L
            try {
                while (true) {
                    val remainingMillis = stopAt - SystemClock.elapsedRealtime()
                    if (remainingMillis <= 0L) break
                    delay(minOf(ALERT_REPEAT_INTERVAL_MILLIS, remainingMillis))
                    if (SystemClock.elapsedRealtime() < stopAt) {
                        check(toneGenerator.startTone(toneType, ALERT_TONE_DURATION_MILLIS)) {
                            "The device stopped the phone alert sound."
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                publishState(
                    VoiceState.Error(error.message ?: "The phone alert sound stopped unexpectedly.")
                )
            } finally {
                if (alertStopJob === currentCoroutineContext()[Job]) {
                    alertStopJob = null
                    stopPhoneAlertSound(updateNotification = _isMonitoring.value)
                    if (!_isMonitoring.value) {
                        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
                        foreground = false
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun stopPhoneAlertSound(updateNotification: Boolean = true) {
        alertStopJob?.cancel()
        alertStopJob = null
        alertTone?.stopTone()
        alertTone?.release()
        alertTone = null
        alertAudioFocusRequest?.let {
            getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it)
        }
        alertAudioFocusRequest = null
        val wasActive = alertActive
        alertActive = false
        releaseWakeLockIfIdle()
        if (updateNotification && wasActive) updateNotification()
    }

    private fun releaseWakeLockIfIdle() {
        if (_isMonitoring.value || alertActive) return
        wakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wakeLock = null
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
        monitorJob = serviceScope.launch {
            try {
                try {
                    voiceConversation.prepareForWakeWord()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    // Wake detection stays available; voice startup reports ASR errors after activation.
                }
                while (_isMonitoring.value && currentCoroutineContext().isActive) {
                    conversationActive = false
                    publishState(VoiceState.Preparing)
                    checkNotNull(wakeWordDetector).awaitWakeWord {
                        if (_isMonitoring.value) publishState(VoiceState.WakeWordListening)
                    }
                    if (!_isMonitoring.value) break

                    conversationActive = true
                    voiceConversation.start(afterWakeWord = true)
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
            buildNotification(errorState, ongoing = false),
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
        stopPhoneAlertSound(updateNotification = false)
        _isMonitoring.value = false
        conversationActive = false
        monitorJob?.cancel()
        monitorJob = null
        voiceStateJob?.cancel()
        voiceStateJob = null
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

    private fun acquireWakeLock(tag: String = "WakeWord", timeoutMillis: Long? = null) {
        if (wakeLock?.isHeld == true) return
        val lock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:$tag")
            .apply { setReferenceCounted(false) }
        wakeLock = lock
        if (timeoutMillis == null) lock.acquire() else lock.acquire(timeoutMillis)
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
        ongoing: Boolean = true,
    ): Notification {
        val status = when {
            alertActive ->
                "Phone alert is ringing. Say 'stop phone alert' or tap Stop alert."
            state is VoiceState.Error -> state.message
            state is VoiceState.WakeWordListening ->
                "Listening for Hey Assistant, Hello Assistant, or Wakeup Assistant. Wake-word detection runs on-device."
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
        val stopAlertIntent = PendingIntent.getService(
            this,
            STOP_ALERT_REQUEST_CODE,
            Intent(this, WakeWordForegroundService::class.java).setAction(ACTION_STOP_ALERT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(
                when {
                    !ongoing -> "Hey Assistant voice stopped"
                    alertActive -> "Phone finder alert is playing"
                    else -> "Hey Assistant background voice is on"
                }
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
                if (alertActive) {
                    addAction(android.R.drawable.ic_media_pause, "Stop alert", stopAlertIntent)
                }
                if (ongoing && _isMonitoring.value) {
                    addAction(android.R.drawable.ic_media_pause, "Stop voice", stopIntent)
                }
            }
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Hey Assistant background voice",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shown while background voice or a phone-finder alert is active."
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
        const val ACTION_PLAY_ALERT = "com.naira.assistant.action.PLAY_PHONE_ALERT"
        const val ACTION_STOP_ALERT = "com.naira.assistant.action.STOP_PHONE_ALERT"
        const val CHANNEL_ID = "hey_naira_background_voice"

        private const val PREFERENCES_NAME = "background_voice"
        private const val PREFERENCE_MONITORING_REQUESTED = "monitoring_requested"
        private const val EXTRA_ALERT_REQUEST_ID = "alert_request_id"
        private const val EXTRA_ALERT_SOUND = "alert_sound"
        private const val EXTRA_ALERT_DURATION_SECONDS = "alert_duration_seconds"
        private const val NOTIFICATION_ID = 4217
        private const val OPEN_APP_REQUEST_CODE = 4218
        private const val STOP_REQUEST_CODE = 4219
        private const val STOP_ALERT_REQUEST_CODE = 4220
        private const val DEFAULT_ALERT_DURATION_SECONDS = 30
        private const val MIN_ALERT_DURATION_SECONDS = 5
        private const val MAX_ALERT_DURATION_SECONDS = 60
        private const val DEFAULT_ALERT_SOUND = "beep"
        private const val ALERT_TONE_DURATION_MILLIS = 800
        private const val ALERT_REPEAT_INTERVAL_MILLIS = 1_600L
        private const val ALERT_WAKE_LOCK_GRACE_MILLIS = 5_000L
        private const val ALERT_REQUEST_TIMEOUT_MILLIS = 5_000L
        private const val ERROR_DISPLAY_MILLIS = 2_500L
        private val pendingAlertRequests = ConcurrentHashMap<String, CompletableDeferred<String>>()

        suspend fun controlPhoneAlert(
            context: android.content.Context,
            action: String,
            sound: String,
            durationSeconds: Int,
        ): String {
            require(action == ACTION_PLAY_ALERT || action == ACTION_STOP_ALERT) {
                "Unsupported phone alert action."
            }
            val requestId = UUID.randomUUID().toString()
            val result = CompletableDeferred<String>()
            check(pendingAlertRequests.putIfAbsent(requestId, result) == null) {
                "Couldn't register the phone alert request."
            }

            try {
                val intent = Intent(context, WakeWordForegroundService::class.java)
                    .setAction(action)
                    .putExtra(EXTRA_ALERT_REQUEST_ID, requestId)
                    .putExtra(EXTRA_ALERT_SOUND, sound)
                    .putExtra(EXTRA_ALERT_DURATION_SECONDS, durationSeconds)
                val started = if (action == ACTION_PLAY_ALERT) {
                    ContextCompat.startForegroundService(context.applicationContext, intent)
                } else {
                    context.applicationContext.startService(intent)
                }
                checkNotNull(started) { "Phone alert service could not be started." }
                return withTimeout(ALERT_REQUEST_TIMEOUT_MILLIS) { result.await() }
            } finally {
                pendingAlertRequests.remove(requestId, result)
            }
        }

        private fun completePhoneAlertRequest(
            requestId: String?,
            outcome: Result<String>,
        ) {
            val request = requestId?.let(pendingAlertRequests::remove) ?: return
            outcome.fold(
                onSuccess = request::complete,
                onFailure = request::completeExceptionally,
            )
        }

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
        override fun send(text: String) = this@asVoiceChatController.sendMessageStream(text)
        override fun cancelCurrent() = this@asVoiceChatController.cancelCurrent()
    }
