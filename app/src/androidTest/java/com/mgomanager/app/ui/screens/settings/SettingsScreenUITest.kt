package com.mgomanager.app.ui.screens.settings

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.rememberNavController
import com.mgomanager.app.ui.theme.MGOManagerTheme
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UI Tests for P6 Settings/Mehr-Menü Screen
 * Verifies:
 * - All five sections displayed (Allgemein, Backup & Speicher, Import/Export, SSH/Server, Über die App)
 * - No forbidden actions (backup/restore/account-edit)
 * - Prefix validation UI
 * - Import warning dialog
 * - SSH manual test button
 * - About section with app description
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

    // ========== Backup Path Warning Tests ==========

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
    fun importWarningDialog_confirmButton_triggersImport() {
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

        verify { viewModel.confirmImport() }
    }

    // ========== Export Tests ==========

    @Test
    fun exportButton_triggersExport() {
        composeTestRule.setContent {
            MGOManagerTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Alle Backups exportieren").performClick()

        verify { viewModel.exportData() }
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
        composeTestRule.onNodeWithText("✓ Verfügbar").assertIsDisplayed()
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

        composeTestRule.onNodeWithText("← Zurück zur Liste").assertIsDisplayed()
    }
}
