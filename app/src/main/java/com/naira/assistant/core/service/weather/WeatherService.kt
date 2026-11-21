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
    val windDirection: String,
    val pressureMb: Double,
    val visibilityKm: Double,
    val cloudPercent: Int,
    val uvIndex: Double,
    val updatedAt: String,
    val hourly: List<String>,
    val daily: List<WeatherDay>,
)

data class WeatherDay(
    val date: String,
    val condition: String,
    val highC: Double,
    val lowC: Double,
    val averageHumidity: Int,
    val maxWindKmh: Double,
    val precipitationMm: Double,
    val chanceOfRain: Int,
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

    suspend fun currentForDhaka(apiKey: String): WeatherReport = forecast(apiKey, "Dhaka")

    suspend fun forPlace(place: String, apiKey: String): WeatherReport {
        val normalizedPlace = place.trim().take(100)
        require(normalizedPlace.isNotBlank()) { "Enter a city or place name." }
        return forecast(apiKey, normalizedPlace)
    }

    private suspend fun forecast(apiKey: String, place: String): WeatherReport {
        val key = apiKey.trim()
        require(key.isNotBlank()) {
            "Add your free WeatherAPI key in Settings → Providers & API Keys → API Keys."
        }
        val responseText = http.get(
            requestUrl(
                "https://api.weatherapi.com/v1/forecast.json",
                "key" to key,
                "q" to place,
                "days" to "3",
                "aqi" to "no",
                "alerts" to "no",
            )
        ).bodyAsText()
        val root = json.parseToJsonElement(responseText).jsonObject
        root["error"]?.jsonObject?.let { error ->
            throw IllegalStateException(error.string("message") ?: "WeatherAPI request failed.")
        }
        val current = root["current"]?.jsonObject ?: error("WeatherAPI did not return current conditions.")
        val location = root["location"]?.jsonObject
        val currentEpoch = current.int("last_updated_epoch") ?: 0
        val forecastDays = root["forecast"]?.jsonObject?.array("forecastday") ?: JsonArray(emptyList())
        val hourlyValues = forecastDays.flatMap { day ->
            day.jsonObject.array("hour").map { it.jsonObject }
        }
        val upcomingHours = hourlyValues
            .filter { (it.int("time_epoch") ?: 0) > currentEpoch }
            .take(12)
            .ifEmpty { hourlyValues.take(12) }

        return WeatherReport(
            location = listOfNotNull(location?.string("name"), location?.string("country"))
                .joinToString(", ").ifBlank { place },
            temperatureC = current.number("temp_c") ?: error("Temperature was not returned."),
            condition = current.objectAt("condition")?.string("text") ?: "Unknown",
            feelsLikeC = current.number("feelslike_c") ?: 0.0,
            humidityPercent = current.int("humidity") ?: 0,
            precipitationMm = current.number("precip_mm") ?: 0.0,
            windKmh = current.number("wind_kph") ?: 0.0,
            windDirection = current.string("wind_dir").orEmpty(),
            pressureMb = current.number("pressure_mb") ?: 0.0,
            visibilityKm = current.number("vis_km") ?: 0.0,
            cloudPercent = current.int("cloud") ?: 0,
            uvIndex = current.number("uv") ?: 0.0,
            updatedAt = current.string("last_updated").orEmpty(),
            hourly = upcomingHours.map { hour ->
                val time = hour.string("time").orEmpty().substringAfter(' ')
                val condition = hour.objectAt("condition")?.string("text") ?: "Unknown"
                val rainChance = hour.int("chance_of_rain") ?: 0
                "$time · ${hour.number("temp_c")?.rounded() ?: 0}°C · $condition · rain chance $rainChance%"
            },
            daily = forecastDays.mapNotNull { element ->
                val day = element.jsonObject
                val metrics = day.objectAt("day") ?: return@mapNotNull null
                val astronomy = day.objectAt("astro")
                WeatherDay(
                    date = day.string("date").orEmpty(),
                    condition = metrics.objectAt("condition")?.string("text") ?: "Unknown",
                    highC = metrics.number("maxtemp_c") ?: 0.0,
                    lowC = metrics.number("mintemp_c") ?: 0.0,
                    averageHumidity = metrics.int("avghumidity") ?: 0,
                    maxWindKmh = metrics.number("maxwind_kph") ?: 0.0,
                    precipitationMm = metrics.number("totalprecip_mm") ?: 0.0,
                    chanceOfRain = metrics.int("daily_chance_of_rain") ?: 0,
                    sunrise = astronomy?.string("sunrise").orEmpty(),
                    sunset = astronomy?.string("sunset").orEmpty(),
                )
            },
        )
    }

    fun describe(report: WeatherReport): String = buildString {
        appendLine("Weather for ${report.location}")
        appendLine("Current: ${report.temperatureC.rounded()}°C, ${report.condition}; feels like ${report.feelsLikeC.rounded()}°C")
        appendLine("Updated: ${report.updatedAt}")
        appendLine("Humidity: ${report.humidityPercent}%; wind: ${report.windKmh.rounded()} km/h ${report.windDirection}; precipitation: ${report.precipitationMm} mm")
        appendLine("Pressure: ${report.pressureMb} mb; visibility: ${report.visibilityKm} km; cloud cover: ${report.cloudPercent}%; UV: ${report.uvIndex}")
        if (report.hourly.isNotEmpty()) {
            appendLine("Next 12 hours:")
            report.hourly.forEach { appendLine("- $it") }
        }
        if (report.daily.isNotEmpty()) {
            appendLine("3-day outlook:")
            report.daily.forEach { day ->
                appendLine("- ${day.date}: ${day.condition}, ${day.lowC.rounded()}–${day.highC.rounded()}°C, average humidity ${day.averageHumidity}%, max wind ${day.maxWindKmh.rounded()} km/h, rain ${day.precipitationMm} mm (${day.chanceOfRain}% chance), sunrise ${day.sunrise}, sunset ${day.sunset}")
            }
        }
        append("Source: WeatherAPI.com")
    }

    private fun requestUrl(base: String, vararg parameters: Pair<String, String>): String =
        parameters.fold(Uri.parse(base).buildUpon()) { builder, (key, value) ->
            builder.appendQueryParameter(key, value)
            builder
        }.build().toString()

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.number(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull
    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
    private fun JsonObject.objectAt(key: String): JsonObject? = this[key]?.jsonObject
    private fun JsonObject.array(key: String): JsonArray = this[key]?.jsonArray ?: JsonArray(emptyList())
    private fun Double.rounded(): Int = roundToInt()
}
