package com.nidoham.thrisa.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nidoham.thrisa.core.service.SupabaseAuthService
import com.nidoham.thrisa.core.service.hasValidatedInternet
import com.nidoham.thrisa.domain.LoginStateStorage
import com.nidoham.thrisa.presentation.auth.pages.LoginScreen
import com.nidoham.thrisa.presentation.auth.pages.RegisterScreen
import com.nidoham.thrisa.presentation.auth.pages.ResetPasswordScreen
import com.nidoham.thrisa.presentation.main.MainScreen
import com.nidoham.thrisa.presentation.schedule.ScheduleScreen
import com.nidoham.thrisa.presentation.settings.SettingsScreen
import com.nidoham.thrisa.presentation.splash.SplashScreen

private const val NOTICE_KEY = "notice"

@Composable
fun ThrisaNavScaffold() {
    val navController = rememberNavController()
    val context = LocalContext.current.applicationContext
    val loginStateStorage = remember(context) { LoginStateStorage(context) }
    // One instance shared by every auth screen, so session state stays consistent.
    val authService = remember { SupabaseAuthService() }
    var checkingSignIn by remember { mutableStateOf(true) }
    var startupOffline by remember { mutableStateOf(false) }
    var canContinueOffline by remember { mutableStateOf(false) }
    var retryStartup by remember { mutableStateOf(0) }

    NavHost(navController = navController, startDestination = Routes.Splash) {

        // ---- Splash: decide where to send the user based on session state ----
        composable(Routes.Splash) {
            LaunchedEffect(retryStartup) {
                checkingSignIn = true
                startupOffline = false
                canContinueOffline = false
                authService.awaitInitialization()

                val hasInternet = hasValidatedInternet(context)
                val cachedLogin = loginStateStorage.getLastKnownLoggedIn() == true
                if (!hasInternet) {
                    canContinueOffline = cachedLogin
                    startupOffline = true
                    checkingSignIn = false
                    return@LaunchedEffect
                }

                authService.verifySessionWithServer().fold(
                    onSuccess = { isLoggedIn ->
                        loginStateStorage.saveLoggedIn(isLoggedIn)
                        val destination = if (isLoggedIn) Routes.Main else Routes.Login
                        navController.navigate(destination) {
                            popUpTo(Routes.Splash) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onFailure = {
                        canContinueOffline = cachedLogin
                        startupOffline = true
                        checkingSignIn = false
                    }
                )
            }

            SplashScreen(
                isChecking = checkingSignIn,
                isOffline = startupOffline,
                canContinueOffline = canContinueOffline,
                onRetry = { retryStartup++ },
                onContinueOffline = {
                    navController.navigate(Routes.Main) {
                        popUpTo(Routes.Splash) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        // ---- Login: root of the auth flow, no back button ----
        composable(Routes.Login) { entry ->
            // Read the one-off notice from the entry's saved state so it survives config changes.
            val notice by entry.savedStateHandle
                .getStateFlow<String?>(NOTICE_KEY, null)
                .collectAsState()

            LoginScreen(
                authService = authService,
                onLoggedIn = {
                    loginStateStorage.saveLoggedIn(true)
                    navController.navigate(Routes.Main) {
                        popUpTo(Routes.Login) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onRegisterClick = { navController.navigate(Routes.Register) },
                onForgotPasswordClick = { navController.navigate(Routes.ForgotPassword) },
                notice = notice,
                onNoticeShown = { entry.savedStateHandle[NOTICE_KEY] = null },
            )
        }

        // ---- Register ----
        composable(Routes.Register) {
            RegisterScreen(
                authService = authService,
                onBack = { navController.popBackStack() },
                onRegistered = {
                    // Auto-confirmed: straight into the app.
                    loginStateStorage.saveLoggedIn(true)
                    navController.navigate(Routes.Main) {
                        popUpTo(Routes.Login) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onRequiresConfirmation = {
                    // Fresh Login screen with a one-off notice.
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.Login) { inclusive = true }
                        launchSingleTop = true
                    }
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set(NOTICE_KEY, "Check your email to confirm your account, then sign in.")
                },
                onLogin = { navController.popBackStack() },
            )
        }

        // ---- Forgot password ----
        composable(Routes.ForgotPassword) {
            ResetPasswordScreen(
                authService = authService,
                onBack = { navController.popBackStack() },
                onCompleted = {
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.Login) { inclusive = true }
                        launchSingleTop = true
                    }
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set(NOTICE_KEY, "Password updated. Sign in with your new password.")
                },
            )
        }

        // ---- App ----
        composable(Routes.Main) {
            MainScreen(
                onOpenSettings = { navController.navigate(Routes.Settings) },
                onOpenSchedule = { navController.navigate(Routes.Schedule) }
            )
        }
        composable(Routes.Settings) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.Schedule) {
            ScheduleScreen(onBack = { navController.popBackStack() })
        }
    }
}
