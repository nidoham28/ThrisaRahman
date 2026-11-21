package com.naira.assistant.core.tools.executor

import com.naira.assistant.core.service.weather.WeatherService
import com.naira.assistant.core.tools.CommandExecution
import com.naira.assistant.domain.FunctionCall
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

suspend fun executeWeatherReport(call: FunctionCall) = try {
    val location = CommandExecution.jsonStringArg(call, "location")
        ?: throw IllegalArgumentException("A city or place name is required.")
    buildJsonObject {
        put("success", true)
        put("report", WeatherService.describe(WeatherService.forPlace(location)))
    }
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    buildJsonObject {
        put("success", false)
        put("error", e.message ?: "Weather could not be loaded.")
    }
}
