
package com.naira.assistant.core.tools.executor

import android.content.Context
import com.naira.assistant.core.service.cricket.CricApiService
import com.naira.assistant.core.tools.CommandExecution.jsonStringArg
import com.naira.assistant.domain.FunctionCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

class CricketToolExecutor(context: Context) {

    private val service = CricApiService(context.applicationContext)

    /**
     * Fetch currently live cricket matches.
     */
    suspend fun getLiveScores(): JsonObject {
        return executeSafely {
            service.getCurrentMatches()
        }
    }

    /**
     * Fetch recent and upcoming cricket matches.
     */
    suspend fun getMatches(call: FunctionCall): JsonObject {
        val offsetArgument = call.args["offset"]

        val offset = when {
            offsetArgument == null -> 0

            offsetArgument !is JsonPrimitive ||
                    offsetArgument.isString ||
                    offsetArgument.contentOrNull == null -> {
                return errorResult(
                    "The match-list offset must be a non-negative integer."
                )
            }

            else -> {
                val parsedOffset = offsetArgument.content.toIntOrNull()

                if (parsedOffset == null || parsedOffset < 0) {
                    return errorResult(
                        "The match-list offset must be a non-negative integer."
                    )
                }

                parsedOffset
            }
        }

        return executeSafely {
            service.getMatches(offset)
        }
    }

    /**
     * Fetch the detailed scorecard for a specific match.
     */
    suspend fun getScorecard(call: FunctionCall): JsonObject {
        val matchId = jsonStringArg(call, "match_id")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return errorResult(
                "A valid match_id is required to fetch a cricket scorecard."
            )

        return executeSafely {
            service.getMatchScorecard(matchId)
        }
    }

    /**
     * Safely execute API requests without allowing exceptions
     * to crash the tool execution flow.
     */
    private suspend fun executeSafely(
        request: suspend () -> JsonObject
    ): JsonObject {
        return try {
            val result = request()

            if (result["error"] != null) {
                result
            } else {
                result
            }
        } catch (exception: kotlinx.coroutines.CancellationException) {
            // Never swallow coroutine cancellation.
            throw exception
        } catch (exception: Exception) {
            errorResult(
                exception.message
                    ?.takeIf { it.isNotBlank() }
                    ?: "Unable to retrieve cricket information. Please try again."
            )
        }
    }

    private fun errorResult(message: String): JsonObject {
        return buildJsonObject {
            put("success", false)
            put("error", message)
        }
    }
}
