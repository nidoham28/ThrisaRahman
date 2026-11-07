
package com.nidoham.thrisa.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
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
import kotlinx.coroutines.CancellationException

private const val NOTICE_KEY = "notice"

@Composable
fun ThrisaNavScaffold(
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    val context = LocalContext.current.applicationContext

    val loginStateStorage = remember(context) {
        LoginStateStorage(context)
    }

    // Shared authentication service.
    val authService = remember {
        SupabaseAuthService()
    }

    var checkingSignIn by remember {
        mutableStateOf(true)
    }

    var startupOffline by remember {
        mutableStateOf(false)
    }

    var canContinueOffline by remember {
        mutableStateOf(false)
    }

    var retryStartup by remember {
        mutableStateOf(0)
    }

    NavHost(
        navController = navController,
        startDestination = Routes.Splash,
        modifier = modifier
    ) {

        // -------------------------------------------------
        // SPLASH
        // -------------------------------------------------

        composable(Routes.Splash) {

            LaunchedEffect(retryStartup) {

                checkingSignIn = true
                startupOffline = false
                canContinueOffline = false

                try {
                    authService.awaitInitialization()

                    val cachedLogin =
                        loginStateStorage.getLastKnownLoggedIn() == true

                    val hasInternet = hasValidatedInternet(context)

                    if (!hasInternet) {
                        canContinueOffline = cachedLogin
                        startupOffline = true
                        checkingSignIn = false
                        return@LaunchedEffect
                    }

                    authService.verifySessionWithServer().fold(

                        onSuccess = { isLoggedIn ->

                            loginStateStorage.saveLoggedIn(isLoggedIn)

                            navigateToRoot(
                                navController = navController,
                                destination = if (isLoggedIn) {
                                    Routes.Main
                                } else {
                                    Routes.Login
                                }
                            )
                        },

                        onFailure = {

                            // Network or server verification failure.
                            // Allow cached users to continue offline.
                            canContinueOffline = cachedLogin
                            startupOffline = true
                            checkingSignIn = false
                        }
                    )

                } catch (error: CancellationException) {
                    throw error

                } catch (error: Exception) {

                    val cachedLogin =
                        loginStateStorage.getLastKnownLoggedIn() == true

                    canContinueOffline = cachedLogin
                    startupOffline = true
                    checkingSignIn = false
                }
            }

            SplashScreen(
                isChecking = checkingSignIn,
                isOffline = startupOffline,
                canContinueOffline = canContinueOffline,

                onRetry = {
                    retryStartup++
                },

                onContinueOffline = {
                    navigateToRoot(
                        navController = navController,
                        destination = Routes.Main
                    )
                }
            )
        }

        // -------------------------------------------------
        // LOGIN
        // -------------------------------------------------

        composable(Routes.Login) { entry ->

            val notice by entry.savedStateHandle
                .getStateFlow<String?>(NOTICE_KEY, null)
                .collectAsState()

            LoginScreen(
                authService = authService,

                onLoggedIn = {
                    loginStateStorage.saveLoggedIn(true)

                    navigateToRoot(
                        navController,
                        Routes.Main
                    )
                },

                onRegisterClick = {
                    navController.navigate(Routes.Register)
                },

                onForgotPasswordClick = {
                    navController.navigate(Routes.ForgotPassword)
                },

                notice = notice,

                onNoticeShown = {
                    entry.savedStateHandle[NOTICE_KEY] = null
                }
            )
        }

        // -------------------------------------------------
        // REGISTER
        // -------------------------------------------------

        composable(Routes.Register) {

            RegisterScreen(
                authService = authService,

                onBack = {
                    navController.popBackStack()
                },

                onRegistered = {

                    loginStateStorage.saveLoggedIn(true)

                    navigateToRoot(
                        navController,
                        Routes.Main
                    )
                },

                onRequiresConfirmation = {

                    navigateToRoot(
                        navController,
                        Routes.Login
                    )

                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set(
                            NOTICE_KEY,
                            "Check your email to confirm your account, then sign in."
                        )
                },

                onLogin = {
                    navController.popBackStack()
                }
            )
        }

        // -------------------------------------------------
        // FORGOT PASSWORD
        // -------------------------------------------------

        composable(Routes.ForgotPassword) {

            ResetPasswordScreen(
                authService = authService,

                onBack = {
                    navController.popBackStack()
                },

                onCompleted = {

                    navigateToRoot(
                        navController,
                        Routes.Login
                    )

                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set(
                            NOTICE_KEY,
                            "Password updated. Sign in with your new password."
                        )
                }
            )
        }

        // -------------------------------------------------
        // MAIN
        // -------------------------------------------------

        composable(Routes.Main) {

            MainScreen(
                onOpenSettings = {
                    navController.navigate(Routes.Settings) {
                        launchSingleTop = true
                    }
                },

                onOpenSchedule = {
                    navController.navigate(Routes.Schedule) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // -------------------------------------------------
        // SETTINGS
        // -------------------------------------------------

        composable(Routes.Settings) {

            SettingsScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // -------------------------------------------------
        // SCHEDULE
        // -------------------------------------------------

        composable(Routes.Schedule) {

            ScheduleScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

// -----------------------------------------------------
// ROOT NAVIGATION HELPER
// -----------------------------------------------------

private fun navigateToRoot(
    navController: NavHostController,
    destination: String
) {
    navController.navigate(destination) {
        popUpTo(Routes.Splash) {
            inclusive = true
        }

        launchSingleTop = true
    }
}
