package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Function declarations for the AI screen-access tools (accessibility powered). */

fun captureScreenTool() = ToolDefinition(
    name = "capture_screen",
    description = "Take a screenshot of the device's current screen and look at it. " +
            "Use this whenever executing a task on the device requires knowing what is on screen: " +
            "call it right after opening an app, before tapping anything (to find the exact pixel " +
            "coordinates of buttons and fields), and after each action to verify the result. " +
            "Requires the user to have enabled 'Screen access' for Thrisa in the accessibility " +
            "settings; if the tool reports it is disabled, ask the user to enable it in " +
            "Thrisa Settings → Permissions. On older Android versions the tool returns the " +
            "screen's text content instead of an image.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
        putJsonArray("required") {}
    },
)

fun readScreenTextTool() = ToolDefinition(
    name = "read_screen_text",
    description = "Read the text content of the device's current screen (labels, button titles, " +
            "input fields and view ids) without taking a screenshot. Cheaper and more precise " +
            "than capture_screen when you only need to know what text is visible — for example " +
            "to find the exact wording of a button before tapping it, or to read a message. " +
            "Requires the user to have enabled 'Screen access' for Thrisa in the accessibility settings.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
        putJsonArray("required") {}
    },
)

fun performScreenActionTool() = ToolDefinition(
    name = "perform_screen_action",
    description = "Perform a touch or navigation action on the device's current screen: tap coordinates, " +
            "click a visible element by its label, swipe or scroll a container, type into an input field, " +
            "or press back/home/recents. " +
            "Use it to carry out on-device tasks the user asks for; do not ask the user to perform " +
            "the taps, typing, or scrolling themselves. IMPORTANT: inspect the current screen first " +
            "with read_screen_text or capture_screen. Prefer click_element and scroll_element " +
            "with labels from read_screen_text when available; " +
            "use coordinates from a fresh screenshot only when semantic controls are unavailable. " +
            "type_text can target a field by its visible label or ID. " +
            "To send a chat message in any app, follow the full sequence: capture_screen → " +
            "tap the app's message field → type_text → capture_screen to verify the text landed → " +
            "tap the send button when sending was requested. After any action, call capture_screen " +
            "again to verify the result. Requires the user to enable 'Screen access' for Thrisa once " +
            "in Android accessibility settings; this system permission cannot be granted automatically.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("description", "The action to perform. " +
                        "'tap', 'double_tap' and 'long_press' touch absolute screen coordinates, " +
                        "'click_element' clicks a visible element matching target text or ID, " +
                        "'swipe' drags from one point to another, " +
                        "'scroll_element' scrolls a target container using its accessibility action, " +
                        "'scroll_up'/'scroll_down' scroll the content under the screen center, " +
                        "'back'/'home'/'recents'/'notifications' are the system navigation actions, " +
                        "'type_text' writes text into the currently focused input field.")
                putJsonArray("enum") {
                    add("tap")
                    add("double_tap")
                    add("long_press")
                    add("click_element")
                    add("swipe")
                    add("scroll_element")
                    add("scroll_up")
                    add("scroll_down")
                    add("back")
                    add("home")
                    add("recents")
                    add("notifications")
                    add("type_text")
                }
            }
            putJsonObject("x") {
                put("type", "integer")
                put("description", "X coordinate in pixels for 'tap', 'double_tap', 'long_press' and the swipe start point. Get exact values from the latest capture_screen screenshot.")
            }
            putJsonObject("y") {
                put("type", "integer")
                put("description", "Y coordinate in pixels for 'tap', 'double_tap', 'long_press' and the swipe start point. Get exact values from the latest capture_screen screenshot.")
            }
            putJsonObject("x2") {
                put("type", "integer")
                put("description", "End X coordinate in pixels for 'swipe'.")
            }
            putJsonObject("y2") {
                put("type", "integer")
                put("description", "End Y coordinate in pixels for 'swipe'.")
            }
            putJsonObject("duration_ms") {
                put("type", "integer")
                put("description", "How long the gesture takes in milliseconds. Defaults to 300 for 'swipe'; use ~600+ for slow drags. Ignored for taps.")
            }
            putJsonObject("text") {
                put("type", "string")
                put("description", "Text to enter for 'type_text'. Replaces the field's current content; " +
                        "nothing is sent automatically — afterwards capture the screen and tap the " +
                        "send/submit button if the task requires sending.")
            }
            putJsonObject("target") {
                put("type", "string")
                put("description", "Visible text or accessibility ID of the element to click or the input field to type into. For scroll_element, optionally identify the scroll container.")
            }
            putJsonObject("direction") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add("forward")
                    add("backward")
                })
                put("description", "For scroll_element, 'forward' moves to later content and 'backward' moves to earlier content.")
            }
        }
        putJsonArray("required") {
            add("action")
        }
    },
)
