
package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Function declarations for AI screen-access tools.
 * Screen interaction is powered by Android AccessibilityService.
 */

fun captureScreenTool() = ToolDefinition(
    name = "capture_screen",
    description = universalToolDescription(
        purpose = "Captures the current screen and returns the image plus its visible text for visual layout or coordinate work.",
        trigger = "The user asks about visible UI/layout or a task requires screen coordinates.",
        notFor = "Use `read_screen_text` to find visible text/elements and `perform_screen_action` to interact with the screen.",
        prerequisite = "The user must enable the Accessibility Service; use coordinates only from the latest capture and inspect task-relevant content only.",
    ),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
        putJsonArray("required") {}
    }
)

fun readScreenTextTool() = ToolDefinition(
    name = "read_screen_text",
    description = universalToolDescription(
        purpose = "Reads visible text and accessibility labels on the current screen.",
        trigger = "The user asks about on-screen text, a button, or an element's label/state.",
        notFor = "Use `capture_screen` for visual layout/coordinates and `perform_screen_action` to interact with an element.",
        prerequisite = "The user must enable the Accessibility Service; read only information relevant to the request.",
    ),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
        putJsonArray("required") {}
    }
)

fun performScreenActionTool() = ToolDefinition(
    name = "perform_screen_action",
    description = universalToolDescription(
        purpose = "Performs a specified UI action on the current screen using accessibility targets, gestures, or text entry.",
        trigger = "The user explicitly asks for a specific action on the current screen.",
        notFor = "Never guess coordinates/actions without inspecting the screen. Typing does not submit/send; consequential actions require authorization.",
        prerequisite = "The user must enable the Accessibility Service. Inspect with `read_screen_text` or `capture_screen` first; use a recent capture for coordinates and provide the action's required fields.",
    ),
    parameters = buildJsonObject {
        put("type", "object")

        putJsonObject("properties") {

            putJsonObject("action") {
                put("type", "string")
                put(
                    "description",
                    "Action to perform. Coordinate actions: tap, double_tap, " +
                            "long_press. Semantic actions: click_element, " +
                            "scroll_element. Gesture actions: swipe, scroll_up, " +
                            "scroll_down. Navigation actions: back, home, recents, " +
                            "notifications. Text entry: type_text."
                )

                putJsonArray("enum") {
                    add(JsonPrimitive("tap"))
                    add(JsonPrimitive("double_tap"))
                    add(JsonPrimitive("long_press"))
                    add(JsonPrimitive("click_element"))
                    add(JsonPrimitive("swipe"))
                    add(JsonPrimitive("scroll_element"))
                    add(JsonPrimitive("scroll_up"))
                    add(JsonPrimitive("scroll_down"))
                    add(JsonPrimitive("back"))
                    add(JsonPrimitive("home"))
                    add(JsonPrimitive("recents"))
                    add(JsonPrimitive("notifications"))
                    add(JsonPrimitive("type_text"))
                }
            }

            putJsonObject("x") {
                put("type", "integer")
                put("minimum", 0)
                put(
                    "description",
                    "X coordinate in pixels, measured in the most recent " +
                            "capture's image; it is rescaled to the real screen. " +
                            "Required for tap, double_tap, long_press, and as " +
                            "the swipe starting X."
                )
            }

            putJsonObject("y") {
                put("type", "integer")
                put("minimum", 0)
                put(
                    "description",
                    "Y coordinate in pixels, measured in the most recent " +
                            "capture's image; it is rescaled to the real screen. " +
                            "Required for tap, double_tap, long_press, and as " +
                            "the swipe starting Y."
                )
            }

            putJsonObject("x2") {
                put("type", "integer")
                put("minimum", 0)
                put(
                    "description",
                    "Swipe ending X coordinate in screen pixels. Required for swipe."
                )
            }

            putJsonObject("y2") {
                put("type", "integer")
                put("minimum", 0)
                put(
                    "description",
                    "Swipe ending Y coordinate in screen pixels. Required for swipe."
                )
            }

            putJsonObject("duration_ms") {
                put("type", "integer")
                put(
                    "description",
                    "Swipe gesture duration in milliseconds (50-5000 ms; " +
                            "defaults depend on action). Not used by long_press."
                )
                put("minimum", 50)
                put("maximum", 5000)
            }

            putJsonObject("text") {
                put("type", "string")
                put(
                    "description",
                    "Text to enter using type_text. Required for type_text. " +
                            "The text is entered into the focused or identified " +
                            "input field. It does not automatically send or submit."
                )
            }

            putJsonObject("target") {
                put("type", "string")
                put(
                    "description",
                    "Visible element text or accessibility ID. Required for " +
                            "click_element. For type_text, optionally identifies " +
                            "the input field to focus before entering text. " +
                            "For scroll_element, optionally identifies the " +
                            "scrollable container."
                )
            }

            putJsonObject("direction") {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("forward"))
                    add(JsonPrimitive("backward"))
                }
                put(
                    "description",
                    "Required for scroll_element. forward moves to later " +
                            "content; backward moves to earlier content."
                )
            }
        }

        // Only action is universally required.
        // The executor must validate action-specific arguments.
        putJsonArray("required") {
            add(JsonPrimitive("action"))
        }
    }
)
