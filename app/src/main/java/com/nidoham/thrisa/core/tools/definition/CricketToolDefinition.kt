package com.nidoham.thrisa.core.tools.definition

import com.nidoham.thrisa.core.tools.ToolDefinition
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun liveCricketScoresTool() = ToolDefinition(
    name = "get_live_cricket_scores",
    description = "Get current and live cricket matches with their latest scores and status.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
        putJsonArray("required") {}
    },
)

fun cricketMatchesTool() = ToolDefinition(
    name = "get_cricket_matches",
    description = """
        Get recent and upcoming cricket matches. Use offset to request another page
        when the API indicates that more matches are available.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("offset") {
                put("type", "integer")
                put("description", "Optional pagination offset. Defaults to 0.")
                put("minimum", 0)
            }
        }
        putJsonArray("required") {}
    },
)

fun cricketScorecardTool() = ToolDefinition(
    name = "get_cricket_scorecard",
    description = """
        Get the scorecard and match details for a cricket match. Use a match ID
        returned by get_live_cricket_scores or get_cricket_matches.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("match_id") {
                put("type", "string")
                put("description", "The CricAPI match ID.")
            }
        }
        putJsonArray("required") {
            add("match_id")
        }
    },
)
