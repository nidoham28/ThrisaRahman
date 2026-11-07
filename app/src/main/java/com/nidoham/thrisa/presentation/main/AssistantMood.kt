package com.nidoham.thrisa.presentation.main

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal data class AssistantResponse(
    val response: String,
    val mood: String?,
)

internal val assistantMoods = listOf(
    "Happy",
    "Sad",
    "Angry",
    "Calm",
    "Excited",
    "Anxious",
    "Neutral",
    "Crying",
)

private val responseJson = Json

internal fun decodeAssistantResponse(raw: String, isFinal: Boolean): AssistantResponse? {
    val parsed = runCatching { responseJson.parseToJsonElement(raw) }.getOrNull()
    if (parsed is JsonObject) {
        val response = parsed.stringValue("response")
        val mood = parsed.stringValue("mood")
        if (parsed.keys == setOf("response", "mood") && response != null && mood != null) {
            val canonicalMood = assistantMoods.firstOrNull { it.equals(mood, ignoreCase = true) }
            if (canonicalMood != null) return AssistantResponse(response, canonicalMood)
        }
    }

    if (isFinal) {
        throw IllegalStateException(
            "The assistant returned an invalid response format. Please try again."
        )
    }
    return extractPartialResponse(raw)?.let { AssistantResponse(it, mood = null) }
}

internal fun mostFrequentAssistantMood(moods: List<String>): String? {
    if (moods.isEmpty()) return null
    val counts = moods.groupingBy { it }.eachCount()
    val highestCount = counts.values.maxOrNull() ?: return null
    return moods.lastOrNull { counts[it] == highestCount }
}

internal fun assistantMoodCaption(mood: String?): String = when (mood) {
    "Happy" -> "Bright and joyful"
    "Sad" -> "Feeling blue"
    "Angry" -> "Strong frustration"
    "Calm" -> "Peaceful and steady"
    "Excited" -> "Full of energy"
    "Anxious" -> "A little worried"
    "Neutral" -> "Even keel"
    "Crying" -> "Needs comfort"
    else -> "No recent mood"
}

private fun JsonObject.stringValue(key: String): String? =
    (this[key] as? JsonPrimitive)
        ?.takeIf(JsonPrimitive::isString)
        ?.contentOrNull

private fun extractPartialResponse(raw: String): String? {
    val field = Regex("\"response\"\\s*:\\s*\"").find(raw) ?: return null
    val result = StringBuilder()
    var index = field.range.last + 1
    while (index < raw.length) {
        val char = raw[index++]
        when {
            char == '"' -> return result.toString()
            char != '\\' -> result.append(char)
            index >= raw.length -> return result.toString()
            else -> {
                when (val escaped = raw[index++]) {
                    '"', '\\', '/' -> result.append(escaped)
                    'b' -> result.append('\b')
                    'f' -> result.append('\u000C')
                    'n' -> result.append('\n')
                    'r' -> result.append('\r')
                    't' -> result.append('\t')
                    'u' -> {
                        if (index + 4 > raw.length) return result.toString()
                        val codePoint = raw.substring(index, index + 4).toIntOrNull(16)
                            ?: return result.toString()
                        result.append(codePoint.toChar())
                        index += 4
                    }
                    else -> return result.toString()
                }
            }
        }
    }
    return result.toString()
}
