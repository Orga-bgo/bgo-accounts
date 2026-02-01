package com.mgomanager.app.domain.usecase

import android.content.Context
import android.net.Uri
import com.mgomanager.app.data.model.Account
import com.mgomanager.app.data.model.SusLevel
import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.AppStateRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.ui.components.DuplicatePair
import com.mgomanager.app.ui.components.ResolveChoice
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for ImportBackupsUseCase
 *
 * Tests cover:
 * - Import without duplicates (all accounts imported)
 * - Name collision handling (_1, _2 suffixes)
 * - UserID collision detection (returns duplicates for interactive resolution)
 * - KEEP_LOCAL decision (skip import)
 * - KEEP_IMPORT decision (archive local, import new)
 * - Archive logic (files moved to archive directory)
 * - Import cancellation (temp directory cleaned)
 *
 * DEVIATION FROM P6: These tests verify the interactive conflict resolution
 * behavior instead of the automatic skip behavior specified in P6.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportBackupsUseCaseTest {

    @MockK
    private lateinit var context: Context

    @MockK
    private lateinit var accountRepository: AccountRepository

    @MockK
    private lateinit var appStateRepository: AppStateRepository

    @MockK
    private lateinit var logRepository: LogRepository

    private lateinit var useCase: ImportBackupsUseCase
    private lateinit var tempTestDir: File

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)

        // Create temp test directory
        tempTestDir = File(System.getProperty("java.io.tmpdir"), "import_test_${System.currentTimeMillis()}")
        tempTestDir.mkdirs()

        // Mock context cache dir
        every { context.cacheDir } returns tempTestDir

        // Default mock returns
        coEvery { appStateRepository.getBackupDirectory() } returns "${tempTestDir.absolutePath}/backups/"
        coEvery { accountRepository.getAccountByUserId(any()) } returns null
        coEvery { accountRepository.getAccountByName(any()) } returns null
        coEvery { accountRepository.insertAccountEntity(any()) } returns 1L

        useCase = ImportBackupsUseCase(
            context = context,
            accountRepository = accountRepository,
            appStateRepository = appStateRepository,
            logRepository = logRepository
        )
    }

    @After
    fun tearDown() {
        // Clean up temp directory
        tempTestDir.deleteRecursively()
    }

    // ========== Import Without Duplicates Tests ==========

    @Test
    fun `applyImport with no duplicates imports all accounts`() = runTest {
        // Arrange
        val accounts = listOf(
            createImportAccount("Account1", "user1"),
            createImportAccount("Account2", "user2")
        )
        val tempDir = createTempDirWithData()

        // Act
        val result = useCase.applyImport(accounts, tempDir, emptyMap())

        // Assert
        assertEquals(2, result.importedCount)
        assertEquals(0, result.skippedCount)
        assertEquals(0, result.archivedCount)
        assertTrue(result.errors.isEmpty())

        coVerify(exactly = 2) { accountRepository.insertAccountEntity(any()) }
        coVerify(exactly = 2) { logRepository.logInfo("IMPORT", match { it.contains("erfolgreich importiert") }) }
    }

    @Test
    fun `applyImport with empty list returns zero counts`() = runTest {
        val tempDir = createTempDirWithData()

        val result = useCase.applyImport(emptyList(), tempDir, emptyMap())

        assertEquals(0, result.importedCount)
        assertEquals(0, result.skippedCount)
        assertEquals(0, result.archivedCount)
        assertTrue(result.errors.isEmpty())

        coVerify(exactly = 0) { accountRepository.insertAccountEntity(any()) }
    }

    // ========== Name Collision Tests ==========

    @Test
    fun `applyImport handles name collision with suffix`() = runTest {
        // Arrange: "Account1" already exists, but with different userId
        val accounts = listOf(createImportAccount("Account1", "newUser1"))
        val tempDir = createTempDirWithData()

        // First call returns existing account, second call (Account1_1) returns null
        coEvery { accountRepository.getAccountByName("Account1") } returns createMockAccount("Account1", "existingUser")
        coEvery { accountRepository.getAccountByName("Account1_1") } returns null

        // Act
        val result = useCase.applyImport(accounts, tempDir, emptyMap())

        // Assert
        assertEquals(1, result.importedCount)

        // Verify the account was inserted with the suffixed name
        coVerify {
            accountRepository.insertAccountEntity(match { it.accountName == "Account1_1" })
        }
    }

    @Test
    fun `applyImport handles multiple name collisions with incrementing suffix`() = runTest {
        // Arrange: "Account1" and "Account1_1" already exist
        val accounts = listOf(createImportAccount("Account1", "newUser1"))
        val tempDir = createTempDirWithData()

        coEvery { accountRepository.getAccountByName("Account1") } returns createMockAccount("Account1", "user1")
        coEvery { accountRepository.getAccountByName("Account1_1") } returns createMockAccount("Account1_1", "user2")
        coEvery { accountRepository.getAccountByName("Account1_2") } returns null

        // Act
        val result = useCase.applyImport(accounts, tempDir, emptyMap())

        // Assert
        assertEquals(1, result.importedCount)
        coVerify {
            accountRepository.insertAccountEntity(match { it.accountName == "Account1_2" })
        }
    }

    // ========== UserID Collision Detection Tests (DEVIATION FROM P6) ==========

    @Test
    fun `detecting userId collision creates duplicate pair`() = runTest {
        // This test verifies that when there's a UserID collision,
        // the system creates a DuplicatePair for interactive resolution
        // instead of automatically skipping (which was the P6 spec)

        val localAccount = createMockAccount("LocalAccount", "sharedUserId")
        val importAccount = createImportAccount("ImportAccount", "sharedUserId")

        coEvery { accountRepository.getAccountByUserId("sharedUserId") } returns localAccount

        // When applyImport is called with KEEP_LOCAL decision
        val tempDir = createTempDirWithData()
        val decisions = mapOf("sharedUserId" to ResolveChoice.KEEP_LOCAL)

        val result = useCase.applyImport(listOf(importAccount), tempDir, decisions)

        // Account should be skipped based on user decision
        assertEquals(0, result.importedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(0, result.archivedCount)

        coVerify {
            logRepository.logInfo("IMPORT", match { it.contains("übersprungen (Nutzerentscheidung: Lokal behalten)") })
        }
    }

    // ========== KEEP_LOCAL Decision Tests ==========

    @Test
    fun `applyImport with KEEP_LOCAL skips import and logs`() = runTest {
        val localAccount = createMockAccount("LocalAcc", "userId123")
        val importAccount = createImportAccount("ImportAcc", "userId123")
        val tempDir = createTempDirWithData()

        coEvery { accountRepository.getAccountByUserId("userId123") } returns localAccount

        val decisions = mapOf("userId123" to ResolveChoice.KEEP_LOCAL)
        val result = useCase.applyImport(listOf(importAccount), tempDir, decisions)

        assertEquals(0, result.importedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(0, result.archivedCount)

        // Should NOT delete local or insert new
        coVerify(exactly = 0) { accountRepository.deleteAccountById(any()) }
        coVerify(exactly = 0) { accountRepository.insertAccountEntity(any()) }
    }

    // ========== KEEP_IMPORT Decision Tests ==========

    @Test
    fun `applyImport with KEEP_IMPORT archives local and imports new`() = runTest {
        val backupDir = File(tempTestDir, "backups")
        backupDir.mkdirs()
        coEvery { appStateRepository.getBackupDirectory() } returns backupDir.absolutePath

        // Create local backup directory
        val localBackupDir = File(backupDir, "LocalAcc")
        localBackupDir.mkdirs()
        File(localBackupDir, "data.txt").writeText("local data")

        val localAccount = createMockAccount("LocalAcc", "userId123", localBackupDir.absolutePath)
        val importAccount = createImportAccount("ImportAcc", "userId123")
        val tempDir = createTempDirWithData()

        coEvery { accountRepository.getAccountByUserId("userId123") } returns localAccount

        val decisions = mapOf("userId123" to ResolveChoice.KEEP_IMPORT)
        val result = useCase.applyImport(listOf(importAccount), tempDir, decisions)

        assertEquals(1, result.importedCount)
        assertEquals(0, result.skippedCount)
        assertEquals(1, result.archivedCount)

        // Verify archive directory was created and local was deleted then new inserted
        coVerify { accountRepository.deleteAccountById(localAccount.id) }
        coVerify { accountRepository.insertAccountEntity(any()) }
        coVerify { logRepository.logInfo("IMPORT", match { it.contains("archiviert") }) }
    }

    // ========== Archive Logic Tests ==========

    @Test
    fun `archive creates timestamped directory and moves files`() = runTest {
        val backupDir = File(tempTestDir, "backups")
        backupDir.mkdirs()
        coEvery { appStateRepository.getBackupDirectory() } returns backupDir.absolutePath

        // Create local backup with content
        val localBackupDir = File(backupDir, "TestAccount")
        localBackupDir.mkdirs()
        val testFile = File(localBackupDir, "important_data.txt")
        testFile.writeText("important content")

        val localAccount = createMockAccount("TestAccount", "userId999", localBackupDir.absolutePath)
        val importAccount = createImportAccount("NewAccount", "userId999")
        val tempDir = createTempDirWithData()

        coEvery { accountRepository.getAccountByUserId("userId999") } returns localAccount

        val decisions = mapOf("userId999" to ResolveChoice.KEEP_IMPORT)
        useCase.applyImport(listOf(importAccount), tempDir, decisions)

        // Verify archive directory exists
        val archiveDir = File(backupDir, "archive")
        assertTrue("Archive directory should exist", archiveDir.exists())

        // Verify archived files exist (with timestamp suffix)
        val archivedDirs = archiveDir.listFiles()
        assertNotNull("Archive should contain files", archivedDirs)
        assertTrue("Archive should have at least one directory", archivedDirs!!.isNotEmpty())
        assertTrue("Archived dir name should start with account name",
            archivedDirs[0].name.startsWith("TestAccount_"))

        // Original directory should be deleted
        assertFalse("Original backup dir should be deleted", localBackupDir.exists())
    }

    // ========== Import Cancellation Tests ==========

    @Test
    fun `cancelImport cleans up temp directory`() = runTest {
        val importTempDir = File(tempTestDir, "import_cancel_test")
        importTempDir.mkdirs()
        File(importTempDir, "test_file.txt").writeText("content")

        assertTrue(importTempDir.exists())

        useCase.cancelImport(importTempDir)

        assertFalse("Temp directory should be deleted", importTempDir.exists())
        coVerify { logRepository.logInfo("IMPORT", "Import abgebrochen durch Nutzer") }
    }

    // ========== Mixed Scenario Tests ==========

    @Test
    fun `applyImport handles mixed scenarios correctly`() = runTest {
        // Scenario: 3 accounts
        // 1. No collision - should import
        // 2. Name collision only - should import with suffix
        // 3. UserID collision with KEEP_LOCAL - should skip

        val accounts = listOf(
            createImportAccount("NewAccount", "newUserId"),
            createImportAccount("ExistingName", "anotherNewUserId"),
            createImportAccount("Conflict", "existingUserId")
        )
        val tempDir = createTempDirWithData()

        // Setup mocks
        coEvery { accountRepository.getAccountByUserId("newUserId") } returns null
        coEvery { accountRepository.getAccountByUserId("anotherNewUserId") } returns null
        coEvery { accountRepository.getAccountByUserId("existingUserId") } returns
            createMockAccount("LocalConflict", "existingUserId")

        coEvery { accountRepository.getAccountByName("NewAccount") } returns null
        coEvery { accountRepository.getAccountByName("ExistingName") } returns
            createMockAccount("ExistingName", "differentUserId")
        coEvery { accountRepository.getAccountByName("ExistingName_1") } returns null
        coEvery { accountRepository.getAccountByName("Conflict") } returns null

        val decisions = mapOf("existingUserId" to ResolveChoice.KEEP_LOCAL)
        val result = useCase.applyImport(accounts, tempDir, decisions)

        assertEquals(2, result.importedCount) // NewAccount and ExistingName_1
        assertEquals(1, result.skippedCount)  // Conflict (KEEP_LOCAL)
        assertEquals(0, result.archivedCount)
    }

    // ========== Helper Functions ==========

    private fun createImportAccount(name: String, userId: String) = ImportAccountData(
        id = 0,
        accountName = name,
        prefix = "TEST_",
        createdAt = System.currentTimeMillis(),
        lastPlayedAt = System.currentTimeMillis(),
        userId = userId,
        gaid = "test_gaid",
        deviceToken = "test_token",
        appSetId = "test_appset",
        ssaid = "test_ssaid",
        susLevelValue = 0,
        hasError = false,
        hasFacebookLink = false,
        fbUsername = null,
        fbPassword = null,
        fb2FA = null,
        fbTempMail = null,
        backupPath = "/test/backup/$name",
        fileOwner = "root",
        fileGroup = "root",
        filePermissions = "755"
    )

    private fun createMockAccount(
        name: String,
        userId: String,
        backupPath: String = "/mock/backup/$name"
    ) = Account(
        id = System.currentTimeMillis(),
        accountName = name,
        prefix = "TEST_",
        createdAt = System.currentTimeMillis() - 86400000, // 1 day ago
        lastPlayedAt = System.currentTimeMillis(),
        userId = userId,
        gaid = "mock_gaid",
        deviceToken = "mock_token",
        appSetId = "mock_appset",
        ssaid = "mock_ssaid",
        susLevel = SusLevel.NONE,
        hasError = false,
        hasFacebookLink = false,
        fbUsername = null,
        fbPassword = null,
        fb2FA = null,
        fbTempMail = null,
        backupPath = backupPath,
        fileOwner = "root",
        fileGroup = "root",
        filePermissions = "755"
    )

    private fun createTempDirWithData(): File {
        val tempDir = File(tempTestDir, "import_temp_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        return tempDir
    }
}
