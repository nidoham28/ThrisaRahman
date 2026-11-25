package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun randomNumberTool() = ToolDefinition(
    name = "random_number",
    description = universalToolDescription(
        purpose = "Generates one uniformly random integer within the inclusive [min, max] range using the device's cryptographically secure RNG.",
        trigger = "The user asks for a random number, a dice roll, a lottery pick, or any pick inside a numeric range.",
        notFor = "Never invent or mentally pick a random number yourself; do not use this to choose between non-numeric options (use random_boolean for yes/no).",
        prerequisite = "None. min and max are optional and default to 1 and 100.",
    ),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("min") {
                put("type", "integer")
                put("description", "Lower bound of the range, inclusive. Default: 1.")
                put("default", 1)
            }
            putJsonObject("max") {
                put("type", "integer")
                put("description", "Upper bound of the range, inclusive. Default: 100.")
                put("default", 100)
            }
        }
        putJsonArray("required") {}
        put("additionalProperties", false)
    },
    parallelizable = true,
)

fun randomBooleanTool() = ToolDefinition(
    name = "random_boolean",
    description = universalToolDescription(
        purpose = "Flips a fair coin with the device's cryptographically secure RNG and returns true (yes/heads) or false (no/tails).",
        trigger = "The user asks for a coin flip, a yes/no decision, or makes a true/false or heads/tails guess.",
        notFor = "Never pick the outcome yourself; do not use this for numeric ranges (use random_number).",
        prerequisite = "Optional `guess`: set it only when the user made an explicit true/false or heads/tails guess.",
    ),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("guess") {
                put("type", "boolean")
                put("description", "The user's guess, if any: true for yes/heads, false for no/tails.")
            }
        }
        putJsonArray("required") {}
        put("additionalProperties", false)
    },
    parallelizable = true,
)

fun randomPictureTool() = ToolDefinition(
    name = "random_picture",
    description = universalToolDescription(
        purpose = "Picks one uniformly random photo from the device's local picture library and attaches it to the conversation.",
        trigger = "The user asks to see a random picture or photo, or asks Naira to surprise them with one of their photos.",
        notFor = "Never invent, imagine, or describe a photo before this tool returns; do not use this to generate or edit images.",
        prerequisite = "Photos/media permission must be granted in Settings → Permissions, and the device must have at least one saved photo.",
    ),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
        putJsonArray("required") {}
        put("additionalProperties", false)
    },
)
