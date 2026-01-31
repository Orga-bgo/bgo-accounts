package com.mgomanager.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mgomanager.app.data.repository.AppStateRepository
import com.mgomanager.app.ui.navigation.MainScreen
import com.mgomanager.app.ui.screens.onboarding.OnboardingScreen
import com.mgomanager.app.ui.screens.splash.SplashScreen
import com.mgomanager.app.ui.screens.systemcheck.SystemCheckScreen
import com.mgomanager.app.ui.theme.MGOManagerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Navigation routes for the app startup flow
 */
sealed class AppRoute(val route: String) {
    object Splash : AppRoute("splash")
    object Onboarding : AppRoute("onboarding")
    object SystemCheck : AppRoute("systemCheck")
    object Home : AppRoute("home")
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appStateRepository: AppStateRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MGOManagerTheme {
                val navController = rememberNavController()
                var startDestination by remember { mutableStateOf<String?>(null) }

                // Determine start destination based on onboarding status
                LaunchedEffect(Unit) {
                    startDestination = determineStartDestination()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    startDestination?.let { destination ->
                        NavHost(
                            navController = navController,
                            startDestination = AppRoute.Splash.route
                        ) {
                            // Splash Screen (500ms)
                            composable(AppRoute.Splash.route) {
                                SplashScreen(
                                    onSplashComplete = {
                                        navController.navigate(destination) {
                                            popUpTo(AppRoute.Splash.route) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            // Onboarding Flow (first launch)
                            composable(AppRoute.Onboarding.route) {
                                OnboardingScreen(
                                    onComplete = {
                                        // After onboarding, go to system check
                                        navController.navigate(AppRoute.SystemCheck.route) {
                                            popUpTo(AppRoute.Onboarding.route) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            // System Check Screen (normal app start)
                            composable(AppRoute.SystemCheck.route) {
                                SystemCheckScreen(
                                    onComplete = {
                                        navController.navigate(AppRoute.Home.route) {
                                            popUpTo(AppRoute.SystemCheck.route) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            // Main App with Bottom Navigation
                            composable(AppRoute.Home.route) {
                                MainScreen()
                            }
                        }
                    } ?: run {
                        // Loading state while determining start destination
                        SplashScreen(onSplashComplete = {})
                    }
                }
            }
        }
    }

    /**
     * Determines the start destination based on onboarding status:
     * - First launch or onboarding not completed → Onboarding
     * - Otherwise → System Check
     */
    private suspend fun determineStartDestination(): String {
        val isFirstLaunch = appStateRepository.isFirstLaunch()
        val onboardingCompleted = appStateRepository.isOnboardingCompleted()

        return when {
            isFirstLaunch || !onboardingCompleted -> AppRoute.Onboarding.route
            else -> AppRoute.SystemCheck.route
        }
    }
}
