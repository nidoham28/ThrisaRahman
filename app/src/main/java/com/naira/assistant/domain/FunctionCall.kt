package com.naira.assistant.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class FunctionCall(
    val id: String? = null,
    val name: String,
    val args: JsonObject = JsonObject(emptyMap()),
)
