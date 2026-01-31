package com.mgomanager.app.domain.util

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FilePermissionManagerTest {

    private lateinit var rootUtil: RootUtil
    private lateinit var permissionManager: FilePermissionManager

    @Before
    fun setup() {
        rootUtil = mockk(relaxed = true)
        permissionManager = FilePermissionManager(rootUtil)
    }

    @Test
    fun `getFilePermissions should parse GNU stat format successfully`() = runTest {
        // Given
        val path = "/data/data/com.scopely.monopolygo/files/DiskBasedCacheDirectory"
        val expectedOutput = "u0_a123:u0_a456 755"
        
        coEvery { rootUtil.executeCommand("stat -c '%U:%G %a' \"$path\"") } returns Result.success(expectedOutput)

        // When
        val result = permissionManager.getFilePermissions(path)

        // Then
        assertTrue(result.isSuccess)
        val permissions = result.getOrThrow()
        assertEquals("u0_a123", permissions.owner)
        assertEquals("u0_a456", permissions.group)
        assertEquals("755", permissions.permissions)
    }

    @Test
    fun `getFilePermissions should parse Android stat format successfully`() = runTest {
        // Given
        val path = "/data/data/com.scopely.monopolygo/files/DiskBasedCacheDirectory"
        val statOutput = """
            Uid: ( 10123/ u0_a123)   Gid: ( 10456/ u0_a456)
            Access: (0755/drwxr-xr-x)
        """.trimIndent()
        
        // First stat command fails, second one succeeds
        coEvery { rootUtil.executeCommand("stat -c '%U:%G %a' \"$path\"") } returns Result.failure(Exception("Command failed"))
        coEvery { rootUtil.executeCommand("stat \"$path\" | grep -E 'Uid:|Access:' | head -2") } returns Result.success(statOutput)

        // When
        val result = permissionManager.getFilePermissions(path)

        // Then
        assertTrue(result.isSuccess)
        val permissions = result.getOrThrow()
        assertEquals("u0_a123", permissions.owner)
        assertEquals("u0_a456", permissions.group)
        assertEquals("755", permissions.permissions)
    }

    @Test
    fun `getFilePermissions should parse ls format successfully`() = runTest {
        // Given
        val path = "/data/data/com.scopely.monopolygo/files/DiskBasedCacheDirectory"
        val lsOutput = "drwxr-xr-x 2 u0_a123 u0_a456 4096 Jan 24 20:00 /data/data/com.scopely.monopolygo/files/DiskBasedCacheDirectory"
        
        // First two stat commands fail, ls succeeds
        coEvery { rootUtil.executeCommand("stat -c '%U:%G %a' \"$path\"") } returns Result.failure(Exception("Command failed"))
        coEvery { rootUtil.executeCommand("stat \"$path\" | grep -E 'Uid:|Access:' | head -2") } returns Result.failure(Exception("Command failed"))
        coEvery { rootUtil.executeCommand("ls -ld \"$path\"") } returns Result.success(lsOutput)

        // When
        val result = permissionManager.getFilePermissions(path)

        // Then
        assertTrue(result.isSuccess)
        val permissions = result.getOrThrow()
        assertEquals("u0_a123", permissions.owner)
        assertEquals("u0_a456", permissions.group)
        assertEquals("755", permissions.permissions)
    }

    @Test
    fun `getFilePermissions should fail when all methods fail`() = runTest {
        // Given
        val path = "/data/data/com.scopely.monopolygo/files/DiskBasedCacheDirectory"
        
        // All commands fail
        coEvery { rootUtil.executeCommand(any()) } returns Result.failure(Exception("Command failed"))

        // When
        val result = permissionManager.getFilePermissions(path)

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `setFileOwnership should use quoted paths`() = runTest {
        // Given
        val path = "/data/data/com.scopely.monopolygo/files/DiskBasedCacheDirectory"
        val owner = "u0_a123"
        val group = "u0_a456"
        
        coEvery { rootUtil.executeCommand("chown -R $owner:$group \"$path\"") } returns Result.success("")

        // When
        val result = permissionManager.setFileOwnership(path, owner, group)

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `setFilePermissions should use quoted paths`() = runTest {
        // Given
        val path = "/data/data/com.scopely.monopolygo/files/DiskBasedCacheDirectory"
        val permissions = "755"
        
        coEvery { rootUtil.executeCommand("chmod -R $permissions \"$path\"") } returns Result.success("")

        // When
        val result = permissionManager.setFilePermissions(path, permissions)

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `getFilePermissions should handle Android stat with different spacing`() = runTest {
        // Given
        val path = "/data/data/com.scopely.monopolygo/files/DiskBasedCacheDirectory"
        // Different spacing and formatting
        val statOutput = """
            Uid: (10123/u0_a123)   Gid: (10456/u0_a456)
            Access: (0700/drwx------)
        """.trimIndent()
        
        coEvery { rootUtil.executeCommand("stat -c '%U:%G %a' \"$path\"") } returns Result.failure(Exception("Command failed"))
        coEvery { rootUtil.executeCommand("stat \"$path\" | grep -E 'Uid:|Access:' | head -2") } returns Result.success(statOutput)

        // When
        val result = permissionManager.getFilePermissions(path)

        // Then
        assertTrue(result.isSuccess)
        val permissions = result.getOrThrow()
        assertEquals("u0_a123", permissions.owner)
        assertEquals("u0_a456", permissions.group)
        assertEquals("700", permissions.permissions)
    }

    @Test
    fun `test zero permissions are normalized correctly`() = runTest {
        // Given
        val path = "/data/data/com.scopely.monopolygo/files/test"
        // Test case: zero permissions (0000) should be normalized to "0"
        val statOutput = """
            Uid: (10123/u0_a123)   Gid: (10456/u0_a456)
            Access: (0000/----------)
        """.trimIndent()
        
        coEvery { rootUtil.executeCommand("stat -c '%U:%G %a' \"$path\"") } returns Result.failure(Exception("Command failed"))
        coEvery { rootUtil.executeCommand("stat \"$path\" | grep -E 'Uid:|Access:' | head -2") } returns Result.success(statOutput)

        // When
        val result = permissionManager.getFilePermissions(path)

        // Then
        assertTrue(result.isSuccess)
        val permissions = result.getOrThrow()
        assertEquals("u0_a123", permissions.owner)
        assertEquals("u0_a456", permissions.group)
        assertEquals("0", permissions.permissions)
    }
}
