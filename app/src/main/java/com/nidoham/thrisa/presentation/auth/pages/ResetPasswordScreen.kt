package com.nidoham.thrisa.presentation.auth.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.auth.status.SessionStatus
import com.nidoham.thrisa.core.service.SupabaseAuthService
import com.nidoham.thrisa.core.service.UpdateResult
import com.nidoham.thrisa.presentation.auth.components.AuthFieldShape
import com.nidoham.thrisa.presentation.auth.components.AuthHeader
import com.nidoham.thrisa.presentation.auth.components.AuthIllustration
import com.nidoham.thrisa.presentation.auth.components.AuthPrimaryButton
import com.nidoham.thrisa.presentation.auth.components.AuthScaffold
import com.nidoham.thrisa.presentation.auth.components.PasswordVisibilityToggle
import com.nidoham.thrisa.presentation.auth.components.authFieldColors
import com.nidoham.thrisa.presentation.auth.components.showFeedback
import com.nidoham.thrisa.presentation.auth.components.stripWhitespace
import com.nidoham.thrisa.presentation.auth.components.validateEmail
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** Internal pages. Stored by ordinal so rememberSaveable works on every platform. */
private enum class ResetStep { Request, Sent, Update }

/**
 * Password recovery flow in one screen:
 *  1. Request: enter email, send reset link
 *  2. Sent: "check your email", resend with cooldown
 *  3. Update: set the new password (entered automatically once the
 *     recovery link signs the user in)
 *
 * @param redirectUrl Deep link the email points to, e.g. "aurafeed://auth-callback".
 *   It must be in Supabase > Auth > URL Configuration > Redirect URLs and match
 *   the scheme/host you set in install(Auth).
 * @param onBack Leave the flow without finishing (pop the screen).
 * @param onCompleted Password changed (navigate to Login).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ResetPasswordScreen(
    modifier: Modifier = Modifier,
    authService: SupabaseAuthService = remember { SupabaseAuthService() },
    redirectUrl: String? = null,
    onBack: () -> Unit,
    onCompleted: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    var stepOrdinal by rememberSaveable { mutableIntStateOf(ResetStep.Request.ordinal) }
    val step = ResetStep.entries[stepOrdinal]
    fun goTo(s: ResetStep) { stepOrdinal = s.ordinal }

    var email by rememberSaveable { mutableStateOf("") }
    // Passwords are deliberately NOT saveable: they would be written into the saved-state bundle.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var otp by rememberSaveable { mutableStateOf("") }

    var obscure by rememberSaveable { mutableStateOf(true) }
    var needsOtp by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var cooldown by remember { mutableIntStateOf(0) }

    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmError by remember { mutableStateOf<String?>(null) }
    var otpError by remember { mutableStateOf<String?>(null) }

    val confirmFocus = remember { FocusRequester() }
    val otpFocus = remember { FocusRequester() }

    // requestFocus() throws if the target isn't attached yet (e.g. the OTP field mid-animation).
    fun FocusRequester.requestFocusSafely() {
        try { requestFocus() } catch (_: IllegalStateException) { /* not on screen yet */ }
    }

    // The recovery link signs the user in -> jump to the Update page.
    val sessionStatus by authService.sessionStatus.collectAsState()
    LaunchedEffect(sessionStatus) {
        if (sessionStatus is SessionStatus.Authenticated && step != ResetStep.Update) {
            goTo(ResetStep.Update)
        }
    }

    LaunchedEffect(cooldown) {
        if (cooldown > 0) {
            delay(1_000.milliseconds)
            cooldown--
        }
    }

    LaunchedEffect(needsOtp) {
        if (needsOtp) otpFocus.requestFocusSafely()
    }

    fun showSnack(message: String, isError: Boolean = false) =
        scope.showFeedback(snackbarHostState, message, isError)

    // ---- Page 1/2: request link ----------------------------------------------

    fun sendLink() {
        if (loading || (step == ResetStep.Sent && cooldown > 0)) return
        focusManager.clearFocus()
        keyboard?.hide()

        val trimmed = email.trim()
        emailError = validateEmail(trimmed)
        if (emailError != null) return

        loading = true
        scope.launch {
            try {
                authService.forgotPassword(trimmed, redirectUrl)
                    .onSuccess {
                        goTo(ResetStep.Sent)
                        cooldown = 60
                    }
                    .onFailure { showSnack(authService.friendlyMessage(it), isError = true) }
            } finally {
                loading = false
            }
        }
    }

    // ---- Page 3: update password ---------------------------------------------

    fun validateUpdate(): Boolean {
        passwordError = authService.passwordError(password)
        confirmError = if (confirm != password) "Passwords do not match" else null
        otpError = if (needsOtp && otp.length != 6) "Enter the 6-digit code from the email" else null
        return passwordError == null && confirmError == null && otpError == null
    }

    fun submitUpdate() {
        if (loading) return
        focusManager.clearFocus()
        keyboard?.hide()
        if (!validateUpdate()) return

        loading = true
        scope.launch {
            try {
                authService.updatePassword(
                    newPassword = password,
                    reauthNonce = if (needsOtp) otp.trim() else null,
                )
                    .onSuccess { result ->
                        when (result) {
                            UpdateResult.Updated -> {
                                // Don't leave the recovery session active; user logs in with the new password.
                                authService.logout()
                                onCompleted()
                            }
                            UpdateResult.ReauthenticationRequired -> {
                                needsOtp = true
                                authService.reauthenticate()
                                    .onSuccess { showSnack("For your security, enter the code we just emailed you.") }
                                    .onFailure { showSnack(authService.friendlyMessage(it), isError = true) }
                            }
                        }
                    }
                    .onFailure { showSnack(authService.friendlyMessage(it), isError = true) }
            } finally {
                loading = false
            }
        }
    }

    fun resendOtp() {
        if (loading) return
        loading = true
        scope.launch {
            try {
                authService.reauthenticate()
                    .onSuccess { showSnack("A new code is on its way.") }
                    .onFailure { showSnack(authService.friendlyMessage(it), isError = true) }
            } finally {
                loading = false
            }
        }
    }

    // ---- Back / cancel -------------------------------------------------------

    fun goBack() {
        if (loading) return
        when (step) {
            ResetStep.Request -> onBack()
            ResetStep.Sent -> goTo(ResetStep.Request)
            ResetStep.Update -> {
                loading = true
                scope.launch {
                    try {
                        authService.logout() // drop the recovery session
                    } finally {
                        loading = false
                    }
                    password = ""; confirm = ""; otp = ""; needsOtp = false
                    goTo(ResetStep.Request)
                    onBack()
                }
            }
        }
    }

  //  BackHandler(enabled = true) { goBack() }

    AuthScaffold(
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        onNavigate = { goBack() },
        navigationIcon = Icons.Default.Close,
        navigationDescription = "Close",
        navigationEnabled = !loading,
    ) {
        AuthIllustration(height = 220.dp)

        when (step) {
            // ---------------- PAGE 1 ----------------
            ResetStep.Request -> {
                AuthHeader(
                    title = "Forgot password?",
                    subtitle = "Enter your email and we'll send you a link to reset your password.",
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it.stripWhitespace()
                        if (emailError != null) emailError = null
                    },
                    enabled = !loading,
                    singleLine = true,
                    isError = emailError != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { sendLink() }),
                    leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                    placeholder = { Text("Email") },
                    supportingText = { emailError?.let { Text(it) } },
                    shape = AuthFieldShape,
                    colors = authFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.EmailAddress },
                )
                Spacer(Modifier.height(16.dp))
                AuthPrimaryButton(
                    text = "Send reset link",
                    loading = loading,
                    onClick = { sendLink() },
                )
            }

            // ---------------- PAGE 2 ----------------
            ResetStep.Sent -> {
                AuthHeader(
                    title = "Check your email",
                    subtitle = "We sent a reset link to\n${email.trim()}\n\n" +
                            "Open it on this device and you'll be brought back here to choose a new password.",
                )
                AuthPrimaryButton(
                    text = if (cooldown > 0) "Resend in ${cooldown}s" else "Resend link",
                    loading = loading,
                    enabled = cooldown == 0,
                    onClick = { sendLink() },
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { goTo(ResetStep.Request) }, enabled = !loading) {
                    Text("Use a different email", fontWeight = FontWeight.W700)
                }
            }

            // ---------------- PAGE 3 ----------------
            ResetStep.Update -> {
                AuthHeader(
                    title = "Set a new password",
                    subtitle = "Choose a new password for your account",
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        if (passwordError != null) passwordError = null
                        if (confirm.isNotEmpty()) {
                            confirmError = if (confirm != it) "Passwords do not match" else null
                        }
                    },
                    enabled = !loading,
                    singleLine = true,
                    isError = passwordError != null,
                    visualTransformation = if (obscure) PasswordVisualTransformation()
                    else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(onNext = { confirmFocus.requestFocusSafely() }),
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    trailingIcon = { PasswordVisibilityToggle(obscure) { obscure = !obscure } },
                    placeholder = { Text("New password") },
                    supportingText = {
                        Text(passwordError ?: "At least 8 characters, with a letter and a number.")
                    },
                    shape = AuthFieldShape,
                    colors = authFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.NewPassword },
                )

                AnimatedVisibility(visible = password.isNotEmpty()) {
                    StrengthMeter(
                        password = password,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = confirm,
                    onValueChange = {
                        confirm = it
                        confirmError = if (it != password) "Passwords do not match" else null
                    },
                    enabled = !loading,
                    singleLine = true,
                    isError = confirmError != null,
                    visualTransformation = if (obscure) PasswordVisualTransformation()
                    else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (needsOtp) ImeAction.Next else ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { otpFocus.requestFocusSafely() },
                        onDone = { submitUpdate() },
                    ),
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    trailingIcon = { PasswordVisibilityToggle(obscure) { obscure = !obscure } },
                    placeholder = { Text("Confirm password") },
                    supportingText = { confirmError?.let { Text(it) } },
                    shape = AuthFieldShape,
                    colors = authFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(confirmFocus)
                        .semantics { contentType = ContentType.NewPassword },
                )

                AnimatedVisibility(visible = needsOtp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = otp,
                            onValueChange = { raw ->
                                otp = raw.filter { it.isDigit() }.take(6)
                                otpError = if (otp.length == 6) null
                                else "Enter the 6-digit code from the email"
                            },
                            enabled = !loading,
                            singleLine = true,
                            isError = otpError != null,
                            textStyle = TextStyle(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.W700,
                                letterSpacing = 6.sp,
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { submitUpdate() }),
                            leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                            placeholder = { Text("6-digit code") },
                            supportingText = { otpError?.let { Text(it) } },
                            shape = AuthFieldShape,
                            colors = authFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(otpFocus),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { resendOtp() }, enabled = !loading) {
                                Text("Resend code", fontWeight = FontWeight.W700)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                AuthPrimaryButton(
                    text = if (needsOtp) "Verify and update password" else "Update password",
                    loading = loading,
                    onClick = { submitUpdate() },
                )
            }
        }
    }
}

/** Visual-only hint; real rules are enforced by authService.passwordError(). */
@Composable
private fun StrengthMeter(password: String, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val score = listOf(
        password.length >= 8,
        password.any { it.isLetter() } && password.any { it.isDigit() },
        password.length >= 12,
        password.any { !it.isLetterOrDigit() },
    ).count { it }

    val (label, tint) = when (score) {
        0, 1 -> "Weak" to c.error
        2 -> "Fair" to Color(0xFFF59E0B)
        3 -> "Good" to c.primary
        else -> "Strong" to c.tertiary
    }
    val animated by animateColorAsState(tint, label = "strength")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(4) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (i < score.coerceAtLeast(1)) animated else c.outlineVariant),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(color = animated),
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}