
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
    description = """
        Capture the current screen only when visual layout or coordinates are needed. Refresh
        after actions when the next step depends on the result; use coordinates only from the latest
        capture. Requires the user-enabled accessibility service; inspect only task-relevant content.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
        putJsonArray("required") {}
    }
)

fun readScreenTextTool() = ToolDefinition(
    name = "read_screen_text",
    description = """
        Read visible text and accessibility labels without a screenshot. Prefer for text/element
        lookup; use capture_screen for visual layout or coordinates. Requires user-enabled
        accessibility access.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
        putJsonArray("required") {}
    }
)

fun performScreenActionTool() = ToolDefinition(
    name = "perform_screen_action",
    description = """
        Interact with the current screen only after inspecting it. Prefer accessibility targets;
        use coordinates from a recent screenshot. Supply fields for the chosen action and verify
        afterward only when needed. Typing does not submit or send; sending requires an explicit
        request and verified recipient/content. Consequential actions require authorization.
        Accessibility access must be enabled manually by the user.
    """.trimIndent(),
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
                    "X coordinate in screen pixels. Required for tap, " +
                            "double_tap, long_press, and as the swipe starting X."
                )
            }

            putJsonObject("y") {
                put("type", "integer")
                put("minimum", 0)
                put(
                    "description",
                    "Y coordinate in screen pixels. Required for tap, " +
                            "double_tap, long_press, and as the swipe starting Y."
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
                put("minimum", 1)
                put(
                    "description",
                    "Gesture duration in milliseconds. Used for swipe and " +
                            "long_press when supported (50-5000 ms; defaults depend on action)."
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
                    "Scroll direction for scroll_element. " +
                            "forward moves to later content; backward moves to earlier content."
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
