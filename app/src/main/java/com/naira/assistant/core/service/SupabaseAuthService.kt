package com.naira.assistant.core.service

import android.util.Log
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.HttpRequestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow

enum class UpdateResult {
    Updated,
    ReauthenticationRequired,
}

enum class RegisterResult {
    /** A session exists: the user is logged in. */
    SignedIn,

    /** Email confirmation is on: the user must confirm before logging in. */
    ConfirmationRequired,
}

private const val TAG = "SupabaseAuthService"
private const val GENERIC_ERROR = "Something went wrong. Please try again."

/**
 * Like runCatching, but never swallows coroutine cancellation.
 * (Not `suspend` itself: it is inlined into suspend callers, so suspend calls work inside.)
 */
private inline fun <T> suspendRunCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

class SupabaseAuthService(
    private val auth: Auth = Supabase.auth,
) {

    /**
     * Blocks until the SDK has restored the persisted session from storage.
     *
     * **Must** be called before [isLoggedIn] / [currentUser] / [verifySessionWithServer]
     * on app start. Without it, `currentSessionOrNull()` returns null until
     * initialization finishes — so a returning user with a valid stored
     * session would appear logged out for the first frame(s).
     */
    suspend fun awaitInitialization() {
        auth.awaitInitialization()
    }

    val sessionStatus: StateFlow<SessionStatus>
        get() = auth.sessionStatus

    /** Requires [awaitInitialization] to have completed. */
    fun isLoggedIn(): Boolean = auth.currentSessionOrNull() != null

    /** Requires [awaitInitialization] to have completed. */
    fun currentUser(): UserInfo? = auth.currentUserOrNull()

    /** Verifies the restored session with Supabase instead of trusting local session data alone. */
    suspend fun verifySessionWithServer(): Result<Boolean> = suspendRunCatching {
        if (auth.currentSessionOrNull() == null) {
            false
        } else {
            auth.retrieveUserForCurrentSession(updateSession = true)
            true
        }
    }

    // -------------------------------------------------------------------------
    // Login / Register / Logout
    // -------------------------------------------------------------------------

    suspend fun login(email: String, password: String): Result<Unit> =
        suspendRunCatching {
            auth.signInWith(Email) {
                // Trim the email (copy-paste often includes whitespace).
                // Do NOT trim the password — leading/trailing whitespace is
                // significant, and Supabase stores it verbatim.
                this.email = email.trim()
                this.password = password
            }
        }

    suspend fun register(email: String, password: String): Result<RegisterResult> =
        suspendRunCatching {
            auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password
            }
            if (auth.currentSessionOrNull() != null) RegisterResult.SignedIn
            else RegisterResult.ConfirmationRequired
        }

    suspend fun logout(): Result<Unit> =
        suspendRunCatching {
            auth.signOut()
        }

    // -------------------------------------------------------------------------
    // Forgot / Reset Password
    // -------------------------------------------------------------------------

    suspend fun forgotPassword(
        email: String,
        redirectUrl: String? = null,
    ): Result<Unit> =
        suspendRunCatching {
            auth.resetPasswordForEmail(
                email = email.trim(),
                redirectUrl = redirectUrl,
            )
        }

    /** Returns [UpdateResult.ReauthenticationRequired] when the server wants a nonce (emailed code). */
    suspend fun updatePassword(
        newPassword: String,
        reauthNonce: String? = null,
    ): Result<UpdateResult> =
        suspendRunCatching {
            try {
                auth.updateUser {
                    password = newPassword
                    // Coerce blank/whitespace nonces to null so a caller that
                    // passed "" (rather than null) doesn't send an empty nonce.
                    nonce = reauthNonce?.trim()?.takeIf { it.isNotEmpty() }
                }
                UpdateResult.Updated
            } catch (e: AuthRestException) {
                if (isReauthenticationRequired(e)) UpdateResult.ReauthenticationRequired
                else throw e
            }
        }

    suspend fun reauthenticate(): Result<Unit> =
        suspendRunCatching { auth.reauthenticate() }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * App-side password policy.
     *
     * Does not trim: leading/trailing whitespace is part of the password,
     * matching Supabase's server-side storage. Keep in sync with
     * Supabase > Auth > Password settings.
     */
    fun passwordError(value: String): String? = when {
        value.length < 8 -> "At least 8 characters"
        value.none { it.isLetter() } -> "Must contain a letter"
        value.none { it.isDigit() } -> "Must contain a number"
        else -> null
    }

    /**
     * Maps exceptions to text that is safe and useful to show to a user.
     *
     * **Never** surfaces the raw server message. Server-side errors can leak
     * implementation details (SQL, JWT internals, internal routes) and are
     * rarely phrased for end users. Every exception is logged with stack
     * trace at [Log.e] level so diagnostics are still available to developers.
     *
     * Unknown error codes fall back to [GENERIC_ERROR] rather than the raw
     * message — this closes the leak that previously grew worse with every
     * SDK upgrade that added new error codes.
     *
     * Note: not every `AuthErrorCode` constant in this `when` may exist in
     * every `supabase-kt` version. If the compiler flags one as unresolved,
     * delete that branch — the `else -> GENERIC_ERROR` fallback keeps the
     * behaviour correct.
     */
    fun friendlyMessage(e: Throwable): String {
        // Single funnel for user-facing error reporting. Logging here (rather
        // than at every call site) guarantees diagnostics without forcing
        // every caller to remember to log.
        Log.e(TAG, "Auth error", e)

        return when (e) {
            is AuthRestException -> when (e.errorCode) {
                AuthErrorCode.InvalidCredentials ->
                    "Incorrect email or password."

                AuthErrorCode.EmailNotConfirmed ->
                    "Please confirm your email first. Check your inbox."

                AuthErrorCode.UserAlreadyExists,
                AuthErrorCode.EmailExists ->
                    "An account with this email already exists."

                AuthErrorCode.WeakPassword ->
                    "That password is too weak. Try a longer one."

                AuthErrorCode.SamePassword ->
                    "Your new password must be different from the old one."

                AuthErrorCode.SignupDisabled ->
                    "Sign-ups are currently disabled."

                AuthErrorCode.OverRequestRateLimit,
                AuthErrorCode.OverEmailSendRateLimit ->
                    "Too many attempts. Please wait a moment and try again."

                AuthErrorCode.UserNotFound ->
                    "No account was found with that email."

                AuthErrorCode.ValidationFailed ->
                    "Please check the information you entered."

                AuthErrorCode.SessionExpired,
                AuthErrorCode.SessionNotFound,
                AuthErrorCode.RefreshTokenNotFound ->
                    "Your session has expired. Please sign in again."

                AuthErrorCode.FlowStateExpired,
                AuthErrorCode.FlowStateNotFound ->
                    "This link has expired. Please request a new one."

                AuthErrorCode.ReauthenticationNotValid ->
                    "That code is incorrect or has expired. Please try again."

                AuthErrorCode.UserBanned ->
                    "This account has been suspended. Please contact support."

                AuthErrorCode.ProviderDisabled ->
                    "That sign-in method is currently unavailable."

                else -> GENERIC_ERROR
            }

            is HttpRequestException ->
                "Can't reach the server. Check your connection and try again."

            else -> GENERIC_ERROR
        }
    }

    private fun isReauthenticationRequired(e: AuthRestException): Boolean =
        e.errorCode == AuthErrorCode.ReauthenticationNeeded ||
                e.message?.contains("reauthentication", ignoreCase = true) == true
}