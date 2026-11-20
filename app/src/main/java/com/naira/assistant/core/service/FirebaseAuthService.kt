package com.naira.assistant.core.service

import android.util.Log
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

enum class UpdateResult {
    Updated,
    ReauthenticationRequired,
}

enum class RegisterResult {
    SignedIn,
    ConfirmationRequired,
}

sealed interface FirebaseSessionStatus {
    data object Authenticated : FirebaseSessionStatus
    data object NotAuthenticated : FirebaseSessionStatus
}

private const val TAG = "FirebaseAuthService"
private const val GENERIC_ERROR = "Something went wrong. Please try again."

class FirebaseAuthService(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    private val _sessionStatus = MutableStateFlow<FirebaseSessionStatus>(
        if (auth.currentUser != null) FirebaseSessionStatus.Authenticated
        else FirebaseSessionStatus.NotAuthenticated,
    )

    val sessionStatus: StateFlow<FirebaseSessionStatus> = _sessionStatus.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _sessionStatus.value =
                if (firebaseAuth.currentUser != null) FirebaseSessionStatus.Authenticated
                else FirebaseSessionStatus.NotAuthenticated
        }
    }

    suspend fun awaitInitialization() {
        if (auth.currentUser != null) {
            auth.currentUser?.reload()?.await()
        }
        _sessionStatus.value =
            if (auth.currentUser != null) FirebaseSessionStatus.Authenticated
            else FirebaseSessionStatus.NotAuthenticated
    }

    fun isLoggedIn(): Boolean = auth.currentUser != null

    fun currentUser(): FirebaseUser? = auth.currentUser

    suspend fun verifySessionWithServer(): Result<Boolean> =
        runSuspendCatching {
            val user = auth.currentUser ?: return@runSuspendCatching false
            user.reload().await()
            auth.currentUser != null
        }

    suspend fun login(email: String, password: String): Result<Unit> =
        runSuspendCatching {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
            Unit
        }

    suspend fun register(email: String, password: String): Result<RegisterResult> =
        runSuspendCatching {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = result.user
            if (user != null) {
                user.sendEmailVerification().await()
                auth.signOut()
                RegisterResult.ConfirmationRequired
            } else {
                RegisterResult.ConfirmationRequired
            }
        }

    suspend fun logout(): Result<Unit> =
        runSuspendCatching {
            auth.signOut()
            Unit
        }

    suspend fun forgotPassword(
        email: String,
        redirectUrl: String? = null,
    ): Result<Unit> =
        runSuspendCatching {
            auth.sendPasswordResetEmail(email.trim()).await()
            Unit
        }

    suspend fun updatePassword(
        newPassword: String,
        reauthNonce: String? = null,
    ): Result<UpdateResult> =
        runSuspendCatching {
            try {
                auth.currentUser?.updatePassword(newPassword)?.await()
                UpdateResult.Updated
            } catch (e: FirebaseAuthRecentLoginRequiredException) {
                UpdateResult.ReauthenticationRequired
            }
        }

    suspend fun reauthenticate(): Result<Unit> =
        runSuspendCatching {
            val email = auth.currentUser?.email
                ?: return@runSuspendCatching
            auth.sendPasswordResetEmail(email).await()
        }

    fun passwordError(value: String): String? = when {
        value.length < 8 -> "At least 8 characters"
        value.none { it.isLetter() } -> "Must contain a letter"
        value.none { it.isDigit() } -> "Must contain a number"
        else -> null
    }

    fun friendlyMessage(e: Throwable): String {
        Log.e(TAG, "Auth error", e)
        return when (e) {
            is FirebaseAuthInvalidCredentialsException ->
                "Incorrect email or password."
            is FirebaseAuthInvalidUserException ->
                "No account was found with that email."
            is FirebaseAuthUserCollisionException ->
                "An account with this email already exists."
            is FirebaseAuthWeakPasswordException ->
                "That password is too weak. Try a longer one."
            is FirebaseAuthRecentLoginRequiredException ->
                "Please sign in again before changing your password."
            else -> GENERIC_ERROR
        }
    }

    private inline fun <T> runSuspendCatching(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
}
