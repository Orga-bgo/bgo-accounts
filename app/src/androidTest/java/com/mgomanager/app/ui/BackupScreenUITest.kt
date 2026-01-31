package com.mgomanager.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mgomanager.app.data.model.Account
import com.mgomanager.app.ui.screens.backup.*
import com.mgomanager.app.ui.theme.MGOManagerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI Tests for BackupScreen (P3 - Backup Tab 1: Account sichern)
 */
@RunWith(AndroidJUnit4::class)
class BackupScreenUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testAccount = Account(
        id = 1,
        accountName = "TestAccount",
        prefix = "MGO_",
        createdAt = System.currentTimeMillis(),
        lastPlayedAt = System.currentTimeMillis(),
        userId = "user123456",
        backupPath = "/storage/emulated/0/bgo_backups/MGO_TestAccount/",
        fileOwner = "u0_a123",
        fileGroup = "u0_a123",
        filePermissions = "rwxr-xr-x"
    )

    // ============================================================
    // NameInputStep Tests
    // ============================================================

    @Test
    fun nameInputStep_displaysTitle() {
        composeTestRule.setContent {
            MGOManagerTheme {
                NameInputStep(
                    accountName = "",
                    prefix = "MGO_",
                    onAccountNameChange = {},
                    onNext = {},
                    error = null
                )
            }
        }

        composeTestRule.onNodeWithText("Account sichern").assertIsDisplayed()
    }

    @Test
    fun nameInputStep_displaysPrefix() {
        composeTestRule.setContent {
            MGOManagerTheme {
                NameInputStep(
                    accountName = "",
                    prefix = "MGO_",
                    onAccountNameChange = {},
                    onNext = {},
                    error = null
                )
            }
        }

        composeTestRule.onNodeWithText("Präfix:").assertIsDisplayed()
        composeTestRule.onNodeWithText("MGO_").assertIsDisplayed()
    }

    @Test
    fun nameInputStep_displaysAccountNameInput() {
        composeTestRule.setContent {
            MGOManagerTheme {
                NameInputStep(
                    accountName = "",
                    prefix = "MGO_",
                    onAccountNameChange = {},
                    onNext = {},
                    error = null
                )
            }
        }

        composeTestRule.onNodeWithText("Accountname").assertIsDisplayed()
    }

    @Test
    fun nameInputStep_weiterButtonDisabled_whenNameEmpty() {
        composeTestRule.setContent {
            MGOManagerTheme {
                NameInputStep(
                    accountName = "",
                    prefix = "MGO_",
                    onAccountNameChange = {},
                    onNext = {},
                    error = null
                )
            }
        }

        composeTestRule.onNodeWithText("Weiter").assertIsNotEnabled()
    }

    @Test
    fun nameInputStep_weiterButtonEnabled_whenNameEntered() {
        composeTestRule.setContent {
            MGOManagerTheme {
                NameInputStep(
                    accountName = "TestAccount",
                    prefix = "MGO_",
                    onAccountNameChange = {},
                    onNext = {},
                    error = null
                )
            }
        }

        composeTestRule.onNodeWithText("Weiter").assertIsEnabled()
    }

    @Test
    fun nameInputStep_showsFullNamePreview() {
        composeTestRule.setContent {
            MGOManagerTheme {
                NameInputStep(
                    accountName = "TestAccount",
                    prefix = "MGO_",
                    onAccountNameChange = {},
                    onNext = {},
                    error = null
                )
            }
        }

        composeTestRule.onNodeWithText("Voller Name: MGO_TestAccount").assertIsDisplayed()
    }

    @Test
    fun nameInputStep_showsError_whenErrorProvided() {
        composeTestRule.setContent {
            MGOManagerTheme {
                NameInputStep(
                    accountName = "",
                    prefix = "MGO_",
                    onAccountNameChange = {},
                    onNext = {},
                    error = "Bitte gib einen Account-Namen ein"
                )
            }
        }

        composeTestRule.onNodeWithText("Bitte gib einen Account-Namen ein").assertIsDisplayed()
    }

    @Test
    fun nameInputStep_callsOnNext_whenWeiterClicked() {
        var nextClicked = false
        composeTestRule.setContent {
            MGOManagerTheme {
                NameInputStep(
                    accountName = "TestAccount",
                    prefix = "MGO_",
                    onAccountNameChange = {},
                    onNext = { nextClicked = true },
                    error = null
                )
            }
        }

        composeTestRule.onNodeWithText("Weiter").performClick()

        assert(nextClicked)
    }

    // ============================================================
    // SocialMediaStep Tests
    // ============================================================

    @Test
    fun socialMediaStep_displaysTitle() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SocialMediaStep(
                    hasFacebookLink = false,
                    onHasFacebookLinkChange = {},
                    onNext = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Social Media Verbindung").assertIsDisplayed()
    }

    @Test
    fun socialMediaStep_displaysYesNoOptions() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SocialMediaStep(
                    hasFacebookLink = false,
                    onHasFacebookLinkChange = {},
                    onNext = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Ja").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nein").assertIsDisplayed()
    }

    @Test
    fun socialMediaStep_displaysNavigationButtons() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SocialMediaStep(
                    hasFacebookLink = false,
                    onHasFacebookLinkChange = {},
                    onNext = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Zurück").assertIsDisplayed()
        composeTestRule.onNodeWithText("Weiter").assertIsDisplayed()
    }

    @Test
    fun socialMediaStep_callsOnBack_whenZurückClicked() {
        var backClicked = false
        composeTestRule.setContent {
            MGOManagerTheme {
                SocialMediaStep(
                    hasFacebookLink = false,
                    onHasFacebookLinkChange = {},
                    onNext = {},
                    onBack = { backClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Zurück").performClick()

        assert(backClicked)
    }

    // ============================================================
    // FacebookDetailsStep Tests
    // ============================================================

    @Test
    fun facebookDetailsStep_displaysTitle() {
        composeTestRule.setContent {
            MGOManagerTheme {
                FacebookDetailsStep(
                    fbUsername = "",
                    fbPassword = "",
                    fb2FA = "",
                    fbTempMail = "",
                    onFbUsernameChange = {},
                    onFbPasswordChange = {},
                    onFb2FAChange = {},
                    onFbTempMailChange = {},
                    onNext = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Facebook-Daten").assertIsDisplayed()
    }

    @Test
    fun facebookDetailsStep_displaysInputFields() {
        composeTestRule.setContent {
            MGOManagerTheme {
                FacebookDetailsStep(
                    fbUsername = "",
                    fbPassword = "",
                    fb2FA = "",
                    fbTempMail = "",
                    onFbUsernameChange = {},
                    onFbPasswordChange = {},
                    onFb2FAChange = {},
                    onFbTempMailChange = {},
                    onNext = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Nutzername / E-Mail").assertIsDisplayed()
        composeTestRule.onNodeWithText("Passwort").assertIsDisplayed()
        composeTestRule.onNodeWithText("2FA-Code (optional)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Temp-Mail (optional)").assertIsDisplayed()
    }

    @Test
    fun facebookDetailsStep_displaysBackupStartenButton() {
        composeTestRule.setContent {
            MGOManagerTheme {
                FacebookDetailsStep(
                    fbUsername = "",
                    fbPassword = "",
                    fb2FA = "",
                    fbTempMail = "",
                    onFbUsernameChange = {},
                    onFbPasswordChange = {},
                    onFb2FAChange = {},
                    onFbTempMailChange = {},
                    onNext = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Backup starten").assertIsDisplayed()
    }

    // ============================================================
    // BackupProgressStep Tests
    // ============================================================

    @Test
    fun backupProgressStep_showsProgressIndicator() {
        composeTestRule.setContent {
            MGOManagerTheme {
                BackupProgressStep(
                    progressMessage = "Backup wird erstellt..."
                )
            }
        }

        composeTestRule.onNode(hasProgressBarRangeInfo(0f..1f)).assertExists()
    }

    @Test
    fun backupProgressStep_showsProgressMessage() {
        composeTestRule.setContent {
            MGOManagerTheme {
                BackupProgressStep(
                    progressMessage = "Backup wird erstellt..."
                )
            }
        }

        composeTestRule.onNodeWithText("Backup wird erstellt...").assertIsDisplayed()
    }

    // ============================================================
    // BackupSuccessStep Tests
    // ============================================================

    @Test
    fun backupSuccessStep_displaysSuccessMessage() {
        composeTestRule.setContent {
            MGOManagerTheme {
                BackupSuccessStep(
                    account = testAccount,
                    onViewAccount = {},
                    onLaunchMonopolyGo = {},
                    onGoToOverview = {},
                    isLoading = false
                )
            }
        }

        composeTestRule.onNodeWithText("Backup erfolgreich!").assertIsDisplayed()
    }

    @Test
    fun backupSuccessStep_displaysAccountName() {
        composeTestRule.setContent {
            MGOManagerTheme {
                BackupSuccessStep(
                    account = testAccount,
                    onViewAccount = {},
                    onLaunchMonopolyGo = {},
                    onGoToOverview = {},
                    isLoading = false
                )
            }
        }

        composeTestRule.onNodeWithText("MGO_TestAccount").assertIsDisplayed()
    }

    @Test
    fun backupSuccessStep_displaysAllActionButtons() {
        composeTestRule.setContent {
            MGOManagerTheme {
                BackupSuccessStep(
                    account = testAccount,
                    onViewAccount = {},
                    onLaunchMonopolyGo = {},
                    onGoToOverview = {},
                    isLoading = false
                )
            }
        }

        composeTestRule.onNodeWithText("Account anzeigen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Monopoly Go starten").assertIsDisplayed()
        composeTestRule.onNodeWithText("Zur Übersicht").assertIsDisplayed()
    }

    @Test
    fun backupSuccessStep_buttonsDisabled_whenLoading() {
        composeTestRule.setContent {
            MGOManagerTheme {
                BackupSuccessStep(
                    account = testAccount,
                    onViewAccount = {},
                    onLaunchMonopolyGo = {},
                    onGoToOverview = {},
                    isLoading = true
                )
            }
        }

        composeTestRule.onNodeWithText("Account anzeigen").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Zur Übersicht").assertIsNotEnabled()
    }

    @Test
    fun backupSuccessStep_callsOnViewAccount() {
        var viewAccountClicked = false
        composeTestRule.setContent {
            MGOManagerTheme {
                BackupSuccessStep(
                    account = testAccount,
                    onViewAccount = { viewAccountClicked = true },
                    onLaunchMonopolyGo = {},
                    onGoToOverview = {},
                    isLoading = false
                )
            }
        }

        composeTestRule.onNodeWithText("Account anzeigen").performClick()

        assert(viewAccountClicked)
    }

    @Test
    fun backupSuccessStep_callsOnLaunchMonopolyGo() {
        var launchClicked = false
        composeTestRule.setContent {
            MGOManagerTheme {
                BackupSuccessStep(
                    account = testAccount,
                    onViewAccount = {},
                    onLaunchMonopolyGo = { launchClicked = true },
                    onGoToOverview = {},
                    isLoading = false
                )
            }
        }

        composeTestRule.onNodeWithText("Monopoly Go starten").performClick()

        assert(launchClicked)
    }

    // ============================================================
    // BackupErrorStep Tests
    // ============================================================

    @Test
    fun backupErrorStep_displaysErrorTitle() {
        composeTestRule.setContent {
            MGOManagerTheme {
                BackupErrorStep(
                    errorMessage = "Da ist etwas schief gelaufen.. Prüfe den Log",
                    duplicateInfo = null,
                    onRetry = {},
                    onGoBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Backup fehlgeschlagen").assertIsDisplayed()
    }

    @Test
    fun backupErrorStep_displaysErrorMessage() {
        composeTestRule.setContent {
            MGOManagerTheme {
                BackupErrorStep(
                    errorMessage = "Da ist etwas schief gelaufen.. Prüfe den Log",
                    duplicateInfo = null,
                    onRetry = {},
                    onGoBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Da ist etwas schief gelaufen.. Prüfe den Log").assertIsDisplayed()
    }

    @Test
    fun backupErrorStep_displaysDuplicateInfo_whenProvided() {
        composeTestRule.setContent {
            MGOManagerTheme {
                BackupErrorStep(
                    errorMessage = "User ID bereits vorhanden",
                    duplicateInfo = DuplicateInfo(
                        userId = "user123456",
                        existingAccountName = "ExistingAccount"
                    ),
                    onRetry = {},
                    onGoBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("User ID: user123456").assertIsDisplayed()
    }

    @Test
    fun backupErrorStep_displaysRetryButton() {
        composeTestRule.setContent {
            MGOManagerTheme {
                BackupErrorStep(
                    errorMessage = "Error",
                    duplicateInfo = null,
                    onRetry = {},
                    onGoBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Erneut versuchen").assertIsDisplayed()
    }

    @Test
    fun backupErrorStep_callsOnRetry() {
        var retryClicked = false
        composeTestRule.setContent {
            MGOManagerTheme {
                BackupErrorStep(
                    errorMessage = "Error",
                    duplicateInfo = null,
                    onRetry = { retryClicked = true },
                    onGoBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Erneut versuchen").performClick()

        assert(retryClicked)
    }

    // ============================================================
    // BackupTab2Placeholder Tests
    // ============================================================

    @Test
    fun backupTab2Placeholder_displaysTitle() {
        composeTestRule.setContent {
            MGOManagerTheme {
                BackupTab2Placeholder()
            }
        }

        composeTestRule.onNodeWithText("Account erstellen").assertIsDisplayed()
    }

    @Test
    fun backupTab2Placeholder_displaysPlaceholderMessage() {
        composeTestRule.setContent {
            MGOManagerTheme {
                BackupTab2Placeholder()
            }
        }

        composeTestRule.onNodeWithText("Diese Funktion wird in P4 implementiert.").assertIsDisplayed()
    }
}
