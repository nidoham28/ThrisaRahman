
package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun liveCricketScoresTool() = ToolDefinition(
    name = "get_live_cricket_scores",
    description = "Get current live matches and available scores. Report only returned data; do not invent missing details.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
        putJsonArray("required") {}
    }
)

fun cricketMatchesTool() = ToolDefinition(
    name = "get_cricket_matches",
    description = "Get recent, live, or upcoming matches. Start at offset 0; increase it only when another page is needed.",
    parameters = buildJsonObject {
        put("type", "object")

        putJsonObject("properties") {
            putJsonObject("offset") {
                put("type", "integer")
                put("description", "Zero-based pagination offset. Defaults to 0.")
                put("minimum", 0)
            }
        }

        putJsonArray("required") {}
    }
)

fun cricketScorecardTool() = ToolDefinition(
    name = "get_cricket_scorecard",
    description = "Get a match scorecard using an exact ID returned by a cricket match tool. Do not guess IDs or missing stats.",
    parameters = buildJsonObject {
        put("type", "object")

        putJsonObject("properties") {
            putJsonObject("match_id") {
                put("type", "string")
                put(
                    "description",
                    "The exact CricAPI match ID returned by a cricket match tool."
                )
                put("minLength", 1)
            }
        }

        putJsonArray("required") {
            add(JsonPrimitive("match_id"))
        }
    }
)
