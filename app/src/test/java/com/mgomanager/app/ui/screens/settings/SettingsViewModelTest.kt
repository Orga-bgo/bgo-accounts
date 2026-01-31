package com.mgomanager.app.ui.screens.settings

import android.content.Context
import com.mgomanager.app.data.local.preferences.SettingsDataStore
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.usecase.ExportImportUseCase
import com.mgomanager.app.domain.util.RootUtil
import com.mgomanager.app.domain.util.SSHSyncService
import com.mgomanager.app.domain.util.SSHOperationResult
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for P6 Settings/Mehr-Menü ViewModel
 * Verifies:
 * - No backup/restore/account-edit operations
 * - Prefix validation (not empty, no path chars)
 * - DataStore storage
 * - Export/Import functionality
 * - SSH settings and manual test
 * - Logging for all actions
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @MockK
    private lateinit var context: Context

    @MockK
    private lateinit var settingsDataStore: SettingsDataStore

    @MockK
    private lateinit var rootUtil: RootUtil

    @MockK
    private lateinit var exportImportUseCase: ExportImportUseCase

    @MockK
    private lateinit var sshSyncService: SSHSyncService

    @MockK
    private lateinit var logRepository: LogRepository

    private lateinit var viewModel: SettingsViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)
        Dispatchers.setMain(testDispatcher)

        // Setup default DataStore flows
        every { settingsDataStore.accountPrefix } returns flowOf("MGO_")
        every { settingsDataStore.backupRootPath } returns flowOf("/storage/emulated/0/mgo/backups/")
        every { settingsDataStore.appStartCount } returns flowOf(5)
        every { settingsDataStore.sshPrivateKeyPath } returns flowOf("")
        every { settingsDataStore.sshServer } returns flowOf("")
        every { settingsDataStore.sshBackupPath } returns flowOf("")
        every { settingsDataStore.sshPassword } returns flowOf("")
        every { settingsDataStore.sshAuthMethod } returns flowOf("key_only")
        every { settingsDataStore.sshLastSyncTimestamp } returns flowOf(0L)
        every { settingsDataStore.sshAutoCheckOnStart } returns flowOf(false)
        every { settingsDataStore.sshAutoUploadOnExport } returns flowOf(false)

        coEvery { rootUtil.isRooted() } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SettingsViewModel {
        return SettingsViewModel(
            settingsDataStore = settingsDataStore,
            rootUtil = rootUtil,
            exportImportUseCase = exportImportUseCase,
            sshSyncService = sshSyncService,
            logRepository = logRepository,
            context = context
        )
    }

    // ========== Prefix Validation Tests ==========

    @Test
    fun `updatePrefix with valid prefix saves to DataStore`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updatePrefix("TEST_")
        advanceUntilIdle()

        coVerify { settingsDataStore.setAccountPrefix("TEST_") }
        coVerify { logRepository.logInfo("SETTINGS", match { it.contains("Präfix geändert") }) }
        assertTrue(viewModel.uiState.value.prefixSaved)
        assertNull(viewModel.uiState.value.prefixError)
    }

    @Test
    fun `updatePrefix with empty prefix shows error`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updatePrefix("")
        advanceUntilIdle()

        coVerify(exactly = 0) { settingsDataStore.setAccountPrefix(any()) }
        assertEquals("Präfix darf nicht leer sein", viewModel.uiState.value.prefixError)
    }

    @Test
    fun `updatePrefix with blank prefix shows error`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updatePrefix("   ")
        advanceUntilIdle()

        coVerify(exactly = 0) { settingsDataStore.setAccountPrefix(any()) }
        assertEquals("Präfix darf nicht leer sein", viewModel.uiState.value.prefixError)
    }

    @Test
    fun `updatePrefix with path characters shows error`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Test various invalid characters
        val invalidPrefixes = listOf("test/prefix", "test\\prefix", "test:prefix", "test*prefix")

        for (prefix in invalidPrefixes) {
            viewModel.updatePrefix(prefix)
            advanceUntilIdle()
            assertEquals("Präfix enthält ungültige Zeichen", viewModel.uiState.value.prefixError)
        }
    }

    @Test
    fun `resetPrefixSaved clears saved state and error`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updatePrefix("TEST_")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.prefixSaved)

        viewModel.resetPrefixSaved()

        assertFalse(viewModel.uiState.value.prefixSaved)
        assertNull(viewModel.uiState.value.prefixError)
    }

    // ========== Backup Path Tests ==========

    @Test
    fun `updateBackupPath saves to DataStore and logs`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateBackupPath("/new/backup/path/")
        advanceUntilIdle()

        coVerify { settingsDataStore.setBackupRootPath("/new/backup/path/") }
        coVerify { logRepository.logInfo("SETTINGS", match { it.contains("Backup-Pfad geändert") }) }
        assertTrue(viewModel.uiState.value.pathSaved)
    }

    // ========== Export Tests ==========

    @Test
    fun `exportData shows progress and logs success`() = runTest {
        viewModel = createViewModel()
        coEvery { exportImportUseCase.exportData(any()) } returns Result.success("Export erfolgreich: /path/to/export.zip")
        advanceUntilIdle()

        viewModel.exportData()
        advanceUntilIdle()

        coVerify { logRepository.logInfo("SETTINGS", "Starte Export aller Backups") }
        coVerify { logRepository.logInfo("SETTINGS", match { it.contains("Export erfolgreich") }) }
        assertFalse(viewModel.uiState.value.isExporting)
    }

    @Test
    fun `exportData logs error on failure`() = runTest {
        viewModel = createViewModel()
        coEvery { exportImportUseCase.exportData(any()) } returns Result.failure(Exception("Export error"))
        advanceUntilIdle()

        viewModel.exportData()
        advanceUntilIdle()

        coVerify { logRepository.logError("SETTINGS", any(), any()) }
        assertEquals("Da ist etwas schief gelaufen.. Prüfe den Log", viewModel.uiState.value.exportResult)
    }

    // ========== Import Tests ==========

    @Test
    fun `showImportWarning sets flag to true`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showImportWarning()

        assertTrue(viewModel.uiState.value.showImportWarning)
    }

    @Test
    fun `hideImportWarning sets flag to false`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showImportWarning()
        viewModel.hideImportWarning()

        assertFalse(viewModel.uiState.value.showImportWarning)
    }

    @Test
    fun `confirmImport hides warning and starts import`() = runTest {
        viewModel = createViewModel()
        coEvery { exportImportUseCase.importData(any()) } returns Result.success(Unit)
        advanceUntilIdle()

        viewModel.showImportWarning()
        viewModel.confirmImport()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showImportWarning)
        coVerify { logRepository.logInfo("SETTINGS", "Starte Import von Backups") }
        coVerify { exportImportUseCase.importData(any()) }
    }

    @Test
    fun `import logs error on failure`() = runTest {
        viewModel = createViewModel()
        coEvery { exportImportUseCase.importData(any()) } returns Result.failure(Exception("Import error"))
        advanceUntilIdle()

        viewModel.confirmImport()
        advanceUntilIdle()

        coVerify { logRepository.logError("SETTINGS", any(), any()) }
        assertEquals("Da ist etwas schief gelaufen.. Prüfe den Log", viewModel.uiState.value.importResult)
    }

    // ========== SSH Tests ==========

    @Test
    fun `updateSshAutoCheckOnStart saves to DataStore and logs`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateSshAutoCheckOnStart(true)
        advanceUntilIdle()

        coVerify { settingsDataStore.setSshAutoCheckOnStart(true) }
        coVerify { logRepository.logInfo("SETTINGS", match { it.contains("SSH Auto-Check") }) }
    }

    @Test
    fun `testSshConnection logs and shows success on success`() = runTest {
        viewModel = createViewModel()
        coEvery { sshSyncService.testConnection() } returns SSHOperationResult.Success("Connected")
        advanceUntilIdle()

        viewModel.testSshConnection()
        advanceUntilIdle()

        coVerify { logRepository.logInfo("SETTINGS", "SSH-Verbindungstest gestartet") }
        coVerify { logRepository.logInfo("SETTINGS", "SSH-Verbindung erfolgreich") }
        assertEquals("Verbindung erfolgreich", viewModel.uiState.value.sshTestResult)
        assertFalse(viewModel.uiState.value.isSshTesting)
    }

    @Test
    fun `testSshConnection logs error on failure`() = runTest {
        viewModel = createViewModel()
        coEvery { sshSyncService.testConnection() } returns SSHOperationResult.Error("Connection failed")
        advanceUntilIdle()

        viewModel.testSshConnection()
        advanceUntilIdle()

        coVerify { logRepository.logError("SETTINGS", any(), any()) }
        assertEquals("Da ist etwas schief gelaufen.. Prüfe den Log", viewModel.uiState.value.sshTestResult)
    }

    @Test
    fun `SSH test only triggered manually not on enable`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Enable SSH - should NOT trigger test
        viewModel.updateSshEnabled(true)
        advanceUntilIdle()

        // Verify testConnection was NOT called
        coVerify(exactly = 0) { sshSyncService.testConnection() }
        coVerify { logRepository.logInfo("SETTINGS", "SSH aktiviert") }
    }

    // ========== P6 Forbidden Actions Tests ==========

    @Test
    fun `settings does not perform backup operations`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // SettingsViewModel should have no backup-creating methods
        // Verify by checking the class methods don't include backup operations
        val vmMethods = SettingsViewModel::class.java.declaredMethods.map { it.name }

        assertFalse(vmMethods.contains("createBackup"))
        assertFalse(vmMethods.contains("performBackup"))
    }

    @Test
    fun `settings does not perform restore operations`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val vmMethods = SettingsViewModel::class.java.declaredMethods.map { it.name }

        assertFalse(vmMethods.contains("restoreBackup"))
        assertFalse(vmMethods.contains("performRestore"))
    }

    @Test
    fun `settings does not edit accounts`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val vmMethods = SettingsViewModel::class.java.declaredMethods.map { it.name }

        assertFalse(vmMethods.contains("editAccount"))
        assertFalse(vmMethods.contains("updateAccount"))
        assertFalse(vmMethods.contains("deleteAccount"))
    }

    // ========== Utility Tests ==========

    @Test
    fun `clearExportResult sets exportResult to null`() = runTest {
        viewModel = createViewModel()
        coEvery { exportImportUseCase.exportData(any()) } returns Result.success("Done")
        advanceUntilIdle()

        viewModel.exportData()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.exportResult)

        viewModel.clearExportResult()

        assertNull(viewModel.uiState.value.exportResult)
    }

    @Test
    fun `clearImportResult sets importResult to null`() = runTest {
        viewModel = createViewModel()
        coEvery { exportImportUseCase.importData(any()) } returns Result.success(Unit)
        advanceUntilIdle()

        viewModel.confirmImport()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.importResult)

        viewModel.clearImportResult()

        assertNull(viewModel.uiState.value.importResult)
    }

    @Test
    fun `refreshRootStatus updates isRootAvailable`() = runTest {
        viewModel = createViewModel()
        coEvery { rootUtil.isRooted() } returns false
        advanceUntilIdle()

        viewModel.refreshRootStatus()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRootAvailable)
    }
}
