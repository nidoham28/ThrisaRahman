package com.naira.assistant.core.tools.executor

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import com.naira.assistant.core.service.screen.ThrisaAccessibilityService
import com.naira.assistant.core.tools.executor.AppToolExecutor.executeOpenApp
import com.naira.assistant.core.tools.executor.BuiltinToolExecutor.jsonStringArg
import com.naira.assistant.domain.FunctionCall
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Executes the AI screen-access tools on top of [ThrisaAccessibilityService]:
 * capture_screen (screenshot for the model + the chat bubble), read_screen_text
 * (accessibility tree) and perform_screen_action (gestures, navigation, typing).
 *
 * Every tool degrades gracefully: when the service is off or the platform lacks
 * a capability, the model receives a structured error that tells it (and the
 * user) what to enable instead of failing the chat.
 */
object ScreenToolExecutor {

    /**
     * Screenshot bytes from the most recent capture_screen call. The ViewModel
     * consumes them once per tool round and ships them to the model as an
     * inline image turn right after the function responses.
     */
    private val lastCapture = AtomicReference<ByteArray?>(null)
    private val lastCaptureDimensions = AtomicReference<Pair<Int, Int>?>(null)

    fun consumeLastCapture(): ByteArray? = lastCapture.getAndSet(null)

    /**
     * Executes the `capture_screen` tool: screenshots the active display
     * (API 30+) and falls back to the accessibility tree text when a bitmap
     * is unavailable (older Android, rate limit, secure window).
     */
    suspend fun executeCaptureScreen(call: FunctionCall, context: Context): JsonObject {
        val service = ThrisaAccessibilityService.instance
            ?: return screenAccessDisabled()

        // Let foreground transitions and animations settle so the capture
        // reflects the screen after the previous action, not mid-animation.
        // A freshly launched app especially needs this beat to draw its UI.
        delay(600)
        lastCapture.set(null)
        lastCaptureDimensions.set(null)

        val screenText = try {
            service.readScreenText()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val screenshot = try {
            service.takeScreenshot()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

        if (screenshot == null) {
            return if (!screenText.isNullOrBlank()) buildJsonObject {
                put("success", true)
                put("action", "capture_screen")
                put("mode", "text_only")
                put("note", "A screenshot is unavailable right now (Android version or system rate limit). Only the screen text is provided; base coordinate decisions on it cautiously.")
                put("screen_text", screenText)
            } else {
                error("Could not capture the screen right now - the system rate-limits screenshots to about one per second. Wait a moment and try again, or use read_screen_text.")
            }
        }

        val imageUri = saveForDisplay(context, screenshot)
        lastCapture.set(screenshot)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(screenshot, 0, screenshot.size, options)
        if (options.outWidth > 0 && options.outHeight > 0) {
            lastCaptureDimensions.set(options.outWidth to options.outHeight)
        }

        return buildJsonObject {
            put("success", true)
            put("action", "capture_screen")
            if (imageUri != null) put("image_uri", imageUri)
            put("note", "The screenshot is attached right after this result. Read it to decide the next action and exact tap coordinates.")
            if (!screenText.isNullOrBlank()) put("screen_text", screenText.take(1500))
        }
    }

    /**
     * Executes the `read_screen_text` tool: dumps the visible text of the
     * active window from the accessibility tree.
     */
    suspend fun executeReadScreenText(call: FunctionCall, context: Context): JsonObject {
        val service = ThrisaAccessibilityService.instance
            ?: return screenAccessDisabled()

        val text = runCatching { service.readScreenText() }.getOrNull()
        return if (text.isNullOrBlank()) {
            error("Could not read the screen content - the current window exposes no readable text (for example the launcher or a video player). Use capture_screen instead.")
        } else {
            buildJsonObject {
                put("success", true)
                put("action", "read_screen_text")
                put("screen_text", text)
            }
        }
    }

    /**
     * Executes the `perform_screen_action` tool: taps, swipes, scrolls,
     * system navigation and text entry via the accessibility service.
     */
    suspend fun executePerformScreenAction(call: FunctionCall, context: Context): JsonObject {
        val service = ThrisaAccessibilityService.instance
            ?: return screenAccessDisabled()

        val action = jsonStringArg(call, "action")?.trim()?.lowercase()
        return when (action) {
            "tap" -> withPoint(call, service) { x, y ->
                service.tap(x, y).toResult("tap", "Tapped the screen at (${x.toInt()}, ${y.toInt()}).")
            }
            "double_tap" -> withPoint(call, service) { x, y ->
                service.doubleTap(x, y).toResult("double_tap", "Double-tapped the screen at (${x.toInt()}, ${y.toInt()}).")
            }
            "long_press" -> withPoint(call, service) { x, y ->
                service.longPress(x, y).toResult("long_press", "Long-pressed the screen at (${x.toInt()}, ${y.toInt()}).")
            }
            "click_element" -> {
                val target = jsonStringArg(call, "target")
                    ?: return error("Provide the visible text or accessibility ID of the element to click.")
                service.clickElement(target).toResult("click_element", "Clicked '$target'.")
            }
            "swipe" -> withSwipe(call, service) { x1, y1, x2, y2, durationMs ->
                service.swipe(x1, y1, x2, y2, durationMs)
                    .toResult("swipe", "Swiped from (${x1.toInt()}, ${y1.toInt()}) to (${x2.toInt()}, ${y2.toInt()}).")
            }
            "scroll_element" -> scrollElement(service, call)
            "scroll_up", "scroll_down" -> scroll(service, action)
            "back" -> service.performGlobal(AccessibilityService.GLOBAL_ACTION_BACK)
                .toResult("back", "Pressed Back.")
            "home" -> service.performGlobal(AccessibilityService.GLOBAL_ACTION_HOME)
                .toResult("home", "Went to the home screen.")
            "recents" -> service.performGlobal(AccessibilityService.GLOBAL_ACTION_RECENTS)
                .toResult("recents", "Opened the recent apps overview.")
            "notifications" -> service.performGlobal(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                .toResult("notifications", "Opened the notification shade.")
            "type_text" -> executeTypeText(service, call)
            else -> error("Unknown screen action '${action ?: "<missing>"}'. Use one of: tap, double_tap, long_press, swipe, scroll_up, scroll_down, back, home, recents, notifications, type_text.")
        }
    }

    suspend fun executeComputerUseAction(call: FunctionCall, context: Context): JsonObject {
        val service = ThrisaAccessibilityService.instance
            ?: return screenAccessDisabled()

        val actionResult = when (call.name.trim().lowercase()) {
            "open_app" -> executeOpenApp(call, context)
            "click" -> withNormalizedPoint(call, service) { x, y ->
                service.tap(x, y).toResult("click", "Tapped the requested screen position.")
            }
            "double_click" -> withNormalizedPoint(call, service) { x, y ->
                service.doubleTap(x, y).toResult("double_click", "Double-tapped the requested screen position.")
            }
            "triple_click" -> withNormalizedPoint(call, service) { x, y ->
                repeat(3) { index ->
                    if (index > 0) delay(100)
                    if (!service.tap(x, y)) return@withNormalizedPoint error("The triple-click gesture was cancelled.")
                }
                buildJsonObject {
                    put("success", true)
                    put("action", "triple_click")
                }
            }
            "long_press", "right_click" -> withNormalizedPoint(call, service) { x, y ->
                service.longPress(x, y).toResult(call.name, "Long-pressed the requested screen position.")
            }
            "scroll" -> executeComputerUseScroll(call, service)
            "swipe", "drag" -> executeComputerUseSwipe(call, service)
            "type" -> executeComputerUseType(call, service)
            "keypress", "press_key" -> executeComputerUseKeypress(call, service)
            "navigate" -> executeComputerUseNavigate(call, context)
            "go_back" -> service.performGlobal(AccessibilityService.GLOBAL_ACTION_BACK)
                .toResult("go_back", "Went back.")
            "wait" -> {
                val seconds = (extractNumberArg(call, "seconds") ?: 1.0).coerceIn(0.1, 5.0)
                delay((seconds * 1000).toLong())
                buildJsonObject {
                    put("success", true)
                    put("action", "wait")
                    put("seconds", seconds)
                }
            }
            "go_forward" -> error("Forward navigation is not supported by Android accessibility.")
            else -> error("Unsupported Gemini Computer Use action '${call.name}'.")
        }

        val captureResponse = executeCaptureScreen(
            FunctionCall(name = "capture_screen"),
            context,
        )
        return buildJsonObject {
            actionResult.forEach { (key, value) -> put(key, value) }
            captureResponse["image_uri"]?.let { put("image_uri", it) }
            captureResponse["screen_text"]?.let { put("screen_text", it) }
            captureResponse["error"]?.let { put("screen_capture_error", it) }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private suspend fun executeTypeText(service: ThrisaAccessibilityService, call: FunctionCall): JsonObject {
        val text = jsonStringArg(call, "text")
        if (text.isNullOrBlank()) {
            return error("Please provide the 'text' to type.")
        }
        val target = jsonStringArg(call, "target")
        return if (service.typeText(text, target)) {
            buildJsonObject {
                put("success", true)
                put("action", "type_text")
                put("message", "Entered the text into ${target?.let { "'$it'" } ?: "the app's focused input field"}. It has NOT been sent or submitted - " +
                        "capture the screen and tap the send/submit button if the task requires that.")
            }
        } else {
            error(
                if (target == null) {
                    "No text input on screen could receive the text. Inspect the screen, select the input, then try again."
                } else {
                    "Could not find an editable text field matching '$target'. Read the current screen and choose its exact label or accessibility ID."
                }
            )
        }
    }

    private suspend fun executeComputerUseType(
        call: FunctionCall,
        service: ThrisaAccessibilityService,
    ): JsonObject {
        val text = jsonStringArg(call, "text")
            ?: return error("The Computer Use type action did not include text.")
        val x = extractNumberArg(call, "x")
        val y = extractNumberArg(call, "y")
        if (x != null && y != null) {
            val point = normalizedPoint(x, y, service)
                ?: return error("The Computer Use type action included off-screen coordinates.")
            if (!service.tap(point.first, point.second)) {
                return error("Could not focus the text field; the tap gesture was cancelled.")
            }
            delay(100)
        }
        if (!service.typeText(text)) {
            return error("No editable text field could receive the requested text.")
        }
        if (call.args["press_enter"]?.jsonPrimitive?.booleanOrNull == true && !service.pressImeEnter()) {
            return error("The text was entered, but this field does not expose an Enter/submit accessibility action.")
        }
        return buildJsonObject {
            put("success", true)
            put("action", "type")
            put("message", "Entered the requested text.")
        }
    }

    private suspend fun executeComputerUseKeypress(
        call: FunctionCall,
        service: ThrisaAccessibilityService,
    ): JsonObject {
        val key = listOf("key", "key_name", "keycode")
            .firstNotNullOfOrNull { jsonStringArg(call, it) }
            ?.trim()
            ?.uppercase()
            ?: return error("The Computer Use keypress action did not include a key.")
        return when (key) {
            "ENTER", "RETURN" -> service.pressImeEnter()
                .toResult("keypress", "Pressed Enter.")
            "BACK", "ESC", "ESCAPE" -> service.performGlobal(AccessibilityService.GLOBAL_ACTION_BACK)
                .toResult("keypress", "Pressed Back.")
            "HOME" -> service.performGlobal(AccessibilityService.GLOBAL_ACTION_HOME)
                .toResult("keypress", "Went to the home screen.")
            "RECENTS" -> service.performGlobal(AccessibilityService.GLOBAL_ACTION_RECENTS)
                .toResult("keypress", "Opened recent apps.")
            else -> error("Android accessibility cannot synthesize the '$key' key.")
        }
    }

    private fun executeComputerUseNavigate(call: FunctionCall, context: Context): JsonObject {
        val target = listOf("url", "uri")
            .firstNotNullOfOrNull { jsonStringArg(call, it) }
            ?: return error("The Computer Use navigate action did not include a URL.")
        val uri = Uri.parse(target)
        if (uri.scheme !in setOf("http", "https")) {
            return error("Only http and https URLs can be opened through the Computer Use navigate action.")
        }
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            buildJsonObject {
                put("success", true)
                put("action", "navigate")
                put("url", uri.toString())
            }
        } catch (failure: ActivityNotFoundException) {
            error("No installed app can open '$target'.")
        }
    }

    private suspend fun executeComputerUseScroll(
        call: FunctionCall,
        service: ThrisaAccessibilityService,
    ): JsonObject {
        val direction = jsonStringArg(call, "direction")?.lowercase()
            ?: return error("The Computer Use scroll action did not include a direction.")
        val metrics = service.resources.displayMetrics
        val xArg = extractNumberArg(call, "x")
        val yArg = extractNumberArg(call, "y")
        val x = if (xArg == null) metrics.widthPixels / 2f
        else normalizedToScreen(xArg, metrics.widthPixels)
            ?: return error("The Computer Use scroll action included an off-screen x coordinate.")
        val y = if (yArg == null) metrics.heightPixels / 2f
        else normalizedToScreen(yArg, metrics.heightPixels)
            ?: return error("The Computer Use scroll action included an off-screen y coordinate.")
        val amount = (extractNumberArg(call, "amount") ?: 0.5).coerceIn(0.15, 0.8).toFloat()
        val halfDistanceX = metrics.widthPixels * amount / 2f
        val halfDistanceY = metrics.heightPixels * amount / 2f
        val (fromX, fromY, toX, toY) = when (direction) {
            "down" -> listOf(x, y + halfDistanceY, x, y - halfDistanceY)
            "up" -> listOf(x, y - halfDistanceY, x, y + halfDistanceY)
            "left" -> listOf(x - halfDistanceX, y, x + halfDistanceX, y)
            "right" -> listOf(x + halfDistanceX, y, x - halfDistanceX, y)
            else -> return error("Unsupported scroll direction '$direction'.")
        }
        return service.swipe(
            fromX.coerceIn(0f, metrics.widthPixels.toFloat()),
            fromY.coerceIn(0f, metrics.heightPixels.toFloat()),
            toX.coerceIn(0f, metrics.widthPixels.toFloat()),
            toY.coerceIn(0f, metrics.heightPixels.toFloat()),
            400,
        ).toResult("scroll", "Scrolled $direction.")
    }

    private suspend fun executeComputerUseSwipe(
        call: FunctionCall,
        service: ThrisaAccessibilityService,
    ): JsonObject {
        val x1 = extractNumberArg(call, "x") ?: extractNumberArg(call, "start_x")
        val y1 = extractNumberArg(call, "y") ?: extractNumberArg(call, "start_y")
        val x2 = extractNumberArg(call, "destination_x")
            ?: extractNumberArg(call, "x2")
            ?: extractNumberArg(call, "end_x")
        val y2 = extractNumberArg(call, "destination_y")
            ?: extractNumberArg(call, "y2")
            ?: extractNumberArg(call, "end_y")
        if (x1 == null || y1 == null || x2 == null || y2 == null) {
            return error("The Computer Use drag action needs start and destination coordinates.")
        }
        val start = normalizedPoint(x1, y1, service)
            ?: return error("The Computer Use swipe action started off-screen.")
        val end = normalizedPoint(x2, y2, service)
            ?: return error("The Computer Use swipe action ended off-screen.")
        val duration = (extractNumberArg(call, "duration_ms") ?: 400.0).toLong().coerceIn(50, 5000)
        return service.swipe(start.first, start.second, end.first, end.second, duration)
            .toResult(call.name, "Dragged across the screen.")
    }

    private suspend fun scrollElement(
        service: ThrisaAccessibilityService,
        call: FunctionCall,
    ): JsonObject {
        val direction = jsonStringArg(call, "direction")?.trim()?.lowercase()
            ?: return error("Provide 'direction' as 'forward' or 'backward' for scroll_element.")
        if (direction != "forward" && direction != "backward") {
            return error("Invalid scroll direction '$direction'. Use 'forward' or 'backward'.")
        }
        val target = jsonStringArg(call, "target")
        return if (service.scrollElement(target, forward = direction == "forward")) {
            buildJsonObject {
                put("success", true)
                put("action", "scroll_element")
                put("message", "Scrolled ${target?.let { "'$it'" } ?: "the available scroll container"} $direction.")
            }
        } else if (target.isNullOrBlank()) {
            val action = if (direction == "forward") "scroll_down" else "scroll_up"
            scroll(service, action)
        } else {
            error("Could not scroll '$target'. Inspect the current screen and select a visible scrollable container.")
        }
    }

    /** Finger up = content scrolls down (more content below becomes visible). */
    private suspend fun scroll(service: ThrisaAccessibilityService, action: String): JsonObject {
        val metrics = service.resources.displayMetrics
        val centerX = metrics.widthPixels / 2f
        val (fromY, toY) = if (action == "scroll_down") {
            (metrics.heightPixels * 0.7f) to (metrics.heightPixels * 0.3f)
        } else {
            (metrics.heightPixels * 0.3f) to (metrics.heightPixels * 0.7f)
        }
        return service.swipe(centerX, fromY, centerX, toY, 400)
            .toResult(action, if (action == "scroll_down") "Scrolled down." else "Scrolled up.")
    }

    private inline fun withPoint(
        call: FunctionCall,
        service: ThrisaAccessibilityService,
        block: (x: Float, y: Float) -> JsonObject,
    ): JsonObject {
        val x = extractNumberArg(call, "x")?.toFloat()
        val y = extractNumberArg(call, "y")?.toFloat()
        if (x == null || y == null) {
            return error("Please provide integer 'x' and 'y' screen coordinates from the latest capture_screen screenshot.")
        }
        val point = screenshotPointToScreen(x, y, service)
            ?: return error("Coordinates must be within the latest screenshot, and a fresh screenshot must be available.")
        return block(point.first, point.second)
    }

    private inline fun withSwipe(
        call: FunctionCall,
        service: ThrisaAccessibilityService,
        block: (x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long) -> JsonObject,
    ): JsonObject {
        val x1 = extractNumberArg(call, "x")?.toFloat()
        val y1 = extractNumberArg(call, "y")?.toFloat()
        val x2 = extractNumberArg(call, "x2")?.toFloat()
        val y2 = extractNumberArg(call, "y2")?.toFloat()
        if (x1 == null || y1 == null || x2 == null || y2 == null) {
            return error("Please provide 'x', 'y' (start) and 'x2', 'y2' (end) coordinates for the swipe.")
        }
        val duration = (extractNumberArg(call, "duration_ms") ?: 300.0).toLong().coerceIn(50, 5000)
        val start = screenshotPointToScreen(x1, y1, service)
        val end = screenshotPointToScreen(x2, y2, service)
        if (start == null || end == null) {
            return error("Swipe coordinates must be within the latest screenshot, and a fresh screenshot must be available.")
        }
        return block(start.first, start.second, end.first, end.second, duration)
    }

    private suspend fun withNormalizedPoint(
        call: FunctionCall,
        service: ThrisaAccessibilityService,
        block: suspend (x: Float, y: Float) -> JsonObject,
    ): JsonObject {
        val x = extractNumberArg(call, "x")
            ?: return error("The Computer Use action did not include an x coordinate.")
        val y = extractNumberArg(call, "y")
            ?: return error("The Computer Use action did not include a y coordinate.")
        val point = normalizedPoint(x, y, service)
            ?: return error("Computer Use coordinates must be normalized to the 0-1000 screen range.")
        return block(point.first, point.second)
    }

    private fun normalizedPoint(
        x: Double,
        y: Double,
        service: ThrisaAccessibilityService,
    ): Pair<Float, Float>? {
        val metrics = service.resources.displayMetrics
        val screenX = normalizedToScreen(x, metrics.widthPixels) ?: return null
        val screenY = normalizedToScreen(y, metrics.heightPixels) ?: return null
        return screenX to screenY
    }

    private fun screenshotPointToScreen(
        x: Float,
        y: Float,
        service: ThrisaAccessibilityService,
    ): Pair<Float, Float>? {
        val screenshot = lastCaptureDimensions.get() ?: return null
        if (x < 0 || y < 0 || x >= screenshot.first || y >= screenshot.second) return null
        val bounds = screenBounds(service)
        return (x * bounds.first / screenshot.first) to (y * bounds.second / screenshot.second)
    }

    private fun screenBounds(service: ThrisaAccessibilityService): Pair<Float, Float> =
        service.resources.displayMetrics.let { it.widthPixels.toFloat() to it.heightPixels.toFloat() }

    /** Saves the capture under cacheDir for the chat bubble; never user-visible storage. */
    private fun saveForDisplay(context: Context, bytes: ByteArray): String? = runCatching {
        val dir = File(context.cacheDir, "screen_captures").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        file.writeBytes(bytes)
        Uri.fromFile(file).toString()
    }.getOrNull()

    private fun screenAccessDisabled(): JsonObject = error(
        "Screen access is disabled. Ask the user to enable 'Thrisa AI Screen Access' for Thrisa " +
                "in the Android accessibility settings (Thrisa Settings → Permissions → Screen access), then try again."
    )

    private fun Boolean.toResult(action: String, message: String): JsonObject = if (this) {
        buildJsonObject {
            put("success", true)
            put("action", action)
            put("message", message)
        }
    } else {
        error("The system cancelled the '$action' gesture (for example because another gesture or a secure window was active). Try again.")
    }

    private fun extractNumberArg(call: FunctionCall, key: String): Double? =
        runCatching {
            val element = call.args[key] ?: return null
            element.jsonPrimitive.doubleOrNull ?: element.jsonPrimitive.content.trim().toDoubleOrNull()
        }.getOrNull()

    private fun error(message: String): JsonObject = buildJsonObject {
        put("success", false)
        put("error", message)
    }
}

internal fun normalizedToScreen(value: Double, dimension: Int): Float? {
    if (!value.isFinite() || value !in 0.0..1000.0 || dimension <= 0) return null
    return (value * (dimension - 1) / 1000.0).toFloat()
}
