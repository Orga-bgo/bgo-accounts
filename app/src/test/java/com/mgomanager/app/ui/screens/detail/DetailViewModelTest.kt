package com.mgomanager.app.ui.screens.detail

import android.content.ClipboardManager
import android.content.Context
import com.mgomanager.app.data.model.Account
import com.mgomanager.app.data.model.RestoreResult
import com.mgomanager.app.data.model.SusLevel
import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.BackupRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.util.RootUtil
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

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    @MockK
    private lateinit var context: Context

    @MockK
    private lateinit var accountRepository: AccountRepository

    @MockK
    private lateinit var backupRepository: BackupRepository

    @MockK
    private lateinit var logRepository: LogRepository

    @MockK
    private lateinit var rootUtil: RootUtil

    @MockK
    private lateinit var clipboardManager: ClipboardManager

    private lateinit var viewModel: DetailViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testAccount = Account(
        id = 1L,
        accountName = "TestAccount",
        prefix = "test_",
        userId = "user123",
        ssaid = "ssaid123",
        gaid = "gaid123",
        deviceToken = "token123",
        appSetId = "appset123",
        backupPath = "/backup/test_TestAccount/",
        susLevel = SusLevel.NONE,
        hasError = false,
        hasFacebookLink = true,
        fbUsername = "fbuser",
        fbPassword = "fbpass",
        fb2FA = "123456",
        fbTempMail = "test@temp.com"
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)
        Dispatchers.setMain(testDispatcher)

        every { context.getSystemService(Context.CLIPBOARD_SERVICE) } returns clipboardManager
        coEvery { accountRepository.getAccountByIdFlow(any()) } returns flowOf(testAccount)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): DetailViewModel {
        return DetailViewModel(
            context = context,
            accountRepository = accountRepository,
            backupRepository = backupRepository,
            logRepository = logRepository,
            rootUtil = rootUtil
        )
    }

    @Test
    fun `loadAccount updates state with account data`() = runTest {
        viewModel = createViewModel()

        viewModel.loadAccount(1L)
        advanceUntilIdle()

        assertEquals(testAccount, viewModel.uiState.value.account)
        coVerify { accountRepository.getAccountByIdFlow(1L) }
    }

    @Test
    fun `showRestoreDialog sets flag to true`() = runTest {
        viewModel = createViewModel()

        viewModel.showRestoreDialog()

        assertTrue(viewModel.uiState.value.showRestoreDialog)
    }

    @Test
    fun `hideRestoreDialog sets flag to false and clears result`() = runTest {
        viewModel = createViewModel()

        viewModel.showRestoreDialog()
        viewModel.hideRestoreDialog()

        assertFalse(viewModel.uiState.value.showRestoreDialog)
        assertNull(viewModel.uiState.value.restoreResult)
    }

    @Test
    fun `showEditDialog sets flag to true`() = runTest {
        viewModel = createViewModel()

        viewModel.showEditDialog()

        assertTrue(viewModel.uiState.value.showEditDialog)
    }

    @Test
    fun `hideEditDialog sets flag to false`() = runTest {
        viewModel = createViewModel()

        viewModel.showEditDialog()
        viewModel.hideEditDialog()

        assertFalse(viewModel.uiState.value.showEditDialog)
    }

    @Test
    fun `showDeleteDialog sets flag to true`() = runTest {
        viewModel = createViewModel()

        viewModel.showDeleteDialog()

        assertTrue(viewModel.uiState.value.showDeleteDialog)
    }

    @Test
    fun `hideDeleteDialog sets flag to false`() = runTest {
        viewModel = createViewModel()

        viewModel.showDeleteDialog()
        viewModel.hideDeleteDialog()

        assertFalse(viewModel.uiState.value.showDeleteDialog)
    }

    @Test
    fun `restoreAccount calls backupRepository and shows success dialog on success`() = runTest {
        viewModel = createViewModel()
        coEvery { backupRepository.restoreBackup(any()) } returns RestoreResult.Success

        viewModel.loadAccount(1L)
        advanceUntilIdle()

        viewModel.restoreAccount()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showRestoreSuccessDialog)
        assertFalse(viewModel.uiState.value.showRestoreDialog)
        coVerify { backupRepository.restoreBackup(1L) }
        coVerify { logRepository.logInfo("DETAIL", any()) }
    }

    @Test
    fun `restoreAccount logs error on failure`() = runTest {
        viewModel = createViewModel()
        val errorMessage = "Restore fehlgeschlagen"
        coEvery { backupRepository.restoreBackup(any()) } returns RestoreResult.Failure(errorMessage)

        viewModel.loadAccount(1L)
        advanceUntilIdle()

        viewModel.restoreAccount()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showRestoreSuccessDialog)
        coVerify { logRepository.logError("DETAIL", any(), any()) }
    }

    @Test
    fun `deleteAccount deletes backup folder first then DB entry`() = runTest {
        viewModel = createViewModel()
        var deleteOrderCheck = mutableListOf<String>()

        coEvery { rootUtil.executeCommand(any()) } answers {
            deleteOrderCheck.add("folder")
            Result.success(Unit)
        }
        coEvery { accountRepository.deleteAccount(any()) } answers {
            deleteOrderCheck.add("db")
        }

        viewModel.loadAccount(1L)
        advanceUntilIdle()

        var deletedCalled = false
        viewModel.deleteAccount { deletedCalled = true }
        advanceUntilIdle()

        // Verify order: folder first, then DB (per P5 spec)
        assertEquals(listOf("folder", "db"), deleteOrderCheck)
        assertTrue(deletedCalled)
        assertFalse(viewModel.uiState.value.showDeleteDialog)

        coVerify { rootUtil.executeCommand("rm -rf /backup/test_TestAccount/") }
        coVerify { accountRepository.deleteAccount(testAccount) }
        coVerify { logRepository.logInfo("DETAIL", match { it.contains("Backup-Ordner gelöscht") }) }
        coVerify { logRepository.logInfo("DETAIL", match { it.contains("Account aus DB gelöscht") }) }
    }

    @Test
    fun `deleteAccount continues with DB deletion even if folder deletion fails`() = runTest {
        viewModel = createViewModel()
        coEvery { rootUtil.executeCommand(any()) } returns Result.failure(Exception("Permission denied"))
        coEvery { accountRepository.deleteAccount(any()) } just Runs

        viewModel.loadAccount(1L)
        advanceUntilIdle()

        var deletedCalled = false
        viewModel.deleteAccount { deletedCalled = true }
        advanceUntilIdle()

        assertTrue(deletedCalled)
        coVerify { logRepository.logWarning("DETAIL", any()) }
        coVerify { accountRepository.deleteAccount(testAccount) }
    }

    @Test
    fun `updateAccount updates account in repository`() = runTest {
        viewModel = createViewModel()
        coEvery { accountRepository.updateAccount(any()) } just Runs

        viewModel.loadAccount(1L)
        advanceUntilIdle()
        viewModel.showEditDialog()

        viewModel.updateAccount(
            name = "NewName",
            susLevel = SusLevel.MEDIUM,
            hasError = true,
            fbUsername = "newuser",
            fbPassword = "newpass",
            fb2FA = "654321",
            fbTempMail = "new@temp.com"
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showEditDialog)
        coVerify {
            accountRepository.updateAccount(match {
                it.accountName == "NewName" &&
                it.susLevel == SusLevel.MEDIUM &&
                it.hasError &&
                it.fbUsername == "newuser"
            })
        }
    }

    @Test
    fun `updateAccountFull updates all fields in repository`() = runTest {
        viewModel = createViewModel()
        coEvery { accountRepository.updateAccount(any()) } just Runs

        viewModel.loadAccount(1L)
        advanceUntilIdle()
        viewModel.showEditDialog()

        viewModel.updateAccountFull(
            name = "NewName",
            userId = "newUserId",
            ssaid = "newSsaid",
            gaid = "newGaid",
            deviceToken = "newToken",
            appSetId = "newAppSet",
            susLevel = SusLevel.HIGH,
            hasError = true,
            hasFacebookLink = false,
            fbUsername = null,
            fbPassword = null,
            fb2FA = null,
            fbTempMail = null,
            backupPath = "/new/backup/path/"
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showEditDialog)
        coVerify {
            accountRepository.updateAccount(match {
                it.accountName == "NewName" &&
                it.userId == "newUserId" &&
                it.ssaid == "newSsaid" &&
                it.gaid == "newGaid" &&
                it.deviceToken == "newToken" &&
                it.appSetId == "newAppSet" &&
                it.susLevel == SusLevel.HIGH &&
                it.hasError &&
                !it.hasFacebookLink &&
                it.backupPath == "/new/backup/path/"
            })
        }
        coVerify { logRepository.logInfo("DETAIL", match { it.contains("Account aktualisiert") }) }
    }

    @Test
    fun `copyToClipboard copies value to clipboard`() = runTest {
        viewModel = createViewModel()

        viewModel.copyToClipboard("Test Label", "Test Value")

        verify { clipboardManager.setPrimaryClip(any()) }
    }

    @Test
    fun `hideRestoreSuccessDialog sets flag to false`() = runTest {
        viewModel = createViewModel()
        coEvery { backupRepository.restoreBackup(any()) } returns RestoreResult.Success

        viewModel.loadAccount(1L)
        advanceUntilIdle()
        viewModel.restoreAccount()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showRestoreSuccessDialog)

        viewModel.hideRestoreSuccessDialog()

        assertFalse(viewModel.uiState.value.showRestoreSuccessDialog)
    }

    @Test
    fun `clearError sets errorMessage to null`() = runTest {
        viewModel = createViewModel()

        viewModel.clearError()

        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `three actions available - Restore, Edit, Delete`() = runTest {
        viewModel = createViewModel()

        // Verify all three dialog methods exist and work
        viewModel.showRestoreDialog()
        assertTrue(viewModel.uiState.value.showRestoreDialog)
        viewModel.hideRestoreDialog()

        viewModel.showEditDialog()
        assertTrue(viewModel.uiState.value.showEditDialog)
        viewModel.hideEditDialog()

        viewModel.showDeleteDialog()
        assertTrue(viewModel.uiState.value.showDeleteDialog)
        viewModel.hideDeleteDialog()
    }
}
