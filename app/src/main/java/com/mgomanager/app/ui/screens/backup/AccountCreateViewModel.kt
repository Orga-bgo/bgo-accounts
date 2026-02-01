package com.mgomanager.app.ui.screens.backup

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mgomanager.app.data.model.Account
import com.mgomanager.app.data.model.BackupResult
import com.mgomanager.app.data.model.RestoreResult
import com.mgomanager.app.data.repository.AppStateRepository
import com.mgomanager.app.data.repository.BackupRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.usecase.BackupRequest
import com.mgomanager.app.domain.usecase.RestoreBackupUseCase
import com.mgomanager.app.domain.util.RootUtil
import com.mgomanager.app.domain.util.SsaidUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject

/**
 * Wizard steps for Tab 2 (Account erstellen)
 */
enum class AccountCreateStep {
    NAME_INPUT,           // Step 1: Account Name + Prefix
    WARNING,              // Step 2: Warning confirmation
    SSAID_SELECTION,      // Step 3: SSAID choice (new or current)
    PROGRESS_WIPE,        // Step 4: Wiping app data
    PROGRESS_WAIT,        // Step 5: Waiting for stabilization
    PROGRESS_PATCH,       // Step 6: Patching SSAID
    PROGRESS_START,       // Step 7: Starting Monopoly GO
    PROGRESS_STOP,        // Step 8: Stopping Monopoly GO
    PROGRESS_BACKUP,      // Step 9: Backup + extraction
    SUCCESS,              // Step 12: Success
    ERROR                 // Error state
}

/**
 * SSAID option selection
 */
enum class SsaidOption {
    NEW,     // Generate new SSAID
    CURRENT  // Use current SSAID
}

/**
 * UI State for Account Create (Tab 2)
 */
data class AccountCreateUiState(
    // Wizard state
    val currentStep: AccountCreateStep = AccountCreateStep.NAME_INPUT,

    // Input fields
    val accountName: String = "",
    val accountPrefix: String = "MGO_",

    // SSAID selection
    val ssaidOption: SsaidOption = SsaidOption.NEW,
    val selectedSsaid: String? = null,
    val currentSsaid: String? = null,

    // Progress state
    val isLoading: Boolean = false,
    val progressMessage: String = "",
    val progressPercent: Float = 0f,

    // Result state
    val createdAccount: Account? = null,
    val errorMessage: String? = null,

    // Duplicate handling
    val duplicateUserIdInfo: DuplicateInfo? = null
)

@HiltViewModel
class AccountCreateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupRepository: BackupRepository,
    private val appStateRepository: AppStateRepository,
    private val restoreBackupUseCase: RestoreBackupUseCase,
    private val logRepository: LogRepository,
    private val rootUtil: RootUtil,
    private val ssaidUtil: SsaidUtil
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountCreateUiState())
    val uiState: StateFlow<AccountCreateUiState> = _uiState.asStateFlow()

    private var accountCreateLogBuilder: StringBuilder? = null

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val prefix = appStateRepository.getDefaultPrefix() ?: "MGO_"
            _uiState.update { it.copy(accountPrefix = prefix) }
        }
    }

    // ============================================================
    // Input Handlers
    // ============================================================

    fun onAccountNameChange(name: String) {
        _uiState.update { it.copy(accountName = name) }
    }

    fun onSsaidOptionChange(option: SsaidOption) {
        _uiState.update { it.copy(ssaidOption = option) }
    }

    // ============================================================
    // Step 1: Name Input
    // ============================================================

    fun proceedFromNameInput() {
        val name = _uiState.value.accountName.trim()

        // Validate name
        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Bitte gib einen Account-Namen ein") }
            return
        }

        // Check for invalid path characters
        if (name.contains(Regex("[/\\\\:*?\"<>|]"))) {
            _uiState.update { it.copy(errorMessage = "Der Name darf keine Sonderzeichen enthalten") }
            return
        }

        _uiState.update {
            it.copy(
                currentStep = AccountCreateStep.WARNING,
                errorMessage = null
            )
        }
    }

    // ============================================================
    // Step 2: Warning
    // ============================================================

    fun confirmWarning() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    currentStep = AccountCreateStep.SSAID_SELECTION,
                    isLoading = true
                )
            }

            // First, ensure root access is ready (critical for Magisk timing)
            val logBuilder = initializeAccountCreateLog()
            logBuilder.appendLine("1. Root-Zugriff prüfen ..")
            val rootReady = rootUtil.requestRootAccess()
            if (!rootReady) {
                appendStatusLine(logBuilder, "Root-Zugriff vorhanden ..", "Fehler", "Fehlende Rootrechte")
                finalizeAccountCreateLog(
                    level = "ERROR",
                    message = "Account ${_uiState.value.accountName.trim()} Create."
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentStep = AccountCreateStep.ERROR,
                        errorMessage = "Root-Zugriff nicht verfügbar. Bitte erteile Root-Berechtigung."
                    )
                }
                return@launch
            }
            appendStatusLine(logBuilder, "Root-Zugriff vorhanden ..", "Erfolg")

            // Try to read current SSAID
            try {
                logBuilder.appendLine()
                logBuilder.appendLine("2. Aktuelle SSAID auslesen ..")
                val currentSsaidResult = ssaidUtil.readCurrentSsaid()
                val currentSsaid = currentSsaidResult.getOrNull()
                val ssaidStatus = if (currentSsaid.isNullOrBlank()) "Warnung" else "Erfolg"
                appendStatusLine(logBuilder, "SSAID ausgelesen ..", ssaidStatus)

                _uiState.update {
                    it.copy(
                        currentSsaid = currentSsaid,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                logBuilder.appendLine()
                logBuilder.appendLine("2. Aktuelle SSAID auslesen ..")
                appendStatusLine(logBuilder, "SSAID ausgelesen ..", "Warnung")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun cancelWarning() {
        resetAccountCreateLog()
        _uiState.update { it.copy(currentStep = AccountCreateStep.NAME_INPUT) }
    }

    // ============================================================
    // Step 3: SSAID Selection
    // ============================================================

    fun proceedFromSsaidSelection() {
        viewModelScope.launch {
            val selectedSsaid = when (_uiState.value.ssaidOption) {
                SsaidOption.NEW -> ssaidUtil.generateNewSsaid()
                SsaidOption.CURRENT -> _uiState.value.currentSsaid ?: ssaidUtil.generateNewSsaid()
            }

            _uiState.update {
                it.copy(
                    selectedSsaid = selectedSsaid,
                    currentStep = AccountCreateStep.PROGRESS_WIPE,
                    progressMessage = "App-Daten werden gelöscht...",
                    progressPercent = 0.1f
                )
            }

            // Start the creation flow
            startAccountCreationFlow()
        }
    }

    fun goBackFromSsaidSelection() {
        _uiState.update { it.copy(currentStep = AccountCreateStep.WARNING) }
    }

    // ============================================================
    // Account Creation Flow
    // ============================================================

    private fun startAccountCreationFlow() {
        viewModelScope.launch {
            val logBuilder = accountCreateLogBuilder ?: initializeAccountCreateLog()
            try {
                // Step 4: Wipe app data
                updateProgress(AccountCreateStep.PROGRESS_WIPE, "App-Daten werden gelöscht...", 0.15f)
                val wipeResult = ssaidUtil.clearMonopolyGoData()
                if (wipeResult.isFailure) {
                    appendStatusLine(
                        logBuilder,
                        "3. App-Daten löschen ..",
                        "Fehler",
                        "App-Daten löschen fehlgeschlagen: ${sanitizeMessage(wipeResult.exceptionOrNull())}"
                    )
                    finalizeAccountCreateLog(
                        level = "ERROR",
                        message = "Account ${_uiState.value.accountName.trim()} Create."
                    )
                    _uiState.update {
                        it.copy(
                            currentStep = AccountCreateStep.ERROR,
                            errorMessage = "Da ist etwas schief gelaufen.. Prüfe den Log"
                        )
                    }
                    return@launch
                }
                appendStatusLine(logBuilder, "3. App-Daten löschen ..", "Erfolg")

                // Step 5: Wait for stabilization
                updateProgress(AccountCreateStep.PROGRESS_WAIT, "Warte auf Stabilisierung...", 0.25f)
                delay(2000) // Wait 2 seconds
                appendStatusLine(logBuilder, "4. Warte auf Stabilisierung ..", "Erfolg")

                // Step 6: Patch SSAID
                updateProgress(AccountCreateStep.PROGRESS_PATCH, "SSAID wird gepatcht...", 0.35f)
                val patchResult = ssaidUtil.patchSsaid(_uiState.value.selectedSsaid!!)
                if (patchResult.isFailure) {
                    appendStatusLine(
                        logBuilder,
                        "5. SSAID patchen ..",
                        "Fehler",
                        "SSAID patchen fehlgeschlagen: ${sanitizeMessage(patchResult.exceptionOrNull())}"
                    )
                    finalizeAccountCreateLog(
                        level = "ERROR",
                        message = "Account ${_uiState.value.accountName.trim()} Create."
                    )
                    _uiState.update {
                        it.copy(
                            currentStep = AccountCreateStep.ERROR,
                            errorMessage = "Da ist etwas schief gelaufen.. Prüfe den Log"
                        )
                    }
                    return@launch
                }
                appendStatusLine(logBuilder, "5. SSAID patchen ..", "Erfolg")

                // Step 7: Start Monopoly GO for initialization
                updateProgress(AccountCreateStep.PROGRESS_START, "Monopoly GO wird gestartet...", 0.45f)
                val startResult = ssaidUtil.startMonopolyGo()
                if (startResult.isFailure) {
                    appendStatusLine(logBuilder, "6. Monopoly GO starten ..", "Warnung")
                    // Don't abort, just log warning
                } else {
                    appendStatusLine(logBuilder, "6. Monopoly GO starten ..", "Erfolg")
                }

                // Wait for app to initialize
                updateProgress(AccountCreateStep.PROGRESS_START, "Warte auf Initialisierung...", 0.55f)
                delay(5000) // Wait 5 seconds for app to create initial files
                appendStatusLine(logBuilder, "7. Warte auf Initialisierung ..", "Erfolg")

                // Step 8: Stop Monopoly GO
                updateProgress(AccountCreateStep.PROGRESS_STOP, "Monopoly GO wird gestoppt...", 0.65f)
                val stopResult = ssaidUtil.stopMonopolyGo()
                if (stopResult.isFailure) {
                    appendStatusLine(logBuilder, "8. Monopoly GO stoppen ..", "Warnung")
                    // Don't abort, just log warning
                } else {
                    appendStatusLine(logBuilder, "8. Monopoly GO stoppen ..", "Erfolg")
                }

                // Additional wait for clean stop
                delay(1000)

                // Step 9: Backup + extraction
                updateProgress(AccountCreateStep.PROGRESS_BACKUP, "Backup wird erstellt...", 0.75f)
                val backupResult = performBackup()
                handleBackupResultForLog(backupResult, logBuilder)

            } catch (e: Exception) {
                appendStatusLine(
                    logBuilder,
                    "Account erstellen ..",
                    "Fehler",
                    "Exception: ${sanitizeMessage(e)}"
                )
                finalizeAccountCreateLog(
                    level = "ERROR",
                    message = "Account ${_uiState.value.accountName.trim()} Create."
                )
                _uiState.update {
                    it.copy(
                        currentStep = AccountCreateStep.ERROR,
                        errorMessage = "Da ist etwas schief gelaufen.. Prüfe den Log"
                    )
                }
            }
        }
    }

    private suspend fun performBackup(): BackupResult {
        return try {
            val prefix = appStateRepository.getDefaultPrefix() ?: "MGO_"
            val backupPath = appStateRepository.getBackupDirectory() ?: "/storage/emulated/0/bgo_backups/"

            val request = BackupRequest(
                accountName = _uiState.value.accountName,
                prefix = prefix,
                backupRootPath = backupPath,
                hasFacebookLink = false, // Tab 2 doesn't have social media options
                fbUsername = null,
                fbPassword = null,
                fb2FA = null,
                fbTempMail = null
            )

            updateProgress(AccountCreateStep.PROGRESS_BACKUP, "Daten werden kopiert...", 0.85f)

            backupRepository.createBackup(request)
        } catch (e: Exception) {
            BackupResult.Failure("Backup erstellen fehlgeschlagen: ${e.message}", e)
        }
    }

    private fun updateProgress(step: AccountCreateStep, message: String, percent: Float) {
        _uiState.update {
            it.copy(
                currentStep = step,
                progressMessage = message,
                progressPercent = percent
            )
        }
    }

    private fun handleBackupResultForLog(result: BackupResult, logBuilder: StringBuilder) {
        when (result) {
            is BackupResult.Success -> {
                appendStatusLine(logBuilder, "9. Backup erstellen ..", "Erfolg")
                _uiState.update {
                    it.copy(
                        currentStep = AccountCreateStep.SUCCESS,
                        createdAccount = result.account,
                        progressPercent = 1f
                    )
                }
                finalizeAccountCreateLog(
                    level = "INFO",
                    message = "Account ${result.account.fullName} Create."
                )
            }
            is BackupResult.PartialSuccess -> {
                appendStatusLine(logBuilder, "9. Backup erstellen ..", "Warnung")
                _uiState.update {
                    it.copy(
                        currentStep = AccountCreateStep.SUCCESS,
                        createdAccount = result.account,
                        progressPercent = 1f
                    )
                }
                finalizeAccountCreateLog(
                    level = "WARNING",
                    message = "Account ${result.account.fullName} Create."
                )
            }
            is BackupResult.DuplicateUserId -> {
                appendStatusLine(
                    logBuilder,
                    "9. Backup erstellen ..",
                    "Fehler",
                    "Duplicate UserId: ${hashValue(result.userId)} besteht bereits als ${result.existingAccountName}"
                )
                _uiState.update {
                    it.copy(
                        currentStep = AccountCreateStep.ERROR,
                        duplicateUserIdInfo = DuplicateInfo(
                            userId = result.userId,
                            existingAccountName = result.existingAccountName
                        ),
                        errorMessage = "User ID bereits als '${result.existingAccountName}' vorhanden."
                    )
                }
                finalizeAccountCreateLog(
                    level = "WARNING",
                    message = "Account ${_uiState.value.accountName.trim()} Create."
                )
            }
            is BackupResult.Failure -> {
                appendStatusLine(
                    logBuilder,
                    "9. Backup erstellen ..",
                    "Fehler",
                    "Backup fehlgeschlagen: ${sanitizeMessage(result.exception ?: Exception(result.error))}"
                )
                _uiState.update {
                    it.copy(
                        currentStep = AccountCreateStep.ERROR,
                        errorMessage = "Da ist etwas schief gelaufen.. Prüfe den Log"
                    )
                }
                finalizeAccountCreateLog(
                    level = "ERROR",
                    message = "Account ${_uiState.value.accountName.trim()} Create."
                )
            }
        }
    }

    private fun handleError(step: String, exception: Throwable?) {
        viewModelScope.launch {
            val logBuilder = accountCreateLogBuilder ?: initializeAccountCreateLog()
            appendStatusLine(
                logBuilder,
                "$step ..",
                "Fehler",
                "Exception: ${sanitizeMessage(exception)}"
            )
            finalizeAccountCreateLog(
                level = "ERROR",
                message = "Account ${_uiState.value.accountName.trim()} Create."
            )
        }

        _uiState.update {
            it.copy(
                currentStep = AccountCreateStep.ERROR,
                errorMessage = "Da ist etwas schief gelaufen.. Prüfe den Log"
            )
        }
    }

    private suspend fun initializeAccountCreateLog(): StringBuilder {
        if (accountCreateLogBuilder != null) {
            return accountCreateLogBuilder!!
        }
        val accountName = _uiState.value.accountName.trim()
        val sessionId = logRepository.getCurrentSessionId()
        val correlationId = UUID.randomUUID().toString()
        return StringBuilder().apply {
            appendLine("Starte Account-Erstellung von $accountName")
            appendLine("correlationId: $correlationId")
            appendLine("sessionId: $sessionId")
            appendLine()
        }.also { accountCreateLogBuilder = it }
    }

    private fun finalizeAccountCreateLog(level: String, message: String) {
        val logBuilder = accountCreateLogBuilder ?: return
        viewModelScope.launch {
            logRepository.addLog(
                level,
                "ACCOUNT_CREATE",
                message,
                _uiState.value.accountName.trim(),
                logBuilder.toString()
            )
        }
        resetAccountCreateLog()
    }

    private fun resetAccountCreateLog() {
        accountCreateLogBuilder = null
    }

    private fun appendStatusLine(
        builder: StringBuilder,
        line: String,
        status: String,
        errorDetail: String? = null
    ) {
        builder.appendLine("$line [$status]")
        if (status == "Fehler" || status == "Userabbruch") {
            builder.appendLine("Fehlerdetails: ${errorDetail ?: "Unbekannter Fehler"}")
        }
    }

    private fun sanitizeMessage(exception: Throwable?): String {
        return exception?.message?.lineSequence()?.firstOrNull()?.ifBlank { "Unbekannter Fehler" } ?: "Unbekannter Fehler"
    }

    private fun hashValue(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return "sha256:" + digest.joinToString("") { "%02x".format(it) }.take(12)
    }

    // ============================================================
    // Navigation
    // ============================================================

    fun goBack() {
        val newStep = when (_uiState.value.currentStep) {
            AccountCreateStep.WARNING -> AccountCreateStep.NAME_INPUT
            AccountCreateStep.SSAID_SELECTION -> AccountCreateStep.WARNING
            else -> _uiState.value.currentStep
        }
        _uiState.update { it.copy(currentStep = newStep) }
    }

    // ============================================================
    // Success Actions
    // ============================================================

    fun getCreatedAccountId(): Long? {
        return _uiState.value.createdAccount?.id
    }

    fun launchMonopolyGoWithCreatedAccount() {
        val account = _uiState.value.createdAccount ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                // Use RestoreBackupUseCase to set SSAID and copy data
                val restoreResult = restoreBackupUseCase.execute(account.id)

                when (restoreResult) {
                    is RestoreResult.Success -> {
                    }

                    is RestoreResult.Failure -> {
                        showErrorToast()
                    }
                }

                _uiState.update { it.copy(isLoading = false) }

            } catch (e: Exception) {
                showErrorToast()
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun resetForNewAccount() {
        _uiState.update {
            AccountCreateUiState(accountPrefix = it.accountPrefix)
        }
    }

    fun retryAccountCreation() {
        _uiState.update {
            it.copy(
                currentStep = AccountCreateStep.NAME_INPUT,
                errorMessage = null,
                duplicateUserIdInfo = null
            )
        }
    }

    /**
     * Force backup even with duplicate User ID
     * Called when user clicks "Trotzdem sichern" after duplicate warning
     */
    fun forceBackupWithDuplicate() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    currentStep = AccountCreateStep.PROGRESS_BACKUP,
                    progressMessage = "Backup wird erstellt (forceDuplicate)...",
                    progressPercent = 0.85f,
                    errorMessage = null
                )
            }

            try {
                val logBuilder = accountCreateLogBuilder ?: initializeAccountCreateLog()
                val prefix = appStateRepository.getDefaultPrefix() ?: "MGO_"
                val backupPath = appStateRepository.getBackupDirectory() ?: "/storage/emulated/0/bgo_backups/"

                val request = BackupRequest(
                    accountName = _uiState.value.accountName,
                    prefix = prefix,
                    backupRootPath = backupPath,
                    hasFacebookLink = false,
                    fbUsername = null,
                    fbPassword = null,
                    fb2FA = null,
                    fbTempMail = null
                )

                // Call with forceDuplicate = true
                val result = backupRepository.createBackup(request, forceDuplicate = true)

                when (result) {
                    is BackupResult.Success -> {
                        appendStatusLine(logBuilder, "9. Backup erstellen (force) ..", "Erfolg")
                        _uiState.update {
                            it.copy(
                                currentStep = AccountCreateStep.SUCCESS,
                                createdAccount = result.account,
                                progressPercent = 1f,
                                duplicateUserIdInfo = null
                            )
                        }
                        finalizeAccountCreateLog(
                            level = "INFO",
                            message = "Account ${result.account.fullName} Create."
                        )
                    }
                    is BackupResult.PartialSuccess -> {
                        appendStatusLine(logBuilder, "9. Backup erstellen (force) ..", "Warnung")
                        _uiState.update {
                            it.copy(
                                currentStep = AccountCreateStep.SUCCESS,
                                createdAccount = result.account,
                                progressPercent = 1f,
                                duplicateUserIdInfo = null
                            )
                        }
                        finalizeAccountCreateLog(
                            level = "WARNING",
                            message = "Account ${result.account.fullName} Create."
                        )
                    }
                    is BackupResult.DuplicateUserId -> {
                        // Should not happen with forceDuplicate=true
                        appendStatusLine(
                            logBuilder,
                            "9. Backup erstellen (force) ..",
                            "Fehler",
                            "Duplicate UserId: ${hashValue(result.userId)} besteht bereits als ${result.existingAccountName}"
                        )
                        _uiState.update {
                            it.copy(
                                currentStep = AccountCreateStep.ERROR,
                                errorMessage = "User ID bereits als '${result.existingAccountName}' vorhanden."
                            )
                        }
                        finalizeAccountCreateLog(
                            level = "WARNING",
                            message = "Account ${_uiState.value.accountName.trim()} Create."
                        )
                    }
                    is BackupResult.Failure -> {
                        appendStatusLine(
                            logBuilder,
                            "9. Backup erstellen (force) ..",
                            "Fehler",
                            "Backup fehlgeschlagen: ${sanitizeMessage(result.exception ?: Exception(result.error))}"
                        )
                        finalizeAccountCreateLog(
                            level = "ERROR",
                            message = "Account ${_uiState.value.accountName.trim()} Create."
                        )
                        _uiState.update {
                            it.copy(
                                currentStep = AccountCreateStep.ERROR,
                                errorMessage = "Da ist etwas schief gelaufen.. Prüfe den Log"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                handleError("Force Backup erstellen", e)
            }
        }
    }

    private fun showErrorToast() {
        Toast.makeText(context, "Da lief etwas schief .. Prüfe den Log.", Toast.LENGTH_LONG).show()
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
