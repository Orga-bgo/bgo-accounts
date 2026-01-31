package com.mgomanager.app.ui.screens.backup

import android.content.Context
import com.mgomanager.app.data.model.Account
import com.mgomanager.app.data.model.BackupResult
import com.mgomanager.app.data.model.RestoreResult
import com.mgomanager.app.data.repository.AppStateRepository
import com.mgomanager.app.data.repository.BackupRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.usecase.BackupRequest
import com.mgomanager.app.domain.usecase.RestoreBackupUseCase
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
class BackupViewModelTest {

    private lateinit var viewModel: BackupViewModel
    private lateinit var context: Context
    private lateinit var backupRepository: BackupRepository
    private lateinit var appStateRepository: AppStateRepository
    private lateinit var restoreBackupUseCase: RestoreBackupUseCase
    private lateinit var logRepository: LogRepository
    private lateinit var rootUtil: RootUtil

    private val testDispatcher = StandardTestDispatcher()

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

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        context = mockk(relaxed = true)
        backupRepository = mockk(relaxed = true)
        appStateRepository = mockk(relaxed = true)
        restoreBackupUseCase = mockk(relaxed = true)
        logRepository = mockk(relaxed = true)
        rootUtil = mockk(relaxed = true)

        // Default mock behaviors
        coEvery { appStateRepository.getDefaultPrefix() } returns "MGO_"
        coEvery { appStateRepository.getBackupDirectory() } returns "/storage/emulated/0/bgo_backups/"
        coEvery { logRepository.logInfo(any(), any(), any()) } just Runs
        coEvery { logRepository.logError(any(), any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): BackupViewModel {
        return BackupViewModel(
            context = context,
            backupRepository = backupRepository,
            appStateRepository = appStateRepository,
            restoreBackupUseCase = restoreBackupUseCase,
            logRepository = logRepository,
            rootUtil = rootUtil
        )
    }

    // ============================================================
    // Initial State Tests
    // ============================================================

    @Test
    fun `initial state should have NAME_INPUT step`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(BackupWizardStep.NAME_INPUT, viewModel.uiState.value.currentStep)
        assertEquals(0, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun `initial state should load prefix from settings`() = runTest {
        coEvery { appStateRepository.getDefaultPrefix() } returns "BGO_"

        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("BGO_", viewModel.uiState.value.accountPrefix)
    }

    // ============================================================
    // Tab Navigation Tests
    // ============================================================

    @Test
    fun `selectTab should change selected tab`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectTab(1)

        assertEquals(1, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun `selectTab should reset wizard state`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("Test")
        viewModel.selectTab(1)

        assertEquals("", viewModel.uiState.value.accountName)
        assertEquals(BackupWizardStep.NAME_INPUT, viewModel.uiState.value.currentStep)
    }

    // ============================================================
    // Input Field Tests
    // ============================================================

    @Test
    fun `onAccountNameChange should update account name`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("MyAccount")

        assertEquals("MyAccount", viewModel.uiState.value.accountName)
    }

    @Test
    fun `onHasFacebookLinkChange should update facebook link state`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onHasFacebookLinkChange(true)

        assertTrue(viewModel.uiState.value.hasFacebookLink)
    }

    @Test
    fun `onFbUsernameChange should update facebook username`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onFbUsernameChange("testuser")

        assertEquals("testuser", viewModel.uiState.value.fbUsername)
    }

    @Test
    fun `onFbPasswordChange should update facebook password`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onFbPasswordChange("password123")

        assertEquals("password123", viewModel.uiState.value.fbPassword)
    }

    // ============================================================
    // Wizard Navigation Tests
    // ============================================================

    @Test
    fun `proceedFromNameInput should show error when name is empty`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.proceedFromNameInput()

        assertEquals(BackupWizardStep.NAME_INPUT, viewModel.uiState.value.currentStep)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `proceedFromNameInput should go to SOCIAL_MEDIA step`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()

        assertEquals(BackupWizardStep.SOCIAL_MEDIA, viewModel.uiState.value.currentStep)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `proceedFromSocialMedia should go to FACEBOOK_DETAILS when hasFacebookLink`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.onHasFacebookLinkChange(true)
        viewModel.proceedFromSocialMedia()

        assertEquals(BackupWizardStep.FACEBOOK_DETAILS, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `proceedFromSocialMedia should start backup when no facebook link`() = runTest {
        coEvery { backupRepository.createBackup(any()) } returns BackupResult.Success(testAccount)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.onHasFacebookLinkChange(false)
        viewModel.proceedFromSocialMedia()
        advanceUntilIdle()

        // Should transition to BACKUP_PROGRESS and then SUCCESS
        coVerify { backupRepository.createBackup(any()) }
    }

    @Test
    fun `goBack should navigate backwards correctly`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        assertEquals(BackupWizardStep.SOCIAL_MEDIA, viewModel.uiState.value.currentStep)

        viewModel.goBack()
        assertEquals(BackupWizardStep.NAME_INPUT, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `goBack from FACEBOOK_DETAILS should go to SOCIAL_MEDIA`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.onHasFacebookLinkChange(true)
        viewModel.proceedFromSocialMedia()
        assertEquals(BackupWizardStep.FACEBOOK_DETAILS, viewModel.uiState.value.currentStep)

        viewModel.goBack()
        assertEquals(BackupWizardStep.SOCIAL_MEDIA, viewModel.uiState.value.currentStep)
    }

    // ============================================================
    // Backup Execution Tests
    // ============================================================

    @Test
    fun `backup success should show SUCCESS step`() = runTest {
        coEvery { backupRepository.createBackup(any()) } returns BackupResult.Success(testAccount)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.proceedFromSocialMedia()
        advanceUntilIdle()

        assertEquals(BackupWizardStep.SUCCESS, viewModel.uiState.value.currentStep)
        assertNotNull(viewModel.uiState.value.createdAccount)
        assertEquals(testAccount.id, viewModel.uiState.value.createdAccount?.id)
    }

    @Test
    fun `backup partial success should show SUCCESS step`() = runTest {
        val missingIds = listOf("GAID", "Device Token")
        coEvery { backupRepository.createBackup(any()) } returns BackupResult.PartialSuccess(testAccount, missingIds)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.proceedFromSocialMedia()
        advanceUntilIdle()

        assertEquals(BackupWizardStep.SUCCESS, viewModel.uiState.value.currentStep)
        assertNotNull(viewModel.uiState.value.createdAccount)
    }

    @Test
    fun `backup duplicate user id should show ERROR step`() = runTest {
        coEvery { backupRepository.createBackup(any()) } returns BackupResult.DuplicateUserId(
            userId = "user123456",
            existingAccountName = "ExistingAccount"
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.proceedFromSocialMedia()
        advanceUntilIdle()

        assertEquals(BackupWizardStep.ERROR, viewModel.uiState.value.currentStep)
        assertNotNull(viewModel.uiState.value.duplicateUserIdInfo)
        assertEquals("user123456", viewModel.uiState.value.duplicateUserIdInfo?.userId)
    }

    @Test
    fun `backup failure should show ERROR step with message`() = runTest {
        coEvery { backupRepository.createBackup(any()) } returns BackupResult.Failure("Test error")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.proceedFromSocialMedia()
        advanceUntilIdle()

        assertEquals(BackupWizardStep.ERROR, viewModel.uiState.value.currentStep)
        assertEquals("Da ist etwas schief gelaufen.. Prüfe den Log", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `backup should pass facebook data when hasFacebookLink`() = runTest {
        coEvery { backupRepository.createBackup(any()) } returns BackupResult.Success(testAccount)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.onHasFacebookLinkChange(true)
        viewModel.proceedFromSocialMedia()
        viewModel.onFbUsernameChange("fbuser")
        viewModel.onFbPasswordChange("fbpass")
        viewModel.proceedFromFacebookDetails()
        advanceUntilIdle()

        coVerify {
            backupRepository.createBackup(match { request ->
                request.hasFacebookLink &&
                request.fbUsername == "fbuser" &&
                request.fbPassword == "fbpass"
            })
        }
    }

    // ============================================================
    // Retry Tests
    // ============================================================

    @Test
    fun `retryBackup should reset to NAME_INPUT step`() = runTest {
        coEvery { backupRepository.createBackup(any()) } returns BackupResult.Failure("Test error")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.proceedFromSocialMedia()
        advanceUntilIdle()

        assertEquals(BackupWizardStep.ERROR, viewModel.uiState.value.currentStep)

        viewModel.retryBackup()

        assertEquals(BackupWizardStep.NAME_INPUT, viewModel.uiState.value.currentStep)
        assertNull(viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.duplicateUserIdInfo)
    }

    // ============================================================
    // Success Actions Tests
    // ============================================================

    @Test
    fun `getCreatedAccountId should return account id after success`() = runTest {
        coEvery { backupRepository.createBackup(any()) } returns BackupResult.Success(testAccount)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.proceedFromSocialMedia()
        advanceUntilIdle()

        assertEquals(testAccount.id, viewModel.getCreatedAccountId())
    }

    @Test
    fun `launchMonopolyGoWithCreatedAccount should call restore and launch`() = runTest {
        coEvery { backupRepository.createBackup(any()) } returns BackupResult.Success(testAccount)
        coEvery { restoreBackupUseCase.execute(testAccount.id) } returns RestoreResult.Success("TestAccount")
        coEvery { rootUtil.executeCommand(any()) } returns Result.success("")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.proceedFromSocialMedia()
        advanceUntilIdle()

        viewModel.launchMonopolyGoWithCreatedAccount()
        advanceUntilIdle()

        coVerify { restoreBackupUseCase.execute(testAccount.id) }
        coVerify { rootUtil.executeCommand("am start -n com.scopely.monopolygo/.MainActivity") }
    }

    @Test
    fun `launchMonopolyGoWithCreatedAccount should log error on restore failure`() = runTest {
        coEvery { backupRepository.createBackup(any()) } returns BackupResult.Success(testAccount)
        coEvery { restoreBackupUseCase.execute(testAccount.id) } returns RestoreResult.Failure("Restore error")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.proceedFromSocialMedia()
        advanceUntilIdle()

        viewModel.launchMonopolyGoWithCreatedAccount()
        advanceUntilIdle()

        coVerify { logRepository.logError("BACKUP_VM", match { it.contains("Restore fehlgeschlagen") }) }
    }

    @Test
    fun `resetForNewBackup should clear all state`() = runTest {
        coEvery { backupRepository.createBackup(any()) } returns BackupResult.Success(testAccount)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.proceedFromSocialMedia()
        advanceUntilIdle()

        viewModel.resetForNewBackup()

        assertEquals(BackupWizardStep.NAME_INPUT, viewModel.uiState.value.currentStep)
        assertEquals("", viewModel.uiState.value.accountName)
        assertNull(viewModel.uiState.value.createdAccount)
        assertNull(viewModel.uiState.value.backupResult)
    }

    // ============================================================
    // Error Handling Tests
    // ============================================================

    @Test
    fun `clearError should clear error message`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.proceedFromNameInput() // Will set error because name is empty
        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.clearError()
        assertNull(viewModel.uiState.value.errorMessage)
    }
}
