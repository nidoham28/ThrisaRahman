package com.nidoham.thrisa.core.tools.definition

import kotlinx.serialization.json.*
import com.nidoham.thrisa.core.tools.ToolDefinition

/** Function declarations for the persistent long-term-memory tools. */

fun insertMemoryTool() = ToolDefinition(
    name = "insert_memory",
    description = "Save a new persistent memory about the user into the local database. " +
            "Use proactively whenever the user shares facts, preferences, family/friends, " +
            "habits, work, health, goals, or explicitly says 'remember this'.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("content") {
                put("type", "string")
                put("description", "Clear, concise fact or preference to remember about the user.")
            }
            putJsonObject("category") {
                put("type", "string")
                put(
                    "description",
                    "Category: 'profile', 'preference', 'fact', 'work', 'relationship', 'health', 'goal', or 'general'."
                )
                putJsonArray("enum") {
                    add("profile")
                    add("preference")
                    add("fact")
                    add("work")
                    add("relationship")
                    add("health")
                    add("goal")
                    add("general")
                }
            }
            putJsonObject("importance") {
                put("type", "integer")
                put("description", "Importance rating from 1 (minor detail) to 5 (critical core fact). Default is 3.")
            }
            putJsonObject("tags") {
                put("type", "string")
                put("description", "Optional comma-separated keywords for indexing, e.g. 'coffee, beverage, morning'.")
            }
        }
        putJsonArray("required") {
            add("content")
        }
    },
)

fun updateMemoryTool() = ToolDefinition(
    name = "update_memory",
    description = "Update an existing stored memory by its numeric ID when the user corrects, " +
            "updates, or refines information they previously shared.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("id") {
                put("type", "integer")
                put("description", "The numeric ID of the memory to update.")
            }
            putJsonObject("content") {
                put("type", "string")
                put("description", "The updated text content of the memory.")
            }
            putJsonObject("category") {
                put("type", "string")
                put("description", "Optional updated category.")
            }
            putJsonObject("importance") {
                put("type", "integer")
                put("description", "Optional updated importance from 1 to 5.")
            }
            putJsonObject("tags") {
                put("type", "string")
                put("description", "Optional updated comma-separated tags.")
            }
        }
        putJsonArray("required") {
            add("id")
            add("content")
        }
    },
)

fun removeMemoryTool() = ToolDefinition(
    name = "remove_memory",
    description = "Remove/forget a stored memory by its numeric ID when the user asks to forget " +
            "or delete something, or when a memory is no longer true.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("id") {
                put("type", "integer")
                put("description", "The numeric ID of the memory to remove.")
            }
            putJsonObject("reason") {
                put("type", "string")
                put("description", "Optional reason why the memory is being removed.")
            }
        }
        putJsonArray("required") {
            add("id")
        }
    },
)

fun mergeMemoriesTool() = ToolDefinition(
    name = "merge_memories",
    description = "Atomically combine two or more related, overlapping, or fragmented memories into " +
            "one single consolidated memory, removing the old ones.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("memory_ids") {
                put("type", "string")
                put("description", "Comma-separated list of numeric IDs to merge, e.g. '1, 2, 4'.")
            }
            putJsonObject("merged_content") {
                put("type", "string")
                put("description", "Consolidated text combining the essential facts from all merged memories.")
            }
            putJsonObject("category") {
                put("type", "string")
                put("description", "Optional category for the unified memory.")
            }
            putJsonObject("importance") {
                put("type", "integer")
                put("description", "Optional importance rating (1 to 5) for the unified memory.")
            }
            putJsonObject("tags") {
                put("type", "string")
                put("description", "Optional consolidated comma-separated tags.")
            }
        }
        putJsonArray("required") {
            add("memory_ids")
            add("merged_content")
        }
    },
)

fun searchMemoriesTool() = ToolDefinition(
    name = "search_memories",
    description = "Search stored memories by keyword query or category. " +
            "Use when answering questions about the user's past, preferences, contacts, or background.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put("description", "Keyword or phrase to search within memory content and tags.")
            }
            putJsonObject("category") {
                put("type", "string")
                put("description", "Optional category filter ('profile', 'preference', 'fact', etc.).")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Maximum number of memories to return (1-50, default 10).")
            }
        }
        putJsonArray("required") {}
    },
)

fun listMemoriesTool() = ToolDefinition(
    name = "list_memories",
    description = "List recent memories stored about the user, optionally filtered by category.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("category") {
                put("type", "string")
                put("description", "Optional category filter, or 'all'.")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Maximum number of memories to return (default 20).")
            }
        }
        putJsonArray("required") {}
    },
)
