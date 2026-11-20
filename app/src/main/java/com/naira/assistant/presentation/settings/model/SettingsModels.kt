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

enum class SettingsDestination {
    APPEARANCE
}

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
    OPENROUTER_MODEL(
        "OpenRouter model",
        "Enter an OpenRouter model ID. Use openrouter/auto for automatic routing.",
        AssistantProvider.OPENROUTER.defaultModelId,
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
        val children: List<SettingsNode>
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

internal val rootCategoryOrder = listOf(
    "appearance",
    "account",
    "assistant",
    "ai",
    "permissions",
    "engine",
)
