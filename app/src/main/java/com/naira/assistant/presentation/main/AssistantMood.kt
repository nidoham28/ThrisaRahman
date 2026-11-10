package com.naira.assistant.presentation.main

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal data class AssistantResponse(
    val response: String,
    val mood: String?,
)

internal val assistantMoods = listOf(
    "Admiring",
    "Afraid",
    "Affectionate",
    "Agitated",
    "Amused",
    "Angry",
    "Annoyed",
    "Anxious",
    "Apologetic",
    "Ashamed",
    "AweStruck",
    "Bashful",
    "Bored",
    "Brave",
    "Calm",
    "Caring",
    "Cheerful",
    "Cold",
    "Confident",
    "Confused",
    "Concerned",
    "Content",
    "Curious",
    "Crying",
    "Delighted",
    "Determined",
    "Disappointed",
    "Disgusted",
    "Distracted",
    "Drowsy",
    "Embarrassed",
    "Empathetic",
    "Encouraging",
    "Energetic",
    "Envious",
    "Excited",
    "Exhausted",
    "Fearful",
    "Flirty",
    "Focused",
    "Fond",
    "Frustrated",
    "Furious",
    "Giddy",
    "Grateful",
    "Guilty",
    "Happy",
    "Heartbroken",
    "Homesick",
    "Hopeful",
    "Horrified",
    "Hungry",
    "Hurt",
    "Inspired",
    "Irritated",
    "Jealous",
    "Joyful",
    "Kind",
    "Lonely",
    "Loved",
    "Loving",
    "Melancholy",
    "Mischievous",
    "Motivated",
    "Nauseated",
    "Naughty",
    "Nervous",
    "Neutral",
    "Nostalgic",
    "Optimistic",
    "Overwhelmed",
    "Pain",
    "Peaceful",
    "Playful",
    "Pleased",
    "Proud",
    "Protective",
    "Queasy",
    "Relieved",
    "Remorseful",
    "Restless",
    "Romantic",
    "Sad",
    "Scared",
    "Secure",
    "Sensitive",
    "Shy",
    "Sick",
    "Sleepy",
    "Sore",
    "Stressed",
    "Surprised",
    "Tender",
    "Tense",
    "Thankful",
    "Thoughtful",
    "Tired",
    "Uncomfortable",
    "Unwell",
    "Upset",
    "Vulnerable",
    "Warm",
    "Wary",
    "Wistful",
    "Worried",
    "Yearning",
)

private val responseJson = Json

internal fun decodeAssistantResponse(raw: String, isFinal: Boolean): AssistantResponse? {
    val trimmedRaw = raw.trim()
    val normalizedRaw = when {
        trimmedRaw.startsWith("```json", ignoreCase = true) -> trimmedRaw.drop(7)
        trimmedRaw.startsWith("```") -> trimmedRaw.drop(3)
        else -> trimmedRaw
    }.removeSuffix("```").trim()
    val parsed = runCatching { responseJson.parseToJsonElement(normalizedRaw) }.getOrNull()
    if (parsed is JsonObject) {
        val response = parsed.stringValue("response")
        val mood = parsed.stringValue("mood")
        if (response != null) {
            val canonicalMood = mood?.let { outputMood ->
                assistantMoods.firstOrNull { it.equals(outputMood.trim(), ignoreCase = true) }
            }
            return AssistantResponse(response, canonicalMood ?: "Neutral")
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
    "Admiring" -> "Looking up warmly"
    "Afraid" -> "Feeling afraid"
    "Affectionate" -> "Feeling warmly close"
    "Agitated" -> "Feeling unsettled"
    "Amused" -> "Finding it funny"
    "Angry" -> "Strong frustration"
    "Annoyed" -> "A little irritated"
    "Anxious" -> "A little worried"
    "Apologetic" -> "Feeling sorry"
    "Ashamed" -> "Feeling ashamed"
    "AweStruck" -> "Filled with wonder"
    "Bashful" -> "Feeling bashful"
    "Bored" -> "Seeking something new"
    "Brave" -> "Feeling courageous"
    "Calm" -> "Peaceful and steady"
    "Caring" -> "Showing gentle care"
    "Cheerful" -> "Bright and cheerful"
    "Cold" -> "Feeling chilly"
    "Confident" -> "Feeling self-assured"
    "Confused" -> "Trying to understand"
    "Concerned" -> "Gently concerned"
    "Content" -> "Quietly satisfied"
    "Curious" -> "Wondering about it"
    "Crying" -> "Feeling tearful"
    "Delighted" -> "Pleasantly delighted"
    "Determined" -> "Ready to persist"
    "Disappointed" -> "Feeling let down"
    "Disgusted" -> "Feeling repulsed"
    "Distracted" -> "Mind elsewhere"
    "Drowsy" -> "Feeling drowsy"
    "Embarrassed" -> "Feeling sheepish"
    "Empathetic" -> "Sharing the feeling"
    "Encouraging" -> "Offering encouragement"
    "Energetic" -> "Full of energy"
    "Envious" -> "Feeling envious"
    "Excited" -> "Full of excitement"
    "Exhausted" -> "Completely worn out"
    "Fearful" -> "Feeling fearful"
    "Flirty" -> "Playfully flirtatious"
    "Focused" -> "Paying close attention"
    "Fond" -> "Feeling fond"
    "Frustrated" -> "Feeling stuck"
    "Furious" -> "Fiercely angry"
    "Giddy" -> "Light and giddy"
    "Grateful" -> "Feeling appreciative"
    "Guilty" -> "Feeling guilty"
    "Happy" -> "Bright and joyful"
    "Heartbroken" -> "Deeply heartbroken"
    "Homesick" -> "Missing home"
    "Hopeful" -> "Looking ahead"
    "Horrified" -> "Filled with horror"
    "Hungry" -> "Feeling hungry"
    "Hurt" -> "Feeling hurt"
    "Inspired" -> "Feeling inspired"
    "Irritated" -> "Feeling irritated"
    "Jealous" -> "Feeling jealous"
    "Joyful" -> "Full of joy"
    "Kind" -> "Feeling kind"
    "Lonely" -> "Wanting connection"
    "Loved" -> "Feeling cared for"
    "Loving" -> "Feeling loving"
    "Melancholy" -> "Quietly melancholy"
    "Mischievous" -> "Up to mischief"
    "Motivated" -> "Ready to act"
    "Nauseated" -> "Feeling nauseous"
    "Naughty" -> "Feeling mischievous"
    "Nervous" -> "A little nervous"
    "Neutral" -> "Even keel"
    "Nostalgic" -> "Fondly remembering"
    "Optimistic" -> "Expecting good things"
    "Overwhelmed" -> "Feeling overwhelmed"
    "Pain" -> "In physical pain"
    "Peaceful" -> "At ease"
    "Playful" -> "Feeling playful"
    "Pleased" -> "Quietly pleased"
    "Proud" -> "Pleased with progress"
    "Protective" -> "Wanting to protect"
    "Queasy" -> "Feeling queasy"
    "Relieved" -> "Feeling relieved"
    "Remorseful" -> "Feeling remorseful"
    "Restless" -> "Unable to settle"
    "Romantic" -> "Feeling romantic"
    "Sad" -> "Feeling blue"
    "Scared" -> "Feeling scared"
    "Secure" -> "Feeling safe"
    "Sensitive" -> "Feeling sensitive"
    "Shy" -> "A little bashful"
    "Sick" -> "Feeling unwell"
    "Sleepy" -> "Ready for sleep"
    "Sore" -> "Feeling sore"
    "Stressed" -> "Under some pressure"
    "Surprised" -> "Caught by surprise"
    "Tender" -> "Soft and caring"
    "Tense" -> "Feeling tense"
    "Thankful" -> "Feeling thankful"
    "Thoughtful" -> "Thinking it through"
    "Tired" -> "Feeling tired"
    "Uncomfortable" -> "Feeling uncomfortable"
    "Unwell" -> "Not feeling well"
    "Upset" -> "Feeling upset"
    "Vulnerable" -> "Feeling vulnerable"
    "Warm" -> "Feeling warm"
    "Wary" -> "Staying cautious"
    "Wistful" -> "Quietly longing"
    "Worried" -> "Feeling concerned"
    "Yearning" -> "Longing for something"
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
