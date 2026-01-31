package com.mgomanager.app.domain.util

import javax.inject.Inject
import javax.inject.Singleton

data class FilePermissions(
    val owner: String,
    val group: String,
    val permissions: String
)

@Singleton
class FilePermissionManager @Inject constructor(
    private val rootUtil: RootUtil
) {

    /**
     * Read file ownership and permissions
     */
    suspend fun getFilePermissions(path: String): Result<FilePermissions> {
        // Try multiple methods to get file permissions
        // Method 1: stat command with proper quoting (newer Android)
        val statResult = rootUtil.executeCommand("stat -c '%U:%G %a' \"$path\"")

        if (statResult.isSuccess) {
            return statResult.mapCatching { output ->
                // Expected format: "u0_a123:u0_a123 755"
                val parts = output.trim().split(" ")
                if (parts.size != 2) {
                    throw Exception("Unexpected stat output: $output")
                }

                val ownerGroup = parts[0].split(":")
                if (ownerGroup.size != 2) {
                    throw Exception("Unexpected owner:group format: ${parts[0]}")
                }

                FilePermissions(
                    owner = ownerGroup[0],
                    group = ownerGroup[1],
                    permissions = parts[1]
                )
            }
        }

        // Method 2: Try Android-specific stat format (some devices use different format)
        // The piped command filters the stat output to extract only the relevant lines.
        // If any part fails (stat, grep, or head), the entire command will fail and we
        // fall back to Method 3 (ls -ld). This is intentional for robustness.
        val statAndroidResult = rootUtil.executeCommand("stat \"$path\" | grep -E 'Uid:|Access:' | head -2")
        
        if (statAndroidResult.isSuccess) {
            val parseResult = parseAndroidStatOutput(statAndroidResult.getOrThrow(), path)
            if (parseResult.isSuccess) {
                return parseResult
            }
        }

        // Method 3: ls -l command (fallback for older Android)
        val lsResult = rootUtil.executeCommand("ls -ld \"$path\"")

        return lsResult.mapCatching { output ->
            // Expected format: "drwxr-xr-x 2 u0_a123 u0_a123 4096 Jan 24 20:00 /data/data/..."
            val parts = output.trim().split(Regex("\\s+"))
            if (parts.size < 4) {
                throw Exception("Cannot read file permissions from path: $path (output: $output)")
            }

            val permString = parts[0]
            val owner = parts[2]
            val group = parts[3]

            // Convert permission string to numeric (e.g., "rwxr-xr-x" -> "755")
            val permissions = convertPermissionsToNumeric(permString.substring(1))

            FilePermissions(
                owner = owner,
                group = group,
                permissions = permissions
            )
        }
    }

    /**
     * Parse Android stat output format
     * Example:
     * Uid: ( 10123/ u0_a123)   Gid: ( 10123/ u0_a123)
     * Access: (0755/drwxr-xr-x)
     */
    private fun parseAndroidStatOutput(output: String, path: String): Result<FilePermissions> {
        return try {
            val lines = output.trim().split("\n")
            if (lines.size < 2) {
                return Result.failure(Exception("Unexpected stat output format"))
            }

            // Parse Uid/Gid line
            val uidGidLine = lines.firstOrNull { it.contains("Uid:") } 
                ?: return Result.failure(Exception("No Uid line found"))
            
            // Extract owner and group from format: "Uid: ( 10123/ u0_a123)   Gid: ( 10123/ u0_a123)"
            val uidMatch = Regex("Uid:\\s*\\(\\s*\\d+/\\s*(\\w+)\\)").find(uidGidLine)
            val gidMatch = Regex("Gid:\\s*\\(\\s*\\d+/\\s*(\\w+)\\)").find(uidGidLine)
            
            val owner = uidMatch?.groupValues?.getOrNull(1) 
                ?: return Result.failure(Exception("Cannot parse owner from: $uidGidLine"))
            val group = gidMatch?.groupValues?.getOrNull(1) 
                ?: return Result.failure(Exception("Cannot parse group from: $uidGidLine"))

            // Parse Access line for permissions
            val accessLine = lines.firstOrNull { it.contains("Access:") && it.contains("/") }
                ?: return Result.failure(Exception("No Access line found"))
            
            // Extract permissions from format: "Access: (0755/drwxr-xr-x)"
            // Remove leading zeros (e.g., "0755" -> "755", "0000" -> "0")
            val permMatch = Regex("Access:\\s*\\((\\d+)/").find(accessLine)
            val rawPermissions = permMatch?.groupValues?.getOrNull(1)
                ?: return Result.failure(Exception("Cannot parse permissions from: $accessLine"))
            
            // Use dropWhile for clarity: remove leading zeros but keep at least one digit
            val permissions = rawPermissions.dropWhile { it == '0' }.ifEmpty { "0" }

            Result.success(FilePermissions(
                owner = owner,
                group = group,
                permissions = permissions
            ))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse stat output for $path: ${e.message}"))
        }
    }

    private fun convertPermissionsToNumeric(permString: String): String {
        // Convert "rwxr-xr-x" to "755"
        val ownerPerm = permString.substring(0, 3).let {
            (if (it[0] == 'r') 4 else 0) +
            (if (it[1] == 'w') 2 else 0) +
            (if (it[2] == 'x') 1 else 0)
        }
        val groupPerm = permString.substring(3, 6).let {
            (if (it[0] == 'r') 4 else 0) +
            (if (it[1] == 'w') 2 else 0) +
            (if (it[2] == 'x') 1 else 0)
        }
        val otherPerm = permString.substring(6, 9).let {
            (if (it[0] == 'r') 4 else 0) +
            (if (it[1] == 'w') 2 else 0) +
            (if (it[2] == 'x') 1 else 0)
        }

        return "$ownerPerm$groupPerm$otherPerm"
    }

    /**
     * Set file ownership
     */
    suspend fun setFileOwnership(path: String, owner: String, group: String): Result<Unit> {
        return rootUtil.executeCommand("chown -R $owner:$group \"$path\"").map { }
    }

    /**
     * Set file permissions
     */
    suspend fun setFilePermissions(path: String, permissions: String): Result<Unit> {
        return rootUtil.executeCommand("chmod -R $permissions \"$path\"").map { }
    }
}
