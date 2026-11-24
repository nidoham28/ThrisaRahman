package com.naira.assistant.domain.settings

enum class AssistantProviderApiType {
    GEMINI,
    OPENAI_COMPATIBLE,
}

enum class AssistantProviderBaseUrlPolicy {
    NONE,
    FIXED,
    OPTIONAL_OVERRIDE,
    REQUIRED,
}

data class AssistantProviderInfo(
    val displayName: String,
    val apiKeyPlaceholder: String,
    val defaultModelId: String,
    val apiType: AssistantProviderApiType,
    val defaultBaseUrl: String? = null,
    val baseUrlPolicy: AssistantProviderBaseUrlPolicy = AssistantProviderBaseUrlPolicy.NONE,
    val fallbackModelId: String = defaultModelId,
    val supportsCustomModel: Boolean = false,
    val normalizeApiKey: Boolean = false,
)
