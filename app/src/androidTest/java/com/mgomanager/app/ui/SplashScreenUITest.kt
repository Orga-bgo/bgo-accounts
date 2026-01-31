package com.mgomanager.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mgomanager.app.ui.screens.splash.SplashScreen
import com.mgomanager.app.ui.theme.MGOManagerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI Tests for Splash Screen
 */
@RunWith(AndroidJUnit4::class)
class SplashScreenUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun splashScreen_displaysAppName() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SplashScreen(onSplashComplete = {})
            }
        }

        composeTestRule.onNodeWithText("babixGO").assertIsDisplayed()
    }

    @Test
    fun splashScreen_displaysSubtitle() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SplashScreen(onSplashComplete = {})
            }
        }

        composeTestRule.onNodeWithText("Account Manager").assertIsDisplayed()
    }

    @Test
    fun splashScreen_showsLoadingIndicator() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SplashScreen(onSplashComplete = {})
            }
        }

        // Verify progress indicator exists
        composeTestRule.onNode(hasProgressBarRangeInfo(0f..1f)).assertExists()
    }

    @Test
    fun splashScreen_callsOnCompleteAfterDelay() {
        var completed = false
        composeTestRule.setContent {
            MGOManagerTheme {
                SplashScreen(onSplashComplete = { completed = true })
            }
        }

        // Wait for splash to complete (500ms + animation time)
        composeTestRule.waitUntil(timeoutMillis = 2000) {
            completed
        }

        assert(completed)
    }
}
