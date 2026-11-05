package com.nidoham.thrisa.core.service

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import com.nidoham.thrisa.core.config.SUPABASE_ANON_KEY
import com.nidoham.thrisa.core.config.SUPABASE_URL

object Supabase {

    private val url: String = SUPABASE_URL

    private val key: String = SUPABASE_ANON_KEY

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = key
        ) {
            install(Auth) {
                scheme = "aurafeed"
                host = "auth-callback"
            }
            install(Postgrest)
            install(Storage)
            install(Realtime)
            install(Functions)
        }
    }

    val auth: Auth
        get() = client.auth
}