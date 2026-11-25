package com.naira.assistant.core.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.ArrayDeque
import java.util.Locale

data class PhoneSensorReading(
    val name: String,
    val values: List<Float>,
    val unit: String,
    val maximumRange: Float? = null,
    val precisionDigits: Int = 2,
)

data class PhoneSensorSnapshot(
    val readings: List<PhoneSensorReading>,
    val unavailableSensors: List<String>,
    val permissionRequiredSensors: List<String>,
    val uncapturedSensors: List<String>,
    val activityEstimate: String,
) {
    fun toPromptContext(): String = buildString {
        if (readings.isEmpty()) {
            appendLine("- No live sensor readings were captured for this request.")
        } else {
            appendLine("Live readings sampled from this phone for this request:")
            readings.forEach { reading ->
                val values = reading.values.joinToString(", ") { value ->
                    String.format(Locale.US, "%.${reading.precisionDigits}f", value)
                }
                append("- ${reading.name}: $values ${reading.unit}")
                reading.maximumRange?.let { maximumRange ->
                    append(" (maximum range ${String.format(Locale.US, "%.2f", maximumRange)} ${reading.unit})")
                }
                appendLine()
            }
        }
        if (unavailableSensors.isNotEmpty()) {
            appendLine("Not present on this phone: ${unavailableSensors.joinToString()}.")
        }
        if (permissionRequiredSensors.isNotEmpty()) {
            appendLine(
                "Not read because physical activity permission is not granted: " +
                    "${permissionRequiredSensors.joinToString()}.",
            )
        }
        if (uncapturedSensors.isNotEmpty()) {
            appendLine("Present but no reading was captured: ${uncapturedSensors.joinToString()}.")
        }
        appendLine("Phone-carrier activity estimate: $activityEstimate")
        append("Treat walking/running labels as rough cadence cues; the phone may not be carried by the user.")
    }.trim()
}

data class PhoneActivityEstimate(
    val label: String,
    val stepsInWindow: Int,
    val cadenceStepsPerMinute: Int?,
) {
    fun describe(): String = buildString {
        append(label)
        append("; $stepsInWindow steps observed in the last ${STEP_WINDOW_MILLIS / 1_000} seconds")
        cadenceStepsPerMinute?.let { append("; estimated cadence $it steps/minute") }
    }

    private companion object {
        const val STEP_WINDOW_MILLIS = 10_000L
    }
}

internal fun classifyPhoneActivity(
    stepsInWindow: Int,
    hasFullObservationWindow: Boolean,
): PhoneActivityEstimate {
    if (!hasFullObservationWindow) {
        return PhoneActivityEstimate("INDETERMINATE", stepsInWindow, null)
    }

    val cadence = stepsInWindow * 60_000 / 10_000
    val label = when {
        stepsInWindow == 0 -> "STATIONARY-LIKE OR PHONE NOT CARRIED"
        cadence >= 150 -> "RUNNING-LIKE"
        cadence >= 40 -> "WALKING-LIKE"
        else -> "LIGHT OR IRREGULAR MOVEMENT"
    }
    return PhoneActivityEstimate(label, stepsInWindow, cadence)
}

class PhoneSensorSnapshotReader(context: Context) {
    private val appContext = context.applicationContext
    private val sensorManager = requireNotNull(
        appContext.getSystemService(SensorManager::class.java),
    ) { "Android SensorManager is unavailable." }
    private val recentStepTimes = ArrayDeque<Long>()
    private val registeredStepSensors = mutableListOf<Sensor>()
    private var stepTrackingStartedAtMillis = 0L
    private var stepDetectorAvailable = false
    private var stepTrackingActive = false
    private var stepCounterAvailable = false
    private var stepCounterTrackingActive = false
    private var stepsSinceBoot: Long? = null

    private val stepDetectorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_STEP_DETECTOR -> {
                    val eventTimeMillis = event.timestamp / NANOS_PER_MILLI
                    recentStepTimes.addLast(eventTimeMillis)
                    discardOldSteps(eventTimeMillis)
                }
                Sensor.TYPE_STEP_COUNTER -> stepsSinceBoot = event.values.firstOrNull()?.toLong()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun startTracking() {
        stopTracking()
        stepTrackingStartedAtMillis = SystemClock.elapsedRealtime()
        if (!hasActivityRecognitionPermission()) return

        val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        val stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetectorAvailable = stepDetector != null
        stepCounterAvailable = stepCounter != null

        listOfNotNull(stepDetector, stepCounter).forEach { sensor ->
            if (
                sensorManager.registerListener(
                    stepDetectorListener,
                    sensor,
                    SensorManager.SENSOR_DELAY_NORMAL,
                )
            ) {
                registeredStepSensors += sensor
            }
        }
        stepTrackingActive = stepDetector != null && registeredStepSensors.contains(stepDetector)
        stepCounterTrackingActive = stepCounter != null && registeredStepSensors.contains(stepCounter)
    }

    fun stopTracking() {
        if (registeredStepSensors.isNotEmpty()) sensorManager.unregisterListener(stepDetectorListener)
        registeredStepSensors.clear()
        stepTrackingActive = false
        stepDetectorAvailable = false
        stepCounterAvailable = false
        stepCounterTrackingActive = false
        stepsSinceBoot = null
        stepTrackingStartedAtMillis = 0L
        recentStepTimes.clear()
    }

    suspend fun capture(): PhoneSensorSnapshot = withContext(Dispatchers.Main.immediate) {
        val activityPermissionGranted = hasActivityRecognitionPermission()
        val available = mutableListOf<Pair<SensorSpec, Sensor>>()
        val unavailable = mutableListOf<String>()
        val permissionRequired = mutableListOf<String>()

        SENSOR_SPECS.forEach { spec ->
            val sensor = sensorManager.getDefaultSensor(spec.type)
            if (sensor == null) unavailable += spec.name else available += spec to sensor
        }

        val detectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        when {
            detectorSensor == null -> unavailable += "Step detector"
            !activityPermissionGranted -> permissionRequired += "Step detector"
        }
        val counterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        when {
            counterSensor == null -> unavailable += "Step counter"
            !activityPermissionGranted -> permissionRequired += "Step counter"
        }

        val pendingTypes = available.mapTo(mutableSetOf()) { (spec, _) -> spec.type }
        val readings = mutableMapOf<Int, PhoneSensorReading>()
        val uncaptured = mutableListOf<String>()
        val completed = CompletableDeferred<Unit>()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val spec = SENSOR_SPECS_BY_TYPE[event.sensor.type] ?: return
                if (!pendingTypes.remove(event.sensor.type)) return

                readings[event.sensor.type] = PhoneSensorReading(
                    name = spec.name,
                    values = event.values.toList(),
                    unit = spec.unit,
                    maximumRange = event.sensor.maximumRange.takeIf {
                        event.sensor.type == Sensor.TYPE_PROXIMITY
                    },
                    precisionDigits = spec.precisionDigits,
                )
                if (pendingTypes.isEmpty()) completed.complete(Unit)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        try {
            available.forEach { (spec, sensor) ->
                if (!sensorManager.registerListener(
                        listener,
                        sensor,
                        SensorManager.SENSOR_DELAY_NORMAL,
                    )
                ) {
                    pendingTypes.remove(spec.type)
                    uncaptured += spec.name
                }
            }
            if (pendingTypes.isNotEmpty()) {
                withTimeoutOrNull(CAPTURE_TIMEOUT_MILLIS) { completed.await() }
                uncaptured += pendingTypes.mapNotNull(SENSOR_SPECS_BY_TYPE::get).map(SensorSpec::name)
            }
        } finally {
            sensorManager.unregisterListener(listener)
        }

        if (activityPermissionGranted && stepCounterAvailable) {
            val totalSteps = stepsSinceBoot
            if (!stepCounterTrackingActive) {
                uncaptured += "Step counter (sensor registration failed)"
            } else if (totalSteps == null) {
                uncaptured += "Step counter (waiting for first reading)"
            } else {
                readings[Sensor.TYPE_STEP_COUNTER] = PhoneSensorReading(
                    name = "Steps since device boot",
                    values = listOf(totalSteps.toFloat()),
                    unit = "steps",
                    precisionDigits = 0,
                )
            }
        }

        val activityEstimate = if (!activityPermissionGranted) {
            "UNAVAILABLE; grant the optional Android physical activity permission to enable step sensing."
        } else if (!stepDetectorAvailable || !stepTrackingActive) {
            "UNAVAILABLE; this phone's step detector is absent or not active."
        } else {
            val now = SystemClock.elapsedRealtime()
            discardOldSteps(now)
            val hasFullWindow =
                stepTrackingStartedAtMillis > 0L &&
                    now - stepTrackingStartedAtMillis >= STEP_WINDOW_MILLIS
            classifyPhoneActivity(
                stepsInWindow = recentStepTimes.size,
                hasFullObservationWindow = hasFullWindow,
            ).describe()
        }

        PhoneSensorSnapshot(
            readings = SENSOR_SPECS.mapNotNull { spec -> readings[spec.type] } +
                listOfNotNull(readings[Sensor.TYPE_STEP_COUNTER]),
            unavailableSensors = unavailable,
            permissionRequiredSensors = permissionRequired,
            uncapturedSensors = uncaptured,
            activityEstimate = activityEstimate,
        )
    }

    private fun discardOldSteps(nowMillis: Long) {
        val oldestAllowed = nowMillis - STEP_WINDOW_MILLIS
        while (true) {
            val firstStepTime = recentStepTimes.peekFirst() ?: break
            if (firstStepTime >= oldestAllowed) break
            recentStepTimes.removeFirst()
        }
    }

    private fun hasActivityRecognitionPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACTIVITY_RECOGNITION,
            ) == PackageManager.PERMISSION_GRANTED

    private data class SensorSpec(
        val type: Int,
        val name: String,
        val unit: String,
        val precisionDigits: Int = 2,
    )

    private companion object {
        const val CAPTURE_TIMEOUT_MILLIS = 1_000L
        const val STEP_WINDOW_MILLIS = 10_000L
        const val NANOS_PER_MILLI = 1_000_000L

        val SENSOR_SPECS = listOf(
            SensorSpec(Sensor.TYPE_ACCELEROMETER, "Accelerometer", "m/s^2"),
            SensorSpec(Sensor.TYPE_LINEAR_ACCELERATION, "Linear acceleration", "m/s^2"),
            SensorSpec(Sensor.TYPE_GRAVITY, "Gravity", "m/s^2"),
            SensorSpec(Sensor.TYPE_GYROSCOPE, "Gyroscope", "rad/s"),
            SensorSpec(Sensor.TYPE_MAGNETIC_FIELD, "Magnetometer", "uT"),
            SensorSpec(Sensor.TYPE_ROTATION_VECTOR, "Rotation vector", "unitless"),
            SensorSpec(Sensor.TYPE_LIGHT, "Ambient light", "lux"),
            SensorSpec(Sensor.TYPE_PROXIMITY, "Proximity", "cm"),
            SensorSpec(Sensor.TYPE_AMBIENT_TEMPERATURE, "Ambient temperature", "C"),
            SensorSpec(Sensor.TYPE_PRESSURE, "Air pressure", "hPa"),
            SensorSpec(Sensor.TYPE_RELATIVE_HUMIDITY, "Relative humidity", "%"),
        )
        val SENSOR_SPECS_BY_TYPE = SENSOR_SPECS.associateBy(SensorSpec::type)
    }
}
