package com.naira.assistant.core.util

import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** Bangladesh Standard Time — fixed UTC+06:00 (no DST). */
val BDT_OFFSET: UtcOffset = UtcOffset(hours = 6)
private val BDT_TIME_ZONE = FixedOffsetTimeZone(BDT_OFFSET)

/** Greeting buckets, useful for icons / localization. */
enum class Greeting(val message: String, val emoji: String) {
    MORNING("Good Morning", "🌅"),
    AFTERNOON("Good Afternoon", "☀️"),
    EVENING("Good Evening", "🌇"),
    NIGHT("Good Night", "🌙")
}

/** Configurable hour boundaries (24h clock, BDT). */
data class GreetingSchedule(
    val morningStart: Int = 5,   // 05:00 – 11:59
    val afternoonStart: Int = 12,// 12:00 – 16:59
    val eveningStart: Int = 17,  // 17:00 – 20:59
    val nightStart: Int = 21     // 21:00 – 04:59
)

@Suppress("unused")
class GreetingProvider(
    private val clock: Clock = Clock.System,
    private val schedule: GreetingSchedule = GreetingSchedule(),
    private val timeZone: FixedOffsetTimeZone = BDT_TIME_ZONE
) {

    /** Greeting for "right now" in Bangladesh time. */
    fun current(): Greeting = at(clock.now())

    /** Greeting for an arbitrary [Instant], evaluated in BDT. */
    fun at(instant: Instant): Greeting {
        val hour = instant.toLocalDateTime(timeZone).hour
        return forHour(hour)
    }

    /** Pure function — easy to unit test. */
    fun forHour(hour: Int): Greeting = when (hour) {
        in schedule.morningStart until schedule.afternoonStart -> Greeting.MORNING
        in schedule.afternoonStart until schedule.eveningStart -> Greeting.AFTERNOON
        in schedule.eveningStart until schedule.nightStart -> Greeting.EVENING
        else -> Greeting.NIGHT
    }

    /** Convenience: "Good Morning 🌅" */
    fun currentMessage(): String = current().let { "${it.message} ${it.emoji}" }
}