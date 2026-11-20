
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
    description = """
        Get currently live cricket matches and their latest available scores.
        Include match status, teams, innings, runs, wickets, overs, and result
        details when available. Do not invent missing score information.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
        putJsonArray("required") {}
    }
)

fun cricketMatchesTool() = ToolDefinition(
    name = "get_cricket_matches",
    description = """
        Get recent, ongoing, and upcoming cricket matches.
        Returns match details and match IDs when available.
        Use the offset argument to retrieve another page when more results
        are available. Start with offset 0.
    """.trimIndent(),
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
    description = """
        Get the detailed scorecard and match information for a cricket match.
        Requires a valid match ID returned by get_live_cricket_scores
        or get_cricket_matches. Include batting, bowling, innings totals,
        fall of wickets, extras, and player statistics when available.
        Do not guess or fabricate scorecard information.
    """.trimIndent(),
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
