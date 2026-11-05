package com.nidoham.thrisa.core.database.todo

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class TodoPriority(val id: String, val level: Int) {
    LOW("low", 1),
    NORMAL("normal", 2),
    HIGH("high", 3),
    URGENT("urgent", 4);

    companion object {
        fun fromId(raw: String?): TodoPriority {
            if (raw.isNullOrBlank()) return NORMAL
            val norm = raw.trim().lowercase()
            return entries.firstOrNull { it.id == norm || it.name.lowercase() == norm } ?: NORMAL
        }
    }
}

@Serializable
data class TodoItem(
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val title: String,
    val notes: String = "",
    val done: Boolean = false,
    val dueAtMs: Long = 0,
    val priority: TodoPriority = TodoPriority.NORMAL,
    val tags: List<String> = emptyList(),
    val listName: String = "inbox",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0,
    val source: String = "ai_tool",
)
