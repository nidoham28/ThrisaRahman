package com.naira.assistant.core.database.memory

/**
 * Standard classification categories for user memories.
 */
enum class MemoryCategory(val id: String, val title: String) {
    GENERAL("general", "General"),
    PROFILE("profile", "Profile"),
    PREFERENCE("preference", "Preference"),
    FACT("fact", "Fact"),
    WORK("work", "Work & Study"),
    RELATIONSHIP("relationship", "Relationship"),
    HEALTH("health", "Health & Habit"),
    GOAL("goal", "Goal & Project");

    companion object {
        fun fromId(raw: String?): MemoryCategory {
            if (raw.isNullOrBlank()) return GENERAL
            val norm = raw.trim().lowercase()
            return entries.firstOrNull {
                it.id == norm || it.name.lowercase() == norm
            } ?: GENERAL
        }

        val allCategories: List<String> = entries.map { it.id }
    }
}
