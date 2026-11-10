package com.naira.assistant.core.tools.executor

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.naira.assistant.core.tools.executor.BuiltinToolExecutor.jsonStringArg
import com.naira.assistant.domain.FunctionCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Implementation of the device-toggle tools: `control_flashlight`,
 * `control_bluetooth` and `control_wifi`.
 *
 * - Flashlight uses [CameraManager.setTorchMode], which needs no camera
 *   permission; the current torch state is read through a
 *   [CameraManager.TorchCallback], which reports every camera's state
 *   immediately upon registration.
 * - Bluetooth goes through [BluetoothAdapter]. Direct switching is blocked
 *   from Android 13, so newer devices get the system Bluetooth settings panel
 *   instead. Reading the state needs BLUETOOTH_CONNECT (API 31+), which the
 *   app requests from its Settings screen.
 * - Wi-Fi programmatic switching is blocked since Android 10, so those
 *   devices get the system Wi-Fi settings panel instead; the state itself is
 *   always readable.
 *
 * The [context] is the Android application context. It is injected once when
 * the tool executor is built, NOT supplied by the model.
 */
object DeviceToggleToolExecutor {

    /** How long to wait for the torch callback's immediate state dispatch. */
    private const val TORCH_STATE_TIMEOUT_MS = 500L

    /**
     * Value of `Settings.Panel.ACTION_BLUETOOTH`. Referenced by its documented
     * string value because the platform jar used by the build omits the constant.
     */
    private const val ACTION_BLUETOOTH_PANEL = "android.settings.panel.action.BLUETOOTH"

    /**
     * Executes the `control_flashlight` tool.
     *
     * @param call    the function call from Gemini, carrying `action`.
     * @param context Android application context for the camera service.
     * @return a [JsonObject] with the flashlight state after the action.
     */
    fun executeFlashlightControl(call: FunctionCall, context: Context): JsonObject {
        val action = parseToggleAction(jsonStringArg(call, "action"))
            ?: return toggleActionError()

        val cameraManager =
            context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return error("This device has no camera service.")

        val cameraId = torchCameraId(cameraManager)
            ?: return error("This device has no flashlight unit.")

        return when (action) {
            "turn_on" -> {
                cameraManager.setTorchMode(cameraId, true)
                toggleResult("flashlight", action, enabled = true, message = "Flashlight turned on.")
            }

            "turn_off" -> {
                cameraManager.setTorchMode(cameraId, false)
                toggleResult("flashlight", action, enabled = false, message = "Flashlight turned off.")
            }

            else -> {
                val lit = currentTorchState(cameraManager, cameraId) ?: false
                toggleResult(
                    "flashlight",
                    action,
                    enabled = lit,
                    message = if (lit) "The flashlight is on." else "The flashlight is off."
                )
            }
        }
    }

    /**
     * Executes the `control_bluetooth` tool.
     *
     * @param call    the function call from Gemini, carrying `action`.
     * @param context Android application context for the Bluetooth service.
     * @return a [JsonObject] with the Bluetooth state after the action.
     */
    fun executeBluetoothControl(call: FunctionCall, context: Context): JsonObject {
        val action = parseToggleAction(jsonStringArg(call, "action"))
            ?: return toggleActionError()

        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter
            ?: return error("This device does not support Bluetooth.")

        when (action) {
            "check_status" -> {
                val enabled = adapterState(adapter)
                    ?: return error(
                        "Bluetooth permission is not granted, so its state cannot be read. " +
                                "Ask the user to grant Bluetooth permission from the app's settings screen."
                    )
                return toggleResult(
                    "bluetooth",
                    action,
                    enabled = enabled,
                    message = if (enabled) "Bluetooth is on." else "Bluetooth is off."
                )
            }

            "turn_on", "turn_off" -> {
                // Android 13 removed programmatic Bluetooth switching; ask the
                // user to flip it in the system panel instead.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    return openBluetoothFallback(context, action, adapter)
                }
                val switched = runCatching {
                    setBluetoothEnabledLegacy(adapter, action == "turn_on")
                }.getOrDefault(false)

                return if (switched) {
                    val verb = if (action == "turn_on") "on" else "off"
                    toggleResult(
                        "bluetooth",
                        action,
                        enabled = action == "turn_on",
                        message = "Bluetooth is turning $verb."
                    )
                } else {
                    openBluetoothFallback(context, action, adapter)
                }
            }

            else -> return toggleActionError()
        }
    }

    // Programmatic switching remains available on Android 12 and earlier. Android 13+
    // uses the system panel above, where this legacy API is no longer permitted.
    @Suppress("DEPRECATION")
    private fun setBluetoothEnabledLegacy(adapter: BluetoothAdapter, enabled: Boolean): Boolean =
        if (enabled) adapter.enable() else adapter.disable()

    /**
     * Executes the `control_wifi` tool.
     *
     * @param call    the function call from Gemini, carrying `action`.
     * @param context Android application context for the Wi-Fi service.
     * @return a [JsonObject] with the Wi-Fi state after the action.
     */
    fun executeWifiControl(call: FunctionCall, context: Context): JsonObject {
        val action = parseToggleAction(jsonStringArg(call, "action"))
            ?: return toggleActionError()

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
            as? WifiManager
            ?: return error("This device does not support Wi-Fi.")

        return when (action) {
            "check_status" -> {
                val enabled = wifiManager.isWifiEnabled
                toggleResult(
                    "wifi",
                    action,
                    enabled = enabled,
                    message = if (enabled) "Wi-Fi is on." else "Wi-Fi is off."
                )
            }

            "turn_on", "turn_off" -> {
                // Android 10 removed programmatic Wi-Fi switching; ask the
                // user to flip it in the system panel instead.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    return openWifiFallback(context, action, wifiManager)
                }
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = action == "turn_on"
                val verb = if (action == "turn_on") "on" else "off"
                toggleResult(
                    "wifi",
                    action,
                    enabled = action == "turn_on",
                    message = "Wi-Fi is turning $verb."
                )
            }

            else -> return toggleActionError()
        }
    }

    // ------------------------------------------------------------------
    // Pure helpers (unit-tested)
    // ------------------------------------------------------------------

    /**
     * Normalizes the model-supplied action into one of the three supported
     * toggle actions, tolerating a few common synonyms. Returns null when
     * the value is missing or unrecognised.
     */
    internal fun parseToggleAction(raw: String?): String? =
        raw?.trim()?.lowercase()?.let { action ->
            when (action) {
                "turn_on", "on", "enable" -> "turn_on"
                "turn_off", "off", "disable" -> "turn_off"
                "check_status", "status", "check" -> "check_status"
                else -> null
            }
        }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** First back camera whose characteristics report an available flash unit. */
    private fun torchCameraId(cameraManager: CameraManager): String? =
        runCatching {
            cameraManager.cameraIdList.firstOrNull { id ->
                runCatching {
                    cameraManager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }.getOrDefault(false)
            }
        }.getOrNull()

    /**
     * Reads the current torch state for [cameraId]. [CameraManager] reports
     * every camera's torch state immediately when a callback is registered,
     * so a short latch wait is enough; null means the state never arrived.
     */
    private fun currentTorchState(cameraManager: CameraManager, cameraId: String): Boolean? {
        var state: Boolean? = null
        val received = CountDownLatch(1)
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(id: String, enabled: Boolean) {
                if (id == cameraId && state == null) {
                    state = enabled
                    received.countDown()
                }
            }
        }
        cameraManager.registerTorchCallback(callback, null)
        try {
            received.await(TORCH_STATE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } finally {
            cameraManager.unregisterTorchCallback(callback)
        }
        return state
    }

    /** Safe read of the adapter's enabled state; null when unreadable. */
    private fun adapterState(adapter: BluetoothAdapter): Boolean? =
        runCatching { adapter.isEnabled }.getOrNull()

    /**
     * Opens the system Bluetooth panel and reports that the user must
     * confirm the switch manually.
     */
    private fun openBluetoothFallback(
        context: Context,
        action: String,
        adapter: BluetoothAdapter
    ): JsonObject {
        val panelOpened = runCatching {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(ACTION_BLUETOOTH_PANEL)
            } else {
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            }
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)

        val knownState = adapterState(adapter)
        return buildJsonObject {
            put("success", panelOpened)
            put("feature", "bluetooth")
            put("action", action)
            put("panel_opened", panelOpened)
            knownState?.let { put("enabled", it) }
            put(
                "message",
                if (panelOpened) {
                    "Android does not allow apps to switch Bluetooth directly. " +
                            "Opened the Bluetooth settings panel — ask the user to confirm there."
                } else {
                    "Could not switch Bluetooth or open its settings panel."
                }
            )
        }
    }

    /**
     * Opens the system Wi-Fi panel and reports that the user must confirm
     * the switch manually.
     */
    private fun openWifiFallback(
        context: Context,
        action: String,
        wifiManager: WifiManager
    ): JsonObject {
        val panelOpened = runCatching {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_WIFI)
            } else {
                Intent(Settings.ACTION_WIFI_SETTINGS)
            }
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)

        return buildJsonObject {
            put("success", panelOpened)
            put("feature", "wifi")
            put("action", action)
            put("panel_opened", panelOpened)
            put("enabled", wifiManager.isWifiEnabled)
            put(
                "message",
                if (panelOpened) {
                    "Android does not allow apps to switch Wi-Fi directly. " +
                            "Opened the Wi-Fi settings panel — ask the user to confirm there."
                } else {
                    "Could not switch Wi-Fi or open its settings panel."
                }
            )
        }
    }

    private fun toggleResult(
        feature: String,
        action: String,
        enabled: Boolean,
        message: String
    ): JsonObject = buildJsonObject {
        put("success", true)
        put("feature", feature)
        put("action", action)
        put("enabled", enabled)
        put("message", message)
    }

    private fun toggleActionError(): JsonObject = error(
        "Please provide an 'action' to perform: turn_on, turn_off or check_status."
    )

    private fun error(message: String): JsonObject = buildJsonObject {
        put("success", false)
        put("error", message)
    }
}
