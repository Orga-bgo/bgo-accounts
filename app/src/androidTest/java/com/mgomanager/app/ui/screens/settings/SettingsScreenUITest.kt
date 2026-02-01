package com.mgomanager.app.ui.screens.settings

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.rememberNavController
import com.mgomanager.app.domain.usecase.ImportAccountData
import com.mgomanager.app.ui.components.DuplicatePair
import com.mgomanager.app.ui.theme.MGOManagerTheme
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * UI Tests for P6 Settings/Mehr-Menü Screen
 * Verifies:
 * - GlobalHeader with subTitle "Einstellungen"
 * - All five sections displayed (Allgemein, Backup & Speicher, Import/Export, SSH/Server, Über die App)
 * - SAF Backup directory picker button
 * - No forbidden actions (backup/restore/account-edit)
 * - Prefix validation UI
 * - Import warning dialog
 * - DuplicateResolveDialog for interactive conflict resolution (DEVIATION FROM P6)
 * - SSH manual test button
 * - About section with Logs and ID-Vergleich buttons
 */
class SettingsScreenUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        viewModel = mockk(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(
            SettingsUiState(
                accountPrefix = "MGO_",
                backupRootPath = "/storage/emulated/0/mgo/backups/",
                appStartCount = 5,
                isRootAvailable = true
            )
        )
        every { viewModel.formatLastSyncTime() } returns "Noch nie"
    }

    // ========== GlobalHeader Tests (P6 Modernization) ==========

    @Test
    fun settingsScreen_usesGlobalHeaderWithSubtitle() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        // GlobalHeader should display "Einstellungen" as subtitle
        composeTestRule.onNodeWithText("Einstellungen").assertIsDisplayed()
        // GlobalHeader should display "babixGO" title
        composeTestRule.onNodeWithText("babixGO").assertIsDisplayed()
    }

    // ========== Section Display Tests ==========

    @Test
    fun settingsScreen_displaysAllgemeinSection() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Allgemein").assertIsDisplayed()
        composeTestRule.onNodeWithText("Standard-Präfix").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysBackupSpeicherSection() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Backup & Speicher").assertIsDisplayed()
        composeTestRule.onNodeWithText("Aktueller Backup-Pfad:").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysImportExportSection() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Import / Export").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alle Backups exportieren").assertIsDisplayed()
        composeTestRule.onNodeWithText("Backups importieren").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysSshServerSection() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("SSH / Server").assertIsDisplayed()
        composeTestRule.onNodeWithText("SSH Testen").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysUeberDieAppSection() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Über die App").assertIsDisplayed()
        composeTestRule.onNodeWithText("bGO Account Manager").assertIsDisplayed()
        composeTestRule.onNodeWithText("Version 2.0.0").assertIsDisplayed()
        composeTestRule.onNodeWithText("bGO Account Manager – lokales Backup-Tool für Monopoly GO Accounts.")
            .assertIsDisplayed()
    }

    // ========== Prefix Validation UI Tests ==========

    @Test
    fun prefixField_showsValidationError() {
        every { viewModel.uiState } returns MutableStateFlow(
            SettingsUiState(
                prefixError = "Präfix darf nicht leer sein"
            )
        )

        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Präfix darf nicht leer sein").assertIsDisplayed()
    }

    @Test
    fun prefixField_showsSavedState() {
        every { viewModel.uiState } returns MutableStateFlow(
            SettingsUiState(
                prefixSaved = true
            )
        )

        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        // The check icon should be green when saved
        composeTestRule.onAllNodesWithContentDescription("Speichern").onFirst().assertIsDisplayed()
    }

    // ========== Backup Path & SAF Tests ==========

    @Test
    fun backupPath_displaysNotMovedWarning() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Bestehende Backups werden nicht verschoben.").assertIsDisplayed()
    }

    @Test
    fun backupPath_displaysSafDirectoryPickerButton() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        // SAF directory picker button should be displayed
        composeTestRule.onNodeWithText("Backup-Verzeichnis ändern").assertIsDisplayed()
    }

    // ========== Import Warning Dialog Tests ==========

    @Test
    fun importButton_triggersWarningDialog() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Backups importieren").performClick()

        verify { viewModel.showImportWarning() }
    }

    @Test
    fun importWarningDialog_displaysCorrectText() {
        every { viewModel.uiState } returns MutableStateFlow(
            SettingsUiState(showImportWarning = true)
        )

        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Backups importieren").assertIsDisplayed()
        composeTestRule.onNodeWithText("Beim Import können bestehende Backups überschrieben werden.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Importieren").assertIsDisplayed()
        composeTestRule.onNodeWithText("Abbrechen").assertIsDisplayed()
    }

    @Test
    fun importWarningDialog_confirmButton_hidesWarningAndLaunchesSaf() {
        every { viewModel.uiState } returns MutableStateFlow(
            SettingsUiState(showImportWarning = true)
        )

        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Importieren").performClick()

        // The button now hides the warning (SAF launcher is triggered in the composable)
        verify { viewModel.hideImportWarning() }
    }

    // ========== Export Tests ==========

    @Test
    fun exportButton_isDisplayed() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        // Export button should be displayed and enabled
        composeTestRule.onNodeWithText("Alle Backups exportieren").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alle Backups exportieren").assertIsEnabled()
    }

    @Test
    fun exportButton_showsProgressWhenExporting() {
        every { viewModel.uiState } returns MutableStateFlow(
            SettingsUiState(isExporting = true)
        )

        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Exportiere...").assertIsDisplayed()
    }

    // ========== SSH Tests ==========

    @Test
    fun sshTestButton_triggersTest() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("SSH Testen").performClick()

        verify { viewModel.testSshConnection() }
    }

    @Test
    fun sshAutoCheckToggle_isDisplayed() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("App-Start prüfen").assertIsDisplayed()
    }

    // ========== P6 Forbidden Actions Tests ==========

    @Test
    fun settingsScreen_hasNoBackupButton() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        // Should not have any "Backup erstellen" or similar buttons
        composeTestRule.onNodeWithText("Backup erstellen").assertDoesNotExist()
        composeTestRule.onNodeWithText("Account sichern").assertDoesNotExist()
    }

    @Test
    fun settingsScreen_hasNoRestoreButton() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        // Should not have any restore buttons
        composeTestRule.onNodeWithText("Wiederherstellen").assertDoesNotExist()
        composeTestRule.onNodeWithText("Restore").assertDoesNotExist()
    }

    @Test
    fun settingsScreen_hasNoAccountEditButton() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        // Should not have any account edit buttons
        composeTestRule.onNodeWithText("Account bearbeiten").assertDoesNotExist()
        composeTestRule.onNodeWithText("Account löschen").assertDoesNotExist()
    }

    // ========== System Section Tests ==========

    @Test
    fun settingsScreen_displaysRootStatus() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Root-Status").assertIsDisplayed()
        composeTestRule.onNodeWithText("Verfügbar").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysLogsButton() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Logs anzeigen").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysAppStartCount() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("App-Starts: 5").assertIsDisplayed()
    }

    // ========== Navigation Tests ==========

    @Test
    fun settingsScreen_displaysBackLink() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Zurück zur Liste").assertIsDisplayed()
    }

    // ========== About Section Buttons Tests (P6 Requirement) ==========

    @Test
    fun aboutSection_displaysLogsButton() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        // Logs button should be in the About section
        composeTestRule.onNodeWithText("Logs öffnen").assertIsDisplayed()
    }

    @Test
    fun aboutSection_displaysIdVergleichButton() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        // ID-Vergleich button should be in the About section
        composeTestRule.onNodeWithText("ID-Vergleich").assertIsDisplayed()
    }

    // ========== DuplicateResolveDialog Tests (DEVIATION FROM P6) ==========

    @Test
    fun duplicateResolveDialog_displaysWhenDuplicatesExist() {
        val duplicates = listOf(
            DuplicatePair(
                userId = "userId123",
                localName = "LocalAccount",
                localCreatedAt = System.currentTimeMillis() - 86400000,
                importName = "ImportAccount",
                importCreatedAt = System.currentTimeMillis()
            )
        )

        every { viewModel.uiState } returns MutableStateFlow(
            SettingsUiState(
                showDuplicateResolveDialog = true,
                importDuplicates = duplicates
            )
        )

        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        // Dialog title should be displayed
        composeTestRule.onNodeWithText("User ID bereits vorhanden").assertIsDisplayed()
        // Options should be displayed
        composeTestRule.onNodeWithText("Lokal behalten").assertIsDisplayed()
        composeTestRule.onNodeWithText("Import behalten").assertIsDisplayed()
        // Action buttons
        composeTestRule.onNodeWithText("Bestätigen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Abbrechen").assertIsDisplayed()
    }

    @Test
    fun duplicateResolveDialog_cancelButton_triggersCancel() {
        val duplicates = listOf(
            DuplicatePair(
                userId = "userId123",
                localName = "LocalAccount",
                localCreatedAt = System.currentTimeMillis(),
                importName = "ImportAccount",
                importCreatedAt = System.currentTimeMillis()
            )
        )

        every { viewModel.uiState } returns MutableStateFlow(
            SettingsUiState(
                showDuplicateResolveDialog = true,
                importDuplicates = duplicates
            )
        )

        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        // Click the cancel button in the dialog
        composeTestRule.onNodeWithText("Abbrechen").performClick()

        verify { viewModel.cancelImportConflictResolution() }
    }

    @Test
    fun duplicateResolveDialog_confirmButton_triggersApply() {
        val duplicates = listOf(
            DuplicatePair(
                userId = "userId123",
                localName = "LocalAccount",
                localCreatedAt = System.currentTimeMillis(),
                importName = "ImportAccount",
                importCreatedAt = System.currentTimeMillis()
            )
        )

        every { viewModel.uiState } returns MutableStateFlow(
            SettingsUiState(
                showDuplicateResolveDialog = true,
                importDuplicates = duplicates
            )
        )

        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        // Click the confirm button in the dialog
        composeTestRule.onNodeWithText("Bestätigen").performClick()

        verify { viewModel.applyConflictDecisions(any()) }
    }

    @Test
    fun duplicateResolveDialog_multipleDuplicates_showsBulkOption() {
        val duplicates = listOf(
            DuplicatePair(
                userId = "userId123",
                localName = "LocalAccount1",
                localCreatedAt = System.currentTimeMillis(),
                importName = "ImportAccount1",
                importCreatedAt = System.currentTimeMillis()
            ),
            DuplicatePair(
                userId = "userId456",
                localName = "LocalAccount2",
                localCreatedAt = System.currentTimeMillis(),
                importName = "ImportAccount2",
                importCreatedAt = System.currentTimeMillis()
            )
        )

        every { viewModel.uiState } returns MutableStateFlow(
            SettingsUiState(
                showDuplicateResolveDialog = true,
                importDuplicates = duplicates
            )
        )

        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        // Bulk apply option should be shown for multiple duplicates
        composeTestRule.onNodeWithText("Für alle Duplikate übernehmen:").assertIsDisplayed()
    }
}
