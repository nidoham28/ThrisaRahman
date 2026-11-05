package com.nidoham.thrisa.core.config

import android.content.Context
import com.nidoham.thrisa.domain.ApiKeyStorage

const val SUPABASE_URL = "https://avxylvienxhpwfodbofq.supabase.co";
const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImF2eHlsdmllbnhocHdmb2Rib2ZxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTM0NjcwMDgsImV4cCI6MjEwOTA0MzAwOH0.EAmFWj_pWqtd6H0oCNXOx3crJe0SQ2HbKQccvTCNtlg";

const val IMGBB_API_KEY = "352d91ede094aada7a3c5e7f9c6f37da"

fun API_KEYS(context: Context): String {
    return ApiKeyStorage(context).getApiKey("GEMINI").orEmpty()
}
