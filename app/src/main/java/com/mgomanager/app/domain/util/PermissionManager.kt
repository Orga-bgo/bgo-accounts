package com.mgomanager.app.domain.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.mgomanager.app.data.repository.LogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Permission status for storage access
 */
enum class StoragePermissionStatus {
    GRANTED,
    DENIED,
    SHOULD_SHOW_RATIONALE,
    REQUIRES_SETTINGS,
    REQUIRES_SAF
}

/**
 * Result of permission request
 */
sealed class PermissionResult {
    object Granted : PermissionResult()
    object Denied : PermissionResult()
    data class NeedsRationale(val permissions: List<String>) : PermissionResult()
    object NeedsSettings : PermissionResult()
    object NeedsSAF : PermissionResult()
}

@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logRepository: LogRepository
) {
    // Coroutine scope for logging (uses SupervisorJob to not cancel on errors)
    private val logScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        // Permissions for Android 9-10
        val LEGACY_STORAGE_PERMISSIONS = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    /**
     * Check if all necessary storage permissions are granted
     */
    fun hasStoragePermissions(): Boolean {
        val hasPermission = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11+ - Check MANAGE_EXTERNAL_STORAGE
                Environment.isExternalStorageManager()
            }
            else -> {
                // Android 9-10 - Check READ/WRITE
                LEGACY_STORAGE_PERMISSIONS.all { permission ->
                    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                }
            }
        }

        logScope.launch {
            logRepository.logInfo("PERMISSION", "Storage permissions check: $hasPermission (SDK ${Build.VERSION.SDK_INT})")
        }
        return hasPermission
    }

    /**
     * Get the current permission status with more detail
     */
    fun getStoragePermissionStatus(): StoragePermissionStatus {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                if (Environment.isExternalStorageManager()) {
                    StoragePermissionStatus.GRANTED
                } else {
                    // Android 11+ recommends SAF over MANAGE_EXTERNAL_STORAGE
                    StoragePermissionStatus.REQUIRES_SAF
                }
            }
            else -> {
                val allGranted = LEGACY_STORAGE_PERMISSIONS.all { permission ->
                    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                }
                if (allGranted) {
                    StoragePermissionStatus.GRANTED
                } else {
                    StoragePermissionStatus.DENIED
                }
            }
        }
    }

    /**
     * Get permissions to request based on Android version
     */
    fun getRequiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> LEGACY_STORAGE_PERMISSIONS
            else -> emptyArray() // Android 11+ use SAF or MANAGE_EXTERNAL_STORAGE
        }
    }

    /**
     * Check if we should use SAF (Storage Access Framework) instead of direct permissions
     * Recommended for Android 11+
     */
    fun shouldUseSAF(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    /**
     * Get intent for MANAGE_EXTERNAL_STORAGE settings (fallback for Android 11+)
     */
    fun getManageStorageIntent(): Intent {
        logScope.launch {
            logRepository.logInfo("PERMISSION", "Opening MANAGE_EXTERNAL_STORAGE settings")
        }
        return try {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } catch (e: Exception) {
            logScope.launch {
                logRepository.logWarning("PERMISSION", "Fallback to general storage settings")
            }
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }
    }

    /**
     * Log permission request result
     */
    fun logPermissionResult(permissions: Map<String, Boolean>) {
        val granted = permissions.filter { it.value }.keys
        val denied = permissions.filter { !it.value }.keys

        logScope.launch {
            if (granted.isNotEmpty()) {
                logRepository.logInfo("PERMISSION", "Permissions granted: ${granted.joinToString()}")
            }
            if (denied.isNotEmpty()) {
                logRepository.logWarning("PERMISSION", "Permissions denied: ${denied.joinToString()}")
            }
        }
    }

    /**
     * Log SAF folder selection result
     */
    fun logSAFResult(uri: Uri?) {
        logScope.launch {
            if (uri != null) {
                logRepository.logInfo("PERMISSION", "SAF folder selected: $uri")
            } else {
                logRepository.logWarning("PERMISSION", "SAF folder selection cancelled")
            }
        }
    }
}
