package com.mgomanager.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mgomanager.app.ui.screens.onboarding.*
import com.mgomanager.app.ui.theme.MGOManagerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI Tests for Onboarding Screens
 */
@RunWith(AndroidJUnit4::class)
class OnboardingUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ============================================================
    // Welcome Screen Tests
    // ============================================================

    @Test
    fun welcomeScreen_displaysCorrectContent() {
        composeTestRule.setContent {
            MGOManagerTheme {
                WelcomeScreen(onNext = {})
            }
        }

        composeTestRule.onNodeWithText("Willkommen bei").assertIsDisplayed()
        composeTestRule.onNodeWithText("babixGO").assertIsDisplayed()
        composeTestRule.onNodeWithText("Los geht's").assertIsDisplayed()
    }

    @Test
    fun welcomeScreen_nextButtonCallsCallback() {
        var nextClicked = false
        composeTestRule.setContent {
            MGOManagerTheme {
                WelcomeScreen(onNext = { nextClicked = true })
            }
        }

        composeTestRule.onNodeWithText("Los geht's").performClick()

        assert(nextClicked)
    }

    // ============================================================
    // Import Check Screen Tests
    // ============================================================

    @Test
    fun importCheckScreen_showsImportOption_whenZipFound() {
        composeTestRule.setContent {
            MGOManagerTheme {
                ImportCheckScreen(
                    zipFound = true,
                    zipPath = "/storage/emulated/0/Download/test.zip",
                    isLoading = false,
                    error = null,
                    onImport = {},
                    onSkip = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Importieren").assertIsDisplayed()
        composeTestRule.onNodeWithText("Überspringen").assertIsDisplayed()
    }

    @Test
    fun importCheckScreen_showsWeiterButton_whenNoZipFound() {
        composeTestRule.setContent {
            MGOManagerTheme {
                ImportCheckScreen(
                    zipFound = false,
                    zipPath = null,
                    isLoading = false,
                    error = null,
                    onImport = {},
                    onSkip = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Weiter").assertIsDisplayed()
    }

    @Test
    fun importCheckScreen_showsLoadingIndicator_whenLoading() {
        composeTestRule.setContent {
            MGOManagerTheme {
                ImportCheckScreen(
                    zipFound = true,
                    zipPath = "/test.zip",
                    isLoading = true,
                    error = null,
                    onImport = {},
                    onSkip = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNode(hasProgressBarRangeInfo(0f..1f)).assertExists()
    }

    @Test
    fun importCheckScreen_showsError_whenErrorOccurs() {
        val errorMessage = "Import fehlgeschlagen"
        composeTestRule.setContent {
            MGOManagerTheme {
                ImportCheckScreen(
                    zipFound = true,
                    zipPath = "/test.zip",
                    isLoading = false,
                    error = errorMessage,
                    onImport = {},
                    onSkip = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    }

    // ============================================================
    // Prefix Setup Screen Tests
    // ============================================================

    @Test
    fun prefixSetupScreen_displaysInputField() {
        composeTestRule.setContent {
            MGOManagerTheme {
                PrefixSetupScreen(
                    prefix = "",
                    onPrefixChanged = {},
                    onSave = {},
                    onSkip = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Präfix").assertIsDisplayed()
        composeTestRule.onNodeWithText("Speichern & Weiter").assertIsDisplayed()
        composeTestRule.onNodeWithText("Später festlegen").assertIsDisplayed()
    }

    @Test
    fun prefixSetupScreen_saveButtonDisabled_whenPrefixEmpty() {
        composeTestRule.setContent {
            MGOManagerTheme {
                PrefixSetupScreen(
                    prefix = "",
                    onPrefixChanged = {},
                    onSave = {},
                    onSkip = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Speichern & Weiter").assertIsNotEnabled()
    }

    @Test
    fun prefixSetupScreen_saveButtonEnabled_whenPrefixEntered() {
        composeTestRule.setContent {
            MGOManagerTheme {
                PrefixSetupScreen(
                    prefix = "MGO_",
                    onPrefixChanged = {},
                    onSave = {},
                    onSkip = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Speichern & Weiter").assertIsEnabled()
    }

    // ============================================================
    // SSH Setup Screen Tests
    // ============================================================

    @Test
    fun sshSetupScreen_hidesFields_whenDisabled() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SshSetupScreen(
                    enabled = false,
                    host = "",
                    port = "22",
                    username = "",
                    password = "",
                    error = null,
                    onEnabledChanged = {},
                    onHostChanged = {},
                    onPortChanged = {},
                    onUsernameChanged = {},
                    onPasswordChanged = {},
                    onSave = {},
                    onSkip = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("SSH aktivieren").assertIsDisplayed()
        composeTestRule.onNodeWithText("Host").assertDoesNotExist()
    }

    @Test
    fun sshSetupScreen_showsFields_whenEnabled() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SshSetupScreen(
                    enabled = true,
                    host = "",
                    port = "22",
                    username = "",
                    password = "",
                    error = null,
                    onEnabledChanged = {},
                    onHostChanged = {},
                    onPortChanged = {},
                    onUsernameChanged = {},
                    onPasswordChanged = {},
                    onSave = {},
                    onSkip = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Host").assertIsDisplayed()
        composeTestRule.onNodeWithText("Port").assertIsDisplayed()
        composeTestRule.onNodeWithText("Benutzername").assertIsDisplayed()
        composeTestRule.onNodeWithText("Passwort").assertIsDisplayed()
    }

    // ============================================================
    // Backup Directory Screen Tests
    // ============================================================

    @Test
    fun backupDirectoryScreen_displaysInputField() {
        composeTestRule.setContent {
            MGOManagerTheme {
                BackupDirectoryScreen(
                    directory = "/storage/emulated/0/bgo_backups/",
                    isLoading = false,
                    error = null,
                    onDirectoryChanged = {},
                    onSave = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Pfad").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ordner erstellen & Weiter").assertIsDisplayed()
    }

    // ============================================================
    // Root Permissions Screen Tests
    // ============================================================

    @Test
    fun rootPermissionsScreen_showsChecklistItems_afterRootGranted() {
        composeTestRule.setContent {
            MGOManagerTheme {
                RootPermissionsScreen(
                    isLoading = false,
                    rootGranted = true,
                    dataDataChecked = true,
                    monopolyGoInstalled = true,
                    monopolyGoUid = 10123,
                    error = null,
                    onRequestRoot = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Root-Zugriff").assertIsDisplayed()
        composeTestRule.onNodeWithText("/data/data Zugriff").assertIsDisplayed()
        composeTestRule.onNodeWithText("Monopoly Go erkannt").assertIsDisplayed()
        composeTestRule.onNodeWithText("UID: 10123").assertIsDisplayed()
    }

    @Test
    fun rootPermissionsScreen_showsError_whenRootDenied() {
        val errorMessage = "Root-Zugriff verweigert"
        composeTestRule.setContent {
            MGOManagerTheme {
                RootPermissionsScreen(
                    isLoading = false,
                    rootGranted = false,
                    dataDataChecked = false,
                    monopolyGoInstalled = false,
                    monopolyGoUid = null,
                    error = errorMessage,
                    onRequestRoot = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    }

    // ============================================================
    // Complete Screen Tests
    // ============================================================

    @Test
    fun completeScreen_displaysSuccessContent() {
        composeTestRule.setContent {
            MGOManagerTheme {
                CompleteScreen(onFinish = {})
            }
        }

        composeTestRule.onNodeWithText("Alles fertig!").assertIsDisplayed()
        composeTestRule.onNodeWithText("App starten").assertIsDisplayed()
    }

    @Test
    fun completeScreen_finishButtonCallsCallback() {
        var finishClicked = false
        composeTestRule.setContent {
            MGOManagerTheme {
                CompleteScreen(onFinish = { finishClicked = true })
            }
        }

        composeTestRule.onNodeWithText("App starten").performClick()

        assert(finishClicked)
    }

    // ============================================================
    // Checklist Item Tests
    // ============================================================

    @Test
    fun checklistItem_showsGreenIcon_whenChecked() {
        composeTestRule.setContent {
            MGOManagerTheme {
                ChecklistItem(
                    title = "Test Item",
                    checked = true,
                    subtitle = "Subtitle"
                )
            }
        }

        composeTestRule.onNodeWithText("Test Item").assertIsDisplayed()
        composeTestRule.onNodeWithText("Subtitle").assertIsDisplayed()
    }

    @Test
    fun checklistItem_showsGrayIcon_whenUnchecked() {
        composeTestRule.setContent {
            MGOManagerTheme {
                ChecklistItem(
                    title = "Unchecked Item",
                    checked = false
                )
            }
        }

        composeTestRule.onNodeWithText("Unchecked Item").assertIsDisplayed()
    }
}
