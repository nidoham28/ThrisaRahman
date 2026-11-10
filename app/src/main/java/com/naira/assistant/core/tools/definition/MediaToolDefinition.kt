package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Function declarations for media playback and volume-control tools. */

fun mediaPlaybackControlTool() = ToolDefinition(
    name = "media_playback_control",
    description = "Control playback in the currently active media app (music, podcasts, audiobooks, video). " +
            "Use for play, pause, resume, stop, next/previous track, or skipping forward and backward " +
            "within the current track. Works on whatever app owns the active media session, " +
            "like pressing the buttons on wired or Bluetooth headset.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("description", "The playback action to perform. " +
                        "'play' starts or resumes playback, 'pause' pauses it, " +
                        "'toggle_play_pause' flips the current state, 'stop' stops it, " +
                        "'next_track'/'previous_track' change tracks, " +
                        "'fast_forward'/'rewind' skip inside the current track.")
                putJsonArray("enum") {
                    add("play")
                    add("pause")
                    add("toggle_play_pause")
                    add("stop")
                    add("next_track")
                    add("previous_track")
                    add("fast_forward")
                    add("rewind")
                }
            }
            putJsonObject("seconds") {
                put("type", "integer")
                put(
                    "description",
                    "Approximate number of seconds to skip for 'fast_forward' or 'rewind', " +
                            "e.g. 30 for 'skip ahead 30 seconds'. Skips are sent in ~10-second " +
                            "steps, so the result is approximate and depends on the media app. " +
                            "Ignored for all other actions. Defaults to 10."
                )
            }
        }
        putJsonArray("required") {
            add("action")
        }
    },
)

fun mediaVolumeControlTool() = ToolDefinition(
    name = "media_volume_control",
    description = "Control the media volume (STREAM_MUSIC) level. " +
            "Use when the user asks to turn the sound up or down, set the volume to a percentage, " +
            "mute or unmute media, or asks what the current media volume is. " +
            "This is not for ringer or alarm volume.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("description", "The volume action to perform. " +
                        "'raise'/'lower' move the volume by one or more steps, " +
                        "'set_level' jumps to an absolute percentage, " +
                        "'mute'/'unmute' silence or restore the media stream, " +
                        "'get' reports the current volume without changing it.")
                putJsonArray("enum") {
                    add("raise")
                    add("lower")
                    add("set_level")
                    add("mute")
                    add("unmute")
                    add("get")
                }
            }
            putJsonObject("level_percent") {
                put("type", "integer")
                put(
                    "description",
                    "Absolute volume level from 0 to 100. Required for 'set_level', " +
                            "ignored for all other actions."
                )
            }
            putJsonObject("steps") {
                put("type", "integer")
                put(
                    "description",
                    "How many volume steps to move for 'raise'/'lower'. " +
                            "Defaults to 1; only useful for larger changes like 'turn it down a lot'."
                )
            }
        }
        putJsonArray("required") {
            add("action")
        }
    },
)

fun getMediaStatusTool() = ToolDefinition(
    name = "get_media_status",
    description = "Check the current media state: whether audio is actively playing, " +
            "the media volume level, and the ringer mode. " +
            "Use before or after controlling playback to confirm the state, " +
            "or when the user asks if something is playing. " +
            "It cannot report the song or app name.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
        putJsonArray("required") {}
    },
)
