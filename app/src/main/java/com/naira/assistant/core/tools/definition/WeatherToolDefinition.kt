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
    description = "Find current weather, the next 12 hours, and a 7-day forecast for a named city or place.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("location") {
                put("type", "string")
                put("description", "City or place name, optionally including the country, such as Dhaka, Bangladesh.")
            }
        }
        putJsonArray("required") { add(JsonPrimitive("location")) }
    },
)
