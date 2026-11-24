
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
        Start or stop this phone's temporary audible alert only on explicit request. It uses
        configured alarm-volume/Do Not Disturb behavior and stops within 60 seconds; do not use
        it to change other audio settings.
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
    description = """
        Control the active media session, not a selected app; it may be unavailable.
        Seek duration is approximate. Use get_media_status only when actual state needs checking.
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
    description = """
        Read or change media (STREAM_MUSIC) volume only, not ringtone, notification, or alarm
        volume. Report the resulting level, not just the requested operation.
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
    description = """
        Read current playback/audio status without changing settings. It cannot identify media
        details unless the device returns them; distinguish unavailable or unknown values.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
        putJsonArray("required") {}
    }
)
