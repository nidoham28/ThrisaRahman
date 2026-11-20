
package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Function declarations for phone alerts, media playback,
 * media volume control, and media status.
 */

// ---------------------------------------------------------
// PHONE ALERT
// ---------------------------------------------------------

fun phoneAlertControlTool() = ToolDefinition(
    name = "phone_alert_control",
    description = """
        Control a temporary audible alert on this phone.

        Use when the user explicitly asks to find their phone,
        make this phone ring or beep, or stop an active phone alert.

        Actions:
        - start: Play a repeating alert.
        - stop: Stop the currently playing alert.

        The alert automatically stops after its configured duration,
        with a maximum duration of 60 seconds.

        The alert follows the implementation's configured alarm-volume
        and Do Not Disturb behavior. Never claim the alert started or
        stopped unless the executor confirms the result.

        Do not use this tool to change the ringtone, alarm volume,
        media volume, or notification settings.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("description", "Start or stop the phone alert.")
                putJsonArray("enum") {
                    add(JsonPrimitive("start"))
                    add(JsonPrimitive("stop"))
                }
            }

            putJsonObject("sound") {
                put("type", "string")
                put(
                    "description",
                    "Sound pattern for start. Defaults to beep."
                )
                putJsonArray("enum") {
                    add(JsonPrimitive("beep"))
                    add(JsonPrimitive("ring"))
                    add(JsonPrimitive("urgent"))
                }
            }

            putJsonObject("duration_seconds") {
                put("type", "integer")
                put(
                    "description",
                    "Alert duration from 5 to 60 seconds. Defaults to 30. Ignored for stop."
                )
            }
        }

        putJsonArray("required") {
            add(JsonPrimitive("action"))
        }
    }
)

// ---------------------------------------------------------
// MEDIA PLAYBACK
// ---------------------------------------------------------

fun mediaPlaybackControlTool() = ToolDefinition(
    name = "media_playback_control",
    description = """
        Control playback through the currently active Android media session.

        Use for music, podcasts, audiobooks, and other media playback.

        Supported actions:
        - play: Start or resume playback.
        - pause: Pause playback.
        - toggle_play_pause: Toggle between playing and paused.
        - stop: Stop playback.
        - next_track: Skip to the next track.
        - previous_track: Return to the previous track.
        - fast_forward: Seek forward within the current media.
        - rewind: Seek backward within the current media.

        Use seconds only for fast_forward and rewind.
        The requested seek duration is approximate and depends
        on the active media app's supported seek behavior.

        This tool controls the active media session, not a specific
        application. It may fail if no compatible media session exists.

        Do not claim an action succeeded unless execution confirms it.
        Use get_media_status when the actual playback state needs verification.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put(
                    "description",
                    "Playback command to execute."
                )
                putJsonArray("enum") {
                    add(JsonPrimitive("play"))
                    add(JsonPrimitive("pause"))
                    add(JsonPrimitive("toggle_play_pause"))
                    add(JsonPrimitive("stop"))
                    add(JsonPrimitive("next_track"))
                    add(JsonPrimitive("previous_track"))
                    add(JsonPrimitive("fast_forward"))
                    add(JsonPrimitive("rewind"))
                }
            }

            putJsonObject("seconds") {
                put("type", "integer")
                put(
                    "description",
                    "Approximate seek duration in seconds for fast_forward or rewind. Defaults to 10. Ignored for other actions."
                )
            }
        }

        putJsonArray("required") {
            add(JsonPrimitive("action"))
        }
    }
)

// ---------------------------------------------------------
// MEDIA VOLUME
// ---------------------------------------------------------

fun mediaVolumeControlTool() = ToolDefinition(
    name = "media_volume_control",
    description = """
        Read or change the Android media volume (STREAM_MUSIC).

        Use when the user asks to:
        - Increase or decrease media volume.
        - Set media volume to a percentage.
        - Mute or unmute media audio.
        - Check the current media volume.

        Supported actions:
        - raise: Increase volume.
        - lower: Decrease volume.
        - set_level: Set an absolute percentage from 0 to 100.
        - mute: Set media volume to zero.
        - unmute: Restore media audio to a non-zero level.
        - get: Read the current media volume without changing it.

        Use level_percent only for set_level.
        Use steps only for raise and lower. Both default to 1.

        This controls media volume only. It does not change
        ringtone, notification, or alarm volume.

        Report the actual resulting volume, not merely the requested value.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("description", "Media volume operation.")
                putJsonArray("enum") {
                    add(JsonPrimitive("raise"))
                    add(JsonPrimitive("lower"))
                    add(JsonPrimitive("set_level"))
                    add(JsonPrimitive("mute"))
                    add(JsonPrimitive("unmute"))
                    add(JsonPrimitive("get"))
                }
            }

            putJsonObject("level_percent") {
                put("type", "integer")
                put(
                    "description",
                    "Target media volume percentage from 0 to 100. Required for set_level."
                )
            }

            putJsonObject("steps") {
                put("type", "integer")
                put(
                    "description",
                    "Number of volume steps for raise or lower. Defaults to 1."
                )
            }
        }

        putJsonArray("required") {
            add(JsonPrimitive("action"))
        }
    }
)

// ---------------------------------------------------------
// MEDIA STATUS
// ---------------------------------------------------------

fun getMediaStatusTool() = ToolDefinition(
    name = "get_media_status",
    description = """
        Retrieve the current media and audio status of this Android device.

        Use when the user asks:
        - Whether media is currently playing.
        - What the current media volume is.
        - What the current ringer mode is.
        - Whether a playback or volume operation took effect.

        Return only information available from the device.
        Distinguish active playback from paused, stopped,
        and unknown states when supported by the executor.

        This tool does not identify the current song, artist,
        media application, or audio source unless the executor
        explicitly provides that information.

        This tool only reads status. It does not change device settings.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
        putJsonArray("required") {}
    }
)
