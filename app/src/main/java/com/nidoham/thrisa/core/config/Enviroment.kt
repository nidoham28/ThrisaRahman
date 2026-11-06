package com.nidoham.thrisa.core.config

import android.content.Context
import com.nidoham.thrisa.core.service.SupabaseAuthService
import com.nidoham.thrisa.domain.ApiKeyStorage
import com.nidoham.thrisa.domain.ProviderCredentialSession

const val SUPABASE_URL = "https://psddsxehdjtsbskbkwul.supabase.co";
const val SUPABASE_ANON_KEY = "sb_publishable_6T_UuZFjFNPTszPa1lkSGQ_todH-Klv";

const val IMGBB_API_KEY = "352d91ede094aada7a3c5e7f9c6f37da"

fun API_KEYS(context: Context): String {
    val userId = SupabaseAuthService().currentUser()?.id ?: return ""
    return ApiKeyStorage(context).getApiKey("GEMINI", userId).orEmpty()
}

fun isApiKeyVaultUnlocked(): Boolean {
    val userId = SupabaseAuthService().currentUser()?.id ?: return false
    return ProviderCredentialSession.isUnlocked(userId)
}
