package com.naira.assistant.presentation.main

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/* ------------------------------------------------------------------ */
/*  Model                                                              */
/* ------------------------------------------------------------------ */

internal data class AssistantResponse(
    val response: String,
    val mood: String?,
)

/* ------------------------------------------------------------------ */
/*  Mood catalogue                                                     */
/* ------------------------------------------------------------------ */

internal val assistantMoods = listOf(
    "Admiring", "Afraid", "Affectionate", "Agitated", "Amused", "Angry",
    "Annoyed", "Anxious", "Apologetic", "Ashamed", "AweStruck", "Bashful",
    "Bored", "Brave", "Calm", "Caring", "Cheerful", "Cold", "Confident",
    "Confused", "Concerned", "Content", "Curious", "Crying", "Delighted",
    "Determined", "Disappointed", "Disgusted", "Distracted", "Drowsy",
    "Embarrassed", "Empathetic", "Encouraging", "Energetic", "Envious",
    "Excited", "Exhausted", "Fearful", "Flirty", "Focused", "Fond",
    "Frustrated", "Furious", "Giddy", "Grateful", "Guilty", "Happy",
    "Heartbroken", "Homesick", "Hopeful", "Horrified", "Hungry", "Hurt",
    "Inspired", "Irritated", "Jealous", "Joyful", "Kind", "Lonely",
    "Loved", "Loving", "Melancholy", "Mischievous", "Motivated",
    "Nauseated", "Naughty", "Nervous", "Neutral", "Nostalgic",
    "Optimistic", "Overwhelmed", "Pain", "Peaceful", "Playful", "Pleased",
    "Proud", "Protective", "Queasy", "Relieved", "Remorseful", "Restless",
    "Romantic", "Sad", "Scared", "Secure", "Sensitive", "Shy", "Sick",
    "Sleepy", "Sore", "Stressed", "Surprised", "Tender", "Tense",
    "Thankful", "Thoughtful", "Tired", "Uncomfortable", "Unwell", "Upset",
    "Vulnerable", "Warm", "Wary", "Wistful", "Worried", "Yearning",
)

/** Case-insensitive lookup → canonical mood. Built once; O(1) per call. */
private val moodByLowercase: Map<String, String> =
    assistantMoods.associateBy { it.lowercase() }

/* ------------------------------------------------------------------ */
/*  Decoding                                                           */
/* ------------------------------------------------------------------ */

private val responseJson = Json.Default

/**
 * Decodes a model emission.
 *
 * @param raw the raw model output — may be partial while streaming.
 * @param isFinal `true` when this is the last emission of the turn.
 * @return the parsed [AssistantResponse], or `null` if [isFinal] is `false`
 *         and no response text could be recovered yet. Plain-text model output
 *         is accepted as a response without a mood.
 * @throws IllegalStateException if [isFinal] is `true` and the payload
 *         is a structured payload without a string response.
 */
@Throws(IllegalStateException::class)
internal fun decodeAssistantResponse(raw: String, isFinal: Boolean): AssistantResponse? {
    val normalizedRaw = normalize(raw)
    val parsed = runCatching { responseJson.parseToJsonElement(normalizedRaw) }.getOrNull()

    if (parsed is JsonObject) {
        val response = parsed.stringValue("response")
        if (response != null) {
            val mood = parsed.stringValue("mood")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { moodByLowercase[it.lowercase()] }
            return AssistantResponse(response, mood ?: "Neutral")
        }
    }

    val plainText = when (parsed) {
        is JsonPrimitive -> parsed.takeIf(JsonPrimitive::isString)?.contentOrNull
        null -> normalizedRaw.takeIf {
            it.isNotBlank() && !it.startsWith("{")
        }
        else -> null
    }
    if (plainText != null) return AssistantResponse(plainText, mood = null)

    if (isFinal) {
        throw IllegalStateException(
            "The assistant returned an invalid response format. Please try again."
        )
    }
    return extractPartialResponse(raw)?.let { AssistantResponse(it, mood = null) }
}

/**
 * Returns the most frequent mood in [moods], or `null` if empty.
 *
 * Tie-break: when two or more moods share the top count, the **most recent**
 * (i.e. latest in [moods]) wins. This favours the freshest signal over
 * "majority across the whole conversation".
 */
internal fun mostFrequentAssistantMood(moods: List<String>): String? {
    if (moods.isEmpty()) return null
    val counts = moods.groupingBy { it }.eachCount()
    val highest = counts.values.max()
    return moods.last { counts.getValue(it) == highest }
}

/* ------------------------------------------------------------------ */
/*  Mood captions                                                      */
/* ------------------------------------------------------------------ */

private val moodCaptions: Map<String, String> = mapOf(
    "Admiring" to "Looking up warmly",
    "Afraid" to "Feeling afraid",
    "Affectionate" to "Feeling warmly close",
    "Agitated" to "Feeling unsettled",
    "Amused" to "Finding it funny",
    "Angry" to "Strong frustration",
    "Annoyed" to "A little irritated",
    "Anxious" to "A little worried",
    "Apologetic" to "Feeling sorry",
    "Ashamed" to "Feeling ashamed",
    "AweStruck" to "Filled with wonder",
    "Bashful" to "Feeling bashful",
    "Bored" to "Seeking something new",
    "Brave" to "Feeling courageous",
    "Calm" to "Peaceful and steady",
    "Caring" to "Showing gentle care",
    "Cheerful" to "Bright and cheerful",
    "Cold" to "Feeling chilly",
    "Confident" to "Feeling self-assured",
    "Confused" to "Trying to understand",
    "Concerned" to "Gently concerned",
    "Content" to "Quietly satisfied",
    "Curious" to "Wondering about it",
    "Crying" to "Feeling tearful",
    "Delighted" to "Pleasantly delighted",
    "Determined" to "Ready to persist",
    "Disappointed" to "Feeling let down",
    "Disgusted" to "Feeling repulsed",
    "Distracted" to "Mind elsewhere",
    "Drowsy" to "Feeling drowsy",
    "Embarrassed" to "Feeling sheepish",
    "Empathetic" to "Sharing the feeling",
    "Encouraging" to "Offering encouragement",
    "Energetic" to "Full of energy",
    "Envious" to "Feeling envious",
    "Excited" to "Full of excitement",
    "Exhausted" to "Completely worn out",
    "Fearful" to "Feeling fearful",
    "Flirty" to "Playfully flirtatious",
    "Focused" to "Paying close attention",
    "Fond" to "Feeling fond",
    "Frustrated" to "Feeling stuck",
    "Furious" to "Fiercely angry",
    "Giddy" to "Light and giddy",
    "Grateful" to "Feeling appreciative",
    "Guilty" to "Feeling guilty",
    "Happy" to "Bright and joyful",
    "Heartbroken" to "Deeply heartbroken",
    "Homesick" to "Missing home",
    "Hopeful" to "Looking ahead",
    "Horrified" to "Filled with horror",
    "Hungry" to "Feeling hungry",
    "Hurt" to "Feeling hurt",
    "Inspired" to "Feeling inspired",
    "Irritated" to "Feeling irritated",
    "Jealous" to "Feeling jealous",
    "Joyful" to "Full of joy",
    "Kind" to "Feeling kind",
    "Lonely" to "Wanting connection",
    "Loved" to "Feeling cared for",
    "Loving" to "Feeling loving",
    "Melancholy" to "Quietly melancholy",
    "Mischievous" to "Up to mischief",
    "Motivated" to "Ready to act",
    "Nauseated" to "Feeling nauseous",
    "Naughty" to "Feeling mischievous",
    "Nervous" to "A little nervous",
    "Neutral" to "Even keel",
    "Nostalgic" to "Fondly remembering",
    "Optimistic" to "Expecting good things",
    "Overwhelmed" to "Feeling overwhelmed",
    "Pain" to "In physical pain",
    "Peaceful" to "At ease",
    "Playful" to "Feeling playful",
    "Pleased" to "Quietly pleased",
    "Proud" to "Pleased with progress",
    "Protective" to "Wanting to protect",
    "Queasy" to "Feeling queasy",
    "Relieved" to "Feeling relieved",
    "Remorseful" to "Feeling remorseful",
    "Restless" to "Unable to settle",
    "Romantic" to "Feeling romantic",
    "Sad" to "Feeling blue",
    "Scared" to "Feeling scared",
    "Secure" to "Feeling safe",
    "Sensitive" to "Feeling sensitive",
    "Shy" to "A little bashful",
    "Sick" to "Feeling unwell",
    "Sleepy" to "Ready for sleep",
    "Sore" to "Feeling sore",
    "Stressed" to "Under some pressure",
    "Surprised" to "Caught by surprise",
    "Tender" to "Soft and caring",
    "Tense" to "Feeling tense",
    "Thankful" to "Feeling thankful",
    "Thoughtful" to "Thinking it through",
    "Tired" to "Feeling tired",
    "Uncomfortable" to "Feeling uncomfortable",
    "Unwell" to "Not feeling well",
    "Upset" to "Feeling upset",
    "Vulnerable" to "Feeling vulnerable",
    "Warm" to "Feeling warm",
    "Wary" to "Staying cautious",
    "Wistful" to "Quietly longing",
    "Worried" to "Feeling concerned",
    "Yearning" to "Longing for something",
)

/**
 * Human-readable caption for a mood label.
 *
 * `null` → "No recent mood". Unknown (non-canonical) strings →
 * "Feeling something" so the UI never lies about an unrecognised mood.
 */
internal fun assistantMoodCaption(mood: String?): String =
    when (mood) {
        null -> "No recent mood"
        else -> moodCaptions[mood] ?: "Feeling something"
    }

/* ------------------------------------------------------------------ */
/*  Helpers                                                            */
/* ------------------------------------------------------------------ */

private fun normalize(raw: String): String {
    val trimmed = raw.trim()
    return when {
        trimmed.startsWith("```json", ignoreCase = true) -> trimmed.drop(7)
        trimmed.startsWith("```") -> trimmed.drop(3)
        else -> trimmed
    }.removeSuffix("```").trim()
}

private fun JsonObject.stringValue(key: String): String? =
    (this[key] as? JsonPrimitive)
        ?.takeIf(JsonPrimitive::isString)
        ?.contentOrNull

/**
 * Recovers the value of the `"response"` field from a partially-streamed
 * JSON payload (i.e. the closing quote or brace hasn't arrived yet).
 *
 * Handles the standard JSON escapes `\" \\ \/ \b \f \n \r \t \uXXXX`.
 * Returns `null` if the `"response"` field hasn't started yet.
 */
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
            else -> when (val escaped = raw[index++]) {
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
    return result.toString()
}