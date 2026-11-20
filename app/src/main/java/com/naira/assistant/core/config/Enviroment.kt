package com.naira.assistant.core.config

import android.content.Context
import com.naira.assistant.core.service.FirebaseAuthService
import com.naira.assistant.domain.ApiKeyStorage

const val IMGBB_API_KEY = "352d91ede094aada7a3c5e7f9c6f37da"

fun API_KEYS(context: Context): String {
    val userId = FirebaseAuthService().currentUser()?.uid ?: return ""
    return ApiKeyStorage(context).getApiKey("GEMINI", userId).orEmpty()
}
