package com.mgomanager.app.ui.screens.detail

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.rememberNavController
import com.mgomanager.app.data.model.Account
import com.mgomanager.app.data.model.SusLevel
import com.mgomanager.app.ui.theme.MGOManagerTheme
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UI Tests for P5 Account Detail Screen
 * Verifies:
 * - Three actions: Restore, Edit, Delete
 * - Copy-to-clipboard functionality on all fields
 * - Delete dialog with proper warning text
 * - Edit dialog with all editable fields
 */
class DetailScreenUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: DetailViewModel

    private val testAccount = Account(
        id = 1L,
        accountName = "TestAccount",
        prefix = "test_",
        userId = "user123456789",
        ssaid = "a1b2c3d4e5f67890",
        gaid = "gaid-1234-5678-90ab",
        deviceToken = "token-abc-123",
        appSetId = "appset-xyz-789",
        backupPath = "/sdcard/backups/test_TestAccount/",
        fileOwner = "u0_a123",
        fileGroup = "u0_a123",
        filePermissions = "rwxr-xr-x",
        susLevel = SusLevel.NONE,
        hasError = false,
        hasFacebookLink = true,
        fbUsername = "facebook_user",
        fbPassword = "fb_pass_123",
        fb2FA = "654321",
        fbTempMail = "temp@mail.com"
    )

    @Before
    fun setup() {
        viewModel = mockk(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(
            DetailUiState(account = testAccount)
        )
    }

    @Test
    fun detailScreen_displaysAccountName() {
        composeTestRule.setContent {
            MGOManagerTheme {
                DetailScreen(
                    navController = rememberNavController(),
                    accountId = 1L,
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("test_TestAccount").assertIsDisplayed()
    }

    @Test
    fun detailScreen_displaysUserId() {
        composeTestRule.setContent {
            MGOManagerTheme {
                DetailScreen(
                    navController = rememberNavController(),
                    accountId = 1L,
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("MoGo User ID: user123456789").assertIsDisplayed()
    }

    @Test
    fun detailScreen_displaysThreeActionButtons() {
        composeTestRule.setContent {
            MGOManagerTheme {
                DetailScreen(
                    navController = rememberNavController(),
                    accountId = 1L,
                    viewModel = viewModel
                )
            }
        }

        // Three actions per P5 spec: Restore, Edit, Delete
        composeTestRule.onNodeWithText("WIEDERHERS\nTELLEN").assertIsDisplayed()
        composeTestRule.onNodeWithText("EDIT").assertIsDisplayed()
        composeTestRule.onNodeWithText("LÖSCHEN").assertIsDisplayed()
    }

    @Test
    fun detailScreen_displaysDeviceIds() {
        composeTestRule.setContent {
            MGOManagerTheme {
                DetailScreen(
                    navController = rememberNavController(),
                    accountId = 1L,
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Geräte-IDs").assertIsDisplayed()
        composeTestRule.onNodeWithText("SSAID").assertIsDisplayed()
        composeTestRule.onNodeWithText("a1b2c3d4e5f67890").assertIsDisplayed()
        composeTestRule.onNodeWithText("GAID").assertIsDisplayed()
        composeTestRule.onNodeWithText("gaid-1234-5678-90ab").assertIsDisplayed()
    }

    @Test
    fun detailScreen_displaysFacebookInfo_whenLinked() {
        composeTestRule.setContent {
            MGOManagerTheme {
                DetailScreen(
                    navController = rememberNavController(),
                    accountId = 1L,
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Facebook-Verbindung").assertIsDisplayed()
        composeTestRule.onNodeWithText("facebook_user").assertIsDisplayed()
    }

    @Test
    fun detailScreen_displaysBackupInfo() {
        composeTestRule.setContent {
            MGOManagerTheme {
                DetailScreen(
                    navController = rememberNavController(),
                    accountId = 1L,
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Backup-Info").assertIsDisplayed()
        composeTestRule.onNodeWithText("BACKUP-PFAD").assertIsDisplayed()
        composeTestRule.onNodeWithText("/sdcard/backups/test_TestAccount/").assertIsDisplayed()
    }

    @Test
    fun restoreButton_opensRestoreDialog() {
        every { viewModel.uiState } returns MutableStateFlow(
            DetailUiState(account = testAccount, showRestoreDialog = false)
        )

        composeTestRule.setContent {
            MGOManagerTheme {
                DetailScreen(
                    navController = rememberNavController(),
                    accountId = 1L,
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("WIEDERHERS\nTELLEN").performClick()

        verify { viewModel.showRestoreDialog() }
    }

    @Test
    fun editButton_opensEditDialog() {
        composeTestRule.setContent {
            MGOManagerTheme {
                DetailScreen(
                    navController = rememberNavController(),
                    accountId = 1L,
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("EDIT").performClick()

        verify { viewModel.showEditDialog() }
    }

    @Test
    fun deleteButton_opensDeleteDialog() {
        composeTestRule.setContent {
            MGOManagerTheme {
                DetailScreen(
                    navController = rememberNavController(),
                    accountId = 1L,
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("LÖSCHEN").performClick()

        verify { viewModel.showDeleteDialog() }
    }

    @Test
    fun deleteDialog_displaysWarningText() {
        every { viewModel.uiState } returns MutableStateFlow(
            DetailUiState(account = testAccount, showDeleteDialog = true)
        )

        composeTestRule.setContent {
            MGOManagerTheme {
                DetailScreen(
                    navController = rememberNavController(),
                    accountId = 1L,
                    viewModel = viewModel
                )
            }
        }

        // P5 spec warning text
        composeTestRule.onNodeWithText("Account löschen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dieser Account und alle zugehörigen Backups werden dauerhaft gelöscht.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Löschen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Abbrechen").assertIsDisplayed()
    }

    @Test
    fun editDialog_displaysAllEditableFields() {
        every { viewModel.uiState } returns MutableStateFlow(
            DetailUiState(account = testAccount, showEditDialog = true)
        )

        composeTestRule.setContent {
            MGOManagerTheme {
                DetailScreen(
                    navController = rememberNavController(),
                    accountId = 1L,
                    viewModel = viewModel
                )
            }
        }

        // P5 spec: Edit should allow editing all fields
        composeTestRule.onNodeWithText("Account bearbeiten").assertIsDisplayed()
        composeTestRule.onNodeWithText("Allgemein").assertIsDisplayed()
        composeTestRule.onNodeWithText("Geräte-IDs").assertIsDisplayed()
        composeTestRule.onNodeWithText("Backup").assertIsDisplayed()
    }

    @Test
    fun restoreSuccessDialog_showsLaunchOption() {
        every { viewModel.uiState } returns MutableStateFlow(
            DetailUiState(account = testAccount, showRestoreSuccessDialog = true)
        )

        composeTestRule.setContent {
            MGOManagerTheme {
                DetailScreen(
                    navController = rememberNavController(),
                    accountId = 1L,
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Wiederherstellung erfolgreich!").assertIsDisplayed()
        composeTestRule.onNodeWithText("Monopoly Go jetzt starten?").assertIsDisplayed()
    }

    @Test
    fun userIdField_isClickableToCopy() {
        composeTestRule.setContent {
            MGOManagerTheme {
                DetailScreen(
                    navController = rememberNavController(),
                    accountId = 1L,
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("MoGo User ID: user123456789").performClick()

        verify { viewModel.copyToClipboard("User ID", "user123456789") }
    }

    @Test
    fun ssaidField_isClickableToCopy() {
        composeTestRule.setContent {
            MGOManagerTheme {
                DetailScreen(
                    navController = rememberNavController(),
                    accountId = 1L,
                    viewModel = viewModel
                )
            }
        }

        // Click on the SSAID value to copy
        composeTestRule.onNodeWithText("a1b2c3d4e5f67890").performClick()

        verify { viewModel.copyToClipboard("SSAID", "a1b2c3d4e5f67890") }
    }

    @Test
    fun backToListLink_isDisplayed() {
        composeTestRule.setContent {
            MGOManagerTheme {
                DetailScreen(
                    navController = rememberNavController(),
                    accountId = 1L,
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("← Zurück zur Liste").assertIsDisplayed()
    }

    @Test
    fun suspensionStatus_isDisplayed() {
        composeTestRule.setContent {
            MGOManagerTheme {
                DetailScreen(
                    navController = rememberNavController(),
                    accountId = 1L,
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Suspension").assertIsDisplayed()
        composeTestRule.onNodeWithText(testAccount.susLevel.displayName).assertIsDisplayed()
    }

    @Test
    fun errorStatus_isDisplayed() {
        composeTestRule.setContent {
            MGOManagerTheme {
                DetailScreen(
                    navController = rememberNavController(),
                    accountId = 1L,
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Error").assertIsDisplayed()
        composeTestRule.onNodeWithText("nein").assertIsDisplayed()
    }

    @Test
    fun detailScreen_withNoFacebookLink_hidesFacebookCard() {
        val accountWithoutFb = testAccount.copy(hasFacebookLink = false)
        every { viewModel.uiState } returns MutableStateFlow(
            DetailUiState(account = accountWithoutFb)
        )

        composeTestRule.setContent {
            MGOManagerTheme {
                DetailScreen(
                    navController = rememberNavController(),
                    accountId = 1L,
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Facebook-Verbindung").assertDoesNotExist()
    }
}
