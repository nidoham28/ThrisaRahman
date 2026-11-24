
package com.naira.assistant.core.tools.executor

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import com.naira.assistant.core.service.screen.ThrisaAccessibilityService
import com.naira.assistant.core.tools.executor.AppToolExecutor.executeOpenApp
import com.naira.assistant.core.tools.CommandExecution.jsonStringArg
import com.naira.assistant.domain.FunctionCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Executes AI screen-access tools using ThrisaAccessibilityService.
 *
 * Supports:
 * - capture_screen
 * - read_screen_text
 * - perform_screen_action
 * - Computer Use actions
 *
 * Screenshot bytes are consumed separately from screenshot dimensions.
 * This allows the ViewModel to consume an image without invalidating
 * the coordinates belonging to that screenshot.
 */
object ScreenToolExecutor {

    private data class ScreenCapture(
        val bytes: ByteArray?,
        val width: Int,
        val height: Int,
    )

    private val lastCapture = AtomicReference<ScreenCapture?>(null)

    /**
     * Returns screenshot bytes once.
     * Screenshot dimensions remain available for coordinate conversion.
     */
    fun consumeLastCapture(): ByteArray? {
        while (true) {
            val current = lastCapture.get() ?: return null
            val bytes = current.bytes ?: return null

            if (
                lastCapture.compareAndSet(
                    current,
                    current.copy(bytes = null)
                )
            ) {
                return bytes
            }
        }
    }

    // ------------------------------------------------------------------
    // Capture screen
    // ------------------------------------------------------------------

    @Suppress("UNUSED_PARAMETER")
    suspend fun executeCaptureScreen(
        call: FunctionCall,
        context: Context,
    ): JsonObject {
        val service = ThrisaAccessibilityService.instance
            ?: return screenAccessDisabled()

        delay(600)

        // Invalidate coordinates from the previous screenshot.
        lastCapture.set(null)

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
            return if (!screenText.isNullOrBlank()) {
                buildJsonObject {
                    put("success", true)
                    put("action", "capture_screen")
                    put("mode", "text_only")
                    put(
                        "note",
                        "A screenshot is unavailable. Only screen text is provided. " +
                                "Do not use pixel coordinates without a fresh screenshot."
                    )
                    put("screen_text", screenText.take(12000))
                }
            } else {
                error(
                    "Could not capture the screen. Android may be rate-limiting " +
                            "screenshots, or the current window may be secure. " +
                            "Wait briefly and try again, or use read_screen_text."
                )
            }
        }

        val (width, height, imageUri) = withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            BitmapFactory.decodeByteArray(
                screenshot,
                0,
                screenshot.size,
                options
            )

            Triple(
                options.outWidth,
                options.outHeight,
                saveForDisplay(context, screenshot),
            )
        }

        lastCapture.set(
            ScreenCapture(
                bytes = screenshot,
                width = width,
                height = height,
            )
        )

        return buildJsonObject {
            put("success", true)
            put("action", "capture_screen")

            if (imageUri != null) {
                put("image_uri", imageUri)
            }

            put(
                "note",
                "The screenshot is attached after this result. " +
                        "Inspect it before deciding the next screen action."
            )

            if (!screenText.isNullOrBlank()) {
                put("screen_text", screenText.take(12000))
            }
        }
    }

    // ------------------------------------------------------------------
    // Read screen text
    // ------------------------------------------------------------------

    @Suppress("UNUSED_PARAMETER")
    suspend fun executeReadScreenText(
        call: FunctionCall,
        context: Context,
    ): JsonObject {
        val service = ThrisaAccessibilityService.instance
            ?: return screenAccessDisabled()

        val text = try {
            service.readScreenText()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            return error(
                exception.message
                    ?.takeIf { it.isNotBlank() }
                    ?: "Unable to read the current screen."
            )
        }

        return if (text.isNullOrBlank()) {
            error(
                "The current window exposes no readable accessibility text. " +
                        "Try capture_screen instead."
            )
        } else {
            buildJsonObject {
                put("success", true)
                put("action", "read_screen_text")
                put("screen_text", text.take(12000))
            }
        }
    }

    // ------------------------------------------------------------------
    // Standard screen actions
    // ------------------------------------------------------------------

    @Suppress("UNUSED_PARAMETER")
    suspend fun executePerformScreenAction(
        call: FunctionCall,
        context: Context,
    ): JsonObject {
        val service = ThrisaAccessibilityService.instance
            ?: return screenAccessDisabled()

        val action = jsonStringArg(call, "action")
            ?.trim()
            ?.lowercase()
            ?: return error("A screen action is required.")

        return try {
            when (action) {

                "tap" -> withPoint(call, service) { x, y ->
                    service.tap(x, y).toResult(
                        "tap",
                        "Tapped the screen at (${x.toInt()}, ${y.toInt()})."
                    )
                }

                "double_tap" -> withPoint(call, service) { x, y ->
                    service.doubleTap(x, y).toResult(
                        "double_tap",
                        "Double-tapped the screen at (${x.toInt()}, ${y.toInt()})."
                    )
                }

                "long_press" -> withPoint(call, service) { x, y ->
                    service.longPress(x, y).toResult(
                        "long_press",
                        "Long-pressed the screen at (${x.toInt()}, ${y.toInt()})."
                    )
                }

                "click_element" -> {
                    val target = if (call.args.containsKey("target")) {
                        jsonStringArg(call, "target")
                            ?.trim()
                            ?.takeIf(String::isNotEmpty)
                            ?: return error("'target' must be a non-empty string.")
                    } else {
                        null
                    }
                        ?: return error(
                            "Provide the visible text or accessibility ID of the element to click."
                        )

                    service.clickElement(target).toResult(
                        "click_element",
                        "The click action was accepted for '$target'."
                    )
                }

                "swipe" -> withSwipe(call, service) {
                        x1, y1, x2, y2, durationMs ->

                    service.swipe(
                        x1,
                        y1,
                        x2,
                        y2,
                        durationMs
                    ).toResult(
                        "swipe",
                        "Swiped from (${x1.toInt()}, ${y1.toInt()}) " +
                                "to (${x2.toInt()}, ${y2.toInt()})."
                    )
                }

                "scroll_element" -> scrollElement(service, call)

                "scroll_up", "scroll_down" -> scroll(service, action)

                "back" -> service.performGlobal(
                    AccessibilityService.GLOBAL_ACTION_BACK
                ).toResult("back", "Pressed Back.")

                "home" -> service.performGlobal(
                    AccessibilityService.GLOBAL_ACTION_HOME
                ).toResult("home", "Requested the Home action.")

                "recents" -> service.performGlobal(
                    AccessibilityService.GLOBAL_ACTION_RECENTS
                ).toResult("recents", "Requested the recent apps overview.")

                "notifications" -> service.performGlobal(
                    AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                ).toResult("notifications", "Requested the notification shade.")

                "type_text" -> executeTypeText(service, call)

                else -> error(
                    "Unknown screen action '$action'. Supported actions: " +
                            "tap, double_tap, long_press, click_element, swipe, " +
                            "scroll_element, scroll_up, scroll_down, back, home, " +
                            "recents, notifications, type_text."
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            error(
                exception.message
                    ?.takeIf { it.isNotBlank() }
                    ?: "The screen action could not be completed."
            )
        }
    }

    // ------------------------------------------------------------------
    // Computer Use actions
    // ------------------------------------------------------------------

    suspend fun executeComputerUseAction(
        call: FunctionCall,
        context: Context,
    ): JsonObject {
        val service = ThrisaAccessibilityService.instance
            ?: return screenAccessDisabled()

        val actionName = call.name.trim().lowercase()

        val actionResult = try {
            when (actionName) {

                "open_app" -> executeOpenApp(call, context)

                "click" -> withNormalizedPoint(call, service) { x, y ->
                    service.tap(x, y).toResult(
                        "click",
                        "Tapped the requested screen position."
                    )
                }

                "double_click" -> withNormalizedPoint(call, service) { x, y ->
                    service.doubleTap(x, y).toResult(
                        "double_click",
                        "Double-tapped the requested screen position."
                    )
                }

                "triple_click" -> withNormalizedPoint(call, service) { x, y ->
                    repeat(3) { index ->
                        if (index > 0) delay(100)

                        if (!service.tap(x, y)) {
                            return@withNormalizedPoint error(
                                "The triple-click gesture was interrupted."
                            )
                        }
                    }

                    buildJsonObject {
                        put("success", true)
                        put("action", "triple_click")
                        put("message", "Dispatched three taps.")
                    }
                }

                "long_press", "right_click" ->
                    withNormalizedPoint(call, service) { x, y ->
                        service.longPress(x, y).toResult(
                            actionName,
                            "Long-pressed the requested screen position."
                        )
                    }

                "scroll" -> executeComputerUseScroll(call, service)

                "swipe", "drag" -> executeComputerUseSwipe(call, service)

                "type" -> executeComputerUseType(call, service)

                "keypress", "press_key" ->
                    executeComputerUseKeypress(call, service)

                "navigate" -> executeComputerUseNavigate(call, context)

                "go_back" -> service.performGlobal(
                    AccessibilityService.GLOBAL_ACTION_BACK
                ).toResult("go_back", "Requested the Back action.")

                "wait" -> {
                    val seconds = (
                            extractNumberArg(call, "seconds") ?: 1.0
                            ).coerceIn(0.1, 5.0)

                    delay((seconds * 1000).toLong())

                    buildJsonObject {
                        put("success", true)
                        put("action", "wait")
                        put("seconds", seconds)
                    }
                }

                "go_forward" -> error(
                    "Forward navigation is not supported by Android accessibility."
                )

                else -> error(
                    "Unsupported Computer Use action '$actionName'."
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            error(
                exception.message
                    ?.takeIf { it.isNotBlank() }
                    ?: "The Computer Use action failed."
            )
        }

        // Capture the resulting screen even when the action failed.
        // This gives the model useful information about the current state.
        val captureResponse = try {
            executeCaptureScreen(
                FunctionCall(name = "capture_screen"),
                context
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            error(
                exception.message
                    ?: "Unable to capture the resulting screen."
            )
        }

        return buildJsonObject {
            actionResult.forEach { (key, value) ->
                put(key, value)
            }

            captureResponse["image_uri"]?.let {
                put("image_uri", it)
            }

            captureResponse["screen_text"]?.let {
                put("screen_text", it)
            }

            captureResponse["error"]?.let {
                put("screen_capture_error", it)
            }

            captureResponse["mode"]?.let {
                put("screen_capture_mode", it)
            }
        }
    }

    // ------------------------------------------------------------------
    // Text entry
    // ------------------------------------------------------------------

    private suspend fun executeTypeText(
        service: ThrisaAccessibilityService,
        call: FunctionCall,
    ): JsonObject {
        val text = jsonStringArg(call, "text")
            ?: return error("Provide the text to enter.")

        if (text.isEmpty()) {
            return error("The text to enter cannot be empty.")
        }

        val target = if (call.args.containsKey("target")) {
            jsonStringArg(call, "target")
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return error("'target' must be a non-empty string.")
        } else {
            null
        }

        val entered = service.typeText(text, target)

        return if (entered) {
            buildJsonObject {
                put("success", true)
                put("action", "type_text")
                put(
                    "message",
                    "Entered the requested text. It has NOT been sent or submitted."
                )
            }
        } else {
            error(
                if (target == null) {
                    "No editable input field could receive the text. " +
                            "Inspect the screen and focus the correct field."
                } else {
                    "Could not find an editable field matching '$target'. " +
                            "Inspect the screen and use its exact label or accessibility ID."
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

        if (text.isEmpty()) {
            return error("The text to enter cannot be empty.")
        }

        val x = extractNumberArg(call, "x")
        val y = extractNumberArg(call, "y")

        if ((x == null) != (y == null)) {
            return error("Provide both x and y coordinates, or neither.")
        }

        if (x != null && y != null) {
            val point = normalizedPoint(x, y, service)
                ?: return error(
                    "The Computer Use type action included off-screen coordinates."
                )

            if (!service.tap(point.first, point.second)) {
                return error("Could not focus the requested input field.")
            }

            delay(150)
        }

        if (!service.typeText(text)) {
            return error("No editable text field could receive the requested text.")
        }

        val pressEnter = call.args["press_enter"]
            ?.jsonPrimitive
            ?.booleanOrNull == true

        if (pressEnter && !service.pressImeEnter()) {
            return error(
                "The text was entered, but the field did not accept the Enter action."
            )
        }

        return buildJsonObject {
            put("success", true)
            put("action", "type")
            put(
                "message",
                if (pressEnter) {
                    "Entered the text and requested the Enter action."
                } else {
                    "Entered the requested text. It has not been submitted automatically."
                }
            )
        }
    }

    // ------------------------------------------------------------------
    // Computer Use keypress
    // ------------------------------------------------------------------

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
                .toResult("keypress", "Requested the Enter action.")

            "BACK", "ESC", "ESCAPE" -> service.performGlobal(
                AccessibilityService.GLOBAL_ACTION_BACK
            ).toResult("keypress", "Requested the Back action.")

            "HOME" -> service.performGlobal(
                AccessibilityService.GLOBAL_ACTION_HOME
            ).toResult("keypress", "Requested the Home action.")

            "RECENTS" -> service.performGlobal(
                AccessibilityService.GLOBAL_ACTION_RECENTS
            ).toResult("keypress", "Requested the recent apps overview.")

            else -> error(
                "Android accessibility cannot synthesize the '$key' key."
            )
        }
    }

    // ------------------------------------------------------------------
    // Computer Use navigation
    // ------------------------------------------------------------------

    private fun executeComputerUseNavigate(
        call: FunctionCall,
        context: Context,
    ): JsonObject {
        val target = listOf("url", "uri")
            .firstNotNullOfOrNull { jsonStringArg(call, it) }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return error("The Computer Use navigate action requires a URL.")

        val uri = Uri.parse(target)

        if (
            uri.scheme?.lowercase() !in setOf("http", "https") ||
            uri.host.isNullOrBlank()
        ) {
            return error("Only valid HTTP and HTTPS URLs are supported.")
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.applicationContext.startActivity(intent)

            buildJsonObject {
                put("success", true)
                put("action", "navigate")
                put("url", uri.toString())
                put("message", "Requested Android to open the URL.")
            }
        } catch (_: ActivityNotFoundException) {
            error("No installed application can open this URL.")
        } catch (_: SecurityException) {
            error("Android denied permission to open this URL.")
        } catch (exception: Exception) {
            error(
                exception.message
                    ?: "Unable to open the requested URL."
            )
        }
    }

    // ------------------------------------------------------------------
    // Computer Use scroll
    // ------------------------------------------------------------------

    private suspend fun executeComputerUseScroll(
        call: FunctionCall,
        service: ThrisaAccessibilityService,
    ): JsonObject {
        val direction = jsonStringArg(call, "direction")
            ?.trim()
            ?.lowercase()
            ?: return error("The Computer Use scroll action requires a direction.")

        val metrics = service.resources.displayMetrics

        val xArg = extractNumberArg(call, "x")
        val yArg = extractNumberArg(call, "y")

        val x = if (xArg == null) {
            metrics.widthPixels / 2f
        } else {
            normalizedToScreen(xArg, metrics.widthPixels)
                ?: return error("The Computer Use scroll action has an invalid x coordinate.")
        }

        val y = if (yArg == null) {
            metrics.heightPixels / 2f
        } else {
            normalizedToScreen(yArg, metrics.heightPixels)
                ?: return error("The Computer Use scroll action has an invalid y coordinate.")
        }

        val amount = (
                extractNumberArg(call, "amount") ?: 0.5
                ).coerceIn(0.15, 0.8).toFloat()

        val halfDistanceX = metrics.widthPixels * amount / 2f
        val halfDistanceY = metrics.heightPixels * amount / 2f

        val (fromX, fromY, toX, toY) = when (direction) {
            "down" -> listOf(
                x,
                y + halfDistanceY,
                x,
                y - halfDistanceY
            )

            "up" -> listOf(
                x,
                y - halfDistanceY,
                x,
                y + halfDistanceY
            )

            "left" -> listOf(
                x - halfDistanceX,
                y,
                x + halfDistanceX,
                y
            )

            "right" -> listOf(
                x + halfDistanceX,
                y,
                x - halfDistanceX,
                y
            )

            else -> return error(
                "Unsupported scroll direction '$direction'. Use up, down, left, or right."
            )
        }

        return service.swipe(
            fromX.coerceIn(0f, metrics.widthPixels.toFloat() - 1f),
            fromY.coerceIn(0f, metrics.heightPixels.toFloat() - 1f),
            toX.coerceIn(0f, metrics.widthPixels.toFloat() - 1f),
            toY.coerceIn(0f, metrics.heightPixels.toFloat() - 1f),
            400L,
        ).toResult("scroll", "Dispatched a $direction scroll gesture.")
    }

    private suspend fun executeComputerUseSwipe(
        call: FunctionCall,
        service: ThrisaAccessibilityService,
    ): JsonObject {
        val x1 = extractNumberArg(call, "x")
            ?: extractNumberArg(call, "start_x")

        val y1 = extractNumberArg(call, "y")
            ?: extractNumberArg(call, "start_y")

        val x2 = extractNumberArg(call, "destination_x")
            ?: extractNumberArg(call, "x2")
            ?: extractNumberArg(call, "end_x")

        val y2 = extractNumberArg(call, "destination_y")
            ?: extractNumberArg(call, "y2")
            ?: extractNumberArg(call, "end_y")

        if (x1 == null || y1 == null || x2 == null || y2 == null) {
            return error(
                "The Computer Use swipe action requires start and destination coordinates."
            )
        }

        val start = normalizedPoint(x1, y1, service)
            ?: return error("The Computer Use swipe start coordinates are invalid.")

        val end = normalizedPoint(x2, y2, service)
            ?: return error("The Computer Use swipe end coordinates are invalid.")

        val durationValue = if (call.args.containsKey("duration_ms")) {
            extractNumberArg(call, "duration_ms")
                ?: return error("'duration_ms' must be an integer.")
        } else {
            400.0
        }
        if (durationValue % 1.0 != 0.0 || durationValue !in 50.0..5000.0) {
            return error("'duration_ms' must be an integer between 50 and 5000.")
        }
        val duration = durationValue.toLong()

        return service.swipe(
            start.first,
            start.second,
            end.first,
            end.second,
            duration,
        ).toResult(call.name, "Dispatched the requested swipe gesture.")
    }

    // ------------------------------------------------------------------
    // Scroll element
    // ------------------------------------------------------------------

    private suspend fun scrollElement(
        service: ThrisaAccessibilityService,
        call: FunctionCall,
    ): JsonObject {
        val direction = jsonStringArg(call, "direction")
            ?.trim()
            ?.lowercase()
            ?: return error(
                "Provide direction as 'forward' or 'backward' for scroll_element."
            )

        if (direction != "forward" && direction != "backward") {
            return error(
                "Invalid scroll direction '$direction'. Use forward or backward."
            )
        }

        val target = if (call.args.containsKey("target")) {
            jsonStringArg(call, "target")
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return error("'target' must be a non-empty string.")
        } else {
            null
        }

        return if (
            service.scrollElement(
                target,
                forward = direction == "forward"
            )
        ) {
            buildJsonObject {
                put("success", true)
                put("action", "scroll_element")
                put(
                    "message",
                    "Dispatched scrolling ${target?.let { "'$it'" } ?: "the available scroll container"} $direction."
                )
            }
        } else if (target == null) {
            val fallbackAction = if (direction == "forward") {
                "scroll_down"
            } else {
                "scroll_up"
            }

            scroll(service, fallbackAction)
        } else {
            error(
                "Could not scroll '$target'. Inspect the screen and select a visible scrollable container."
            )
        }
    }

    // ------------------------------------------------------------------
    // Standard scrolling
    // ------------------------------------------------------------------

    private suspend fun scroll(
        service: ThrisaAccessibilityService,
        action: String,
    ): JsonObject {
        val metrics = service.resources.displayMetrics

        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) {
            return error("Could not determine the screen dimensions.")
        }

        val centerX = metrics.widthPixels / 2f

        val (fromY, toY) = if (action == "scroll_down") {
            metrics.heightPixels * 0.7f to metrics.heightPixels * 0.3f
        } else {
            metrics.heightPixels * 0.3f to metrics.heightPixels * 0.7f
        }

        return service.swipe(
            centerX,
            fromY,
            centerX,
            toY,
            400L
        ).toResult(
            action,
            if (action == "scroll_down") {
                "Dispatched a scroll down gesture."
            } else {
                "Dispatched a scroll up gesture."
            }
        )
    }

    // ------------------------------------------------------------------
    // Coordinate helpers
    // ------------------------------------------------------------------

    private suspend fun withPoint(
        call: FunctionCall,
        service: ThrisaAccessibilityService,
        block: suspend (x: Float, y: Float) -> JsonObject,
    ): JsonObject {
        val x = extractNumberArg(call, "x")?.toFloat()
        val y = extractNumberArg(call, "y")?.toFloat()

        if (x == null || y == null) {
            return error(
                "Provide valid x and y coordinates from the latest screenshot."
            )
        }

        val point = screenshotPointToScreen(x, y, service)
            ?: return error(
                "Coordinates are outside the latest screenshot, or no fresh screenshot is available."
            )

        return block(point.first, point.second)
    }

    private suspend fun withSwipe(
        call: FunctionCall,
        service: ThrisaAccessibilityService,
        block: suspend (
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
            durationMs: Long,
        ) -> JsonObject,
    ): JsonObject {
        val x1 = extractNumberArg(call, "x")?.toFloat()
        val y1 = extractNumberArg(call, "y")?.toFloat()
        val x2 = extractNumberArg(call, "x2")?.toFloat()
        val y2 = extractNumberArg(call, "y2")?.toFloat()

        if (x1 == null || y1 == null || x2 == null || y2 == null) {
            return error(
                "Provide x, y, x2, and y2 coordinates for the swipe."
            )
        }

        val durationValue = if (call.args.containsKey("duration_ms")) {
            extractNumberArg(call, "duration_ms")
                ?: return error("'duration_ms' must be an integer.")
        } else {
            300.0
        }
        if (durationValue % 1.0 != 0.0 || durationValue !in 50.0..5000.0) {
            return error("'duration_ms' must be an integer between 50 and 5000.")
        }
        val duration = durationValue.toLong()

        val start = screenshotPointToScreen(x1, y1, service)
            ?: return error("The swipe starting coordinates are invalid.")

        val end = screenshotPointToScreen(x2, y2, service)
            ?: return error("The swipe ending coordinates are invalid.")

        return block(
            start.first,
            start.second,
            end.first,
            end.second,
            duration
        )
    }

    private suspend fun withNormalizedPoint(
        call: FunctionCall,
        service: ThrisaAccessibilityService,
        block: suspend (x: Float, y: Float) -> JsonObject,
    ): JsonObject {
        val x = extractNumberArg(call, "x")
            ?: return error("The Computer Use action requires an x coordinate.")

        val y = extractNumberArg(call, "y")
            ?: return error("The Computer Use action requires a y coordinate.")

        val point = normalizedPoint(x, y, service)
            ?: return error(
                "Computer Use coordinates must be within the normalized 0-1000 screen range."
            )

        return block(point.first, point.second)
    }

    private fun normalizedPoint(
        x: Double,
        y: Double,
        service: ThrisaAccessibilityService,
    ): Pair<Float, Float>? {
        val metrics = service.resources.displayMetrics

        val screenX = normalizedToScreen(x, metrics.widthPixels)
            ?: return null

        val screenY = normalizedToScreen(y, metrics.heightPixels)
            ?: return null

        return screenX to screenY
    }

    private fun screenshotPointToScreen(
        x: Float,
        y: Float,
        service: ThrisaAccessibilityService,
    ): Pair<Float, Float>? {
        if (!x.isFinite() || !y.isFinite()) return null

        val capture = lastCapture.get() ?: return null

        if (capture.width <= 0 || capture.height <= 0) return null

        if (
            x < 0f ||
            y < 0f ||
            x >= capture.width ||
            y >= capture.height
        ) {
            return null
        }

        val bounds = screenBounds(service)

        if (bounds.first <= 0f || bounds.second <= 0f) return null

        return (
                x * bounds.first / capture.width
                ) to (
                y * bounds.second / capture.height
                )
    }

    private fun screenBounds(
        service: ThrisaAccessibilityService,
    ): Pair<Float, Float> {
        val metrics = service.resources.displayMetrics

        return metrics.widthPixels.toFloat() to
                metrics.heightPixels.toFloat()
    }

    // ------------------------------------------------------------------
    // File handling
    // ------------------------------------------------------------------

    private fun saveForDisplay(
        context: Context,
        bytes: ByteArray,
    ): String? {
        return try {
            val directory = File(
                context.cacheDir,
                "screen_captures"
            ).apply {
                mkdirs()
            }

            val file = File(
                directory,
                "capture_${System.currentTimeMillis()}.jpg"
            )

            file.writeBytes(bytes)

            Uri.fromFile(file).toString()
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------
    // Result helpers
    // ------------------------------------------------------------------

    private fun screenAccessDisabled(): JsonObject = error(
        "Screen access is disabled. Ask the user to enable " +
                "'Thrisa AI Screen Access' for Thrisa in Android accessibility settings."
    )

    private fun Boolean.toResult(
        action: String,
        message: String,
    ): JsonObject {
        return if (this) {
            buildJsonObject {
                put("success", true)
                put("action", action)
                put("message", message)
            }
        } else {
            error(
                "Android did not accept the '$action' accessibility action. " +
                        "The screen may have changed, or the target may no longer be available."
            )
        }
    }

    private fun extractNumberArg(
        call: FunctionCall,
        key: String,
    ): Double? {
        val element = call.args[key] ?: return null
        val primitive = element as? kotlinx.serialization.json.JsonPrimitive
            ?: return null

        if (primitive.isString) return null

        return primitive.doubleOrNull
            ?.takeIf { it.isFinite() }
    }

    private fun error(message: String): JsonObject {
        return buildJsonObject {
            put("success", false)
            put("error", message)
        }
    }
}

// ----------------------------------------------------------------------
// Normalized Computer Use coordinates: 0-1000
// ----------------------------------------------------------------------

internal fun normalizedToScreen(
    value: Double,
    dimension: Int,
): Float? {
    if (
        !value.isFinite() ||
        value !in 0.0..1000.0 ||
        dimension <= 0
    ) {
        return null
    }

    return (value * (dimension - 1) / 1000.0).toFloat()
}
