package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun getWeatherReportTool() = ToolDefinition(
    name = "get_weather_report",
    description = "For requested weather, get current conditions, 12-hour details, and a 3-day forecast for a named place. Requires a WeatherAPI key in Settings.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("location") {
                put("type", "string")
                put("description", "City/place name; include country to disambiguate.")
                put("minLength", 1)
            }
        }
        putJsonArray("required") { add(JsonPrimitive("location")) }
    },
)
