
package com.naira.assistant.core.tools.executor

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import com.naira.assistant.core.tools.executor.CommandExecution.jsonStringArg
import com.naira.assistant.domain.FunctionCall
import com.naira.assistant.presentation.main.WakeWordForegroundService
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/**
 * Executes phone-alert, media-playback, media-volume and media-status tools.
 *
 * Playback commands are dispatched as paired media key events.
 * They are requests to the active media receiver, not confirmation
 * that the receiving application performed the requested action.
 *
 * Volume operations target STREAM_MUSIC only.
 */
object MediaToolExecutor {

    private const val SEEK_STEP_SECONDS = 10
    private const val DEFAULT_SEEK_SECONDS = 10
    private const val MIN_SEEK_SECONDS = 5
    private const val MAX_SEEK_SECONDS = 120

    private const val MAX_VOLUME_STEPS = 15

    private const val DEFAULT_PHONE_ALERT_DURATION_SECONDS = 30
    private const val MIN_PHONE_ALERT_DURATION_SECONDS = 5
    private const val MAX_PHONE_ALERT_DURATION_SECONDS = 60

    private const val DEFAULT_PHONE_ALERT_SOUND = "beep"

    private val PHONE_ALERT_SOUNDS = setOf(
        "beep",
        "ring",
        "urgent"
    )

    // ------------------------------------------------------------------
    // Phone alert
    // ------------------------------------------------------------------

    suspend fun executePhoneAlertControl(
        call: FunctionCall,
        context: Context
    ): JsonObject {
        val action = jsonStringArg(call, "action")
            ?.trim()
            ?.lowercase()
            ?: return error("Please provide an action: 'start' or 'stop'.")

        return when (action) {
            "start" -> {
                val durationArg = call.args["duration_seconds"]
                val requestedDuration = extractIntArg(call, "duration_seconds")

                if (durationArg != null && requestedDuration == null) {
                    return error("'duration_seconds' must be an integer.")
                }

                val duration = resolvePhoneAlertDuration(requestedDuration)
                    ?: return error(
                        "'duration_seconds' must be between " +
                                "$MIN_PHONE_ALERT_DURATION_SECONDS and " +
                                "$MAX_PHONE_ALERT_DURATION_SECONDS."
                    )

                if (
                    call.args["sound"] != null &&
                    jsonStringArg(call, "sound") == null
                ) {
                    return error("'sound' must be 'beep', 'ring', or 'urgent'.")
                }

                val sound = resolvePhoneAlertSound(
                    jsonStringArg(call, "sound")
                ) ?: return error(
                    "Unknown sound. Choose 'beep', 'ring', or 'urgent'."
                )

                try {
                    val message = WakeWordForegroundService.controlPhoneAlert(
                        context = context.applicationContext,
                        action = WakeWordForegroundService.ACTION_PLAY_ALERT,
                        sound = sound,
                        durationSeconds = duration
                    )

                    buildJsonObject {
                        put("success", true)
                        put("action", action)
                        put("sound", sound)
                        put("duration_seconds", duration)
                        put("message", message)
                    }
                } catch (e: Exception) {
                    error(
                        "Could not start the phone alert: " +
                                (e.message ?: "Unknown error.")
                    )
                }
            }

            "stop" -> {
                try {
                    val message = WakeWordForegroundService.controlPhoneAlert(
                        context = context.applicationContext,
                        action = WakeWordForegroundService.ACTION_STOP_ALERT,
                        sound = DEFAULT_PHONE_ALERT_SOUND,
                        durationSeconds = DEFAULT_PHONE_ALERT_DURATION_SECONDS
                    )

                    buildJsonObject {
                        put("success", true)
                        put("action", action)
                        put("message", message)
                    }
                } catch (e: Exception) {
                    error(
                        "Could not stop the phone alert: " +
                                (e.message ?: "Unknown error.")
                    )
                }
            }

            else -> error(
                "Unknown action '$action'. Use 'start' or 'stop'."
            )
        }
    }

    // ------------------------------------------------------------------
    // Media playback
    // ------------------------------------------------------------------

    fun executeMediaPlaybackControl(
        call: FunctionCall,
        context: Context
    ): JsonObject {
        val action = jsonStringArg(call, "action")
            ?.trim()
            ?.lowercase()
            ?: return error(
                "Please provide an action such as 'pause', 'play', or 'next_track'."
            )

        val keyCode = when (action) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "toggle_play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            "next_track" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous_track" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "fast_forward" -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            "rewind" -> KeyEvent.KEYCODE_MEDIA_REWIND

            else -> return error(
                "Unknown playback action '$action'. Use one of: " +
                        "play, pause, toggle_play_pause, stop, next_track, " +
                        "previous_track, fast_forward, rewind."
            )
        }

        val secondsArg = call.args["seconds"]
        val requestedSeconds = extractIntArg(call, "seconds")

        if (
            action in setOf("fast_forward", "rewind") &&
            secondsArg != null &&
            requestedSeconds == null
        ) {
            return error("'seconds' must be an integer.")
        }

        return try {
            val audio = audioManager(context)
            val mediaWasActive = audio.isMusicActive

            if (action == "fast_forward" || action == "rewind") {
                val seconds = resolveSeekSeconds(requestedSeconds)
                val presses = seekPresses(seconds)

                repeat(presses) {
                    dispatchMediaKey(audio, keyCode)
                }

                val approximateSeconds = presses * SEEK_STEP_SECONDS
                val direction = if (action == "fast_forward") {
                    "forward"
                } else {
                    "backward"
                }

                buildJsonObject {
                    put("success", true)
                    put("dispatched", true)
                    put("action", action)
                    put("requested_seconds", seconds)
                    put("approximate_seconds", approximateSeconds)
                    put("media_was_active", mediaWasActive)
                    put(
                        "message",
                        "Sent a $direction seek request of approximately " +
                                "$approximateSeconds seconds. The actual result " +
                                "depends on the active media app."
                    )
                }
            } else {
                dispatchMediaKey(audio, keyCode)

                buildJsonObject {
                    put("success", true)
                    put("dispatched", true)
                    put("action", action)
                    put("media_was_active", mediaWasActive)
                    put(
                        "message",
                        playbackMessage(action)
                    )
                }
            }
        } catch (e: SecurityException) {
            error(
                "Permission denied while sending the media command: " +
                        (e.message ?: "Access denied.")
            )
        } catch (e: Exception) {
            error(
                "Could not send the media command: " +
                        (e.message ?: "Unknown error.")
            )
        }
    }

    // ------------------------------------------------------------------
    // Media volume
    // ------------------------------------------------------------------

    fun executeMediaVolumeControl(
        call: FunctionCall,
        context: Context
    ): JsonObject {
        val action = jsonStringArg(call, "action")
            ?.trim()
            ?.lowercase()
            ?: return error(
                "Please provide an action such as 'raise', 'lower', " +
                        "'set_level', 'mute', 'unmute', or 'get'."
            )

        return try {
            val audio = audioManager(context)
            val maxIndex = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

            when (action) {
                "raise", "lower" -> {
                    val stepsArg = call.args["steps"]
                    val requestedSteps = extractIntArg(call, "steps")

                    if (stepsArg != null && requestedSteps == null) {
                        return error("'steps' must be an integer.")
                    }

                    val steps = requestedSteps ?: 1

                    if (steps !in 1..MAX_VOLUME_STEPS) {
                        return error(
                            "'steps' must be between 1 and $MAX_VOLUME_STEPS."
                        )
                    }

                    val direction = if (action == "raise") {
                        AudioManager.ADJUST_RAISE
                    } else {
                        AudioManager.ADJUST_LOWER
                    }

                    repeat(steps) {
                        audio.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            direction,
                            AudioManager.FLAG_SHOW_UI
                        )
                    }

                    val verb = if (action == "raise") {
                        "raised"
                    } else {
                        "lowered"
                    }

                    volumeResult(
                        audio = audio,
                        action = action,
                        message = "Media volume $verb by $steps step(s)."
                    )
                }

                "set_level" -> {
                    val percent = extractIntArg(call, "level_percent")
                        ?: return error(
                            "Please provide an integer 'level_percent' " +
                                    "between 0 and 100."
                        )

                    if (percent !in 0..100) {
                        return error(
                            "'level_percent' must be between 0 and 100."
                        )
                    }

                    val index = volumeIndexForPercent(percent, maxIndex)

                    audio.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        index,
                        AudioManager.FLAG_SHOW_UI
                    )

                    volumeResult(
                        audio = audio,
                        action = action,
                        message = "Requested media volume: $percent%."
                    )
                }

                "mute", "unmute" -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                        return error(
                            "Muting the media stream is not supported " +
                                    "on this Android version."
                        )
                    }

                    val adjustment = if (action == "mute") {
                        AudioManager.ADJUST_MUTE
                    } else {
                        AudioManager.ADJUST_UNMUTE
                    }

                    audio.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        adjustment,
                        AudioManager.FLAG_SHOW_UI
                    )

                    volumeResult(
                        audio = audio,
                        action = action,
                        message = if (action == "mute") {
                            "Sent a media mute request."
                        } else {
                            "Sent a media unmute request."
                        }
                    )
                }

                "get" -> {
                    volumeResult(
                        audio = audio,
                        action = action,
                        message = "Current media volume."
                    )
                }

                else -> error(
                    "Unknown volume action '$action'. Use one of: " +
                            "raise, lower, set_level, mute, unmute, get."
                )
            }
        } catch (e: SecurityException) {
            error(
                "Permission denied while changing media volume: " +
                        (e.message ?: "Access denied.")
            )
        } catch (e: Exception) {
            error(
                "Could not control media volume: " +
                        (e.message ?: "Unknown error.")
            )
        }
    }

    // ------------------------------------------------------------------
    // Media status
    // ------------------------------------------------------------------

    fun executeGetMediaStatus(context: Context): JsonObject {
        return try {
            val audio = audioManager(context)

            val maxIndex = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentIndex = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val musicActive = audio.isMusicActive

            buildJsonObject {
                put("success", true)

                // This indicates active music audio output.
                // It does not identify a specific media session.
                put("is_music_active", musicActive)

                put(
                    "message",
                    if (musicActive) {
                        "Music audio is currently active."
                    } else {
                        "No active music audio was detected."
                    }
                )

                put(
                    "ringer_mode",
                    when (audio.ringerMode) {
                        AudioManager.RINGER_MODE_NORMAL -> "normal"
                        AudioManager.RINGER_MODE_SILENT -> "silent"
                        AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                        else -> "unknown"
                    }
                )

                put("volume_current", currentIndex)
                put("volume_max", maxIndex)
                put(
                    "volume_percent",
                    volumePercentForIndex(currentIndex, maxIndex)
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    put(
                        "volume_muted",
                        audio.isStreamMute(AudioManager.STREAM_MUSIC)
                    )
                }
            }
        } catch (e: Exception) {
            error(
                "Could not retrieve media status: " +
                        (e.message ?: "Unknown error.")
            )
        }
    }

    // ------------------------------------------------------------------
    // Pure helpers
    // ------------------------------------------------------------------

    internal fun resolveSeekSeconds(requested: Int?): Int =
        (requested ?: DEFAULT_SEEK_SECONDS)
            .coerceIn(MIN_SEEK_SECONDS, MAX_SEEK_SECONDS)

    internal fun resolvePhoneAlertDuration(requested: Int?): Int? =
        (requested ?: DEFAULT_PHONE_ALERT_DURATION_SECONDS)
            .takeIf {
                it in MIN_PHONE_ALERT_DURATION_SECONDS..MAX_PHONE_ALERT_DURATION_SECONDS
            }

    internal fun resolvePhoneAlertSound(requested: String?): String? {
        val sound = requested
            ?.trim()
            ?.lowercase()
            ?: DEFAULT_PHONE_ALERT_SOUND

        return sound.takeIf { it in PHONE_ALERT_SOUNDS }
    }

    internal fun seekPresses(seconds: Int): Int =
        ((seconds + SEEK_STEP_SECONDS - 1) / SEEK_STEP_SECONDS)
            .coerceAtLeast(1)

    internal fun volumeIndexForPercent(
        percent: Int,
        maxIndex: Int
    ): Int {
        if (maxIndex <= 0) return 0

        return (percent.coerceIn(0, 100) * maxIndex / 100.0)
            .roundToInt()
            .coerceIn(0, maxIndex)
    }

    internal fun volumePercentForIndex(
        index: Int,
        maxIndex: Int
    ): Int {
        if (maxIndex <= 0) return 0

        return (index.coerceIn(0, maxIndex) * 100.0 / maxIndex)
            .roundToInt()
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private fun audioManager(context: Context): AudioManager =
        context.applicationContext.getSystemService(
            Context.AUDIO_SERVICE
        ) as AudioManager

    /**
     * Sends a paired DOWN/UP media key event.
     */
    private fun dispatchMediaKey(
        audio: AudioManager,
        keyCode: Int
    ) {
        audio.dispatchMediaKeyEvent(
            KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        )

        audio.dispatchMediaKeyEvent(
            KeyEvent(KeyEvent.ACTION_UP, keyCode)
        )
    }

    private fun playbackMessage(action: String): String =
        when (action) {
            "play" -> "Sent a play request."
            "pause" -> "Sent a pause request."
            "toggle_play_pause" -> "Sent a play/pause toggle request."
            "stop" -> "Sent a stop request."
            "next_track" -> "Sent a next-track request."
            "previous_track" -> "Sent a previous-track request."
            else -> "Sent the '$action' media command."
        }

    /**
     * Returns the actual volume snapshot after the requested operation.
     */
    private fun volumeResult(
        audio: AudioManager,
        action: String,
        message: String
    ): JsonObject {
        val maxIndex = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentIndex = audio.getStreamVolume(AudioManager.STREAM_MUSIC)

        return buildJsonObject {
            put("success", true)
            put("action", action)
            put("message", message)
            put("volume_current", currentIndex)
            put("volume_max", maxIndex)
            put(
                "volume_percent",
                volumePercentForIndex(currentIndex, maxIndex)
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                put(
                    "volume_muted",
                    audio.isStreamMute(AudioManager.STREAM_MUSIC)
                )
            }
        }
    }

    private fun error(message: String): JsonObject =
        buildJsonObject {
            put("success", false)
            put("error", message)
        }

    /**
     * Supports JSON integer arguments and numeric strings.
     * Returns null for missing or invalid values.
     */
    private fun extractIntArg(
        call: FunctionCall,
        key: String
    ): Int? {
        return runCatching {
            val element = call.args[key] ?: return null
            val primitive = element.jsonPrimitive

            primitive.intOrNull
                ?: primitive.content.trim().toIntOrNull()
        }.getOrNull()
    }
}
