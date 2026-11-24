
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
    description = universalToolDescription(
        purpose = "Starts or stops this phone's temporary audible alert, which runs for at most 60 seconds.",
        trigger = "The user explicitly asks to start or stop the phone alert.",
        notFor = "Use `media_playback_control` for playback and `media_volume_control` for media volume.",
        prerequisite = "`action` must be `start` or `stop`; sound/duration are optional for start. Behavior depends on alarm-volume and Do Not Disturb settings.",
    ),
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
                put("default", "beep")
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
                put("minimum", 5)
                put("maximum", 60)
                put("default", 30)
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
    description = universalToolDescription(
        purpose = "Controls play/pause/stop, track changes, or approximate seeking in the active media session.",
        trigger = "The user asks to control currently active media playback.",
        notFor = "Use `open_app` to launch a specific app, `media_volume_control` for volume, or `get_media_status` to inspect state.",
        prerequisite = "`action` is required; provide `seconds` for seek actions. Report when no active media session is available.",
    ),
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
                    "Seek seconds for fast_forward/rewind only (5-120; default 10)."
                )
                put("minimum", 5)
                put("maximum", 120)
                put("default", 10)
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
    description = universalToolDescription(
        purpose = "Reads or changes media (`STREAM_MUSIC`) volume and returns the resulting level.",
        trigger = "The user asks to get, raise, lower, mute/unmute, or set media volume.",
        notFor = "Does not control ringtone, notification, or alarm volume; use `phone_alert_control` for the phone alert.",
        prerequisite = "`action` is required; `set_level` needs `level_percent` (0-100), and raise/lower may specify `steps`.",
    ),
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
                put("minimum", 0)
                put("maximum", 100)
            }

            putJsonObject("steps") {
                put("type", "integer")
                put(
                    "description",
                    "Steps for raise/lower only (1-15; default 1)."
                )
                put("minimum", 1)
                put("maximum", 15)
                put("default", 1)
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
    description = universalToolDescription(
        purpose = "Reads current playback/audio status without changing settings.",
        trigger = "The user asks whether media is playing or requests current audio status.",
        notFor = "Use `media_playback_control` to change playback and `media_volume_control` to change volume.",
        prerequisite = "No arguments are required; do not infer media details the device does not return.",
    ),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
        putJsonArray("required") {}
    }
)
