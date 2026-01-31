package com.mgomanager.app.domain.util

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootUtil @Inject constructor() {

    // Flag to track if root has been successfully verified in this session
    @Volatile
    private var rootVerified = false

    /**
     * Check if device has root access
     */
    suspend fun isRooted(): Boolean = withContext(Dispatchers.IO) {
        try {
            Shell.getShell().isRoot
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Request root access if not already granted.
     * This function ensures root is truly ready before returning.
     * It will retry multiple times to handle Magisk dialog timing.
     */
    suspend fun requestRootAccess(): Boolean = withContext(Dispatchers.IO) {
        // If already verified in this session, just do a quick check
        if (rootVerified) {
            val shell = try { Shell.getShell() } catch (e: Exception) { null }
            if (shell?.isRoot == true) {
                return@withContext true
            }
            // Root was lost, need to re-verify
            rootVerified = false
        }

        try {
            // Close any cached shell to force new root request
            Shell.getCachedShell()?.close()

            // Small delay to ensure shell is fully closed
            delay(100)

            // Get new shell - this will trigger Magisk/SuperSU dialog
            val shell = Shell.getShell()

            if (!shell.isRoot) {
                return@withContext false
            }

            // Verify root actually works with retries
            // This handles timing issues where Magisk dialog was just dismissed
            for (attempt in 1..5) {
                val testResult = Shell.su("id").exec()
                if (testResult.isSuccess && testResult.out.any { it.contains("uid=0") }) {
                    rootVerified = true
                    return@withContext true
                }

                // Wait before retry, increasing delay
                if (attempt < 5) {
                    delay(200L * attempt)
                }
            }

            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Ensure root is available before executing a command.
     * This is the preferred method for executing root commands as it
     * handles the case where root permission hasn't been granted yet.
     */
    suspend fun executeCommandWithRootCheck(command: String): Result<String> = withContext(Dispatchers.IO) {
        // First ensure root is ready
        if (!ensureRootReady()) {
            return@withContext Result.failure(Exception("Root-Zugriff nicht verfügbar"))
        }

        executeCommandInternal(command)
    }

    /**
     * Internal helper to ensure root shell is ready
     */
    private suspend fun ensureRootReady(): Boolean {
        if (rootVerified) {
            val shell = try { Shell.getShell() } catch (e: Exception) { null }
            if (shell?.isRoot == true) {
                return true
            }
        }
        return requestRootAccess()
    }

    /**
     * Execute a single root command (assumes root is already verified)
     */
    suspend fun executeCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        if (!ensureRootReady()) {
            return@withContext Result.failure(Exception("Root-Zugriff nicht verfügbar"))
        }

        executeCommandInternal(command)
    }

    /**
     * Internal command execution
     */
    private fun executeCommandInternal(command: String): Result<String> {
        return try {
            val shell = Shell.getShell()
            if (!shell.isRoot) {
                return Result.failure(Exception("No root access"))
            }

            val result = Shell.su(command).exec()
            if (result.isSuccess) {
                Result.success(result.out.joinToString("\n"))
            } else {
                Result.failure(Exception("Command failed: ${result.err.joinToString("\n")}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Execute multiple root commands
     */
    suspend fun executeCommands(commands: List<String>): Result<List<String>> =
        withContext(Dispatchers.IO) {
            try {
                if (!ensureRootReady()) {
                    return@withContext Result.failure(Exception("Root-Zugriff nicht verfügbar"))
                }

                val result = Shell.su(*commands.toTypedArray()).exec()
                if (result.isSuccess) {
                    Result.success(result.out)
                } else {
                    Result.failure(Exception("Commands failed: ${result.err.joinToString("\n")}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Check if Monopoly Go is installed
     */
    suspend fun isMonopolyGoInstalled(): Boolean = withContext(Dispatchers.IO) {
        val result = executeCommand("pm list packages com.scopely.monopolygo")
        result.isSuccess && result.getOrNull()?.contains("com.scopely.monopolygo") == true
    }

    /**
     * Force stop Monopoly Go
     */
    suspend fun forceStopMonopolyGo(): Result<Unit> = withContext(Dispatchers.IO) {
        executeCommand("am force-stop com.scopely.monopolygo").map { }
    }

    /**
     * Launch Monopoly Go
     */
    suspend fun launchMonopolyGo(): Result<Unit> = withContext(Dispatchers.IO) {
        executeCommand("monkey -p com.scopely.monopolygo -c android.intent.category.LAUNCHER 1").map { }
    }

    /**
     * Reset root verification state (useful for testing or after errors)
     */
    fun resetRootVerification() {
        rootVerified = false
    }
}
