package com.naira.assistant.presentation.settings.model

import com.naira.assistant.domain.settings.AssistantProvider

/**
 * Suggested model IDs offered by the provider model dropdowns.
 *
 * These are only shortcuts: every provider that supports custom models still
 * accepts any model ID through the "Enter custom model ID…" entry, so a preset
 * list can never lock the user out of a newer model.
 */
internal object ModelPresets {
    val gemini = listOf(
        "gemini-3.5-flash-lite",
        "gemini-3-flash",
        "gemini-3-pro",
        "gemini-2.5-flash",
        "gemini-2.5-pro",
        "gemini-2.5-flash-image",
        "gemini-flash-latest",
        "gemini-pro-latest",
    )

    val openRouter = listOf(
        "openrouter/auto",
        "openrouter/free",
        "anthropic/claude-sonnet-4-5",
        "openai/gpt-oss-120b",
        "google/gemini-3-flash",
        "google/gemini-2.5-flash",
        "deepseek/deepseek-v4-flash",
        "moonshotai/kimi-k3",
        "qwen/qwen3-8b",
    )

    val customProvider = listOf(
        "gpt-4o-mini",
        "gpt-4o",
        "gpt-4.1",
        "gpt-5",
        "deepseek-chat",
        "deepseek-reasoner",
        "qwen3-8b",
        "llama-3.3-70b-instruct",
        "mistral-large-latest",
    )

    fun forProvider(provider: AssistantProvider): List<String> = when (provider) {
        AssistantProvider.GEMINI -> gemini
        AssistantProvider.OPENROUTER -> openRouter
        AssistantProvider.CUSTOM -> customProvider
        AssistantProvider.TOKENS_BD -> emptyList()
    }
}
