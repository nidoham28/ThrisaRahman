package com.naira.assistant.core.service.weather

import android.net.Uri
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

data class WeatherReport(
    val location: String,
    val temperatureC: Double,
    val condition: String,
    val feelsLikeC: Double,
    val humidityPercent: Int,
    val precipitationMm: Double,
    val windKmh: Double,
    val hourly: List<String>,
    val daily: List<WeatherDay>,
)

data class WeatherDay(
    val date: String,
    val condition: String,
    val highC: Double,
    val lowC: Double,
    val precipitationMm: Double,
    val sunrise: String,
    val sunset: String,
)

object WeatherService {
    private val http = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
    }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun currentForDhaka(): WeatherReport = forecast("Dhaka", 23.8103, 90.4125)

    suspend fun forPlace(place: String): WeatherReport {
        val normalizedPlace = place.trim().take(100)
        require(normalizedPlace.isNotBlank()) { "Enter a city or place name." }
        val lookup = http.get(
            requestUrl(
                "https://geocoding-api.open-meteo.com/v1/search",
                "name" to normalizedPlace,
                "count" to "1",
                "language" to "en",
                "format" to "json",
            )
        ).bodyAsText()
        val result = json.parseToJsonElement(lookup).jsonObject["results"]
            ?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw IllegalArgumentException("I couldn't find '$normalizedPlace'. Try a city and country name.")
        val name = result.string("name") ?: normalizedPlace
        val country = result.string("country")
        val latitude = result.number("latitude") ?: error("Location coordinates were missing.")
        val longitude = result.number("longitude") ?: error("Location coordinates were missing.")
        return forecast(listOfNotNull(name, country).joinToString(", "), latitude, longitude)
    }

    private suspend fun forecast(name: String, latitude: Double, longitude: Double): WeatherReport {
        val response = http.get(
            requestUrl(
                "https://api.open-meteo.com/v1/forecast",
                "latitude" to latitude.toString(),
                "longitude" to longitude.toString(),
                "current" to "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m",
                "hourly" to "temperature_2m,precipitation_probability,weather_code",
                "forecast_hours" to "12",
                "daily" to "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,sunrise,sunset",
                "forecast_days" to "7",
                "timezone" to "auto",
            )
        ).bodyAsText()
        val root = json.parseToJsonElement(response).jsonObject
        val current = root["current"]?.jsonObject ?: error("Current weather was not returned.")
        val hourly = root["hourly"]?.jsonObject ?: JsonObject(emptyMap())
        val daily = root["daily"]?.jsonObject ?: JsonObject(emptyMap())
        val currentCode = current.int("weather_code") ?: -1
        val hourlyTimes = hourly.array("time")
        val hourlyTemps = hourly.array("temperature_2m")
        val hourlyCodes = hourly.array("weather_code")
        val hourlyRain = hourly.array("precipitation_probability")
        val dailyTimes = daily.array("time")
        val dailyCodes = daily.array("weather_code")
        val highs = daily.array("temperature_2m_max")
        val lows = daily.array("temperature_2m_min")
        val rain = daily.array("precipitation_sum")
        val sunrises = daily.array("sunrise")
        val sunsets = daily.array("sunset")

        return WeatherReport(
            location = name,
            temperatureC = current.number("temperature_2m") ?: error("Temperature was not returned."),
            condition = condition(currentCode),
            feelsLikeC = current.number("apparent_temperature") ?: 0.0,
            humidityPercent = current.int("relative_humidity_2m") ?: 0,
            precipitationMm = current.number("precipitation") ?: 0.0,
            windKmh = current.number("wind_speed_10m") ?: 0.0,
            hourly = (0 until minOf(hourlyTimes.size, hourlyTemps.size, 12)).map { index ->
                val time = hourlyTimes[index].primitiveText().substringAfter('T', hourlyTimes[index].primitiveText())
                val temp = hourlyTemps[index].jsonPrimitive.doubleOrNull ?: 0.0
                val code = hourlyCodes.getOrNull(index)?.jsonPrimitive?.intOrNull ?: -1
                val chance = hourlyRain.getOrNull(index)?.jsonPrimitive?.intOrNull
                "$time ${temp.rounded()}°C ${condition(code)}${chance?.let { " ($it% rain)" }.orEmpty()}"
            },
            daily = (0 until minOf(dailyTimes.size, highs.size, lows.size, 7)).map { index ->
                WeatherDay(
                    date = dailyTimes[index].primitiveText(),
                    condition = condition(dailyCodes.getOrNull(index)?.jsonPrimitive?.intOrNull ?: -1),
                    highC = highs[index].jsonPrimitive.doubleOrNull ?: 0.0,
                    lowC = lows[index].jsonPrimitive.doubleOrNull ?: 0.0,
                    precipitationMm = rain.getOrNull(index)?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    sunrise = sunrises.getOrNull(index)?.primitiveText().orEmpty().substringAfter('T'),
                    sunset = sunsets.getOrNull(index)?.primitiveText().orEmpty().substringAfter('T'),
                )
            },
        )
    }

    fun describe(report: WeatherReport): String = buildString {
        appendLine("Weather for ${report.location}")
        appendLine("Current: ${report.temperatureC.rounded()}°C, ${report.condition}; feels like ${report.feelsLikeC.rounded()}°C")
        appendLine("Humidity: ${report.humidityPercent}%; wind: ${report.windKmh.rounded()} km/h; current precipitation: ${report.precipitationMm} mm")
        if (report.hourly.isNotEmpty()) {
            appendLine("Next hours:")
            report.hourly.forEach { appendLine("- $it") }
        }
        if (report.daily.isNotEmpty()) {
            appendLine("7-day outlook:")
            report.daily.forEach { day ->
                appendLine("- ${day.date}: ${day.condition}, ${day.lowC.rounded()}–${day.highC.rounded()}°C, precipitation ${day.precipitationMm} mm, sunrise ${day.sunrise}, sunset ${day.sunset}")
            }
        }
        append("Source: Open-Meteo")
    }

    private fun condition(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67 -> "Rain"
        71, 73, 75, 77, 85, 86 -> "Snow"
        80, 81, 82 -> "Rain showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Weather unavailable"
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun requestUrl(base: String, vararg parameters: Pair<String, String>): String =
        parameters.fold(Uri.parse(base).buildUpon()) { builder, (key, value) ->
            builder.appendQueryParameter(key, value)
            builder
        }.build().toString()
    private fun JsonObject.number(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull
    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
    private fun JsonObject.array(key: String): JsonArray = this[key]?.jsonArray ?: JsonArray(emptyList())
    private fun kotlinx.serialization.json.JsonElement.primitiveText(): String = jsonPrimitive.contentOrNull.orEmpty()
    private fun Double.rounded(): Int = roundToInt()
}
