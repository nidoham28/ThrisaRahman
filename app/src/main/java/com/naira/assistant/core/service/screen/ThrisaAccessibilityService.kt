package com.naira.assistant.core.service.screen

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import androidx.core.graphics.scale

/**
 * The app's accessibility service — the engine behind the AI screen-access tools
 * (capture_screen / read_screen_text / perform_screen_action).
 *
 * It is enabled by the user in the system accessibility settings (never at
 * runtime), so app code reaches it through the static [instance] holder:
 * null whenever the service is off, non-null once the system binds it.
 *
 * Three capabilities, all gated on that user grant:
 *  - [takeScreenshot] of the active display (API 30+),
 *  - [readScreenText] from the active window's content tree,
 *  - gestures and global actions ([tap], [swipe], [performGlobal], [typeText]).
 */
class ThrisaAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ThrisaA11y"

        /** Duration of a single tap / long-press stroke, in milliseconds. */
        private const val TAP_DURATION_MS = 50L

        /** Gap between the two strokes of a double-tap. */
        private const val DOUBLE_TAP_GAP_MS = 100L

        /**
         * The live service, or null while accessibility is disabled in system settings.
         *
         * `@Volatile` ensures the read/write barrier is visible to callers on
         * other threads (background coroutines using `isRunning` before
         * calling `instance?.foo()`).
         */
        @Volatile
        var instance: ThrisaAccessibilityService? = null
            private set

        /** True when the user has enabled the service and the system has bound it. */
        val isRunning: Boolean get() = instance != null

        /**
         * True when the service is switched on in the system accessibility
         * settings, even if it is not bound yet (just toggled). Used by the
         * Settings screen for the status row.
         */
        fun isEnabledInSettings(context: Context): Boolean {
            val manager = context.getSystemService(ACCESSIBILITY_SERVICE)
                    as? AccessibilityManager ?: return false
            val expected = ComponentName(context, ThrisaAccessibilityService::class.java)
            return manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            ).any { info ->
                info.resolveInfo?.serviceInfo?.let {
                    it.packageName == expected.packageName && it.name == expected.className
                } == true
            }
        }
    }

    private val mainExecutor = Executor { runnable -> Handler(Looper.getMainLooper()).post(runnable) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Nothing to react to live; the AI polls on demand through its tools.
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Screen capture
    // ------------------------------------------------------------------

    /**
     * Captures the active display and returns it as a downscaled JPEG, or null
     * when unavailable (service off, API < 30, rate-limited, secure window).
     *
     * The system rate-limits screenshots (~1/second); callers should treat
     * null as "try again shortly or fall back to [readScreenText]".
     *
     * Bitmap lifecycle: every bitmap allocated in this method is released on
     * every exit path — success, `null` return, and exception — because the
     * intermediate hardware bitmap, the software copy, and the JPEG-encoded
     * byte array must all release their native memory.
     */
    suspend fun takeScreenshot(maxDim: Int = 1536, quality: Int = 80): ByteArray? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

        val result = suspendCancellableCoroutine<ScreenshotResult?> { continuation ->
            val resumed = AtomicBoolean(false)
            fun resumeOnce(value: ScreenshotResult?) {
                if (resumed.compareAndSet(false, true)) continuation.resume(value)
            }
            val callback = object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    resumeOnce(screenshot)
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "takeScreenshot failed with code $errorCode")
                    resumeOnce(null)
                }
            }
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, callback)
        } ?: return null

        return withContext(Dispatchers.IO) {
            runCatching {
                val buffer = result.hardwareBuffer
                var hardware: Bitmap? = null
                var software: Bitmap? = null
                try {
                    hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        ?: return@runCatching null
                    software = hardware.copy(Bitmap.Config.ARGB_8888, false)
                        ?: return@runCatching null
                    software.downscaleAndCompress(maxDim, quality)
                } finally {
                    // `Bitmap.recycle()` is idempotent, so double-recycling
                    // after `downscaleAndCompress`'s own `recycle()` is safe.
                    // The point of doing it here is to guarantee release when
                    // the pipeline threw before reaching its internal cleanup.
                    software?.recycle()
                    hardware?.recycle()
                    buffer.close()
                }
            }.getOrNull()
        }
    }

    /**
     * Downscales and JPEG-compresses [this], recycling **both** the intermediate
     * scaled bitmap and the receiver on success. Throws if the pipeline fails —
     * the caller is responsible for recycling the receiver on the failure path.
     */
    private fun Bitmap.downscaleAndCompress(maxDim: Int, quality: Int): ByteArray {
        val scale = maxOf(width, height).toFloat() / maxDim
        val output = if (scale > 1f) {
            this.scale((width / scale).toInt(), (height / scale).toInt())
        } else {
            this
        }
        try {
            return ByteArrayOutputStream().use { stream ->
                output.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                stream.toByteArray()
            }
        } finally {
            // Only recycle the *intermediate* here. The receiver is owned by
            // the caller's outer `finally` — recycling it here on the exception
            // path would still be fine (idempotent), but keeping ownership in
            // one place makes the lifecycle easier to reason about.
            if (output !== this) output.recycle()
        }
    }

    // ------------------------------------------------------------------
    // Screen content tree
    // ------------------------------------------------------------------

    /**
     * Reads the visible text of the active window: one header line with the
     * foreground package, then the text / content description / resource-id of
     * up to [maxNodes] nodes, capped at [maxChars] characters.
     *
     * Returns **null** when the active window is unavailable *or* when the
     * window's tree contains no readable text. Callers should treat null as
     * "no screen content" rather than "empty screen".
     */
    suspend fun readScreenText(maxNodes: Int = 150, maxChars: Int = 4000): String? =
        withContext(Dispatchers.Main.immediate) { readScreenTextBlocking(maxNodes, maxChars) }

    private fun readScreenTextBlocking(maxNodes: Int, maxChars: Int): String? {
        val root = rootInActiveWindow ?: return null
        val builder = StringBuilder()
        builder.append("Active app: ")
            .append(root.packageName ?: "unknown")
            .append('\n')
        // Snapshot the header length so we can tell whether *content* was
        // added — the previous check (`length > "Active app: ".length + 2`)
        // passed even when the package name alone pushed length over the
        // threshold, returning a header-only string to the AI.
        val headerEnd = builder.length

        var visited = 0
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && visited < maxNodes && builder.length < maxChars) {
            val node = queue.removeFirst()
            visited++
            node.text?.takeIf { it.isNotBlank() }?.let { text ->
                appendNodeLine(builder, node, text, maxChars)
            } ?: node.contentDescription?.takeIf { it.isNotBlank() }?.let { description ->
                appendNodeLine(builder, node, description, maxChars)
            } ?: node.viewIdResourceName?.takeIf { it.isNotBlank() && node.isClickable }?.let { id ->
                appendNodeLine(builder, node, id, maxChars)
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { child ->
                    if (child.isVisibleToUser) queue.add(child)
                }
            }
        }

        return if (builder.length > headerEnd) builder.toString() else null
    }

    private fun appendNodeLine(
        builder: StringBuilder,
        node: AccessibilityNodeInfo,
        content: CharSequence,
        maxChars: Int,
    ) {
        if (builder.length >= maxChars) return
        val id = node.viewIdResourceName?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        val prefix = if (node.isEditable) "[input] " else if (node.isClickable) "[button] " else ""
        val details = buildList {
            if (id != null) add("id=$id")
            if (node.isScrollable) add("scrollable")
            if (node.isClickable || node.isEditable || node.isScrollable) {
                val bounds = Rect().also(node::getBoundsInScreen)
                add("bounds=${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}")
            }
        }
        val suffix = details.takeIf { it.isNotEmpty() }?.joinToString(prefix = " (", postfix = ")").orEmpty()
        builder.append(prefix)
            .append(content.toString().lineSequence().firstOrNull() ?: "")
            .append(suffix)
            .append('\n')
        if (builder.length > maxChars) builder.setLength(maxChars)
    }

    /** Clicks a visible element by its text, content description, or resource ID. */
    suspend fun clickElement(target: String): Boolean = withContext(Dispatchers.Main.immediate) {
        var candidate = findNode(target) ?: return@withContext false
        var attempts = 0
        while (attempts < 6) {
            val current = candidate
            if (AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK in current.actionList &&
                current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ) {
                return@withContext true
            }
            candidate = current.parent
            attempts++
        }
        false
    }

    /**
     * Scrolls a named element (or the first scrollable element when unnamed)
     * using accessibility actions instead of guessing swipe coordinates.
     */
    suspend fun scrollElement(target: String?, forward: Boolean): Boolean =
        withContext(Dispatchers.Main.immediate) {
            val matched = target?.takeIf { it.isNotBlank() }?.let(::findNode)
            var candidate = matched
            if (candidate == null && target.isNullOrBlank()) {
                candidate = findNodes().firstOrNull { it.isScrollable }
            }
            var attempts = 0
            while (attempts < 6 && candidate != null) {
                val current = candidate
                if (current.isScrollable) {
                    val action = if (forward) {
                        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    } else {
                        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    }
                    if (current.performAction(action)) return@withContext true
                }
                candidate = current.parent
                attempts++
            }
            false
        }

    private fun findNode(target: String): AccessibilityNodeInfo? {
        val normalized = target.trim().lowercase()
        if (normalized.isEmpty()) return null
        val nodes = findNodes()
        fun values(node: AccessibilityNodeInfo) = listOfNotNull(
            node.text?.toString(),
            node.contentDescription?.toString(),
            node.viewIdResourceName?.substringAfterLast('/'),
            node.viewIdResourceName,
        ).map { it.trim().lowercase() }
        return nodes.firstOrNull { node -> values(node).any { it == normalized } }
            ?: nodes.firstOrNull { node -> values(node).any { it.contains(normalized) } }
    }

    private fun findNodes(): List<AccessibilityNodeInfo> {
        val roots = buildList {
            rootInActiveWindow?.let(::add)
            for (window in windows) window.root?.let(::add)
        }
        val result = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addAll(roots)
        while (queue.isNotEmpty() && result.size < 500) {
            val node = queue.removeFirst()
            if (node.isVisibleToUser) {
                result.add(node)
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let(queue::addLast)
                }
            }
        }
        return result
    }

    // ------------------------------------------------------------------
    // Gestures & global actions
    // ------------------------------------------------------------------

    /** Taps at the given screen coordinates. Suspends until the gesture finishes. */
    suspend fun tap(x: Float, y: Float): Boolean =
        stroke(Path().apply { moveTo(x, y); lineTo(x + 0.1f, y + 0.1f) }, TAP_DURATION_MS)

    /** Long-presses at the given screen coordinates. */
    suspend fun longPress(x: Float, y: Float): Boolean =
        stroke(Path().apply { moveTo(x, y); lineTo(x + 0.1f, y + 0.1f) }, 600)

    /**
     * Double-taps at the given screen coordinates.
     *
     * Sent as a **single** [GestureDescription] with two strokes (0 ms and
     * [DOUBLE_TAP_GAP_MS]) rather than two separate taps. Two dispatches get
     * treated as independent single taps by some views because dispatch
     * overhead pushes the inter-tap interval past the double-tap timeout —
     * the fix is to let the OS see both strokes in the same gesture.
     */
    suspend fun doubleTap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y); lineTo(x + 0.1f, y + 0.1f) }
        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS)
            )
            .addStroke(
                GestureDescription.StrokeDescription(path, DOUBLE_TAP_GAP_MS, TAP_DURATION_MS)
            )
            .build()
        return dispatchGestureAndWait(gesture)
    }

    /** Swipes from ([x1], [y1]) to ([x2], [y2]) over [durationMs] milliseconds. */
    suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 300): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        return stroke(path, durationMs)
    }

    private suspend fun stroke(path: Path, durationMs: Long): Boolean {
        // GestureDescription requires duration > 0. Clamp defensively so a
        // caller passing 0 (or a computed negative) doesn't crash here with
        // an unrelated IllegalArgumentException.
        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(1L))
            )
            .build()
        return dispatchGestureAndWait(gesture)
    }

    /** Dispatches [gesture] and suspends until the system reports completion or cancellation. */
    private suspend fun dispatchGestureAndWait(gesture: GestureDescription): Boolean {
        return suspendCancellableCoroutine<Boolean> { continuation ->
            val resumed = AtomicBoolean(false)
            fun resumeOnce(value: Boolean) {
                if (resumed.compareAndSet(false, true)) continuation.resume(value)
            }
            val dispatched = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) =
                        resumeOnce(true)

                    override fun onCancelled(gestureDescription: GestureDescription?) =
                        resumeOnce(false)
                },
                null,
            )
            if (!dispatched) resumeOnce(false)
        }
    }

    /** Performs a global action such as GLOBAL_ACTION_BACK, HOME, RECENTS or NOTIFICATIONS. */
    fun performGlobal(action: Int): Boolean =
        try {
            performGlobalAction(action)
        } catch (t: Throwable) {
            Log.w(TAG, "performGlobalAction($action) failed", t)
            false
        }

    /**
     * Types [text] into the app's active text input via ACTION_SET_TEXT, which
     * replaces the field's content. Returns false when no text input can be
     * found (the model should tap the field and try again).
     *
     * Editable-state detection alone is unreliable on real apps — Compose text
     * fields and WebView inputs often report isEditable=false — so candidates
     * are also matched by ACTION_SET_TEXT support, in priority order: the
     * focused node, any visible editable node, any visible settable node.
     */
    suspend fun typeText(text: String, target: String? = null): Boolean {
        return withContext(Dispatchers.Main.immediate) {
            val input = findTextInput(target) ?: return@withContext false
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }
    }

    suspend fun pressImeEnter(): Boolean = withContext(Dispatchers.Main.immediate) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext false
        val input = findTextInput() ?: return@withContext false
        val action = AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER
        action in input.actionList && input.performAction(action.id)
    }

    /**
     * Searches every interactive window for the node the user's text should go
     * into, preferring the focused field and falling back to the first visible
     * input that can receive text.
     *
     * When [target] is specified and doesn't match any input, returns null —
     * writing into a *different* field would be worse than failing.
     */
    private fun findTextInput(target: String? = null): AccessibilityNodeInfo? {
        var focused: AccessibilityNodeInfo? = null
        var editable: AccessibilityNodeInfo? = null
        var settable: AccessibilityNodeInfo? = null
        var matching: AccessibilityNodeInfo? = null

        for (node in findNodes()) {
            if (node.isFocused) {
                focused = node
            }
            if (supportsSetText(node)) {
                if (editable == null && node.isVisibleToUser && node.isEditable) editable = node
                if (settable == null && node.isVisibleToUser) settable = node
                if (matching == null && !target.isNullOrBlank() &&
                    listOfNotNull(
                        node.text?.toString(),
                        node.contentDescription?.toString(),
                        node.viewIdResourceName,
                    ).any { it.contains(target, ignoreCase = true) }
                ) matching = node
            }
        }

        // Explicit target: only that field is a valid destination. If it wasn't
        // found, return null (a bad guess is worse than a clean failure).
        if (!target.isNullOrBlank()) return matching

        // No target: prefer the focused field if it can receive text; otherwise
        // the first visible editable / settable node (usually the only input on
        // screen). A focused but non-input node (a button, a scroll container)
        // must not hijack the target.
        return focused?.takeIf { it.isEditable || supportsSetText(it) }
            ?: editable
            ?: settable
    }

    private fun supportsSetText(node: AccessibilityNodeInfo): Boolean =
        AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT in node.actionList
}