@file:Suppress("SpellCheckingInspection")

package com.naira.assistant.presentation.auth.pages

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.naira.assistant.core.service.RegisterResult
import com.naira.assistant.core.service.SupabaseAuthService
import com.naira.assistant.presentation.auth.components.AuthFieldShape
import com.naira.assistant.presentation.auth.components.AuthFooter
import com.naira.assistant.presentation.auth.components.AuthHeader
import com.naira.assistant.presentation.auth.components.AuthIllustration
import com.naira.assistant.presentation.auth.components.AuthPrimaryButton
import com.naira.assistant.presentation.auth.components.AuthScaffold
import com.naira.assistant.presentation.auth.components.GoogleButton
import com.naira.assistant.presentation.auth.components.PasswordVisibilityToggle
import com.naira.assistant.presentation.auth.components.SocialDivider
import com.naira.assistant.presentation.auth.components.authFieldColors
import com.naira.assistant.presentation.auth.components.showFeedback
import com.naira.assistant.presentation.auth.components.stripWhitespace
import com.naira.assistant.presentation.auth.components.validateEmail
import kotlinx.coroutines.launch

/**
 * @param onBack                 Shows the top-left back arrow when non-null.
 * @param onRegistered           A session exists, the user is logged in.
 * @param onRequiresConfirmation Email confirmation is on, the user must confirm first.
 */
@Composable
fun RegisterScreen(
    modifier: Modifier = Modifier,
    authService: SupabaseAuthService = remember { SupabaseAuthService() },
    onBack: (() -> Unit)? = null,
    onRegistered: (() -> Unit)? = null,
    onRequiresConfirmation: (() -> Unit)? = null,
    onLogin: (() -> Unit)? = null,
) {
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords are deliberately NOT saveable: they would be written into the saved-state bundle.
    var password by remember { mutableStateOf("") }
    var obscure by rememberSaveable { mutableStateOf(true) }
    // Not saveable on purpose: a restored "loading = true" would lock the form forever.
    var loading by remember { mutableStateOf(false) }
    var submitAttempted by rememberSaveable { mutableStateOf(false) }

    val emailError = if (submitAttempted) validateEmail(email.trim()) else null
    // Same policy as the reset-password screen (previously this one only required 6 characters).
    val passwordError = if (submitAttempted) authService.passwordError(password) else null

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val passwordFocus = remember { FocusRequester() }

    fun submit() {
        if (loading) return
        focusManager.clearFocus()

        submitAttempted = true
        if (validateEmail(email.trim()) != null || authService.passwordError(password) != null) return

        loading = true
        scope.launch {
            try {
                authService.register(email = email.trim(), password = password)
                    .onSuccess { result ->
                        when (result) {
                            RegisterResult.SignedIn -> onRegistered?.invoke()
                            RegisterResult.ConfirmationRequired -> onRequiresConfirmation?.invoke()
                        }
                    }
                    .onFailure {
                        scope.showFeedback(snackbarHostState, authService.friendlyMessage(it), isError = true)
                    }
            } finally {
                loading = false
            }
        }
    }

    AuthScaffold(
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        onNavigate = onBack,
        navigationEnabled = !loading,
    ) {
        AuthIllustration()
        AuthHeader(title = "Register", subtitle = "Create an account to get started")

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.stripWhitespace() },
            enabled = !loading,
            isError = emailError != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            leadingIcon = { Icon(Icons.Outlined.MailOutline, contentDescription = null) },
            placeholder = { Text("Email") },
            supportingText = { emailError?.let { Text(it) } },
            shape = AuthFieldShape,
            colors = authFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.EmailAddress },
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            enabled = !loading,
            isError = passwordError != null,
            singleLine = true,
            visualTransformation = if (obscure) PasswordVisualTransformation()
            else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            trailingIcon = { PasswordVisibilityToggle(obscure) { obscure = !obscure } },
            placeholder = { Text("Password") },
            supportingText = {
                Text(passwordError ?: "At least 8 characters, with a letter and a number.")
            },
            shape = AuthFieldShape,
            colors = authFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(passwordFocus)
                .semantics { contentType = ContentType.NewPassword },
        )
        Spacer(Modifier.height(16.dp))

        AuthPrimaryButton(text = "Register", loading = loading, onClick = { submit() })
        Spacer(Modifier.height(24.dp))

        SocialDivider()
        Spacer(Modifier.height(24.dp))

        GoogleButton(
            enabled = !loading,
            onClick = {
                focusManager.clearFocus()
                scope.showFeedback(snackbarHostState, "Google sign-in isn't set up yet.")
            },
        )
        Spacer(Modifier.height(28.dp))

        AuthFooter(
            prompt = "Already have an account?",
            action = "Login",
            onClick = onLogin,
            enabled = !loading,
        )
    }
}