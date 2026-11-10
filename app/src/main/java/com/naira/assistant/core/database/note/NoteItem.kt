package com.naira.assistant.core.database.note

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class NoteItem(
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String = "",
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val color: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val source: String = "ai_tool",
)
