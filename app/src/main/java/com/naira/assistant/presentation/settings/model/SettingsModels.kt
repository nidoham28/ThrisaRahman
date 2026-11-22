@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.settings.model

import androidx.compose.material.icons.outlined.Phone
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.naira.assistant.core.theme.Aqua
import com.naira.assistant.domain.settings.AssistantProvider
import com.naira.assistant.presentation.settings.component.accent
import com.naira.assistant.presentation.settings.component.icon


/* ------------------------------------------------------------------ */
/*  Enums                                                              */
/* ------------------------------------------------------------------ */

internal enum class RowStyle { NORMAL, PREMIUM, DANGER }

internal enum class EditField(
    val title: String,
    val description: String,
    val placeholder: String,
    val isPhoneNumber: Boolean = false,
    val requiresNonBlank: Boolean = false,
) {
    USERNAME("Username", "How the assistant should address you.", "e.g. Rahim"),
    PHONE("Phone number", "Your profile phone number.", "+880 1XXXXXXXXX", isPhoneNumber = true),
    CUSTOM_USER_AGENT(
        "Custom user-agent",
        "Used for browser-style video requests. Leave blank to use the default browser identity.",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36",
    ),
    GEMINI_MODEL(
        "Gemini model ID",
        "Enter a Gemini API model ID, for example gemini-3.5-flash-lite.",
        AssistantProvider.GEMINI.defaultModelId,
        requiresNonBlank = true,
    ),
    OPENROUTER_MODEL(
        "OpenRouter model",
        "Enter an OpenRouter model ID. Use openrouter/auto for automatic routing.",
        AssistantProvider.OPENROUTER.defaultModelId,
        requiresNonBlank = true,
    ),
    OPENROUTER_BASE_URL(
        "OpenRouter API base URL",
        "Optional HTTPS endpoint override. Leave blank to use OpenRouter's default API URL.",
        "https://openrouter.ai/api/v1",
    ),
    GROQ_MODEL(
        "Groq model ID",
        "Enter a model ID supported by GroqCloud.",
        AssistantProvider.GROQ.defaultModelId,
        requiresNonBlank = true,
    ),
    GROQ_BASE_URL(
        "Groq API base URL",
        "Optional HTTPS endpoint override. Leave blank to use Groq's default API URL.",
        "https://api.groq.com/openai/v1",
    ),
    CUSTOM_PROVIDER_MODEL(
        "Custom provider model",
        "Enter the model ID supported by your OpenAI-compatible provider.",
        "e.g. my-model",
        requiresNonBlank = true,
    ),
    CUSTOM_PROVIDER_BASE_URL(
        "Custom provider API URL",
        "Enter the HTTPS base URL for an OpenAI-compatible API.",
        "https://api.example.com/v1/",
        requiresNonBlank = true,
    ),
}

/* ------------------------------------------------------------------ */
/*  Node model                                                         */
/* ------------------------------------------------------------------ */

internal sealed interface SettingsNode {
    val id: String
    val title: String

    data class Category(
        override val id: String,
        override val title: String,
        val subtitle: String? = null,
        val icon: ImageVector,
        val accent: Color = Aqua,
        val children: List<SettingsNode>,
        val trailing: (@Composable () -> Unit)? = null,
    ) : SettingsNode

    data class Item(
        override val id: String,
        override val title: String,
        val subtitle: String? = null,
        val icon: ImageVector? = null,
        val style: RowStyle = RowStyle.NORMAL,
        val enabled: Boolean = true,
        val onClick: (() -> Unit)? = null,
        val trailing: (@Composable () -> Unit)? = null,
    ) : SettingsNode
}

internal data class SettingsSection(
    val title: String,
    val categoryIds: List<String>,
)

internal val rootSettingsSections = listOf(
    SettingsSection("General Settings", listOf("preferences", "profile", "assistant", "extensions")),
    SettingsSection("Provider & Services", listOf("providers", "integrations", "voice")),
    SettingsSection("Additional Settings", listOf("privacy", "data", "about")),
)

internal val rootCategoryOrder = rootSettingsSections.flatMap { it.categoryIds }
