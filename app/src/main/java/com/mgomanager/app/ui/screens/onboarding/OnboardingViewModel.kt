package com.mgomanager.app.ui.screens.onboarding

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mgomanager.app.data.repository.AppStateRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.util.FileUtil
import com.mgomanager.app.domain.util.ImportUtil
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
import javax.inject.Inject

/**
 * Steps in the onboarding flow
 */
enum class OnboardingStep {
    WELCOME,
    IMPORT_CHECK,
    PREFIX_SETUP,
    SSH_SETUP,
    BACKUP_DIRECTORY,
    ROOT_PERMISSIONS,
    COMPLETE
}

/**
 * UI State for onboarding
 */
data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val importZipFound: Boolean = false,
    val importZipPath: String? = null,
    val prefix: String = "",
    val sshEnabled: Boolean = false,
    val sshHost: String = "",
    val sshPort: String = "22",
    val sshUsername: String = "",
    val sshPassword: String = "",
    val backupDirectory: String = "/storage/emulated/0/bgo_backups/",
    val rootAccessGranted: Boolean = false,
    val dataDataPermissionsChecked: Boolean = false,
    val monopolyGoInstalled: Boolean = false,
    val monopolyGoUid: Int? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val importResult: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appStateRepository: AppStateRepository,
    private val rootUtil: RootUtil,
    private val fileUtil: FileUtil,
    private val importUtil: ImportUtil,
    private val logRepository: LogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        checkImportZip()
    }

    // ============================================================
    // Screen 2: Import-Check
    // ============================================================

    private fun checkImportZip() {
        viewModelScope.launch {
            val importPath = ImportUtil.IMPORT_ZIP_PATH
            val exists = fileUtil.fileExists(importPath)

            _uiState.update {
                it.copy(
                    importZipFound = exists,
                    importZipPath = if (exists) importPath else null
                )
            }
        }
    }

    fun importFromZip() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val zipPath = _uiState.value.importZipPath ?: return@launch
                val result = importUtil.importFromZip(zipPath)

                if (result.isSuccess) {
                    val importResult = result.getOrNull()!!
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            importResult = "Import erfolgreich: ${importResult.importedCount} Accounts importiert" +
                                    if (importResult.skippedCount > 0) ", ${importResult.skippedCount} übersprungen" else ""
                        )
                    }
                    logRepository.logInfo("ONBOARDING", "Import erfolgreich abgeschlossen")
                    nextStep()
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Import fehlgeschlagen: ${result.exceptionOrNull()?.message}"
                        )
                    }
                    logRepository.logError("ONBOARDING", "Import fehlgeschlagen", exception = result.exceptionOrNull() as? Exception)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Fehler beim Import: ${e.message}"
                    )
                }
                logRepository.logError("ONBOARDING", "Fehler beim Import", exception = e)
            }
        }
    }

    fun skipImport() {
        nextStep()
    }

    // ============================================================
    // Screen 3: Präfix-Setup
    // ============================================================

    fun onPrefixChanged(prefix: String) {
        _uiState.update { it.copy(prefix = prefix) }
    }

    fun savePrefixAndContinue() {
        viewModelScope.launch {
            val prefix = _uiState.value.prefix.trim()
            if (prefix.isNotEmpty()) {
                appStateRepository.setDefaultPrefix(prefix)
                logRepository.logInfo("ONBOARDING", "Präfix gespeichert: $prefix")
            }
            nextStep()
        }
    }

    fun skipPrefix() {
        nextStep()
    }

    // ============================================================
    // Screen 4: SSH-Setup
    // ============================================================

    fun onSshEnabledChanged(enabled: Boolean) {
        _uiState.update { it.copy(sshEnabled = enabled) }
    }

    fun onSshHostChanged(host: String) {
        _uiState.update { it.copy(sshHost = host) }
    }

    fun onSshPortChanged(port: String) {
        _uiState.update { it.copy(sshPort = port) }
    }

    fun onSshUsernameChanged(username: String) {
        _uiState.update { it.copy(sshUsername = username) }
    }

    fun onSshPasswordChanged(password: String) {
        _uiState.update { it.copy(sshPassword = password) }
    }

    fun saveSshAndContinue() {
        viewModelScope.launch {
            if (_uiState.value.sshEnabled) {
                // Validation
                if (_uiState.value.sshHost.isBlank() ||
                    _uiState.value.sshUsername.isBlank() ||
                    _uiState.value.sshPassword.isBlank()
                ) {
                    _uiState.update {
                        it.copy(error = "Bitte fülle alle SSH-Felder aus")
                    }
                    return@launch
                }

                // Save SSH config
                appStateRepository.setSshConfig(
                    enabled = true,
                    host = _uiState.value.sshHost,
                    port = _uiState.value.sshPort.toIntOrNull() ?: 22,
                    username = _uiState.value.sshUsername,
                    password = _uiState.value.sshPassword
                )
                logRepository.logInfo("ONBOARDING", "SSH-Konfiguration gespeichert")
            }
            _uiState.update { it.copy(error = null) }
            nextStep()
        }
    }

    fun skipSsh() {
        viewModelScope.launch {
            appStateRepository.setSshEnabled(false)
        }
        nextStep()
    }

    // ============================================================
    // Screen 5: Backup-Ordner
    // ============================================================

    fun onBackupDirectoryChanged(directory: String) {
        _uiState.update { it.copy(backupDirectory = directory) }
    }

    fun saveBackupDirectoryAndContinue() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Create directory if it doesn't exist
            val result = fileUtil.createDirectory(_uiState.value.backupDirectory)

            if (result.isSuccess) {
                appStateRepository.setBackupDirectory(_uiState.value.backupDirectory)
                logRepository.logInfo("ONBOARDING", "Backup-Verzeichnis gespeichert: ${_uiState.value.backupDirectory}")
                _uiState.update { it.copy(isLoading = false, error = null) }
                nextStep()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Fehler beim Erstellen des Ordners: ${result.exceptionOrNull()?.message}"
                    )
                }
                logRepository.logError("ONBOARDING", "Fehler beim Erstellen des Backup-Ordners", exception = result.exceptionOrNull() as? Exception)
            }
        }
    }

    // ============================================================
    // Screen 6: Root & Berechtigungen
    // ============================================================

    fun requestRootAccess() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // 1. Check root access
            val rootGranted = rootUtil.requestRootAccess()

            if (!rootGranted) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Root-Zugriff verweigert. Die App benötigt Root-Rechte."
                    )
                }
                logRepository.logError("ONBOARDING", "Root-Zugriff verweigert")
                return@launch
            }

            // 2. Check /data/data/ permissions
            val dataDataAccessible = checkDataDataPermissions()

            // 3. Check Monopoly Go installation + UID
            val (installed, uid) = checkMonopolyGo()

            // 4. Save results
            appStateRepository.setSystemStatus(
                rootGranted = rootGranted,
                dataDataPermissions = dataDataAccessible,
                monopolyGoInstalled = installed,
                monopolyGoUid = uid
            )

            _uiState.update {
                it.copy(
                    isLoading = false,
                    rootAccessGranted = rootGranted,
                    dataDataPermissionsChecked = dataDataAccessible,
                    monopolyGoInstalled = installed,
                    monopolyGoUid = uid,
                    error = null
                )
            }

            logRepository.logInfo(
                "ONBOARDING",
                "System-Status: Root=$rootGranted, DataData=$dataDataAccessible, MGO=$installed, UID=$uid"
            )

            nextStep()
        }
    }

    private suspend fun checkDataDataPermissions(): Boolean {
        return withContext(Dispatchers.IO) {
            // Check read/write permissions on /data/data/
            val testPath = "/data/data/com.scopely.monopolygo"
            rootUtil.executeCommand("ls $testPath").isSuccess
        }
    }

    private suspend fun checkMonopolyGo(): Pair<Boolean, Int?> {
        return withContext(Dispatchers.IO) {
            val packageName = "com.scopely.monopolygo"

            // Method 1: Via PackageManager
            val installed = try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            } catch (e: Exception) {
                false
            }

            // Method 2: Get UID via Root
            val uid = if (installed) {
                val result = rootUtil.executeCommand("stat -c '%u' /data/data/$packageName")
                result.getOrNull()?.trim()?.replace("'", "")?.toIntOrNull()
            } else {
                null
            }

            Pair(installed, uid)
        }
    }

    // ============================================================
    // Screen 7: Fertig
    // ============================================================

    fun completeOnboarding() {
        viewModelScope.launch {
            appStateRepository.setOnboardingCompleted(true)
            logRepository.logInfo("ONBOARDING", "Onboarding abgeschlossen")
            _uiState.update { it.copy(currentStep = OnboardingStep.COMPLETE) }
        }
    }

    // ============================================================
    // Navigation
    // ============================================================

    fun nextStep() {
        _uiState.update { state ->
            val nextStep = when (state.currentStep) {
                OnboardingStep.WELCOME -> OnboardingStep.IMPORT_CHECK
                OnboardingStep.IMPORT_CHECK -> OnboardingStep.PREFIX_SETUP
                OnboardingStep.PREFIX_SETUP -> OnboardingStep.SSH_SETUP
                OnboardingStep.SSH_SETUP -> OnboardingStep.BACKUP_DIRECTORY
                OnboardingStep.BACKUP_DIRECTORY -> OnboardingStep.ROOT_PERMISSIONS
                OnboardingStep.ROOT_PERMISSIONS -> OnboardingStep.COMPLETE
                OnboardingStep.COMPLETE -> OnboardingStep.COMPLETE
            }
            state.copy(currentStep = nextStep, error = null)
        }
    }

    fun previousStep() {
        _uiState.update { state ->
            val prevStep = when (state.currentStep) {
                OnboardingStep.WELCOME -> OnboardingStep.WELCOME
                OnboardingStep.IMPORT_CHECK -> OnboardingStep.WELCOME
                OnboardingStep.PREFIX_SETUP -> OnboardingStep.IMPORT_CHECK
                OnboardingStep.SSH_SETUP -> OnboardingStep.PREFIX_SETUP
                OnboardingStep.BACKUP_DIRECTORY -> OnboardingStep.SSH_SETUP
                OnboardingStep.ROOT_PERMISSIONS -> OnboardingStep.BACKUP_DIRECTORY
                OnboardingStep.COMPLETE -> OnboardingStep.ROOT_PERMISSIONS
            }
            state.copy(currentStep = prevStep, error = null)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
