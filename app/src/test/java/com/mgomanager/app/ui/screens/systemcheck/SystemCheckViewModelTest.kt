package com.mgomanager.app.ui.screens.systemcheck

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.mgomanager.app.data.repository.AppStateRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.util.FileUtil
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
class SystemCheckViewModelTest {

    private lateinit var viewModel: SystemCheckViewModel
    private lateinit var context: Context
    private lateinit var appStateRepository: AppStateRepository
    private lateinit var rootUtil: RootUtil
    private lateinit var fileUtil: FileUtil
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
        logRepository = mockk(relaxed = true)
        packageManager = mockk(relaxed = true)

        every { context.packageManager } returns packageManager
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SystemCheckViewModel {
        return SystemCheckViewModel(
            context = context,
            appStateRepository = appStateRepository,
            rootUtil = rootUtil,
            fileUtil = fileUtil,
            logRepository = logRepository
        )
    }

    @Test
    fun `initial state should be checking`() = runTest {
        coEvery { rootUtil.requestRootAccess() } returns false

        viewModel = createViewModel()

        assertTrue(viewModel.uiState.value.isChecking)
    }

    @Test
    fun `all checks should pass when conditions are met`() = runTest {
        // Setup all checks to pass
        coEvery { rootUtil.requestRootAccess() } returns true
        every { packageManager.getPackageInfo("com.scopely.monopolygo", 0) } returns PackageInfo()
        coEvery { appStateRepository.getMonopolyGoInstalled() } returns true
        coEvery { appStateRepository.setMonopolyGoInstalled(any()) } just Runs
        coEvery { rootUtil.executeCommand(match { it.contains("stat") }) } returns Result.success("10123")
        coEvery { appStateRepository.getMonopolyGoUid() } returns 10123
        coEvery { appStateRepository.setMonopolyGoUid(any()) } just Runs
        coEvery { appStateRepository.getBackupDirectory() } returns "/storage/emulated/0/bgo_backups/"
        coEvery { fileUtil.directoryExists(any()) } returns true
        coEvery { fileUtil.isDirectoryWritable(any()) } returns true
        coEvery { rootUtil.executeCommand(match { it.contains("ls") }) } returns Result.success("files")
        coEvery { appStateRepository.setLastSystemCheckTimestamp(any()) } just Runs

        viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isChecking)
        assertTrue(viewModel.uiState.value.allChecksPassed)
        assertNull(viewModel.uiState.value.criticalError)
        assertEquals(5, viewModel.uiState.value.checks.size)
    }

    @Test
    fun `should fail when root is not available`() = runTest {
        coEvery { rootUtil.requestRootAccess() } returns false

        viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isChecking)
        assertFalse(viewModel.uiState.value.allChecksPassed)
        assertNotNull(viewModel.uiState.value.criticalError)
        assertTrue(viewModel.uiState.value.criticalError!!.contains("Root"))

        val rootCheck = viewModel.uiState.value.checks.find { it.id == "root" }
        assertNotNull(rootCheck)
        assertEquals(CheckStatus.FAILED, rootCheck?.status)
    }

    @Test
    fun `should fail when Monopoly Go is not installed`() = runTest {
        coEvery { rootUtil.requestRootAccess() } returns true
        every { packageManager.getPackageInfo("com.scopely.monopolygo", 0) } throws PackageManager.NameNotFoundException()
        coEvery { appStateRepository.getMonopolyGoInstalled() } returns false
        coEvery { appStateRepository.setMonopolyGoInstalled(any()) } just Runs

        viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isChecking)
        assertFalse(viewModel.uiState.value.allChecksPassed)
        assertNotNull(viewModel.uiState.value.criticalError)
        assertTrue(viewModel.uiState.value.criticalError!!.contains("Monopoly Go"))

        val mgoCheck = viewModel.uiState.value.checks.find { it.id == "monopoly_go" }
        assertNotNull(mgoCheck)
        assertEquals(CheckStatus.FAILED, mgoCheck?.status)
    }

    @Test
    fun `retryChecks should restart all checks`() = runTest {
        // First run - fail root
        coEvery { rootUtil.requestRootAccess() } returns false

        viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.allChecksPassed)

        // Setup for retry - pass everything
        coEvery { rootUtil.requestRootAccess() } returns true
        every { packageManager.getPackageInfo("com.scopely.monopolygo", 0) } returns PackageInfo()
        coEvery { appStateRepository.getMonopolyGoInstalled() } returns true
        coEvery { appStateRepository.setMonopolyGoInstalled(any()) } just Runs
        coEvery { rootUtil.executeCommand(match { it.contains("stat") }) } returns Result.success("10123")
        coEvery { appStateRepository.getMonopolyGoUid() } returns 10123
        coEvery { appStateRepository.setMonopolyGoUid(any()) } just Runs
        coEvery { appStateRepository.getBackupDirectory() } returns "/storage/emulated/0/bgo_backups/"
        coEvery { fileUtil.directoryExists(any()) } returns true
        coEvery { fileUtil.isDirectoryWritable(any()) } returns true
        coEvery { rootUtil.executeCommand(match { it.contains("ls") }) } returns Result.success("files")
        coEvery { appStateRepository.setLastSystemCheckTimestamp(any()) } just Runs

        viewModel.retryChecks()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.allChecksPassed)
    }

    @Test
    fun `should detect UID change and update repository`() = runTest {
        coEvery { rootUtil.requestRootAccess() } returns true
        every { packageManager.getPackageInfo("com.scopely.monopolygo", 0) } returns PackageInfo()
        coEvery { appStateRepository.getMonopolyGoInstalled() } returns true
        coEvery { appStateRepository.setMonopolyGoInstalled(any()) } just Runs
        coEvery { rootUtil.executeCommand(match { it.contains("stat") }) } returns Result.success("10456") // New UID
        coEvery { appStateRepository.getMonopolyGoUid() } returns 10123 // Old UID
        coEvery { appStateRepository.setMonopolyGoUid(any()) } just Runs
        coEvery { appStateRepository.getBackupDirectory() } returns "/storage/emulated/0/bgo_backups/"
        coEvery { fileUtil.directoryExists(any()) } returns true
        coEvery { fileUtil.isDirectoryWritable(any()) } returns true
        coEvery { rootUtil.executeCommand(match { it.contains("ls") }) } returns Result.success("files")
        coEvery { appStateRepository.setLastSystemCheckTimestamp(any()) } just Runs

        viewModel = createViewModel()
        advanceUntilIdle()

        // Verify UID was updated
        coVerify { appStateRepository.setMonopolyGoUid(10456) }
        coVerify { logRepository.logInfo("UID_CHANGED", match { it.contains("10123") && it.contains("10456") }) }
    }

    @Test
    fun `should show warning when backup directory is not accessible`() = runTest {
        coEvery { rootUtil.requestRootAccess() } returns true
        every { packageManager.getPackageInfo("com.scopely.monopolygo", 0) } returns PackageInfo()
        coEvery { appStateRepository.getMonopolyGoInstalled() } returns true
        coEvery { appStateRepository.setMonopolyGoInstalled(any()) } just Runs
        coEvery { rootUtil.executeCommand(match { it.contains("stat") }) } returns Result.success("10123")
        coEvery { appStateRepository.getMonopolyGoUid() } returns 10123
        coEvery { appStateRepository.setMonopolyGoUid(any()) } just Runs
        coEvery { appStateRepository.getBackupDirectory() } returns null // No backup directory set
        coEvery { rootUtil.executeCommand(match { it.contains("ls") }) } returns Result.success("files")
        coEvery { appStateRepository.setLastSystemCheckTimestamp(any()) } just Runs

        viewModel = createViewModel()
        advanceUntilIdle()

        val backupDirCheck = viewModel.uiState.value.checks.find { it.id == "backup_dir" }
        assertNotNull(backupDirCheck)
        assertEquals(CheckStatus.WARNING, backupDirCheck?.status)

        // Should still pass overall since backup_dir is not critical
        assertTrue(viewModel.uiState.value.allChecksPassed)
    }

    @Test
    fun `should fail when data_data access is denied`() = runTest {
        coEvery { rootUtil.requestRootAccess() } returns true
        every { packageManager.getPackageInfo("com.scopely.monopolygo", 0) } returns PackageInfo()
        coEvery { appStateRepository.getMonopolyGoInstalled() } returns true
        coEvery { appStateRepository.setMonopolyGoInstalled(any()) } just Runs
        coEvery { rootUtil.executeCommand(match { it.contains("stat") }) } returns Result.success("10123")
        coEvery { appStateRepository.getMonopolyGoUid() } returns 10123
        coEvery { appStateRepository.setMonopolyGoUid(any()) } just Runs
        coEvery { appStateRepository.getBackupDirectory() } returns "/storage/emulated/0/bgo_backups/"
        coEvery { fileUtil.directoryExists(any()) } returns true
        coEvery { fileUtil.isDirectoryWritable(any()) } returns true
        coEvery { rootUtil.executeCommand(match { it.contains("ls") }) } returns Result.failure(Exception("Permission denied"))
        coEvery { appStateRepository.setLastSystemCheckTimestamp(any()) } just Runs

        viewModel = createViewModel()
        advanceUntilIdle()

        val dataDataCheck = viewModel.uiState.value.checks.find { it.id == "data_data" }
        assertNotNull(dataDataCheck)
        assertEquals(CheckStatus.FAILED, dataDataCheck?.status)

        assertFalse(viewModel.uiState.value.allChecksPassed)
    }

    @Test
    fun `check order should be correct`() = runTest {
        coEvery { rootUtil.requestRootAccess() } returns true
        every { packageManager.getPackageInfo("com.scopely.monopolygo", 0) } returns PackageInfo()
        coEvery { appStateRepository.getMonopolyGoInstalled() } returns true
        coEvery { appStateRepository.setMonopolyGoInstalled(any()) } just Runs
        coEvery { rootUtil.executeCommand(match { it.contains("stat") }) } returns Result.success("10123")
        coEvery { appStateRepository.getMonopolyGoUid() } returns 10123
        coEvery { appStateRepository.setMonopolyGoUid(any()) } just Runs
        coEvery { appStateRepository.getBackupDirectory() } returns "/storage/emulated/0/bgo_backups/"
        coEvery { fileUtil.directoryExists(any()) } returns true
        coEvery { fileUtil.isDirectoryWritable(any()) } returns true
        coEvery { rootUtil.executeCommand(match { it.contains("ls") }) } returns Result.success("files")
        coEvery { appStateRepository.setLastSystemCheckTimestamp(any()) } just Runs

        viewModel = createViewModel()
        advanceUntilIdle()

        val checkIds = viewModel.uiState.value.checks.map { it.id }
        assertEquals(listOf("root", "monopoly_go", "uid", "backup_dir", "data_data"), checkIds)
    }
}
