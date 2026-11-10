package com.naira.assistant.core.tools.executor

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import com.naira.assistant.core.tools.executor.BuiltinToolExecutor.jsonStringArg
import com.naira.assistant.domain.FunctionCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/**
 * Implementation of the media-control tools: `media_playback_control`,
 * `media_volume_control` and `get_media_status`.
 *
 * Playback actions are delivered as media key events through
 * [AudioManager.dispatchMediaKeyEvent], which routes them to whichever app
 * currently owns the active media session — exactly like headset buttons.
 * This needs no special permissions and works across player apps.
 *
 * Volume actions go through [AudioManager] on [AudioManager.STREAM_MUSIC],
 * the stream used by music, video and podcast apps.
 *
 * The [context] is the Android application context used to obtain the
 * [AudioManager]. It is injected once when the tool executor is built,
 * NOT supplied by the model.
 */
object MediaToolExecutor {

    /** Media apps handle fast-forward/rewind key presses in ~10 s steps. */
    private const val SEEK_STEP_SECONDS = 10

    private const val DEFAULT_SEEK_SECONDS = 10
    private const val MIN_SEEK_SECONDS = 5
    private const val MAX_SEEK_SECONDS = 120

    private const val MAX_VOLUME_STEPS = 15

    /**
     * Executes the `media_playback_control` tool.
     *
     * @param call    the function call from Gemini, carrying `action` and
     *                optionally `seconds`.
     * @param context Android application context for the AudioManager.
     * @return a [JsonObject] describing the performed action.
     */
    fun executeMediaPlaybackControl(call: FunctionCall, context: Context): JsonObject {
        val action = jsonStringArg(call, "action")?.trim()?.lowercase()
            ?: return error("Please provide an 'action' to perform, e.g. 'pause', 'play' or 'next_track'.")

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
                "Unknown playback action '$action'. Use one of: play, pause, toggle_play_pause, " +
                        "stop, next_track, previous_track, fast_forward, rewind."
            )
        }

        val audio = audioManager(context)
        val mediaWasActive = audio.isMusicActive

        if (action == "fast_forward" || action == "rewind") {
            val seconds = resolveSeekSeconds(extractIntArg(call, "seconds"))
            val presses = seekPresses(seconds)
            repeat(presses) { dispatchMediaKey(audio, keyCode) }
            val direction = if (action == "fast_forward") "forward" else "backward"
            return buildJsonObject {
                put("success", true)
                put("action", action)
                put("approximate_seconds", presses * SEEK_STEP_SECONDS)
                put("media_was_active", mediaWasActive)
                put(
                    "message",
                    "Skipped $direction about ${presses * SEEK_STEP_SECONDS} seconds. " +
                            "The exact result depends on the media app."
                )
            }
        }

        dispatchMediaKey(audio, keyCode)
        return buildJsonObject {
            put("success", true)
            put("action", action)
            put("media_was_active", mediaWasActive)
            put("message", playbackMessage(action))
        }
    }

    /**
     * Executes the `media_volume_control` tool.
     *
     * @param call    the function call from Gemini, carrying `action` and
     *                optionally `level_percent` / `steps`.
     * @param context Android application context for the AudioManager.
     * @return a [JsonObject] with the resulting volume snapshot.
     */
    fun executeMediaVolumeControl(call: FunctionCall, context: Context): JsonObject {
        val action = jsonStringArg(call, "action")?.trim()?.lowercase()
            ?: return error(
                "Please provide an 'action' such as 'raise', 'lower', 'set_level', " +
                        "'mute', 'unmute' or 'get'."
            )

        val audio = audioManager(context)
        val maxIndex = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        when (action) {
            "raise", "lower" -> {
                val steps = (extractIntArg(call, "steps") ?: 1).coerceIn(1, MAX_VOLUME_STEPS)
                val direction =
                    if (action == "raise") AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                repeat(steps) {
                    audio.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        direction,
                        AudioManager.FLAG_SHOW_UI
                    )
                }
                val verb = if (action == "raise") "raised" else "lowered"
                return volumeResult(audio, action, "Media volume $verb by $steps step(s).")
            }

            "set_level" -> {
                val percent = extractIntArg(call, "level_percent")
                    ?: return error("Please provide 'level_percent' (0-100) for 'set_level'.")
                val index = volumeIndexForPercent(percent, maxIndex)
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, index, AudioManager.FLAG_SHOW_UI)
                return volumeResult(
                    audio,
                    action,
                    "Media volume set to ${volumePercentForIndex(index, maxIndex)}%."
                )
            }

            "mute", "unmute" -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    return error("Muting the media stream is not supported on this Android version.")
                }
                val adjustment =
                    if (action == "mute") AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
                audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    adjustment,
                    AudioManager.FLAG_SHOW_UI
                )
                return volumeResult(
                    audio,
                    action,
                    if (action == "mute") "Media volume muted." else "Media volume unmuted."
                )
            }

            "get" -> {
                val percent = volumePercentForIndex(
                    audio.getStreamVolume(AudioManager.STREAM_MUSIC),
                    maxIndex
                )
                return volumeResult(audio, action, "Media volume is at $percent%.")
            }

            else -> return error(
                "Unknown volume action '$action'. Use one of: raise, lower, " +
                        "set_level, mute, unmute, get."
            )
        }
    }

    /**
     * Executes the `get_media_status` tool.
     *
     * Reports whether audio is actively playing, the media volume snapshot
     * and the ringer mode. It cannot identify the playing track or app.
     *
     * @param context Android application context for the AudioManager.
     * @return a [JsonObject] describing the current media state.
     */
    fun executeGetMediaStatus(context: Context): JsonObject {
        val audio = audioManager(context)
        val maxIndex = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentIndex = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val musicActive = audio.isMusicActive

        return buildJsonObject {
            put("success", true)
            put("is_music_active", musicActive)
            put(
                "message",
                if (musicActive) "Media is currently playing."
                else "No media appears to be playing right now."
            )
            put("ringer_mode", when (audio.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> "normal"
                AudioManager.RINGER_MODE_SILENT -> "silent"
                AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                else -> "unknown"
            })
            put("volume_current", currentIndex)
            put("volume_max", maxIndex)
            put("volume_percent", volumePercentForIndex(currentIndex, maxIndex))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                put("volume_muted", audio.isStreamMute(AudioManager.STREAM_MUSIC))
            }
        }
    }

    // ------------------------------------------------------------------
    // Pure helpers (unit-tested)
    // ------------------------------------------------------------------

    /** Clamps a requested seek length into the supported range. */
    internal fun resolveSeekSeconds(requested: Int?): Int =
        (requested ?: DEFAULT_SEEK_SECONDS).coerceIn(MIN_SEEK_SECONDS, MAX_SEEK_SECONDS)

    /**
     * Number of fast-forward/rewind key presses needed for a requested
     * duration, assuming media apps seek [SEEK_STEP_SECONDS] per press.
     */
    internal fun seekPresses(seconds: Int): Int =
        ((seconds + SEEK_STEP_SECONDS - 1) / SEEK_STEP_SECONDS).coerceAtLeast(1)

    /** Maps a 0-100 percentage onto a stream index in 0..[maxIndex]. */
    internal fun volumeIndexForPercent(percent: Int, maxIndex: Int): Int =
        (percent.coerceIn(0, 100) * maxIndex / 100.0).roundToInt()

    /** Maps a stream index in 0..[maxIndex] back to a 0-100 percentage. */
    internal fun volumePercentForIndex(index: Int, maxIndex: Int): Int =
        if (maxIndex <= 0) 0 else (index * 100.0 / maxIndex).roundToInt()

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun audioManager(context: Context): AudioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun dispatchMediaKey(audio: AudioManager, keyCode: Int) {
        // Every media key must be delivered as a paired DOWN/UP, like a
        // real button press.
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun playbackMessage(action: String): String = when (action) {
        "play" -> "Playback started."
        "pause" -> "Playback paused."
        "toggle_play_pause" -> "Toggled play/pause."
        "stop" -> "Playback stopped."
        "next_track" -> "Skipped to the next track."
        "previous_track" -> "Went back to the previous track."
        else -> "Playback action '$action' performed."
    }

    /** Builds a volume-action response including the fresh volume snapshot. */
    private fun volumeResult(audio: AudioManager, action: String, message: String): JsonObject {
        val maxIndex = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentIndex = audio.getStreamVolume(AudioManager.STREAM_MUSIC)

        return buildJsonObject {
            put("success", true)
            put("action", action)
            put("message", message)
            put("volume_current", currentIndex)
            put("volume_max", maxIndex)
            put("volume_percent", volumePercentForIndex(currentIndex, maxIndex))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                put("volume_muted", audio.isStreamMute(AudioManager.STREAM_MUSIC))
            }
        }
    }

    private fun error(message: String): JsonObject = buildJsonObject {
        put("success", false)
        put("error", message)
    }

    private fun extractIntArg(call: FunctionCall, key: String): Int? =
        runCatching {
            val element = call.args[key] ?: return null
            element.jsonPrimitive.intOrNull
                ?: element.jsonPrimitive.content.trim().toIntOrNull()
        }.getOrNull()
}
