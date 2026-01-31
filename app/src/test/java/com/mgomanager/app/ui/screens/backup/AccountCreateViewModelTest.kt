package com.mgomanager.app.ui.screens.backup

import android.content.Context
import com.mgomanager.app.data.model.Account
import com.mgomanager.app.data.model.BackupResult
import com.mgomanager.app.data.model.RestoreResult
import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.AppStateRepository
import com.mgomanager.app.data.repository.BackupRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.usecase.RestoreBackupUseCase
import com.mgomanager.app.domain.util.RootUtil
import com.mgomanager.app.domain.util.SsaidUtil
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountCreateViewModelTest {

    private lateinit var viewModel: AccountCreateViewModel
    private lateinit var context: Context
    private lateinit var accountRepository: AccountRepository
    private lateinit var backupRepository: BackupRepository
    private lateinit var appStateRepository: AppStateRepository
    private lateinit var restoreBackupUseCase: RestoreBackupUseCase
    private lateinit var logRepository: LogRepository
    private lateinit var rootUtil: RootUtil
    private lateinit var ssaidUtil: SsaidUtil

    private val testDispatcher = StandardTestDispatcher()

    private val testAccount = Account(
        id = 1,
        accountName = "TestAccount",
        prefix = "MGO_",
        createdAt = System.currentTimeMillis(),
        lastPlayedAt = System.currentTimeMillis(),
        userId = "user123456",
        ssaid = "abcdef0123456789",
        backupPath = "/storage/emulated/0/bgo_backups/MGO_TestAccount/",
        fileOwner = "u0_a123",
        fileGroup = "u0_a123",
        filePermissions = "rwxr-xr-x"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        context = mockk(relaxed = true)
        accountRepository = mockk(relaxed = true)
        backupRepository = mockk(relaxed = true)
        appStateRepository = mockk(relaxed = true)
        restoreBackupUseCase = mockk(relaxed = true)
        logRepository = mockk(relaxed = true)
        rootUtil = mockk(relaxed = true)
        ssaidUtil = mockk(relaxed = true)

        // Default mock behaviors
        coEvery { appStateRepository.getDefaultPrefix() } returns "MGO_"
        coEvery { appStateRepository.getBackupDirectory() } returns "/storage/emulated/0/bgo_backups/"
        coEvery { logRepository.logInfo(any(), any(), any()) } just Runs
        coEvery { logRepository.logError(any(), any(), any(), any()) } just Runs
        coEvery { logRepository.logWarning(any(), any(), any()) } just Runs
        coEvery { ssaidUtil.generateNewSsaid() } returns "0123456789abcdef"
        coEvery { ssaidUtil.readCurrentSsaid() } returns Result.success("fedcba9876543210")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AccountCreateViewModel {
        return AccountCreateViewModel(
            context = context,
            accountRepository = accountRepository,
            backupRepository = backupRepository,
            appStateRepository = appStateRepository,
            restoreBackupUseCase = restoreBackupUseCase,
            logRepository = logRepository,
            rootUtil = rootUtil,
            ssaidUtil = ssaidUtil
        )
    }

    // ============================================================
    // Initial State Tests
    // ============================================================

    @Test
    fun `initial state should have NAME_INPUT step`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(AccountCreateStep.NAME_INPUT, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `initial state should load prefix from settings`() = runTest {
        coEvery { appStateRepository.getDefaultPrefix() } returns "BGO_"

        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("BGO_", viewModel.uiState.value.accountPrefix)
    }

    @Test
    fun `initial SSAID option should be NEW`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(SsaidOption.NEW, viewModel.uiState.value.ssaidOption)
    }

    // ============================================================
    // Input Tests
    // ============================================================

    @Test
    fun `onAccountNameChange should update account name`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("MyNewAccount")

        assertEquals("MyNewAccount", viewModel.uiState.value.accountName)
    }

    @Test
    fun `onSsaidOptionChange should update SSAID option`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSsaidOptionChange(SsaidOption.CURRENT)

        assertEquals(SsaidOption.CURRENT, viewModel.uiState.value.ssaidOption)
    }

    // ============================================================
    // Step 1: Name Input Tests
    // ============================================================

    @Test
    fun `proceedFromNameInput should show error when name is empty`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.proceedFromNameInput()

        assertEquals(AccountCreateStep.NAME_INPUT, viewModel.uiState.value.currentStep)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `proceedFromNameInput should show error when name contains invalid characters`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("Test/Account")
        viewModel.proceedFromNameInput()

        assertEquals(AccountCreateStep.NAME_INPUT, viewModel.uiState.value.currentStep)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("Sonderzeichen"))
    }

    @Test
    fun `proceedFromNameInput should go to WARNING step when valid`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("ValidAccount")
        viewModel.proceedFromNameInput()

        assertEquals(AccountCreateStep.WARNING, viewModel.uiState.value.currentStep)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    // ============================================================
    // Step 2: Warning Tests
    // ============================================================

    @Test
    fun `confirmWarning should go to SSAID_SELECTION step`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.confirmWarning()
        advanceUntilIdle()

        assertEquals(AccountCreateStep.SSAID_SELECTION, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `confirmWarning should read current SSAID`() = runTest {
        coEvery { ssaidUtil.readCurrentSsaid() } returns Result.success("existingssaid1234")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.confirmWarning()
        advanceUntilIdle()

        assertEquals("existingssaid1234", viewModel.uiState.value.currentSsaid)
    }

    @Test
    fun `cancelWarning should go back to NAME_INPUT`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.cancelWarning()

        assertEquals(AccountCreateStep.NAME_INPUT, viewModel.uiState.value.currentStep)
    }

    // ============================================================
    // Step 3: SSAID Selection Tests
    // ============================================================

    @Test
    fun `proceedFromSsaidSelection with NEW should generate new SSAID`() = runTest {
        coEvery { ssaidUtil.clearMonopolyGoData() } returns Result.success(Unit)
        coEvery { ssaidUtil.patchSsaid(any()) } returns Result.success(Unit)
        coEvery { ssaidUtil.startMonopolyGo() } returns Result.success(Unit)
        coEvery { ssaidUtil.stopMonopolyGo() } returns Result.success(Unit)
        coEvery { backupRepository.createBackup(any()) } returns BackupResult.Success(testAccount)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.confirmWarning()
        advanceUntilIdle()
        viewModel.onSsaidOptionChange(SsaidOption.NEW)
        viewModel.proceedFromSsaidSelection()
        advanceUntilIdle()

        verify { ssaidUtil.generateNewSsaid() }
    }

    @Test
    fun `proceedFromSsaidSelection with CURRENT should use existing SSAID`() = runTest {
        coEvery { ssaidUtil.readCurrentSsaid() } returns Result.success("existingssaid1234")
        coEvery { ssaidUtil.clearMonopolyGoData() } returns Result.success(Unit)
        coEvery { ssaidUtil.patchSsaid(any()) } returns Result.success(Unit)
        coEvery { ssaidUtil.startMonopolyGo() } returns Result.success(Unit)
        coEvery { ssaidUtil.stopMonopolyGo() } returns Result.success(Unit)
        coEvery { backupRepository.createBackup(any()) } returns BackupResult.Success(testAccount)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.confirmWarning()
        advanceUntilIdle()
        viewModel.onSsaidOptionChange(SsaidOption.CURRENT)
        viewModel.proceedFromSsaidSelection()
        advanceUntilIdle()

        assertEquals("existingssaid1234", viewModel.uiState.value.selectedSsaid)
    }

    @Test
    fun `goBackFromSsaidSelection should go to WARNING`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.confirmWarning()
        advanceUntilIdle()
        viewModel.goBackFromSsaidSelection()

        assertEquals(AccountCreateStep.WARNING, viewModel.uiState.value.currentStep)
    }

    // ============================================================
    // Account Creation Flow Tests
    // ============================================================

    @Test
    fun `account creation should show ERROR when wipe fails`() = runTest {
        coEvery { ssaidUtil.clearMonopolyGoData() } returns Result.failure(Exception("Wipe failed"))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.confirmWarning()
        advanceUntilIdle()
        viewModel.proceedFromSsaidSelection()
        advanceUntilIdle()

        assertEquals(AccountCreateStep.ERROR, viewModel.uiState.value.currentStep)
        assertEquals("Da ist etwas schief gelaufen.. Prüfe den Log", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `account creation should show ERROR when SSAID patch fails`() = runTest {
        coEvery { ssaidUtil.clearMonopolyGoData() } returns Result.success(Unit)
        coEvery { ssaidUtil.patchSsaid(any()) } returns Result.failure(Exception("Patch failed"))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.confirmWarning()
        advanceUntilIdle()
        viewModel.proceedFromSsaidSelection()
        advanceUntilIdle()

        assertEquals(AccountCreateStep.ERROR, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `account creation should show SUCCESS on successful backup`() = runTest {
        coEvery { ssaidUtil.clearMonopolyGoData() } returns Result.success(Unit)
        coEvery { ssaidUtil.patchSsaid(any()) } returns Result.success(Unit)
        coEvery { ssaidUtil.startMonopolyGo() } returns Result.success(Unit)
        coEvery { ssaidUtil.stopMonopolyGo() } returns Result.success(Unit)
        coEvery { backupRepository.createBackup(any()) } returns BackupResult.Success(testAccount)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.confirmWarning()
        advanceUntilIdle()
        viewModel.proceedFromSsaidSelection()
        advanceUntilIdle()

        assertEquals(AccountCreateStep.SUCCESS, viewModel.uiState.value.currentStep)
        assertNotNull(viewModel.uiState.value.createdAccount)
    }

    @Test
    fun `account creation should show ERROR on duplicate user ID`() = runTest {
        coEvery { ssaidUtil.clearMonopolyGoData() } returns Result.success(Unit)
        coEvery { ssaidUtil.patchSsaid(any()) } returns Result.success(Unit)
        coEvery { ssaidUtil.startMonopolyGo() } returns Result.success(Unit)
        coEvery { ssaidUtil.stopMonopolyGo() } returns Result.success(Unit)
        coEvery { backupRepository.createBackup(any()) } returns BackupResult.DuplicateUserId(
            userId = "user123456",
            existingAccountName = "ExistingAccount"
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.confirmWarning()
        advanceUntilIdle()
        viewModel.proceedFromSsaidSelection()
        advanceUntilIdle()

        assertEquals(AccountCreateStep.ERROR, viewModel.uiState.value.currentStep)
        assertNotNull(viewModel.uiState.value.duplicateUserIdInfo)
        assertEquals("user123456", viewModel.uiState.value.duplicateUserIdInfo?.userId)
    }

    // ============================================================
    // Success Actions Tests
    // ============================================================

    @Test
    fun `getCreatedAccountId should return account id after success`() = runTest {
        coEvery { ssaidUtil.clearMonopolyGoData() } returns Result.success(Unit)
        coEvery { ssaidUtil.patchSsaid(any()) } returns Result.success(Unit)
        coEvery { ssaidUtil.startMonopolyGo() } returns Result.success(Unit)
        coEvery { ssaidUtil.stopMonopolyGo() } returns Result.success(Unit)
        coEvery { backupRepository.createBackup(any()) } returns BackupResult.Success(testAccount)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.confirmWarning()
        advanceUntilIdle()
        viewModel.proceedFromSsaidSelection()
        advanceUntilIdle()

        assertEquals(testAccount.id, viewModel.getCreatedAccountId())
    }

    @Test
    fun `launchMonopolyGoWithCreatedAccount should call restore and launch`() = runTest {
        coEvery { ssaidUtil.clearMonopolyGoData() } returns Result.success(Unit)
        coEvery { ssaidUtil.patchSsaid(any()) } returns Result.success(Unit)
        coEvery { ssaidUtil.startMonopolyGo() } returns Result.success(Unit)
        coEvery { ssaidUtil.stopMonopolyGo() } returns Result.success(Unit)
        coEvery { backupRepository.createBackup(any()) } returns BackupResult.Success(testAccount)
        coEvery { restoreBackupUseCase.execute(testAccount.id) } returns RestoreResult.Success("TestAccount")
        coEvery { rootUtil.executeCommand(any()) } returns Result.success("")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.confirmWarning()
        advanceUntilIdle()
        viewModel.proceedFromSsaidSelection()
        advanceUntilIdle()

        viewModel.launchMonopolyGoWithCreatedAccount()
        advanceUntilIdle()

        coVerify { restoreBackupUseCase.execute(testAccount.id) }
        coVerify { rootUtil.executeCommand("am start -n com.scopely.monopolygo/.MainActivity") }
    }

    @Test
    fun `resetForNewAccount should clear all state`() = runTest {
        coEvery { ssaidUtil.clearMonopolyGoData() } returns Result.success(Unit)
        coEvery { ssaidUtil.patchSsaid(any()) } returns Result.success(Unit)
        coEvery { ssaidUtil.startMonopolyGo() } returns Result.success(Unit)
        coEvery { ssaidUtil.stopMonopolyGo() } returns Result.success(Unit)
        coEvery { backupRepository.createBackup(any()) } returns BackupResult.Success(testAccount)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.confirmWarning()
        advanceUntilIdle()
        viewModel.proceedFromSsaidSelection()
        advanceUntilIdle()

        viewModel.resetForNewAccount()

        assertEquals(AccountCreateStep.NAME_INPUT, viewModel.uiState.value.currentStep)
        assertEquals("", viewModel.uiState.value.accountName)
        assertNull(viewModel.uiState.value.createdAccount)
    }

    @Test
    fun `retryAccountCreation should reset to NAME_INPUT`() = runTest {
        coEvery { ssaidUtil.clearMonopolyGoData() } returns Result.failure(Exception("Failed"))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        viewModel.confirmWarning()
        advanceUntilIdle()
        viewModel.proceedFromSsaidSelection()
        advanceUntilIdle()

        assertEquals(AccountCreateStep.ERROR, viewModel.uiState.value.currentStep)

        viewModel.retryAccountCreation()

        assertEquals(AccountCreateStep.NAME_INPUT, viewModel.uiState.value.currentStep)
        assertNull(viewModel.uiState.value.errorMessage)
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

    @Test
    fun `goBack should navigate backwards correctly`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAccountNameChange("TestAccount")
        viewModel.proceedFromNameInput()
        assertEquals(AccountCreateStep.WARNING, viewModel.uiState.value.currentStep)

        viewModel.goBack()
        assertEquals(AccountCreateStep.NAME_INPUT, viewModel.uiState.value.currentStep)
    }
}
