package com.naira.assistant.core.service

import com.naira.assistant.core.config.SUPABASE_ANON_KEY
import com.naira.assistant.core.config.SUPABASE_URL
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

/**
 * Supabase client singleton.
 *
 * ### Deep-link contract
 *
 * The `scheme` and `host` below configure the redirect URI Supabase embeds in
 * auth emails (password reset, magic link, email confirmation). They must match
 * in **three** places, or the flow silently breaks:
 *
 * 1. **This file** — [DEEP_LINK_SCHEME] + [DEEP_LINK_HOST].
 * 2. **AndroidManifest.xml** — an `<intent-filter>` on the activity that should
 *    receive the deep link:
 *    ```xml
 *    <intent-filter android:autoVerify="false">
 *        <action android:name="android.intent.action.VIEW" />
 *        <category android:name="android.intent.category.DEFAULT" />
 *        <category android:name="android.intent.category.BROWSABLE" />
 *        <data android:scheme="naira" android:host="auth-callback" />
 *    </intent-filter>
 *    ```
 * 3. **Supabase dashboard** — Authentication → URL Configuration → Redirect
 *    URLs must include `naira://auth-callback`.
 *
 * A mismatch in any one of the three results in "site not reachable", "invalid
 * redirect", or the email link opening the browser instead of the app. Changing
 * these values is a **three-file change**, not a one-file change.
 */
object Supabase {

    /**
     * Deep-link scheme. Must be lowercase and must match the manifest and the
     * Supabase dashboard. "aurafeed" was a leftover from a previous app name;
     * the current app is NAIRA.
     */
    private const val DEEP_LINK_SCHEME = "naira"

    /** Deep-link host component of `naira://auth-callback`. */
    private const val DEEP_LINK_HOST = "auth-callback"

    /**
     * The single Supabase client instance for the app.
     *
     * Initialized on first access. Kotlin's `by lazy` default is
     * `SYNCHRONIZED`, so concurrent access from multiple coroutines is safe.
     *
     * Config is validated with actionable [require] messages so a broken
     * gradle.properties or a bad flavour build fails immediately on first use
     * with a message naming the missing constant, rather than the SDK's
     * downstream NPE.
     */
    val client: SupabaseClient by lazy {
        require(SUPABASE_URL.isNotBlank()) {
            "SUPABASE_URL is blank. Check core/config and gradle.properties."
        }
        require(SUPABASE_ANON_KEY.isNotBlank()) {
            "SUPABASE_ANON_KEY is blank. Check core/config and gradle.properties."
        }
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY,
        ) {
            install(Auth) {
                scheme = DEEP_LINK_SCHEME
                host = DEEP_LINK_HOST
            }
            install(Postgrest)
            install(Storage)
            install(Realtime)
            install(Functions)
        }
    }

    /**
     * Convenience accessor for [client]'s auth module.
     *
     * Equivalent to `client.auth`. Kept because ~20 call sites use
     * `Supabase.auth`; a deprecate-and-migrate pass, not a hard removal, is the
     * right way to consolidate later.
     */
    val auth: Auth get() = client.auth
}