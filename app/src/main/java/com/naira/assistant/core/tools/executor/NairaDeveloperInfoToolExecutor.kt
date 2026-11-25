
package com.naira.assistant.core.tools.executor

import com.naira.assistant.core.tools.CommandExecution
import com.naira.assistant.domain.FunctionCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Provides public identity and developer information about Naira Assistant.
 */
internal fun executeNairaDeveloperInfo(call: FunctionCall): JsonObject {
    val topic = CommandExecution.jsonStringArg(call, "topic")
        ?.trim()
        ?.lowercase()
        ?: return buildJsonObject {
            put("success", false)
            put("error", "'topic' must be a non-empty string from the declared list.")
        }

    val info = when (topic) {
        "about_naira" -> """
            Naira Assistant is an AI-powered personal assistant designed to make
            everyday digital interactions easier, smarter, and more productive.

            It provides conversational assistance through text and voice,
            helping users access information, manage everyday tasks,
            organize personal activities, and interact with supported device features.

            Naira aims to provide a natural, useful, and accessible AI experience.
        """.trimIndent()

        "developer" -> """
            Naira Assistant was created by Nahid Islam.

            Developer: Nahid Islam
            Profession: Compose Multiplatform Developer
            Country: Bangladesh
            Education: Computer Science and Technology (CST) student.

            Nahid is the creator and developer behind Naira Assistant,
            working on building useful AI-powered applications and
            modern cross-platform software experiences.
        """.trimIndent()

        "purpose" -> """
            Naira Assistant was created to provide people with an intelligent
            digital companion that can help with everyday activities.

            Its purpose is to make AI assistance more accessible, practical,
            personalized, and naturally integrated into daily life.

            Naira is intended to assist users rather than replace their
            independent thinking and decisions.
        """.trimIndent()

        "capabilities" -> """
            Naira Assistant offers AI-powered assistance through capabilities
            such as:

            - Text-based conversations.
            - Voice conversations.
            - General questions and answers.
            - Information assistance.
            - Personal notes and memory.
            - Reminders and alarms.
            - To-do and task management.
            - Calendar-related assistance.
            - Supported Android device interactions.
            - Other integrated assistant features.

            Available capabilities depend on the user's application version,
            permissions, and enabled integrations.
        """.trimIndent()

        "identity" -> """
            Name: Naira Assistant
            Category: AI Personal Assistant
            Creator: Nahid Islam
            Developer Profession: Compose Multiplatform Developer
            Developer Country: Bangladesh

            Naira is an AI assistant created by Nahid Islam.
            It is designed to communicate naturally, provide useful assistance,
            and help people manage everyday digital activities.

            Naira's identity is centered around being a practical,
            conversational, and intelligent personal assistant.
        """.trimIndent()

        else -> return buildJsonObject {
            put("success", false)
            put(
                "error",
                "Unsupported topic '$topic'. Choose a topic from the declared list: " +
                    "about_naira, developer, purpose, capabilities, identity.",
            )
        }
    }

    return buildJsonObject {
        put("success", true)
        put("topic", topic)
        put("info", info)
    }
}
