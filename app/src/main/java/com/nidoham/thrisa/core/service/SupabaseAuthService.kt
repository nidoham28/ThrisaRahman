package com.nidoham.thrisa.core.service

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.HttpRequestException
import com.nidoham.thrisa.domain.ProviderCredentialSession
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

    suspend fun awaitInitialization() {
        auth.awaitInitialization()
    }

    val sessionStatus: StateFlow<SessionStatus>
        get() = auth.sessionStatus

    fun isLoggedIn(): Boolean = auth.currentSessionOrNull() != null

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
            ProviderCredentialSession.lock()
            auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }
        }

    suspend fun register(email: String, password: String): Result<RegisterResult> =
        suspendRunCatching {
            ProviderCredentialSession.lock()
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
            ProviderCredentialSession.lock()
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
                    nonce = reauthNonce
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

    /** App-side password policy. Keep in sync with Supabase > Auth > Password settings. */
    fun passwordError(value: String): String? = when {
        value.length < 8 -> "At least 8 characters"
        value.none { it.isLetter() } -> "Must contain a letter"
        value.none { it.isDigit() } -> "Must contain a number"
        else -> null
    }

    /** Maps exceptions to text that is safe and useful to show to a user. */
    fun friendlyMessage(e: Throwable): String = when (e) {
        is AuthRestException -> when (e.errorCode) {
            AuthErrorCode.InvalidCredentials -> "Incorrect email or password."
            AuthErrorCode.EmailNotConfirmed -> "Please confirm your email first. Check your inbox."
            AuthErrorCode.UserAlreadyExists,
            AuthErrorCode.EmailExists -> "An account with this email already exists."
            AuthErrorCode.WeakPassword -> "That password is too weak. Try a longer one."
            AuthErrorCode.SamePassword -> "Your new password must be different from the old one."
            AuthErrorCode.SignupDisabled -> "Sign-ups are currently disabled."
            AuthErrorCode.OverRequestRateLimit,
            AuthErrorCode.OverEmailSendRateLimit -> "Too many attempts. Please wait a moment and try again."
            else -> e.message?.takeIf { it.isNotBlank() } ?: GENERIC_ERROR
        }
        is HttpRequestException -> "Can't reach the server. Check your connection and try again."
        else -> GENERIC_ERROR
    }

    private fun isReauthenticationRequired(e: AuthRestException): Boolean =
        e.errorCode == AuthErrorCode.ReauthenticationNeeded ||
                e.message?.contains("reauthentication", ignoreCase = true) == true
}
