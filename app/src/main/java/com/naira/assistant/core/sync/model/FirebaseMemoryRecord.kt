package com.naira.assistant.core.sync.model

import com.google.firebase.database.IgnoreExtraProperties
import com.naira.assistant.core.database.memory.MemoryItem
import java.util.UUID

@IgnoreExtraProperties
data class FirebaseMemoryRecord(
    val id: Long = 0,
    val uuid: String = "",
    val content: String = "",
    val category: String = "general",
    val importance: Int = 3,
    val tags: List<String> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val source: String = "ai_tool",
) {
    fun toDomain(recordKey: String): MemoryItem {
        val resolvedUuid = uuid.ifBlank { recordKey }
        val resolvedId = id.takeIf { it != 0L } ?: resolvedUuid.toFirebaseId()
        val now = System.currentTimeMillis()
        return MemoryItem(
            id = resolvedId,
            uuid = resolvedUuid,
            content = content,
            category = category,
            importance = importance.coerceIn(1, 5),
            tags = tags,
            createdAt = createdAt.takeIf { it > 0 } ?: now,
            updatedAt = updatedAt.takeIf { it > 0 } ?: now,
            source = source,
        )
    }

    companion object {
        fun fromDomain(item: MemoryItem): FirebaseMemoryRecord = FirebaseMemoryRecord(
            id = item.id.takeIf { it != 0L } ?: item.uuid.toFirebaseId(),
            uuid = item.uuid,
            content = item.content,
            category = item.category,
            importance = item.importance,
            tags = item.tags,
            createdAt = item.createdAt,
            updatedAt = item.updatedAt,
            source = item.source,
        )
    }
}

internal fun String.toFirebaseId(): Long {
    val parsed = try {
        UUID.fromString(this).leastSignificantBits and Long.MAX_VALUE
    } catch (_: IllegalArgumentException) {
        hashCode().toLong()
    }
    return parsed.takeIf { it != 0L } ?: 1L
}
