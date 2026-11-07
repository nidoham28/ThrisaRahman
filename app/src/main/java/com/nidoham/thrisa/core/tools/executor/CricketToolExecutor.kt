package com.nidoham.thrisa.core.tools.executor

import android.content.Context
import com.nidoham.thrisa.core.service.cricket.CricApiService
import com.nidoham.thrisa.core.tools.executor.BuiltinToolExecutor.jsonStringArg
import com.nidoham.thrisa.domain.FunctionCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

class CricketToolExecutor(context: Context) {
    private val service = CricApiService(context.applicationContext)

    suspend fun getLiveScores(): JsonObject = service.getCurrentMatches()

    suspend fun getMatches(call: FunctionCall): JsonObject {
        val offsetArgument = call.args["offset"]
        val offset = when {
            offsetArgument == null -> 0
            else -> (offsetArgument as? JsonPrimitive)
                ?.contentOrNull
                ?.toIntOrNull()
                ?.takeIf { it >= 0 }
                ?: return buildJsonObject {
                    put("error", "The match-list offset must be a non-negative integer.")
                }
        }
        return service.getMatches(offset)
    }

    suspend fun getScorecard(call: FunctionCall): JsonObject {
        val matchId = jsonStringArg(call, "match_id")
            ?: return buildJsonObject {
                put("error", "A match_id is required to fetch a cricket scorecard.")
            }
        return service.getMatchScorecard(matchId)
    }
}
