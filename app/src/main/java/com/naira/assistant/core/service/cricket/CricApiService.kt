package com.naira.assistant.core.service.cricket

import android.content.Context
import com.naira.assistant.core.service.SupabaseAuthService
import com.naira.assistant.domain.ApiKeyStorage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

class CricApiService(
    context: Context,
    private val httpClient: HttpClient = sharedHttpClient,
) {
    private val appContext = context.applicationContext
    private val apiKeyStorage = ApiKeyStorage(appContext)
    private val authService = SupabaseAuthService()

    suspend fun getCurrentMatches(): JsonObject =
        request("currentMatches", mapOf("offset" to "0"))

    suspend fun getMatches(offset: Int): JsonObject =
        request("matches", mapOf("offset" to offset.toString()))

    suspend fun getMatchScorecard(matchId: String): JsonObject {
        require(matchId.isNotBlank()) { "A match ID is required to fetch a scorecard." }
        return request("match_scorecard", mapOf("id" to matchId))
    }

    private suspend fun request(endpoint: String, parameters: Map<String, String>): JsonObject {
        val apiKey = authService.currentUser()?.id
            ?.let(apiKeyStorage::getCricApiKey)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: throw IllegalStateException("Add your CricAPI key in Settings → Cricket first.")

        val response = try {
            httpClient.get("$BASE_URL/$endpoint") {
                parameter("apikey", apiKey)
                parameters.forEach { (name, value) -> parameter(name, value) }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            throw IllegalStateException(
                "CricAPI request could not be completed. Check your connection and try again."
            )
        }
        return parseResponse(response.bodyAsText(), response.status.value, apiKey)
    }

    companion object {
        const val BASE_URL = "https://api.cricapi.com/v1"

        private val json = Json { ignoreUnknownKeys = true }
        private val sharedHttpClient = HttpClient {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
        }

        internal fun parseResponse(
            body: String,
            httpStatusCode: Int,
            apiKey: String = "",
        ): JsonObject {
            val parsed: JsonElement = try {
                json.parseToJsonElement(body)
            } catch (e: SerializationException) {
                throw IllegalStateException(
                    "CricAPI returned an invalid response (HTTP $httpStatusCode).",
                    e,
                )
            }
            val root = parsed as? JsonObject
                ?: throw IllegalStateException(
                    "CricAPI returned an invalid response (HTTP $httpStatusCode)."
                )
            val apiMessage = root.message("reason")
                ?: root.message("message")
                ?: root.message("error")
            val safeApiMessage = apiMessage?.let {
                if (apiKey.isEmpty()) it else it.replace(apiKey, "[redacted]")
            }

            if (httpStatusCode !in 200..299) {
                throw IllegalStateException(
                    buildString {
                        append("CricAPI request failed (HTTP $httpStatusCode).")
                        safeApiMessage?.let { append(" ").append(it) }
                    }
                )
            }
            if (root.message("status") != "success") {
                throw IllegalStateException(
                    safeApiMessage?.let { "CricAPI request failed: $it" }
                        ?: "CricAPI request failed without an error message."
                )
            }
            val data = root["data"]
                ?: throw IllegalStateException("CricAPI returned a response without match data.")

            return buildJsonObject {
                put("status", "success")
                root["info"]?.let { put("info", it) }
                put("data", data)
            }
        }

        private fun JsonObject.message(key: String): String? {
            val value = this[key]
            val primitive = (value as? JsonPrimitive)
                ?: ((value as? JsonObject)?.get("message") as? JsonPrimitive)
            return primitive?.contentOrNull
        }
    }
}
