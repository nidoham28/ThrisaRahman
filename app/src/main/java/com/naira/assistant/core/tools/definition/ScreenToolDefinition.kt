
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
        Capture the device's current screen and return a screenshot for visual
        inspection. Use this when visual layout, icons, coordinates, or other
        visual details are needed.

        Capture a fresh screenshot after opening an app or performing an action
        when the next step depends on the resulting screen. Use coordinates
        from the most recent screenshot only.

        Requires the user to enable Naira's screen-access accessibility service
        in Android Settings. If access is disabled or unavailable, explain that
        the user must enable it manually.

        The screenshot may contain private information. Inspect only information
        relevant to the user's requested task.
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
        Read text and accessibility information from the device's current screen
        without capturing a screenshot.

        Returns visible text, labels, button descriptions, input-field information,
        and accessibility IDs when available.

        Prefer this tool when you only need to identify or read text. Use
        capture_screen when visual layout or exact screen coordinates are needed.

        Requires Naira's screen-access accessibility service to be enabled
        in Android Settings. Do not claim screen contents were read if access
        is unavailable.
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
        Perform an interaction on the device's current screen using its
        accessibility service.

        Supported actions:
        - tap, double_tap, long_press: touch absolute screen coordinates.
        - click_element: click a visible element by its text or accessibility ID.
        - swipe: drag from (x, y) to (x2, y2).
        - scroll_element: scroll a specified accessibility container.
        - scroll_up, scroll_down: scroll the content near the screen center.
        - back, home, recents, notifications: perform system navigation actions.
        - type_text: enter text into the currently focused or identified input field.

        IMPORTANT:
        1. Inspect the current screen using read_screen_text or capture_screen
           before interacting with it.
        2. Prefer click_element and scroll_element when reliable accessibility
           information is available.
        3. Use coordinates only from a recent screenshot of the current screen.
        4. Supply all parameters required by the selected action.
        5. After an action, inspect the screen again when verification is needed.
        6. Never report success unless the action executor confirms that the
           requested operation was performed.
        7. Typing text does not send a message, submit a form, or confirm a purchase.
           Sending or submitting requires a separate explicit action.
        8. Before sending a message, verify the recipient and message content.
           Only send when the user has requested that it be sent.
        9. Do not perform purchases, financial transactions, destructive actions,
           or other consequential operations without the required user authorization.

        Requires the user to enable Naira's screen-access accessibility service
        manually in Android Settings. Android does not allow the app to grant
        this special access automatically.
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
                            "long_press when supported. Defaults to 300 ms for swipe."
                )
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
