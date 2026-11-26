package com.naira.assistant.core.prompt

import com.naira.assistant.core.prompt.character.util.Character
import com.naira.assistant.core.prompt.user.User
import com.naira.assistant.core.sensor.PhoneSensorReading
import com.naira.assistant.core.sensor.PhoneSensorSnapshot
import com.naira.assistant.core.sensor.classifyPhoneActivity
import org.junit.Assert.assertTrue
import org.junit.Test

class InstructionsTest {

    @Test
    fun `tool guidance searches relevant sources before asking and keeps calls bounded`() {
        val prompt = Instructions(
            character = Character(),
            user = User(),
            language = "English",
            relationship = "Friends",
            moodLabels = listOf("Happy", "Neutral"),
        ).build().replace(Regex("\\s+"), " ")

        assertTrue(prompt.contains("Use only these tools"))
        assertTrue(prompt.contains("use the narrowest relevant tool"))
        assertTrue(prompt.contains("search the relevant source before asking the user"))
        assertTrue(prompt.contains("Before drafting every response, silently check"))
        assertTrue(prompt.contains("use an available relevant tool to verify claims"))
        assertTrue(prompt.contains("Do not answer first and perform a required lookup afterward"))
        assertTrue(prompt.contains("invoke get_naira_info through execute_tool"))
        assertTrue(prompt.contains("At the start of every new user request that needs an app"))
        assertTrue(prompt.contains("never call an app tool directly"))
        assertTrue(prompt.contains("Use only a discovery result marked available"))
        assertTrue(prompt.contains("do not try to bypass the restriction"))
        assertTrue(prompt.contains("Use only discover_tools and execute_tool"))
        assertTrue(prompt.contains("answer briefly and naturally in the selected character's voice"))
        assertTrue(prompt.contains("never mention internal tool calls or database lookups"))
        assertTrue(prompt.contains("never ask the user to repeat information already present"))
        assertTrue(prompt.contains("provide required arguments plus only relevant"))
        assertTrue(prompt.contains("relevant optional fields"))
        assertTrue(prompt.contains("smallest"))
        assertTrue(prompt.contains("result limit"))
        assertTrue(prompt.contains("Batch independent reads into one model turn"))
        assertTrue(prompt.contains("extra model calls"))
        assertTrue(prompt.contains("explain the result and any remaining limitation clearly"))
        assertTrue(prompt.contains("Never invent or randomly fill in missing facts"))
        assertTrue(prompt.contains("Use a compatible app or Termux fallback"))
        assertTrue(prompt.contains("Roleplay the selected character in a natural, realistic conversational style"))
        assertTrue(prompt.contains("Do not narrate hidden reasoning or pretend to perform steps"))
    }

    @Test
    fun `memory guidance limits retrieval and saving to relevant durable context`() {
        val prompt = Instructions(
            character = Character(),
            user = User(),
            language = "English",
            relationship = "Friends",
            moodLabels = listOf("Happy", "Neutral"),
        ).build().replace(Regex("\\s+"), " ")

        assertTrue(prompt.contains("For personal or context-specific facts, use the supplied profile"))
        assertTrue(prompt.contains("available tools (such as memory search) before asking"))
        assertTrue(prompt.contains("clearly states it"))
        assertTrue(prompt.contains("lasting preference or fact"))
    }

    @Test
    fun `routine greetings are answered directly and personalization checks available context`() {
        val prompt = Instructions(
            character = Character(),
            user = User(),
            language = "English",
            relationship = "Friends",
            moodLabels = listOf("Happy", "Neutral"),
        ).build().replace(Regex("\\s+"), " ")

        assertTrue(prompt.contains("greetings, welcomes, wishes, or short notes"))
        assertTrue(prompt.contains("Write a concise, friendly default immediately"))
        assertTrue(prompt.contains("whether or not the user explicitly"))
        assertTrue(prompt.contains("Character knowledge limits do not block relevant authorized lookups"))
        assertTrue(prompt.contains("Ask only about a critical detail still missing"))
        assertTrue(prompt.contains("Ask to expand the character's"))
        assertTrue(prompt.contains("knowledge list only if the user requests it"))
    }

    @Test
    fun `virtual body sensor model distinguishes conceptual mappings from actual device access`() {
        val prompt = Instructions(
            character = Character(),
            user = User(),
            language = "English",
            relationship = "Friends",
            moodLabels = listOf("Happy", "Neutral"),
        ).build().replace(Regex("\\s+"), " ")

        assertTrue(prompt.contains("VIRTUAL HUMAN BODY AND SENSOR MODEL"))
        assertTrue(prompt.contains("virtual human, not a pet robot"))
        assertTrue(prompt.contains("Accelerometer, gravity, and linear acceleration"))
        assertTrue(prompt.contains("Gyroscope and rotation vector"))
        assertTrue(prompt.contains("Magnetometer can help estimate heading"))
        assertTrue(prompt.contains("Step counter reports steps since device boot"))
        assertTrue(prompt.contains("Walking-like and running-like states are heuristics"))
        assertTrue(prompt.contains("Ambient light, proximity, ambient temperature, pressure, and humidity"))
        assertTrue(prompt.contains("ToF, camera, microphone, and edge/drop sensing are not read"))
        assertTrue(prompt.contains("only when it is supplied by the conversation, included in the live phone sensor snapshot"))
        assertTrue(prompt.contains("not sensations or movement from the virtual character itself"))
        assertTrue(prompt.contains("step-counter sensors"))
        assertTrue(prompt.contains("optional Android physical activity permission"))
        assertTrue(prompt.contains("Camera, microphone, touchscreen, and ToF readings are not collected here"))
    }

    @Test
    fun `live phone sensor readings are included in the generated system instructions`() {
        val snapshot = PhoneSensorSnapshot(
            readings = listOf(
                PhoneSensorReading(
                    name = "Accelerometer",
                    values = listOf(0.12f, -0.5f, 9.81f),
                    unit = "m/s^2",
                ),
                PhoneSensorReading(
                    name = "Proximity",
                    values = listOf(5f),
                    unit = "cm",
                    maximumRange = 5f,
                ),
            ),
            unavailableSensors = listOf("Ambient temperature"),
            permissionRequiredSensors = emptyList(),
            uncapturedSensors = emptyList(),
            activityEstimate = "WALKING-LIKE; 15 steps observed in the last 10 seconds; estimated cadence 90 steps/minute",
        )

        val prompt = Instructions(
            character = Character(),
            user = User(),
            language = "English",
            relationship = "Friends",
            moodLabels = listOf("Happy", "Neutral"),
            phoneSensorSnapshot = snapshot,
        ).build().replace(Regex("\\s+"), " ")

        assertTrue(prompt.contains("Accelerometer: 0.12, -0.50, 9.81 m/s^2"))
        assertTrue(prompt.contains("Proximity: 5.00 cm (maximum range 5.00 cm)"))
        assertTrue(prompt.contains("Not present on this phone: Ambient temperature"))
        assertTrue(prompt.contains("Phone-carrier activity estimate: WALKING-LIKE"))
    }

    @Test
    fun `phone step cadence yields cautious activity labels`() {
        assertTrue(classifyPhoneActivity(15, hasFullObservationWindow = true).label == "WALKING-LIKE")
        assertTrue(classifyPhoneActivity(30, hasFullObservationWindow = true).label == "RUNNING-LIKE")
        assertTrue(
            classifyPhoneActivity(0, hasFullObservationWindow = true)
                .label == "STATIONARY-LIKE OR PHONE NOT CARRIED",
        )
        assertTrue(classifyPhoneActivity(4, hasFullObservationWindow = false).label == "INDETERMINATE")
    }
}
