package com.naira.assistant.core.tools.executor

import com.naira.assistant.core.service.weather.WeatherService
import com.naira.assistant.core.tools.CommandExecution
import com.naira.assistant.domain.FunctionCall
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

suspend fun executeWeatherReport(call: FunctionCall, apiKey: String) = try {
    val location = CommandExecution.jsonStringArg(call, "location")
        ?: throw IllegalArgumentException("A city or place name is required.")
    buildJsonObject {
        put("success", true)
        put("report", WeatherService.describe(WeatherService.forPlace(location, apiKey)))
    }
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: IllegalArgumentException) {
    // Deliberate validation messages.
    weatherError(e.message ?: "Weather could not be loaded.")
} catch (e: IllegalStateException) {
    // Deliberate service errors: fixed text or the provider's own message
    // field. Other exception types may embed raw transport details, so they
    // get a generic message instead.
    weatherError(e.message ?: "Weather could not be loaded.")
} catch (_: Exception) {
    weatherError("Weather could not be loaded right now. Check the internet connection and try again.")
}

private fun weatherError(message: String) = buildJsonObject {
    put("success", false)
    put("error", message)
}
