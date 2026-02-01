package com.mgomanager.app.ui.screens.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mgomanager.app.data.local.preferences.SettingsDataStore
import com.mgomanager.app.data.repository.AppStateRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.usecase.ExportImportUseCase
import com.mgomanager.app.domain.usecase.ImportBackupsUseCase
import com.mgomanager.app.domain.usecase.ImportPrepareResult
import com.mgomanager.app.domain.usecase.ImportAccountData
import com.mgomanager.app.domain.util.RootUtil
import com.mgomanager.app.domain.util.SSHSyncService
import com.mgomanager.app.domain.util.SSHOperationResult
import com.mgomanager.app.ui.components.DuplicatePair
import com.mgomanager.app.ui.components.ResolveChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val accountPrefix: String = "MGO_",
    val backupRootPath: String = "/storage/emulated/0/mgo/backups/",
    val backupRootUri: String? = null, // SAF URI for backup directory
    val isRootAvailable: Boolean = false,
    val appStartCount: Int = 0,
    val prefixSaved: Boolean = false,
    val prefixError: String? = null,
    val pathSaved: Boolean = false,
    val exportResult: String? = null,
    val importResult: String? = null,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val showImportWarning: Boolean = false,
    // Interactive import conflict resolution (DEVIATION FROM P6)
    val importDuplicates: List<DuplicatePair> = emptyList(),
    val showDuplicateResolveDialog: Boolean = false,
    val pendingImportAccounts: List<ImportAccountData> = emptyList(),
    val pendingImportTempDir: File? = null,
    // SSH settings
    val sshEnabled: Boolean = false,
    val sshHost: String = "",
    val sshPort: String = "22",
    val sshUsername: String = "",
    val sshPassword: String = "",
    val sshAutoCheckOnStart: Boolean = false,
    val sshHostSaved: Boolean = false,
    val sshPortSaved: Boolean = false,
    val sshUsernameSaved: Boolean = false,
    val sshPasswordSaved: Boolean = false,
    val sshTestResult: String? = null,
    val isSshTesting: Boolean = false,
    // Legacy fields for compatibility
    val sshPrivateKeyPath: String = "/storage/emulated/0/.ssh/id_ed25519",
    val sshServer: String = "",
    val sshBackupPath: String = "/home/user/monopolygo/backups/",
    val sshAuthMethod: String = "key_only",
    val sshAutoUploadOnExport: Boolean = false,
    val sshLastSyncTimestamp: Long = 0L,
    val sshKeyPathSaved: Boolean = false,
    val sshServerSaved: Boolean = false,
    val sshBackupPathSaved: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore, // Legacy, still used for SSH settings
    private val appStateRepository: AppStateRepository, // Primary source for Prefix and BackupDirectory
    private val rootUtil: RootUtil,
    private val exportImportUseCase: ExportImportUseCase,
    private val importBackupsUseCase: ImportBackupsUseCase, // Interactive conflict resolution
    private val sshSyncService: SSHSyncService,
    private val logRepository: LogRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // Invalid path characters for prefix validation
    private val invalidPrefixChars = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            // Load prefix and backup path from AppStateRepository (primary source per P6 spec)
            val prefix = appStateRepository.getDefaultPrefix() ?: SettingsDataStore.DEFAULT_PREFIX
            val backupPath = appStateRepository.getBackupDirectory() ?: SettingsDataStore.DEFAULT_BACKUP_PATH
            _uiState.update {
                it.copy(
                    accountPrefix = prefix,
                    backupRootPath = backupPath
                )
            }
        }

        viewModelScope.launch {
            // Load app start count from legacy DataStore
            settingsDataStore.appStartCount.collect { count ->
                _uiState.update { it.copy(appStartCount = count) }
            }
        }

        // Load SSH settings (split into separate collectors due to combine limit of 5 flows)
        viewModelScope.launch {
            combine(
                settingsDataStore.sshPrivateKeyPath,
                settingsDataStore.sshServer,
                settingsDataStore.sshBackupPath
            ) { keyPath, server, backupPath ->
                _uiState.update {
                    it.copy(
                        sshPrivateKeyPath = keyPath,
                        sshServer = server,
                        sshBackupPath = backupPath
                    )
                }
            }.collect { }
        }

        viewModelScope.launch {
            combine(
                settingsDataStore.sshPassword,
                settingsDataStore.sshAuthMethod,
                settingsDataStore.sshLastSyncTimestamp
            ) { password, authMethod, lastSync ->
                _uiState.update {
                    it.copy(
                        sshPassword = password,
                        sshAuthMethod = authMethod,
                        sshLastSyncTimestamp = lastSync
                    )
                }
            }.collect { }
        }

        viewModelScope.launch {
            combine(
                settingsDataStore.sshAutoCheckOnStart,
                settingsDataStore.sshAutoUploadOnExport
            ) { autoCheck, autoUpload ->
                _uiState.update {
                    it.copy(
                        sshAutoCheckOnStart = autoCheck,
                        sshAutoUploadOnExport = autoUpload
                    )
                }
            }.collect { }
        }

        // Check root status separately
        viewModelScope.launch {
            val isRooted = rootUtil.isRooted()
            _uiState.update { it.copy(isRootAvailable = isRooted) }
        }
    }

    fun refreshRootStatus() {
        viewModelScope.launch {
            val isRooted = rootUtil.isRooted()
            _uiState.update { it.copy(isRootAvailable = isRooted) }
        }
    }

    /**
     * Update prefix with validation per P6 spec:
     * - not empty
     * - no path characters
     * Uses AppStateRepository (primary source per P6 consolidation)
     */
    fun updatePrefix(prefix: String) {
        viewModelScope.launch {
            // Validate prefix
            when {
                prefix.isBlank() -> {
                    _uiState.update { it.copy(prefixError = "Präfix darf nicht leer sein") }
                    return@launch
                }
                prefix.any { it in invalidPrefixChars } -> {
                    _uiState.update { it.copy(prefixError = "Präfix enthält ungültige Zeichen") }
                    return@launch
                }
            }

            // Save to AppStateRepository (primary) and SettingsDataStore (legacy backup)
            appStateRepository.setDefaultPrefix(prefix)
            settingsDataStore.setAccountPrefix(prefix) // Keep in sync for backward compatibility
            logRepository.logInfo("SETTINGS", "Präfix geändert: $prefix")
            _uiState.update { it.copy(accountPrefix = prefix, prefixSaved = true, prefixError = null) }
        }
    }

    /**
     * Update backup path via SAF or direct path
     * Uses AppStateRepository (primary source per P6 consolidation)
     */
    fun updateBackupPath(path: String) {
        viewModelScope.launch {
            // Save to AppStateRepository (primary) and SettingsDataStore (legacy backup)
            appStateRepository.setBackupDirectory(path)
            settingsDataStore.setBackupRootPath(path) // Keep in sync for backward compatibility
            logRepository.logInfo("SETTINGS", "Backup-Pfad geändert: $path")
            _uiState.update { it.copy(backupRootPath = path, pathSaved = true) }
        }
    }

    fun resetPrefixSaved() {
        _uiState.update { it.copy(prefixSaved = false, prefixError = null) }
    }

    fun resetPathSaved() {
        _uiState.update { it.copy(pathSaved = false) }
    }

    fun showImportWarning() {
        _uiState.update { it.copy(showImportWarning = true) }
    }

    fun hideImportWarning() {
        _uiState.update { it.copy(showImportWarning = false) }
    }

    fun exportData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            logRepository.logInfo("SETTINGS", "Starte Export aller Backups")

            try {
                val result = exportImportUseCase.exportData(context)
                if (result.isSuccess) {
                    logRepository.logInfo("SETTINGS", "Export erfolgreich: ${result.getOrNull()}")
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            exportResult = result.getOrNull()
                        )
                    }
                } else {
                    logRepository.logError("SETTINGS", "Export fehlgeschlagen", result.exceptionOrNull()?.message)
                    showErrorToast()
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            exportResult = "Da ist etwas schief gelaufen.. Prüfe den Log"
                        )
                    }
                }
            } catch (e: Exception) {
                logRepository.logError("SETTINGS", "Export Exception: ${e.message}", null, e)
                showErrorToast()
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportResult = "Da ist etwas schief gelaufen.. Prüfe den Log"
                    )
                }
            }
        }
    }

    fun confirmImport() {
        _uiState.update { it.copy(showImportWarning = false) }
        importData()
    }

    private fun importData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            logRepository.logInfo("SETTINGS", "Starte Import von Backups")

            try {
                val result = exportImportUseCase.importData(context)
                if (result.isSuccess) {
                    logRepository.logInfo("SETTINGS", "Import erfolgreich")
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            importResult = "Import erfolgreich!"
                        )
                    }
                } else {
                    logRepository.logError("SETTINGS", "Import fehlgeschlagen", result.exceptionOrNull()?.message)
                    showErrorToast()
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            importResult = "Da ist etwas schief gelaufen.. Prüfe den Log"
                        )
                    }
                }
            } catch (e: Exception) {
                logRepository.logError("SETTINGS", "Import Exception: ${e.message}", null, e)
                showErrorToast()
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importResult = "Da ist etwas schief gelaufen.. Prüfe den Log"
                    )
                }
            }
        }
    }

    private fun showErrorToast() {
        Toast.makeText(context, "Da ist etwas schief gelaufen.. Prüfe den Log", Toast.LENGTH_LONG).show()
    }

    fun clearExportResult() {
        _uiState.update { it.copy(exportResult = null) }
    }

    fun clearImportResult() {
        _uiState.update { it.copy(importResult = null) }
    }

    // ========== SAF Backup Directory Functions ==========

    /**
     * Handle SAF directory picker result for backup directory
     * Stores the URI and takes persistable permission
     */
    fun onBackupDirectoryPicked(uri: Uri) {
        viewModelScope.launch {
            try {
                // Take persistable permission
                val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)

                // Test writability
                val canWrite = testUriWritability(uri)
                if (!canWrite) {
                    logRepository.logError("SETTINGS", "Backup-Verzeichnis nicht beschreibbar: $uri")
                    showErrorToast()
                    return@launch
                }

                // Save URI as string
                val uriString = uri.toString()
                appStateRepository.setBackupDirectory(uriString)
                settingsDataStore.setBackupRootPath(uriString)
                logRepository.logInfo("SETTINGS", "Backup-Verzeichnis geändert: $uriString")
                _uiState.update {
                    it.copy(
                        backupRootPath = uriString,
                        backupRootUri = uriString,
                        pathSaved = true
                    )
                }
            } catch (e: Exception) {
                logRepository.logError("SETTINGS", "Fehler bei Backup-Verzeichnis: ${e.message}", exception = e)
                showErrorToast()
            }
        }
    }

    private fun testUriWritability(uri: Uri): Boolean {
        return try {
            // Try to create a test file
            val testUri = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
            testUri?.canWrite() == true
        } catch (e: Exception) {
            false
        }
    }

    // ========== SAF Export Functions ==========

    /**
     * Handle SAF document creation result for export ZIP
     */
    fun onExportDocumentCreated(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            logRepository.logInfo("SETTINGS", "Starte Export nach SAF: $uri")

            try {
                val result = exportImportUseCase.exportDataToUri(context, uri)
                if (result.isSuccess) {
                    logRepository.logInfo("SETTINGS", "Export erfolgreich")
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            exportResult = "Export erfolgreich!"
                        )
                    }
                } else {
                    logRepository.logError("SETTINGS", "Export fehlgeschlagen", result.exceptionOrNull()?.message)
                    showErrorToast()
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            exportResult = "Da ist etwas schief gelaufen.. Prüfe den Log"
                        )
                    }
                }
            } catch (e: Exception) {
                logRepository.logError("SETTINGS", "Export Exception: ${e.message}", exception = e)
                showErrorToast()
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportResult = "Da ist etwas schief gelaufen.. Prüfe den Log"
                    )
                }
            }
        }
    }

    // ========== Interactive Import Functions (DEVIATION FROM P6) ==========

    /**
     * Handle SAF document selection result for import ZIP
     * DEVIATION FROM P6: Always shows interactive dialog for UserID conflicts
     * instead of automatically skipping them.
     */
    fun onImportZipSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            logRepository.logInfo("SETTINGS", "Bereite Import vor: $uri")

            try {
                when (val result = importBackupsUseCase.prepareImport(uri)) {
                    is ImportPrepareResult.Ready -> {
                        // No conflicts, proceed directly
                        applyImportWithDecisions(result.accountsToImport, result.tempDir, emptyMap())
                    }
                    is ImportPrepareResult.NeedsResolution -> {
                        // Show conflict resolution dialog (DEVIATION FROM P6)
                        _uiState.update {
                            it.copy(
                                isImporting = false,
                                importDuplicates = result.duplicates,
                                showDuplicateResolveDialog = true,
                                pendingImportAccounts = result.accountsToImport,
                                pendingImportTempDir = result.tempDir
                            )
                        }
                    }
                    is ImportPrepareResult.Error -> {
                        logRepository.logError("SETTINGS", "Import-Vorbereitung fehlgeschlagen: ${result.message}")
                        showErrorToast()
                        _uiState.update {
                            it.copy(
                                isImporting = false,
                                importResult = "Da ist etwas schief gelaufen.. Prüfe den Log"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                logRepository.logError("SETTINGS", "Import Exception: ${e.message}", exception = e)
                showErrorToast()
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importResult = "Da ist etwas schief gelaufen.. Prüfe den Log"
                    )
                }
            }
        }
    }

    /**
     * Apply conflict resolution decisions and complete import
     */
    fun applyConflictDecisions(decisions: Map<String, ResolveChoice>) {
        val accounts = _uiState.value.pendingImportAccounts
        val tempDir = _uiState.value.pendingImportTempDir

        if (tempDir == null) {
            viewModelScope.launch {
                logRepository.logError("SETTINGS", "Kein pending Import gefunden")
            }
            _uiState.update {
                it.copy(
                    showDuplicateResolveDialog = false,
                    importResult = "Da ist etwas schief gelaufen.. Prüfe den Log"
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                showDuplicateResolveDialog = false,
                isImporting = true
            )
        }

        viewModelScope.launch {
            applyImportWithDecisions(accounts, tempDir, decisions)
        }
    }

    /**
     * Cancel import when user dismisses conflict dialog
     */
    fun cancelImportConflictResolution() {
        val tempDir = _uiState.value.pendingImportTempDir
        if (tempDir != null) {
            viewModelScope.launch {
                importBackupsUseCase.cancelImport(tempDir)
            }
        }

        _uiState.update {
            it.copy(
                showDuplicateResolveDialog = false,
                importDuplicates = emptyList(),
                pendingImportAccounts = emptyList(),
                pendingImportTempDir = null,
                importResult = "Import abgebrochen"
            )
        }
    }

    private suspend fun applyImportWithDecisions(
        accounts: List<ImportAccountData>,
        tempDir: File,
        decisions: Map<String, ResolveChoice>
    ) {
        try {
            val result = importBackupsUseCase.applyImport(accounts, tempDir, decisions)

            val message = buildString {
                append("Import abgeschlossen: ")
                append("${result.importedCount} importiert")
                if (result.skippedCount > 0) append(", ${result.skippedCount} übersprungen")
                if (result.archivedCount > 0) append(", ${result.archivedCount} archiviert")
            }

            if (result.errors.isEmpty()) {
                logRepository.logInfo("SETTINGS", message)
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importResult = message,
                        pendingImportAccounts = emptyList(),
                        pendingImportTempDir = null
                    )
                }
            } else {
                logRepository.logError("SETTINGS", "Import mit Fehlern: ${result.errors.joinToString()}")
                showErrorToast()
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importResult = "Da ist etwas schief gelaufen.. Prüfe den Log",
                        pendingImportAccounts = emptyList(),
                        pendingImportTempDir = null
                    )
                }
            }
        } catch (e: Exception) {
            logRepository.logError("SETTINGS", "Import Exception: ${e.message}", exception = e)
            showErrorToast()
            _uiState.update {
                it.copy(
                    isImporting = false,
                    importResult = "Da ist etwas schief gelaufen.. Prüfe den Log",
                    pendingImportAccounts = emptyList(),
                    pendingImportTempDir = null
                )
            }
        }
    }

    // ========== SSH Settings Functions (P6 spec) ==========

    fun updateSshEnabled(enabled: Boolean) {
        viewModelScope.launch {
            // Toggle only saves to DataStore, does not trigger test
            _uiState.update { it.copy(sshEnabled = enabled) }
            logRepository.logInfo("SETTINGS", "SSH ${if (enabled) "aktiviert" else "deaktiviert"}")
        }
    }

    fun updateSshHost(host: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(sshHost = host, sshHostSaved = false) }
        }
    }

    fun saveSshHost() {
        viewModelScope.launch {
            logRepository.logInfo("SETTINGS", "SSH Host gespeichert")
            _uiState.update { it.copy(sshHostSaved = true) }
        }
    }

    fun updateSshPort(port: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(sshPort = port, sshPortSaved = false) }
        }
    }

    fun saveSshPort() {
        viewModelScope.launch {
            logRepository.logInfo("SETTINGS", "SSH Port gespeichert")
            _uiState.update { it.copy(sshPortSaved = true) }
        }
    }

    fun updateSshUsername(username: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(sshUsername = username, sshUsernameSaved = false) }
        }
    }

    fun saveSshUsername() {
        viewModelScope.launch {
            logRepository.logInfo("SETTINGS", "SSH Benutzername gespeichert")
            _uiState.update { it.copy(sshUsernameSaved = true) }
        }
    }

    fun updateSshPasswordField(password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(sshPassword = password, sshPasswordSaved = false) }
        }
    }

    fun saveSshPassword() {
        viewModelScope.launch {
            settingsDataStore.setSshPassword(_uiState.value.sshPassword)
            logRepository.logInfo("SETTINGS", "SSH Passwort gespeichert")
            _uiState.update { it.copy(sshPasswordSaved = true) }
        }
    }

    // Legacy SSH functions for compatibility
    fun updateSshPrivateKeyPath(path: String) {
        viewModelScope.launch {
            settingsDataStore.setSshPrivateKeyPath(path)
            _uiState.update { it.copy(sshKeyPathSaved = true) }
        }
    }

    fun resetSshKeyPathSaved() {
        _uiState.update { it.copy(sshKeyPathSaved = false) }
    }

    fun updateSshServer(server: String) {
        viewModelScope.launch {
            settingsDataStore.setSshServer(server)
            _uiState.update { it.copy(sshServerSaved = true) }
        }
    }

    fun resetSshServerSaved() {
        _uiState.update { it.copy(sshServerSaved = false) }
    }

    fun updateSshBackupPath(path: String) {
        viewModelScope.launch {
            settingsDataStore.setSshBackupPath(path)
            _uiState.update { it.copy(sshBackupPathSaved = true) }
        }
    }

    fun resetSshBackupPathSaved() {
        _uiState.update { it.copy(sshBackupPathSaved = false) }
    }

    fun updateSshPassword(password: String) {
        viewModelScope.launch {
            settingsDataStore.setSshPassword(password)
            _uiState.update { it.copy(sshPasswordSaved = true) }
        }
    }

    fun resetSshPasswordSaved() {
        _uiState.update { it.copy(sshPasswordSaved = false) }
    }

    fun updateSshAuthMethod(method: String) {
        viewModelScope.launch {
            settingsDataStore.setSshAuthMethod(method)
        }
    }

    fun updateSshAutoCheckOnStart(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setSshAutoCheckOnStart(enabled)
            logRepository.logInfo("SETTINGS", "SSH Auto-Check beim Start ${if (enabled) "aktiviert" else "deaktiviert"}")
        }
    }

    fun updateSshAutoUploadOnExport(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setSshAutoUploadOnExport(enabled)
        }
    }

    /**
     * Manual SSH connection test per P6 spec
     * Success -> Toast "Verbindung erfolgreich"
     * Failure -> Log(ERROR) + standard error message
     */
    fun testSshConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSshTesting = true, sshTestResult = null) }
            logRepository.logInfo("SETTINGS", "SSH-Verbindungstest gestartet")

            try {
                val result = sshSyncService.testConnection()
                when (result) {
                    is SSHOperationResult.Success -> {
                        logRepository.logInfo("SETTINGS", "SSH-Verbindung erfolgreich")
                        Toast.makeText(context, "Verbindung erfolgreich", Toast.LENGTH_SHORT).show()
                        _uiState.update { it.copy(isSshTesting = false, sshTestResult = "Verbindung erfolgreich") }
                    }
                    is SSHOperationResult.Error -> {
                        logRepository.logError("SETTINGS", "SSH-Verbindung fehlgeschlagen: ${result.message}", null)
                        showErrorToast()
                        _uiState.update { it.copy(isSshTesting = false, sshTestResult = "Da ist etwas schief gelaufen.. Prüfe den Log") }
                    }
                }
            } catch (e: Exception) {
                logRepository.logError("SETTINGS", "SSH-Test Exception: ${e.message}", null, e)
                showErrorToast()
                _uiState.update { it.copy(isSshTesting = false, sshTestResult = "Da ist etwas schief gelaufen.. Prüfe den Log") }
            }
        }
    }

    fun clearSshTestResult() {
        _uiState.update { it.copy(sshTestResult = null) }
    }

    fun formatLastSyncTime(): String {
        return sshSyncService.formatTimestamp(_uiState.value.sshLastSyncTimestamp)
    }
}
