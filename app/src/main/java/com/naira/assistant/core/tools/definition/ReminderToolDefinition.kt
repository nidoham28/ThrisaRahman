package com.naira.assistant.core.tools.definition

import com.naira.assistant.core.tools.ToolDefinition
import kotlinx.serialization.json.*

// ---------------------------------------------------------------------------
// Reminder tool definitions — registered with the Gemini function-calling API
// ---------------------------------------------------------------------------

fun createReminderTool() = ToolDefinition(
    name = "create_reminder",
    description = """
        Create a new reminder for the user.
        Use when the user says 'remind me', 'set a reminder', 'don't let me forget', 'alert me', 
        'schedule a reminder', or any similar phrasing. Always confirm the reminder details 
        (title, time, recurrence) back to the user after creation.
        
        Time must be provided as a Unix epoch in milliseconds (ms since 1970-01-01T00:00:00Z).
        Call `get_current_time` first when you need to compute relative times like 
        'in 30 minutes', 'tomorrow at 9am', or 'next Monday'.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("title") {
                put("type", "string")
                put(
                    "description",
                    "Short, clear label for the reminder, e.g. 'Call mom', 'Take medication'."
                )
            }
            putJsonObject("description") {
                put("type", "string")
                put("description", "Optional longer note or context for the reminder.")
            }
            putJsonObject("trigger_at_ms") {
                put("type", "integer")
                put(
                    "description",
                    "Unix epoch milliseconds when the reminder should first fire. Must be in the future."
                )
            }
            putJsonObject("recurrence") {
                put("type", "string")
                put(
                    "description",
                    "How often the reminder repeats: 'none' (one-time, default), 'daily', 'weekly', " +
                            "'monthly', 'yearly', 'weekdays' (Mon–Fri), 'weekends' (Sat–Sun), " +
                            "or 'custom' (repeat every N minutes — set recurrence_interval too)."
                )
                putJsonArray("enum") {
                    add("none"); add("daily"); add("weekly"); add("monthly")
                    add("yearly"); add("weekdays"); add("weekends"); add("custom")
                }
            }
            putJsonObject("recurrence_interval") {
                put("type", "integer")
                put(
                    "description",
                    "Interval in minutes for 'custom' recurrence. Ignored for other recurrence types."
                )
            }
            putJsonObject("priority") {
                put("type", "string")
                put("description", "Urgency level: 'low', 'normal' (default), 'high', or 'urgent'.")
                putJsonArray("enum") { add("low"); add("normal"); add("high"); add("urgent") }
            }
            putJsonObject("tags") {
                put("type", "string")
                put(
                    "description",
                    "Optional comma-separated tags for filtering, e.g. 'health, medication, morning'."
                )
            }
        }
        putJsonArray("required") { add("title"); add("trigger_at_ms") }
    },
)

fun updateReminderTool() = ToolDefinition(
    name = "update_reminder",
    description = """
        Update an existing reminder by its numeric ID.
        Use when the user wants to change the time, title, description, recurrence, priority, 
        or status of an existing reminder. Only the fields you provide are changed; omitted 
        fields keep their current values.
        
        Call `list_reminders` or `search_reminders` first if you don't know the reminder ID.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("id") {
                put("type", "integer")
                put("description", "Numeric ID of the reminder to update.")
            }
            putJsonObject("title") {
                put("type", "string")
                put("description", "New title for the reminder.")
            }
            putJsonObject("description") {
                put("type", "string")
                put("description", "New description/note for the reminder.")
            }
            putJsonObject("trigger_at_ms") {
                put("type", "integer")
                put("description", "New trigger time as Unix epoch milliseconds.")
            }
            putJsonObject("recurrence") {
                put("type", "string")
                put(
                    "description",
                    "New recurrence pattern: 'none', 'daily', 'weekly', 'monthly', 'yearly', 'weekdays', 'weekends', 'custom'."
                )
                putJsonArray("enum") {
                    add("none"); add("daily"); add("weekly"); add("monthly")
                    add("yearly"); add("weekdays"); add("weekends"); add("custom")
                }
            }
            putJsonObject("recurrence_interval") {
                put("type", "integer")
                put("description", "New interval in minutes (for 'custom' recurrence only).")
            }
            putJsonObject("priority") {
                put("type", "string")
                put("description", "New priority: 'low', 'normal', 'high', or 'urgent'.")
                putJsonArray("enum") { add("low"); add("normal"); add("high"); add("urgent") }
            }
            putJsonObject("tags") {
                put("type", "string")
                put("description", "New comma-separated tags.")
            }
        }
        putJsonArray("required") { add("id") }
    },
)

fun deleteReminderTool() = ToolDefinition(
    name = "delete_reminder",
    description = """
        Permanently delete a reminder by its numeric ID.
        Use only when the user explicitly asks to delete or remove a reminder entirely. 
        If the user just wants to stop a reminder from recurring without losing history, 
        use `update_reminder` to set its status or recurrence instead.
        
        Call `list_reminders` or `search_reminders` first if you don't know the reminder ID.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("id") {
                put("type", "integer")
                put("description", "Numeric ID of the reminder to permanently delete.")
            }
        }
        putJsonArray("required") { add("id") }
    },
)

fun listRemindersTool() = ToolDefinition(
    name = "list_reminders",
    description = """
        List the user's reminders, optionally filtered by status or priority.
        Use when the user asks 'what reminders do I have?', 'show my upcoming reminders', 
        'what did I ask you to remind me about?', or similar queries.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("status") {
                put("type", "string")
                put(
                    "description",
                    "Filter by status: 'active', 'done', 'snoozed', 'cancelled', or 'all' (default all active)."
                )
                putJsonArray("enum") {
                    add("active"); add("done"); add("snoozed"); add("cancelled"); add(
                    "all"
                )
                }
            }
            putJsonObject("priority") {
                put("type", "string")
                put(
                    "description",
                    "Optional filter by priority: 'low', 'normal', 'high', 'urgent', or 'all'."
                )
                putJsonArray("enum") { add("low"); add("normal"); add("high"); add("urgent"); add("all") }
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Maximum number of reminders to return (1–100, default 20).")
            }
        }
        putJsonArray("required") {}
    },
)

fun searchRemindersTool() = ToolDefinition(
    name = "search_reminders",
    description = """
        Search reminders by keyword in title, description, or tags, with an optional status filter.
        Use when the user refers to a reminder by keyword or topic and you need to find its ID 
        before updating, deleting, or acting on it.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put(
                    "description",
                    "Keyword or phrase to search in reminder title, description, and tags."
                )
            }
            putJsonObject("status") {
                put("type", "string")
                put(
                    "description",
                    "Optional status filter: 'active', 'done', 'snoozed', 'cancelled', or 'all'."
                )
                putJsonArray("enum") {
                    add("active"); add("done"); add("snoozed"); add("cancelled"); add(
                    "all"
                )
                }
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Maximum number of results to return (1–50, default 10).")
            }
        }
        putJsonArray("required") {}
    },
)

fun snoozeReminderTool() = ToolDefinition(
    name = "snooze_reminder",
    description = """
        Snooze a reminder until a specified time, temporarily pausing it.
        Use when the user says 'snooze this reminder', 'remind me again later', or 
        'postpone this reminder'. The reminder will become active again at the snooze time.
        
        Call `get_current_time` first when computing relative snooze times like 'in 10 minutes'.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("id") {
                put("type", "integer")
                put("description", "Numeric ID of the reminder to snooze.")
            }
            putJsonObject("snooze_until_ms") {
                put("type", "integer")
                put("description", "Unix epoch milliseconds at which the reminder should re-fire.")
            }
        }
        putJsonArray("required") { add("id"); add("snooze_until_ms") }
    },
)

fun markReminderDoneTool() = ToolDefinition(
    name = "mark_reminder_done",
    description = """
        Mark a reminder as completed (done). For one-time reminders this closes it; 
        for recurring reminders it advances the next trigger to the following occurrence 
        and keeps the reminder active.
        Use when the user says 'I did it', 'done', 'completed', or checks off a reminder.
    """.trimIndent(),
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("id") {
                put("type", "integer")
                put("description", "Numeric ID of the reminder to mark as done.")
            }
        }
        putJsonArray("required") { add("id") }
    },
)
