
package com.naira.assistant.core.tools.executor

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import com.naira.assistant.core.tools.CommandExecution.jsonStringArg
import com.naira.assistant.domain.FunctionCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object DeviceToggleToolExecutor {

    private const val TORCH_TIMEOUT_MS = 1000L

    private const val ACTION_BLUETOOTH_PANEL =
        "android.settings.panel.action.BLUETOOTH"

    private val validActions = setOf(
        "turn_on",
        "turn_off",
        "check_status"
    )

    // ---------------------------------------------------------
    // Flashlight
    // ---------------------------------------------------------

    fun executeFlashlightControl(
        call: FunctionCall,
        context: Context
    ): JsonObject {
        val action = parseToggleAction(jsonStringArg(call, "action"))
            ?: return toggleActionError()

        val manager = context.applicationContext
            .getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return error("Camera service is unavailable.")

        return try {
            val cameraId = torchCameraId(manager)
                ?: return error("This device has no available flashlight.")

            when (action) {
                "check_status" -> {
                    val enabled = currentTorchState(manager, cameraId)
                        ?: return error(
                            "Unable to determine the current flashlight state."
                        )

                    toggleResult(
                        "flashlight",
                        action,
                        enabled,
                        "Flashlight is ${if (enabled) "on" else "off"}."
                    )
                }

                "turn_on", "turn_off" -> {
                    val requestedState = action == "turn_on"

                    manager.setTorchMode(cameraId, requestedState)

                    val actualState = currentTorchState(manager, cameraId)

                    if (actualState == requestedState) {
                        toggleResult(
                            "flashlight",
                            action,
                            actualState,
                            "Flashlight turned ${if (actualState) "on" else "off"}."
                        )
                    } else {
                        buildJsonObject {
                            put("success", true)
                            put("feature", "flashlight")
                            put("action", action)
                            put("status", "requested")
                            put("message", "Flashlight state change was requested, but the resulting state could not be confirmed.")
                        }
                    }
                }

                else -> toggleActionError()
            }
        } catch (e: SecurityException) {
            error("Permission denied while accessing the flashlight.")
        } catch (e: CameraAccessException) {
            error("Camera access failed: ${e.message ?: "unknown camera error"}")
        } catch (e: IllegalArgumentException) {
            error("The requested flashlight camera is unavailable.")
        } catch (e: Exception) {
            error("Flashlight operation failed: ${e.message ?: "unknown error"}")
        }
    }

    private fun torchCameraId(manager: CameraManager): String? {
        return try {
            val cameras = manager.cameraIdList

            val available = cameras.filter { id ->
                runCatching {
                    manager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }.getOrDefault(false)
            }

            // Prefer the rear-facing camera.
            available.firstOrNull { id ->
                runCatching {
                    manager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.LENS_FACING) ==
                            CameraCharacteristics.LENS_FACING_BACK
                }.getOrDefault(false)
            } ?: available.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Uses a dedicated HandlerThread so waiting for the torch callback
     * cannot block the main thread that would deliver that callback.
     */
    private fun currentTorchState(
        manager: CameraManager,
        cameraId: String
    ): Boolean? {
        val state = AtomicReference<Boolean?>(null)
        val received = CountDownLatch(1)

        val thread = HandlerThread("NairaTorchCallback").apply {
            start()
        }

        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(
                id: String,
                enabled: Boolean
            ) {
                if (id == cameraId && state.compareAndSet(null, enabled)) {
                    received.countDown()
                }
            }

            override fun onTorchModeUnavailable(id: String) {
                if (id == cameraId && state.compareAndSet(null, null)) {
                    received.countDown()
                }
            }
        }

        try {
            manager.registerTorchCallback(
                callback,
                Handler(thread.looper)
            )

            received.await(
                TORCH_TIMEOUT_MS,
                TimeUnit.MILLISECONDS
            )

            return state.get()
        } catch (_: Exception) {
            return null
        } finally {
            runCatching {
                manager.unregisterTorchCallback(callback)
            }
            thread.quitSafely()
        }
    }

    // ---------------------------------------------------------
    // Bluetooth
    // ---------------------------------------------------------

    fun executeBluetoothControl(
        call: FunctionCall,
        context: Context
    ): JsonObject {
        val action = parseToggleAction(jsonStringArg(call, "action"))
            ?: return toggleActionError()

        val appContext = context.applicationContext

        val adapter = (appContext.getSystemService(
            Context.BLUETOOTH_SERVICE
        ) as? BluetoothManager)?.adapter
            ?: return error("This device does not support Bluetooth.")

        if (action == "check_status") {
            val enabled = readBluetoothState(adapter)
                ?: return error(
                    "Cannot read Bluetooth state. Grant the required Bluetooth permission."
                )

            return toggleResult(
                "bluetooth",
                action,
                enabled,
                "Bluetooth is ${if (enabled) "on" else "off"}."
            )
        }

        // Direct switching is unavailable for Android 13+ and
        // apps targeting Android 13+.
        val directControlAllowed =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
                    appContext.applicationInfo.targetSdkVersion <
                    Build.VERSION_CODES.TIRAMISU

        if (!directControlAllowed) {
            return openBluetoothFallback(appContext, action, adapter)
        }

        return try {
            @Suppress("DEPRECATION")
            val accepted = if (action == "turn_on") {
                adapter.enable()
            } else {
                adapter.disable()
            }

            if (accepted) {
                changeRequestedResult(
                    "bluetooth",
                    action,
                    readBluetoothState(adapter)
                )
            } else {
                openBluetoothFallback(appContext, action, adapter)
            }
        } catch (e: SecurityException) {
            error("Bluetooth permission is missing or was denied.")
        } catch (e: Exception) {
            error("Bluetooth operation failed: ${e.message ?: "unknown error"}")
        }
    }

    private fun readBluetoothState(
        adapter: BluetoothAdapter
    ): Boolean? = try {
        adapter.isEnabled
    } catch (_: SecurityException) {
        null
    } catch (_: Exception) {
        null
    }

    private fun openBluetoothFallback(
        context: Context,
        action: String,
        adapter: BluetoothAdapter
    ): JsonObject {
        val opened = openFirstAvailable(
            context,
            listOf(
                Intent(ACTION_BLUETOOTH_PANEL),
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            )
        )

        return settingsResult(
            "bluetooth",
            action,
            opened,
            readBluetoothState(adapter)
        )
    }

    // ---------------------------------------------------------
    // Wi-Fi
    // ---------------------------------------------------------

    fun executeWifiControl(
        call: FunctionCall,
        context: Context
    ): JsonObject {
        val action = parseToggleAction(jsonStringArg(call, "action"))
            ?: return toggleActionError()

        val appContext = context.applicationContext

        val wifiManager = appContext.getSystemService(
            Context.WIFI_SERVICE
        ) as? WifiManager
            ?: return error("Wi-Fi service is unavailable.")

        if (action == "check_status") {
            val enabled = readWifiState(wifiManager)
                ?: return error("Unable to read the current Wi-Fi state.")

            return toggleResult(
                "wifi",
                action,
                enabled,
                "Wi-Fi is ${if (enabled) "on" else "off"}."
            )
        }

        // Android 10+ does not allow ordinary apps to change
        // Wi-Fi state through WifiManager.setWifiEnabled().
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return openWifiFallback(appContext, action, wifiManager)
        }

        return try {
            @Suppress("DEPRECATION")
            val accepted = wifiManager.isWifiEnabled.let {
                wifiManager.setWifiEnabled(action == "turn_on")
            }

            if (accepted) {
                changeRequestedResult(
                    "wifi",
                    action,
                    readWifiState(wifiManager)
                )
            } else {
                openWifiFallback(appContext, action, wifiManager)
            }
        } catch (e: SecurityException) {
            error("Permission denied while changing Wi-Fi state.")
        } catch (e: Exception) {
            error("Wi-Fi operation failed: ${e.message ?: "unknown error"}")
        }
    }

    private fun readWifiState(
        manager: WifiManager
    ): Boolean? = try {
        @Suppress("DEPRECATION")
        manager.isWifiEnabled
    } catch (_: Exception) {
        null
    }

    private fun openWifiFallback(
        context: Context,
        action: String,
        manager: WifiManager
    ): JsonObject {
        return settingsResult(
            "wifi",
            action,
            openFirstAvailable(
                context,
                listOf(
                    Intent(Settings.Panel.ACTION_WIFI),
                    Intent(Settings.ACTION_WIFI_SETTINGS)
                )
            ),
            readWifiState(manager)
        )
    }

    // ---------------------------------------------------------
    // Settings navigation
    // ---------------------------------------------------------

    private fun openFirstAvailable(
        context: Context,
        intents: List<Intent>
    ): Boolean {
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // Try the next supported settings screen.
            }
        }

        return false
    }

    private fun settingsResult(
        feature: String,
        action: String,
        opened: Boolean,
        enabled: Boolean?
    ): JsonObject = buildJsonObject {
        put("success", opened)
        put("feature", feature)
        put("action", action)
        put("status", if (opened) {
            "awaiting_user_confirmation"
        } else {
            "failed"
        })
        put("panel_opened", opened)

        if (enabled != null) {
            put("enabled", enabled)
        }

        put(
            "message",
            if (opened) {
                "Opened system settings. The user must confirm the requested change."
            } else {
                "Could not open the system settings screen."
            }
        )
    }

    // ---------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------

    internal fun parseToggleAction(raw: String?): String? {
        val action = raw?.trim()?.lowercase(Locale.ROOT)
            ?: return null

        return when (action) {
            "turn_on", "on", "enable" -> "turn_on"
            "turn_off", "off", "disable" -> "turn_off"
            "check_status", "status", "check" -> "check_status"
            else -> null
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
        put("status", "confirmed")
        put("enabled", enabled)
        put("message", message)
    }

    private fun changeRequestedResult(
        feature: String,
        action: String,
        currentState: Boolean?
    ): JsonObject = buildJsonObject {
        put("success", true)
        put("feature", feature)
        put("action", action)
        put("status", "change_requested")

        if (currentState != null) {
            put("enabled", currentState)
        }

        put(
            "requested_enabled",
            action == "turn_on"
        )

        put(
            "message",
            "The system accepted the request. The final state has not yet been confirmed."
        )
    }

    private fun toggleActionError() = error(
        "Invalid action. Allowed values: turn_on, turn_off, check_status."
    )

    private fun error(message: String): JsonObject =
        buildJsonObject {
            put("success", false)
            put("status", "failed")
            put("error", message)
        }
}
