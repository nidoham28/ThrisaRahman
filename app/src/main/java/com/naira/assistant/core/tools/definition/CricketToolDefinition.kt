
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
    description = universalToolDescription(
        purpose = "Returns currently live cricket matches and the scores available for them.",
        trigger = "The user asks for current/live cricket scores or ongoing matches.",
        notFor = "Use `get_cricket_matches` for recent/upcoming fixtures and `get_cricket_scorecard` for one match's detailed scorecard.",
        prerequisite = "No arguments are required; do not add scores or match details absent from the result.",
    ),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
        putJsonArray("required") {}
    },
    parallelizable = true,
)

fun cricketMatchesTool() = ToolDefinition(
    name = "get_cricket_matches",
    description = universalToolDescription(
        purpose = "Returns a list of recent, live, or upcoming cricket matches.",
        trigger = "The user asks for fixtures, a match list, or matches in a particular period.",
        notFor = "Use `get_live_cricket_scores` for live scores only and `get_cricket_scorecard` for a match's detailed scorecard.",
        prerequisite = "Start with `offset=0`; increase the offset only when another page is needed.",
    ),
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
    },
    parallelizable = true,
)

fun cricketScorecardTool() = ToolDefinition(
    name = "get_cricket_scorecard",
    description = universalToolDescription(
        purpose = "Returns a detailed scorecard for one cricket match.",
        trigger = "The user asks for a full/detailed scorecard for an identified match.",
        notFor = "Use `get_cricket_matches` to find/list matches and `get_live_cricket_scores` for live scores.",
        prerequisite = "An exact `match_id` returned by a match tool is required; find the match first if the ID is unknown.",
    ),
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
    },
    parallelizable = true,
)
