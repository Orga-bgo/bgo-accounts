package com.mgomanager.app.ui.screens.systemcheck

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mgomanager.app.data.repository.AppStateRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.util.FileUtil
import com.mgomanager.app.domain.util.RootUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Status of a system check
 */
enum class CheckStatus {
    PENDING,    // Not yet checked
    CHECKING,   // Currently checking
    PASSED,     // Successful
    WARNING,    // Warning (not critical)
    FAILED      // Failed (critical)
}

/**
 * Data class representing a single system check
 */
data class SystemCheck(
    val id: String,
    val title: String,
    val status: CheckStatus,
    val message: String? = null
)

/**
 * UI State for system checks
 */
data class SystemCheckUiState(
    val isChecking: Boolean = true,
    val checks: List<SystemCheck> = emptyList(),
    val allChecksPassed: Boolean = false,
    val criticalError: String? = null
)

@HiltViewModel
class SystemCheckViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appStateRepository: AppStateRepository,
    private val rootUtil: RootUtil,
    private val fileUtil: FileUtil,
    private val logRepository: LogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SystemCheckUiState())
    val uiState: StateFlow<SystemCheckUiState> = _uiState.asStateFlow()

    companion object {
        private const val MONOPOLY_GO_PACKAGE = "com.scopely.monopolygo"
    }

    init {
        performSystemChecks()
    }

    fun performSystemChecks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true, criticalError = null) }

            val checks = mutableListOf<SystemCheck>()

            // Check 1: Root-Zugriff
            checks.add(
                SystemCheck(
                    id = "root",
                    title = "Root-Zugriff",
                    status = CheckStatus.CHECKING
                )
            )
            _uiState.update { it.copy(checks = checks.toList()) }

            val rootGranted = rootUtil.requestRootAccess()
            checks[0] = checks[0].copy(
                status = if (rootGranted) CheckStatus.PASSED else CheckStatus.FAILED,
                message = if (rootGranted) "Root-Zugriff verfügbar" else "Root-Zugriff verweigert"
            )
            _uiState.update { it.copy(checks = checks.toList()) }

            if (!rootGranted) {
                _uiState.update {
                    it.copy(
                        isChecking = false,
                        allChecksPassed = false,
                        criticalError = "Root-Zugriff erforderlich!"
                    )
                }
                logRepository.logError("SYSTEM_CHECK", "Root-Zugriff verweigert")
                return@launch
            }

            // Check 2: Monopoly Go Installation
            checks.add(
                SystemCheck(
                    id = "monopoly_go",
                    title = "Monopoly Go",
                    status = CheckStatus.CHECKING
                )
            )
            _uiState.update { it.copy(checks = checks.toList()) }

            val (installed, hasChanged) = checkMonopolyGoStatus()
            checks[1] = checks[1].copy(
                status = if (installed) CheckStatus.PASSED else CheckStatus.FAILED,
                message = when {
                    installed && hasChanged -> "Installation erkannt (Status aktualisiert)"
                    installed -> "Installiert"
                    else -> "Nicht installiert"
                }
            )
            _uiState.update { it.copy(checks = checks.toList()) }

            // Critical check: Monopoly Go must be installed
            if (!installed) {
                _uiState.update {
                    it.copy(
                        isChecking = false,
                        allChecksPassed = false,
                        criticalError = "Monopoly Go nicht installiert!"
                    )
                }
                logRepository.logError("SYSTEM_CHECK", "Monopoly Go nicht installiert")
                return@launch
            }

            // Check 3: UID-Update (if Monopoly Go installed)
            checks.add(
                SystemCheck(
                    id = "uid",
                    title = "UID-Status",
                    status = CheckStatus.CHECKING
                )
            )
            _uiState.update { it.copy(checks = checks.toList()) }

            val uidCheck = updateMonopolyGoUid()
            checks[2] = checks[2].copy(
                status = if (uidCheck) CheckStatus.PASSED else CheckStatus.WARNING,
                message = if (uidCheck) "UID gespeichert" else "UID konnte nicht ermittelt werden"
            )
            _uiState.update { it.copy(checks = checks.toList()) }

            // Check 4: Backup-Verzeichnis
            checks.add(
                SystemCheck(
                    id = "backup_dir",
                    title = "Backup-Verzeichnis",
                    status = CheckStatus.CHECKING
                )
            )
            _uiState.update { it.copy(checks = checks.toList()) }

            val backupDirAccessible = checkBackupDirectory()
            checks[3] = checks[3].copy(
                status = if (backupDirAccessible) CheckStatus.PASSED else CheckStatus.WARNING,
                message = if (backupDirAccessible) "Zugriff OK" else "Verzeichnis nicht erreichbar"
            )
            _uiState.update { it.copy(checks = checks.toList()) }

            // Check 5: /data/data Permissions
            checks.add(
                SystemCheck(
                    id = "data_data",
                    title = "/data/data Zugriff",
                    status = CheckStatus.CHECKING
                )
            )
            _uiState.update { it.copy(checks = checks.toList()) }

            val dataDataAccess = checkDataDataAccess()
            checks[4] = checks[4].copy(
                status = if (dataDataAccess) CheckStatus.PASSED else CheckStatus.FAILED,
                message = if (dataDataAccess) "Zugriff OK" else "Zugriff verweigert"
            )
            _uiState.update { it.copy(checks = checks.toList()) }

            // Final evaluation - critical checks: root, monopoly_go, data_data
            val allPassed = checks.none { it.status == CheckStatus.FAILED }
            val criticalFailed = checks.any {
                it.status == CheckStatus.FAILED && it.id in listOf("root", "monopoly_go", "data_data")
            }

            _uiState.update {
                it.copy(
                    isChecking = false,
                    allChecksPassed = allPassed,
                    criticalError = if (criticalFailed) "Einige kritische Checks fehlgeschlagen" else null
                )
            }

            // Save timestamp
            appStateRepository.setLastSystemCheckTimestamp(System.currentTimeMillis())

            logRepository.logInfo(
                "SYSTEM_CHECK",
                "System-Checks abgeschlossen: ${if (allPassed) "Alle bestanden" else "Einige fehlgeschlagen"}"
            )
        }
    }

    // ============================================================
    // Check Functions
    // ============================================================

    private suspend fun checkMonopolyGoStatus(): Pair<Boolean, Boolean> {
        return withContext(Dispatchers.IO) {
            // Is installed?
            val installed = try {
                context.packageManager.getPackageInfo(MONOPOLY_GO_PACKAGE, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            } catch (e: Exception) {
                false
            }

            // Has status changed?
            val previousStatus = appStateRepository.getMonopolyGoInstalled()
            val hasChanged = previousStatus != installed

            // Save new status
            appStateRepository.setMonopolyGoInstalled(installed)

            Pair(installed, hasChanged)
        }
    }

    private suspend fun updateMonopolyGoUid(): Boolean {
        return withContext(Dispatchers.IO) {
            // Get UID via Root
            val result = rootUtil.executeCommand("stat -c '%u' /data/data/$MONOPOLY_GO_PACKAGE")
            val uid = result.getOrNull()?.trim()?.replace("'", "")?.toIntOrNull()

            if (uid != null) {
                // Check if UID has changed
                val previousUid = appStateRepository.getMonopolyGoUid()
                if (previousUid != uid) {
                    // UID has changed → save
                    appStateRepository.setMonopolyGoUid(uid)

                    // Log entry
                    logRepository.logInfo(
                        "UID_CHANGED",
                        "Monopoly Go UID aktualisiert: $previousUid → $uid"
                    )
                }
                true
            } else {
                false
            }
        }
    }

    private suspend fun checkBackupDirectory(): Boolean {
        return withContext(Dispatchers.IO) {
            val backupDir = appStateRepository.getBackupDirectory()

            if (backupDir == null) {
                false
            } else {
                // Check if directory exists and is writable
                val dir = File(backupDir)
                val dirExists = dir.exists()
                val canWrite = dir.canWrite()

                dirExists && canWrite
            }
        }
    }

    private suspend fun checkDataDataAccess(): Boolean {
        return withContext(Dispatchers.IO) {
            val testPath = "/data/data/$MONOPOLY_GO_PACKAGE"
            rootUtil.executeCommand("ls \"$testPath\"").isSuccess
        }
    }

    // ============================================================
    // Retry & Continue
    // ============================================================

    fun retryChecks() {
        performSystemChecks()
    }

    fun continueToApp() {
        // Navigation handled by the UI layer
    }
}
