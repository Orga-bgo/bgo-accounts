package com.mgomanager.app.ui.screens.onboarding

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import app.cash.turbine.test
import com.mgomanager.app.data.repository.AppStateRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.util.FileUtil
import com.mgomanager.app.domain.util.ImportResult
import com.mgomanager.app.domain.util.ImportUtil
import com.mgomanager.app.domain.util.RootUtil
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private lateinit var viewModel: OnboardingViewModel
    private lateinit var context: Context
    private lateinit var appStateRepository: AppStateRepository
    private lateinit var rootUtil: RootUtil
    private lateinit var fileUtil: FileUtil
    private lateinit var importUtil: ImportUtil
    private lateinit var logRepository: LogRepository
    private lateinit var packageManager: PackageManager

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        context = mockk(relaxed = true)
        appStateRepository = mockk(relaxed = true)
        rootUtil = mockk(relaxed = true)
        fileUtil = mockk(relaxed = true)
        importUtil = mockk(relaxed = true)
        logRepository = mockk(relaxed = true)
        packageManager = mockk(relaxed = true)

        every { context.packageManager } returns packageManager

        // Default mock behaviors
        coEvery { fileUtil.fileExists(any()) } returns false
        coEvery { importUtil.checkImportZipExists() } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): OnboardingViewModel {
        return OnboardingViewModel(
            context = context,
            appStateRepository = appStateRepository,
            rootUtil = rootUtil,
            fileUtil = fileUtil,
            importUtil = importUtil,
            logRepository = logRepository
        )
    }

    @Test
    fun `initial state should be WELCOME step`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `nextStep from WELCOME should go to IMPORT_CHECK`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.nextStep()

        assertEquals(OnboardingStep.IMPORT_CHECK, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `nextStep should follow correct step sequence`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // WELCOME -> IMPORT_CHECK
        viewModel.nextStep()
        assertEquals(OnboardingStep.IMPORT_CHECK, viewModel.uiState.value.currentStep)

        // IMPORT_CHECK -> PREFIX_SETUP
        viewModel.nextStep()
        assertEquals(OnboardingStep.PREFIX_SETUP, viewModel.uiState.value.currentStep)

        // PREFIX_SETUP -> SSH_SETUP
        viewModel.nextStep()
        assertEquals(OnboardingStep.SSH_SETUP, viewModel.uiState.value.currentStep)

        // SSH_SETUP -> BACKUP_DIRECTORY
        viewModel.nextStep()
        assertEquals(OnboardingStep.BACKUP_DIRECTORY, viewModel.uiState.value.currentStep)

        // BACKUP_DIRECTORY -> ROOT_PERMISSIONS
        viewModel.nextStep()
        assertEquals(OnboardingStep.ROOT_PERMISSIONS, viewModel.uiState.value.currentStep)

        // ROOT_PERMISSIONS -> COMPLETE
        viewModel.nextStep()
        assertEquals(OnboardingStep.COMPLETE, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `previousStep should go back correctly`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Go to SSH_SETUP
        viewModel.nextStep() // -> IMPORT_CHECK
        viewModel.nextStep() // -> PREFIX_SETUP
        viewModel.nextStep() // -> SSH_SETUP
        assertEquals(OnboardingStep.SSH_SETUP, viewModel.uiState.value.currentStep)

        // Go back
        viewModel.previousStep()
        assertEquals(OnboardingStep.PREFIX_SETUP, viewModel.uiState.value.currentStep)

        viewModel.previousStep()
        assertEquals(OnboardingStep.IMPORT_CHECK, viewModel.uiState.value.currentStep)

        viewModel.previousStep()
        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.currentStep)

        // Can't go back from WELCOME
        viewModel.previousStep()
        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `onPrefixChanged should update prefix in state`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onPrefixChanged("MGO_")

        assertEquals("MGO_", viewModel.uiState.value.prefix)
    }

    @Test
    fun `savePrefixAndContinue should save prefix and go to next step`() = runTest {
        coEvery { appStateRepository.setDefaultPrefix(any()) } just Runs

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.nextStep() // -> IMPORT_CHECK
        viewModel.nextStep() // -> PREFIX_SETUP

        viewModel.onPrefixChanged("TEST_")
        viewModel.savePrefixAndContinue()
        advanceUntilIdle()

        coVerify { appStateRepository.setDefaultPrefix("TEST_") }
        assertEquals(OnboardingStep.SSH_SETUP, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `skipPrefix should not save and go to next step`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.nextStep() // -> IMPORT_CHECK
        viewModel.nextStep() // -> PREFIX_SETUP

        viewModel.skipPrefix()

        assertEquals(OnboardingStep.SSH_SETUP, viewModel.uiState.value.currentStep)
        coVerify(exactly = 0) { appStateRepository.setDefaultPrefix(any()) }
    }

    @Test
    fun `SSH setup should validate required fields when enabled`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Navigate to SSH_SETUP
        viewModel.nextStep() // -> IMPORT_CHECK
        viewModel.nextStep() // -> PREFIX_SETUP
        viewModel.nextStep() // -> SSH_SETUP

        // Enable SSH without filling fields
        viewModel.onSshEnabledChanged(true)
        viewModel.saveSshAndContinue()
        advanceUntilIdle()

        // Should show error and stay on SSH_SETUP
        assertNotNull(viewModel.uiState.value.error)
        assertEquals(OnboardingStep.SSH_SETUP, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `SSH setup should save config when all fields are filled`() = runTest {
        coEvery { appStateRepository.setSshConfig(any(), any(), any(), any(), any()) } just Runs

        viewModel = createViewModel()
        advanceUntilIdle()

        // Navigate to SSH_SETUP
        viewModel.nextStep() // -> IMPORT_CHECK
        viewModel.nextStep() // -> PREFIX_SETUP
        viewModel.nextStep() // -> SSH_SETUP

        // Fill all fields
        viewModel.onSshEnabledChanged(true)
        viewModel.onSshHostChanged("192.168.1.100")
        viewModel.onSshPortChanged("22")
        viewModel.onSshUsernameChanged("user")
        viewModel.onSshPasswordChanged("password")
        viewModel.saveSshAndContinue()
        advanceUntilIdle()

        coVerify {
            appStateRepository.setSshConfig(
                enabled = true,
                host = "192.168.1.100",
                port = 22,
                username = "user",
                password = "password"
            )
        }
        assertEquals(OnboardingStep.BACKUP_DIRECTORY, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `skipSsh should disable SSH and continue`() = runTest {
        coEvery { appStateRepository.setSshEnabled(false) } just Runs

        viewModel = createViewModel()
        advanceUntilIdle()

        // Navigate to SSH_SETUP
        viewModel.nextStep() // -> IMPORT_CHECK
        viewModel.nextStep() // -> PREFIX_SETUP
        viewModel.nextStep() // -> SSH_SETUP

        viewModel.skipSsh()
        advanceUntilIdle()

        coVerify { appStateRepository.setSshEnabled(false) }
        assertEquals(OnboardingStep.BACKUP_DIRECTORY, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `saveBackupDirectoryAndContinue should create directory and save`() = runTest {
        coEvery { fileUtil.createDirectory(any()) } returns Result.success(Unit)
        coEvery { appStateRepository.setBackupDirectory(any()) } just Runs

        viewModel = createViewModel()
        advanceUntilIdle()

        // Navigate to BACKUP_DIRECTORY
        viewModel.nextStep() // -> IMPORT_CHECK
        viewModel.nextStep() // -> PREFIX_SETUP
        viewModel.nextStep() // -> SSH_SETUP
        viewModel.nextStep() // -> BACKUP_DIRECTORY

        viewModel.onBackupDirectoryChanged("/storage/emulated/0/test_backups/")
        viewModel.saveBackupDirectoryAndContinue()
        advanceUntilIdle()

        coVerify { fileUtil.createDirectory("/storage/emulated/0/test_backups/") }
        coVerify { appStateRepository.setBackupDirectory("/storage/emulated/0/test_backups/") }
        assertEquals(OnboardingStep.ROOT_PERMISSIONS, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `saveBackupDirectoryAndContinue should show error on failure`() = runTest {
        coEvery { fileUtil.createDirectory(any()) } returns Result.failure(Exception("Permission denied"))

        viewModel = createViewModel()
        advanceUntilIdle()

        // Navigate to BACKUP_DIRECTORY
        viewModel.nextStep() // -> IMPORT_CHECK
        viewModel.nextStep() // -> PREFIX_SETUP
        viewModel.nextStep() // -> SSH_SETUP
        viewModel.nextStep() // -> BACKUP_DIRECTORY

        viewModel.saveBackupDirectoryAndContinue()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.error!!.contains("Fehler"))
        assertEquals(OnboardingStep.BACKUP_DIRECTORY, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `requestRootAccess should check root and Monopoly Go installation`() = runTest {
        coEvery { rootUtil.requestRootAccess() } returns true
        coEvery { rootUtil.executeCommand(any()) } returns Result.success("10123")
        every { packageManager.getPackageInfo("com.scopely.monopolygo", 0) } returns PackageInfo()
        coEvery { appStateRepository.setSystemStatus(any(), any(), any(), any()) } just Runs

        viewModel = createViewModel()
        advanceUntilIdle()

        // Navigate to ROOT_PERMISSIONS
        viewModel.nextStep() // -> IMPORT_CHECK
        viewModel.nextStep() // -> PREFIX_SETUP
        viewModel.nextStep() // -> SSH_SETUP
        viewModel.nextStep() // -> BACKUP_DIRECTORY
        viewModel.nextStep() // -> ROOT_PERMISSIONS

        viewModel.requestRootAccess()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.rootAccessGranted)
        assertTrue(viewModel.uiState.value.monopolyGoInstalled)
        assertEquals(OnboardingStep.COMPLETE, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `requestRootAccess should show error when root is denied`() = runTest {
        coEvery { rootUtil.requestRootAccess() } returns false

        viewModel = createViewModel()
        advanceUntilIdle()

        // Navigate to ROOT_PERMISSIONS
        viewModel.nextStep() // -> IMPORT_CHECK
        viewModel.nextStep() // -> PREFIX_SETUP
        viewModel.nextStep() // -> SSH_SETUP
        viewModel.nextStep() // -> BACKUP_DIRECTORY
        viewModel.nextStep() // -> ROOT_PERMISSIONS

        viewModel.requestRootAccess()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.rootAccessGranted)
        assertNotNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.error!!.contains("Root"))
        assertEquals(OnboardingStep.ROOT_PERMISSIONS, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `completeOnboarding should set onboarding completed`() = runTest {
        coEvery { appStateRepository.setOnboardingCompleted(true) } just Runs

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.completeOnboarding()
        advanceUntilIdle()

        coVerify { appStateRepository.setOnboardingCompleted(true) }
    }

    @Test
    fun `importFromZip should import and continue on success`() = runTest {
        val importResult = ImportResult(
            success = true,
            importedCount = 5,
            skippedCount = 1
        )
        coEvery { fileUtil.fileExists(any()) } returns true
        coEvery { importUtil.importFromZip(any()) } returns Result.success(importResult)

        viewModel = createViewModel()
        advanceUntilIdle()

        // Navigate to IMPORT_CHECK
        viewModel.nextStep()

        viewModel.importFromZip()
        advanceUntilIdle()

        assertEquals(OnboardingStep.PREFIX_SETUP, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `importFromZip should show error on failure`() = runTest {
        coEvery { fileUtil.fileExists(any()) } returns true
        coEvery { importUtil.importFromZip(any()) } returns Result.failure(Exception("Import failed"))

        viewModel = createViewModel()
        advanceUntilIdle()

        // Set importZipPath manually for test
        viewModel.nextStep() // -> IMPORT_CHECK

        viewModel.importFromZip()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.error!!.contains("fehlgeschlagen"))
    }

    @Test
    fun `clearError should clear error state`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Simulate an error scenario
        viewModel.nextStep() // -> IMPORT_CHECK
        viewModel.nextStep() // -> PREFIX_SETUP
        viewModel.nextStep() // -> SSH_SETUP
        viewModel.onSshEnabledChanged(true)
        viewModel.saveSshAndContinue() // Will show error
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)

        viewModel.clearError()

        assertNull(viewModel.uiState.value.error)
    }
}
