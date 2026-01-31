package com.mgomanager.app.ui.screens.home

import android.content.Context
import com.mgomanager.app.data.model.Account
import com.mgomanager.app.data.model.RestoreResult
import com.mgomanager.app.data.model.SusLevel
import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.AppStateRepository
import com.mgomanager.app.data.repository.BackupRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.usecase.RestoreBackupUseCase
import com.mgomanager.app.domain.util.RootUtil
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var viewModel: HomeViewModel
    private lateinit var context: Context
    private lateinit var accountRepository: AccountRepository
    private lateinit var backupRepository: BackupRepository
    private lateinit var appStateRepository: AppStateRepository
    private lateinit var logRepository: LogRepository
    private lateinit var restoreBackupUseCase: RestoreBackupUseCase
    private lateinit var rootUtil: RootUtil

    private val testDispatcher = StandardTestDispatcher()

    // Test data
    private val testAccount1 = Account(
        id = 1,
        accountName = "TestAccount1",
        prefix = "MGO_",
        createdAt = 1000L,
        lastPlayedAt = 5000L,
        userId = "user123456",
        backupPath = "/storage/emulated/0/bgo_backups/MGO_TestAccount1/",
        fileOwner = "u0_a123",
        fileGroup = "u0_a123",
        filePermissions = "rwxr-xr-x"
    )

    private val testAccount2 = Account(
        id = 2,
        accountName = "AnotherAccount",
        prefix = "MGO_",
        createdAt = 2000L,
        lastPlayedAt = 3000L,
        userId = "user789012",
        backupPath = "/storage/emulated/0/bgo_backups/MGO_AnotherAccount/",
        fileOwner = "u0_a123",
        fileGroup = "u0_a123",
        filePermissions = "rwxr-xr-x"
    )

    private val testAccount3 = Account(
        id = 3,
        accountName = "ZebraAccount",
        prefix = "BGO_",
        createdAt = 3000L,
        lastPlayedAt = 0L, // Never played
        userId = "user345678",
        backupPath = "/storage/emulated/0/bgo_backups/BGO_ZebraAccount/",
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
        logRepository = mockk(relaxed = true)
        restoreBackupUseCase = mockk(relaxed = true)
        rootUtil = mockk(relaxed = true)

        // Default mock behaviors
        coEvery { appStateRepository.getLastSearchQuery() } returns null
        coEvery { appStateRepository.getSortOption() } returns "LAST_PLAYED"
        coEvery { appStateRepository.getSortDirection() } returns "DESC"
        coEvery { appStateRepository.getDefaultPrefix() } returns "MGO_"
        coEvery { appStateRepository.setLastSearchQuery(any()) } just Runs
        coEvery { appStateRepository.setSortOption(any()) } just Runs
        coEvery { appStateRepository.setSortDirection(any()) } just Runs
        coEvery { logRepository.logInfo(any(), any(), any()) } just Runs
        coEvery { logRepository.logError(any(), any(), any(), any()) } just Runs

        every { accountRepository.getAllAccounts() } returns flowOf(listOf(testAccount1, testAccount2, testAccount3))
        every { accountRepository.getAccountCount() } returns flowOf(3)
        every { accountRepository.getErrorCount() } returns flowOf(0)
        every { accountRepository.getSusCount() } returns flowOf(0)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HomeViewModel {
        return HomeViewModel(
            context = context,
            accountRepository = accountRepository,
            backupRepository = backupRepository,
            appStateRepository = appStateRepository,
            logRepository = logRepository,
            restoreBackupUseCase = restoreBackupUseCase,
            rootUtil = rootUtil
        )
    }

    // ============================================================
    // Initial State Tests
    // ============================================================

    @Test
    fun `initial state should have default values`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.searchQuery)
        assertEquals(SortOption.LAST_PLAYED, viewModel.uiState.value.sortOption)
        assertEquals(SortDirection.DESC, viewModel.uiState.value.sortDirection)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `initial state should load saved search query`() = runTest {
        coEvery { appStateRepository.getLastSearchQuery() } returns "savedQuery"

        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("savedQuery", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun `initial state should load saved sort options`() = runTest {
        coEvery { appStateRepository.getSortOption() } returns "NAME"
        coEvery { appStateRepository.getSortDirection() } returns "ASC"

        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(SortOption.NAME, viewModel.uiState.value.sortOption)
        assertEquals(SortDirection.ASC, viewModel.uiState.value.sortDirection)
    }

    // ============================================================
    // Current Account Tests
    // ============================================================

    @Test
    fun `currentAccount should be account with newest lastPlayedAt`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // testAccount1 has lastPlayedAt = 5000L (newest)
        assertNotNull(viewModel.uiState.value.currentAccount)
        assertEquals(testAccount1.id, viewModel.uiState.value.currentAccount?.id)
    }

    @Test
    fun `currentAccount should be null when all accounts have lastPlayedAt 0`() = runTest {
        val neverPlayedAccounts = listOf(
            testAccount1.copy(lastPlayedAt = 0L),
            testAccount2.copy(lastPlayedAt = 0L),
            testAccount3.copy(lastPlayedAt = 0L)
        )
        every { accountRepository.getAllAccounts() } returns flowOf(neverPlayedAccounts)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.currentAccount)
    }

    @Test
    fun `currentAccount should be null when accounts list is empty`() = runTest {
        every { accountRepository.getAllAccounts() } returns flowOf(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.currentAccount)
    }

    // ============================================================
    // Search Tests
    // ============================================================

    @Test
    fun `onSearchQueryChange should update search query`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSearchQueryChange("test")

        assertEquals("test", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun `search should filter accounts by account name`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSearchQueryChange("Another")
        advanceUntilIdle()

        val filteredAccounts = viewModel.uiState.value.accounts
        assertEquals(1, filteredAccounts.size)
        assertEquals(testAccount2.id, filteredAccounts[0].id)
    }

    @Test
    fun `search should filter accounts by userId`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSearchQueryChange("user789")
        advanceUntilIdle()

        val filteredAccounts = viewModel.uiState.value.accounts
        assertEquals(1, filteredAccounts.size)
        assertEquals(testAccount2.id, filteredAccounts[0].id)
    }

    @Test
    fun `search should be case insensitive`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSearchQueryChange("ANOTHER")
        advanceUntilIdle()

        val filteredAccounts = viewModel.uiState.value.accounts
        assertEquals(1, filteredAccounts.size)
        assertEquals(testAccount2.id, filteredAccounts[0].id)
    }

    @Test
    fun `empty search should return all accounts`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSearchQueryChange("test")
        advanceUntilIdle()
        viewModel.onSearchQueryChange("")
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.accounts.size)
    }

    @Test
    fun `search with no results should return empty list`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSearchQueryChange("nonexistent")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.accounts.isEmpty())
    }

    @Test
    fun `search query should be persisted with debounce`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSearchQueryChange("test")
        advanceTimeBy(500) // Wait for debounce
        advanceUntilIdle()

        coVerify { appStateRepository.setLastSearchQuery("test") }
    }

    // ============================================================
    // Sort Tests
    // ============================================================

    @Test
    fun `onSortOptionChange should update sort option`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSortOptionChange(SortOption.NAME)
        advanceUntilIdle()

        assertEquals(SortOption.NAME, viewModel.uiState.value.sortOption)
        coVerify { appStateRepository.setSortOption("NAME") }
    }

    @Test
    fun `toggleSortDirection should toggle between ASC and DESC`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Initial is DESC
        assertEquals(SortDirection.DESC, viewModel.uiState.value.sortDirection)

        viewModel.toggleSortDirection()
        advanceUntilIdle()

        assertEquals(SortDirection.ASC, viewModel.uiState.value.sortDirection)
        coVerify { appStateRepository.setSortDirection("ASC") }

        viewModel.toggleSortDirection()
        advanceUntilIdle()

        assertEquals(SortDirection.DESC, viewModel.uiState.value.sortDirection)
        coVerify { appStateRepository.setSortDirection("DESC") }
    }

    @Test
    fun `sort by NAME ASC should order alphabetically`() = runTest {
        coEvery { appStateRepository.getSortOption() } returns "NAME"
        coEvery { appStateRepository.getSortDirection() } returns "ASC"

        viewModel = createViewModel()
        advanceUntilIdle()

        val accounts = viewModel.uiState.value.accounts
        assertEquals("AnotherAccount", accounts[0].accountName)
        assertEquals("TestAccount1", accounts[1].accountName)
        assertEquals("ZebraAccount", accounts[2].accountName)
    }

    @Test
    fun `sort by NAME DESC should order reverse alphabetically`() = runTest {
        coEvery { appStateRepository.getSortOption() } returns "NAME"
        coEvery { appStateRepository.getSortDirection() } returns "DESC"

        viewModel = createViewModel()
        advanceUntilIdle()

        val accounts = viewModel.uiState.value.accounts
        assertEquals("ZebraAccount", accounts[0].accountName)
        assertEquals("TestAccount1", accounts[1].accountName)
        assertEquals("AnotherAccount", accounts[2].accountName)
    }

    @Test
    fun `sort by LAST_PLAYED DESC should order newest first`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val accounts = viewModel.uiState.value.accounts
        // testAccount1 has lastPlayedAt = 5000L (newest)
        // testAccount2 has lastPlayedAt = 3000L
        // testAccount3 has lastPlayedAt = 0L
        assertEquals(testAccount1.id, accounts[0].id)
        assertEquals(testAccount2.id, accounts[1].id)
        assertEquals(testAccount3.id, accounts[2].id)
    }

    @Test
    fun `sort by CREATED_AT ASC should order oldest first`() = runTest {
        coEvery { appStateRepository.getSortOption() } returns "CREATED_AT"
        coEvery { appStateRepository.getSortDirection() } returns "ASC"

        viewModel = createViewModel()
        advanceUntilIdle()

        val accounts = viewModel.uiState.value.accounts
        assertEquals(testAccount1.id, accounts[0].id) // createdAt = 1000L
        assertEquals(testAccount2.id, accounts[1].id) // createdAt = 2000L
        assertEquals(testAccount3.id, accounts[2].id) // createdAt = 3000L
    }

    @Test
    fun `sort by USER_ID should order by userId`() = runTest {
        coEvery { appStateRepository.getSortOption() } returns "USER_ID"
        coEvery { appStateRepository.getSortDirection() } returns "ASC"

        viewModel = createViewModel()
        advanceUntilIdle()

        val accounts = viewModel.uiState.value.accounts
        // user123456, user345678, user789012 (alphabetical)
        assertEquals("user123456", accounts[0].userId)
        assertEquals("user345678", accounts[1].userId)
        assertEquals("user789012", accounts[2].userId)
    }

    // ============================================================
    // Statistics Tests
    // ============================================================

    @Test
    fun `statistics should reflect repository counts`() = runTest {
        every { accountRepository.getAccountCount() } returns flowOf(10)
        every { accountRepository.getErrorCount() } returns flowOf(2)
        every { accountRepository.getSusCount() } returns flowOf(3)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(10, viewModel.uiState.value.totalCount)
        assertEquals(2, viewModel.uiState.value.errorCount)
        assertEquals(3, viewModel.uiState.value.susCount)
    }

    // ============================================================
    // Launch Monopoly Go Tests
    // ============================================================

    @Test
    fun `launchMonopolyGoWithAccountState should show loading`() = runTest {
        coEvery { accountRepository.getAccountById(1L) } returns testAccount1
        coEvery { restoreBackupUseCase.execute(1L) } returns RestoreResult.Success("TestAccount1")
        coEvery { rootUtil.executeCommand(any()) } returns Result.success("")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.launchMonopolyGoWithAccountState(1L)

        // While in progress, isLoading should be true
        assertTrue(viewModel.uiState.value.isLoading)

        advanceUntilIdle()

        // After completion, isLoading should be false
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `launchMonopolyGoWithAccountState should call restoreBackupUseCase`() = runTest {
        coEvery { accountRepository.getAccountById(1L) } returns testAccount1
        coEvery { restoreBackupUseCase.execute(1L) } returns RestoreResult.Success("TestAccount1")
        coEvery { rootUtil.executeCommand(any()) } returns Result.success("")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.launchMonopolyGoWithAccountState(1L)
        advanceUntilIdle()

        coVerify { restoreBackupUseCase.execute(1L) }
    }

    @Test
    fun `launchMonopolyGoWithAccountState should start Monopoly Go on success`() = runTest {
        coEvery { accountRepository.getAccountById(1L) } returns testAccount1
        coEvery { restoreBackupUseCase.execute(1L) } returns RestoreResult.Success("TestAccount1")
        coEvery { rootUtil.executeCommand("am start -n com.scopely.monopolygo/.MainActivity") } returns Result.success("")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.launchMonopolyGoWithAccountState(1L)
        advanceUntilIdle()

        coVerify { rootUtil.executeCommand("am start -n com.scopely.monopolygo/.MainActivity") }
    }

    @Test
    fun `launchMonopolyGoWithAccountState should update lastPlayedAt on success`() = runTest {
        coEvery { accountRepository.getAccountById(1L) } returns testAccount1
        coEvery { restoreBackupUseCase.execute(1L) } returns RestoreResult.Success("TestAccount1")
        coEvery { rootUtil.executeCommand(any()) } returns Result.success("")
        coEvery { accountRepository.updateLastPlayedTimestamp(1L) } just Runs

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.launchMonopolyGoWithAccountState(1L)
        advanceUntilIdle()

        coVerify { accountRepository.updateLastPlayedTimestamp(1L) }
    }

    @Test
    fun `launchMonopolyGoWithAccountState should log error when account not found`() = runTest {
        coEvery { accountRepository.getAccountById(999L) } returns null

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.launchMonopolyGoWithAccountState(999L)
        advanceUntilIdle()

        coVerify { logRepository.logError("LAUNCH_MGO", match { it.contains("nicht gefunden") }) }
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `launchMonopolyGoWithAccountState should log error on restore failure`() = runTest {
        coEvery { accountRepository.getAccountById(1L) } returns testAccount1
        coEvery { restoreBackupUseCase.execute(1L) } returns RestoreResult.Failure("Restore error")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.launchMonopolyGoWithAccountState(1L)
        advanceUntilIdle()

        coVerify { logRepository.logError("LAUNCH_MGO", match { it.contains("Restore fehlgeschlagen") }) }
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `launchMonopolyGoWithAccountState should log error on launch failure`() = runTest {
        coEvery { accountRepository.getAccountById(1L) } returns testAccount1
        coEvery { restoreBackupUseCase.execute(1L) } returns RestoreResult.Success("TestAccount1")
        coEvery { rootUtil.executeCommand(any()) } returns Result.failure(Exception("Launch failed"))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.launchMonopolyGoWithAccountState(1L)
        advanceUntilIdle()

        coVerify { logRepository.logError("LAUNCH_MGO", match { it.contains("Fehler beim Starten") }) }
    }

    @Test
    fun `launchMonopolyGoWithAccountState should log info on start`() = runTest {
        coEvery { accountRepository.getAccountById(1L) } returns testAccount1
        coEvery { restoreBackupUseCase.execute(1L) } returns RestoreResult.Success("TestAccount1")
        coEvery { rootUtil.executeCommand(any()) } returns Result.success("")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.launchMonopolyGoWithAccountState(1L)
        advanceUntilIdle()

        coVerify { logRepository.logInfo("LAUNCH_MGO", match { it.contains("Starte Monopoly GO") }) }
    }

    // ============================================================
    // Backup Dialog Tests
    // ============================================================

    @Test
    fun `showBackupDialog should set showBackupDialog to true`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showBackupDialog()

        assertTrue(viewModel.uiState.value.showBackupDialog)
    }

    @Test
    fun `hideBackupDialog should set showBackupDialog to false`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showBackupDialog()
        viewModel.hideBackupDialog()

        assertFalse(viewModel.uiState.value.showBackupDialog)
    }

    @Test
    fun `clearBackupResult should set backupResult to null`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.clearBackupResult()

        assertNull(viewModel.uiState.value.backupResult)
    }

    // ============================================================
    // Restore Dialog Tests
    // ============================================================

    @Test
    fun `showRestoreConfirm should set showRestoreConfirm to accountId`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showRestoreConfirm(1L)

        assertEquals(1L, viewModel.uiState.value.showRestoreConfirm)
    }

    @Test
    fun `hideRestoreConfirm should set showRestoreConfirm to null`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showRestoreConfirm(1L)
        viewModel.hideRestoreConfirm()

        assertNull(viewModel.uiState.value.showRestoreConfirm)
    }

    @Test
    fun `clearRestoreResult should set restoreResult to null`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.clearRestoreResult()

        assertNull(viewModel.uiState.value.restoreResult)
    }

    @Test
    fun `hideRestoreSuccessDialog should set showRestoreSuccessDialog to false`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.hideRestoreSuccessDialog()

        assertFalse(viewModel.uiState.value.showRestoreSuccessDialog)
    }

    @Test
    fun `clearError should set errorMessage to null`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.clearError()

        assertNull(viewModel.uiState.value.errorMessage)
    }
}
