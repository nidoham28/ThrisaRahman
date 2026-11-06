package com.nidoham.thrisa.core.database.memory

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Represents a persistent memory item remembered by the AI assistant.
 *
 * @property id Primary key (the `user_memories` row id on Supabase).
 * @property uuid Globally unique identifier (UUIDv4) used as the sync key.
 * @property content The core text/fact/preference being remembered.
 * @property category The semantic category (e.g. "preference", "fact", "profile", "work").
 * @property importance Importance rating from 1 (minor detail) to 5 (critical core fact).
 * @property tags Associated search and classification tags.
 * @property createdAt Timestamp in milliseconds when the memory was created.
 * @property updatedAt Timestamp in milliseconds when the memory was last modified.
 * @property source Who or what created this memory ("ai_tool", "user_manual", "system").
 */
@Serializable
data class MemoryItem(
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val content: String,
    val category: String = "general",
    val importance: Int = 3,
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val source: String = "ai_tool",
)
