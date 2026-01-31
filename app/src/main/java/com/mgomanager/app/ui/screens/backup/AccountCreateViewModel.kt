package com.mgomanager.app.ui.screens.backup

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mgomanager.app.data.model.Account
import com.mgomanager.app.data.model.BackupResult
import com.mgomanager.app.data.model.RestoreResult
import com.mgomanager.app.data.repository.AccountRepository
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
    private val accountRepository: AccountRepository,
    private val backupRepository: BackupRepository,
    private val appStateRepository: AppStateRepository,
    private val restoreBackupUseCase: RestoreBackupUseCase,
    private val logRepository: LogRepository,
    private val rootUtil: RootUtil,
    private val ssaidUtil: SsaidUtil
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountCreateUiState())
    val uiState: StateFlow<AccountCreateUiState> = _uiState.asStateFlow()

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

            // Try to read current SSAID
            try {
                val currentSsaidResult = ssaidUtil.readCurrentSsaid()
                val currentSsaid = currentSsaidResult.getOrNull()

                _uiState.update {
                    it.copy(
                        currentSsaid = currentSsaid,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun cancelWarning() {
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
            try {
                // Step 4: Wipe app data
                updateProgress(AccountCreateStep.PROGRESS_WIPE, "App-Daten werden gelöscht...", 0.15f)
                val wipeResult = ssaidUtil.clearMonopolyGoData()
                if (wipeResult.isFailure) {
                    handleError("App-Daten löschen", wipeResult.exceptionOrNull())
                    return@launch
                }

                // Step 5: Wait for stabilization
                updateProgress(AccountCreateStep.PROGRESS_WAIT, "Warte auf Stabilisierung...", 0.25f)
                delay(2000) // Wait 2 seconds

                // Step 6: Patch SSAID
                updateProgress(AccountCreateStep.PROGRESS_PATCH, "SSAID wird gepatcht...", 0.35f)
                val patchResult = ssaidUtil.patchSsaid(_uiState.value.selectedSsaid!!)
                if (patchResult.isFailure) {
                    handleError("SSAID patchen", patchResult.exceptionOrNull())
                    return@launch
                }

                // Step 7: Start Monopoly GO for initialization
                updateProgress(AccountCreateStep.PROGRESS_START, "Monopoly GO wird gestartet...", 0.45f)
                val startResult = ssaidUtil.startMonopolyGo()
                if (startResult.isFailure) {
                    logRepository.logWarning("ACCOUNT_CREATE", "Monopoly GO konnte nicht gestartet werden")
                    // Don't abort, just log warning
                }

                // Wait for app to initialize
                updateProgress(AccountCreateStep.PROGRESS_START, "Warte auf Initialisierung...", 0.55f)
                delay(5000) // Wait 5 seconds for app to create initial files

                // Step 8: Stop Monopoly GO
                updateProgress(AccountCreateStep.PROGRESS_STOP, "Monopoly GO wird gestoppt...", 0.65f)
                val stopResult = ssaidUtil.stopMonopolyGo()
                if (stopResult.isFailure) {
                    logRepository.logWarning("ACCOUNT_CREATE", "Monopoly GO konnte nicht gestoppt werden")
                    // Don't abort, just log warning
                }

                // Additional wait for clean stop
                delay(1000)

                // Step 9: Backup + extraction
                updateProgress(AccountCreateStep.PROGRESS_BACKUP, "Backup wird erstellt...", 0.75f)
                performBackup()

            } catch (e: Exception) {
                handleError("Account erstellen", e)
            }
        }
    }

    private suspend fun performBackup() {
        try {
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

            val result = backupRepository.createBackup(request)

            when (result) {
                is BackupResult.Success -> {
                    // Verify SSAID matches what we set
                    val expectedSsaid = _uiState.value.selectedSsaid
                    if (expectedSsaid != null && result.account.ssaid != expectedSsaid) {
                        logRepository.logWarning(
                            "ACCOUNT_CREATE",
                            "SSAID-Warnung: Erwartet $expectedSsaid, gefunden ${result.account.ssaid}"
                        )
                    }

                    _uiState.update {
                        it.copy(
                            currentStep = AccountCreateStep.SUCCESS,
                            createdAccount = result.account,
                            progressPercent = 1f
                        )
                    }
                }

                is BackupResult.PartialSuccess -> {
                    _uiState.update {
                        it.copy(
                            currentStep = AccountCreateStep.SUCCESS,
                            createdAccount = result.account,
                            progressPercent = 1f
                        )
                    }
                }

                is BackupResult.DuplicateUserId -> {
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
                }

                is BackupResult.Failure -> {
                    handleError("Backup erstellen", Exception(result.error))
                }
            }

        } catch (e: Exception) {
            handleError("Backup erstellen", e)
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

    private fun handleError(step: String, exception: Throwable?) {
        viewModelScope.launch {
            logRepository.logError(
                "ACCOUNT_CREATE",
                "Fehler bei Step '$step': ${exception?.message}",
                exception = exception as? Exception
            )
        }

        _uiState.update {
            it.copy(
                currentStep = AccountCreateStep.ERROR,
                errorMessage = "Da ist etwas schief gelaufen.. Prüfe den Log"
            )
        }
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
                logRepository.logInfo("ACCOUNT_CREATE", "Starte Monopoly GO mit neuem Account: ${account.fullName}")

                // Use RestoreBackupUseCase to set SSAID and copy data
                val restoreResult = restoreBackupUseCase.execute(account.id)

                when (restoreResult) {
                    is RestoreResult.Success -> {
                        // Start Monopoly GO
                        val launchResult = rootUtil.executeCommand(
                            "am start -n com.scopely.monopolygo/.MainActivity"
                        )

                        if (launchResult.isSuccess) {
                            logRepository.logInfo("ACCOUNT_CREATE", "Monopoly GO erfolgreich gestartet")
                        } else {
                            showErrorToast()
                            logRepository.logError(
                                "ACCOUNT_CREATE",
                                "Fehler beim Starten von Monopoly GO: ${launchResult.exceptionOrNull()?.message}"
                            )
                        }
                    }

                    is RestoreResult.Failure -> {
                        showErrorToast()
                        logRepository.logError("ACCOUNT_CREATE", "Restore fehlgeschlagen: ${restoreResult.error}")
                    }
                }

                _uiState.update { it.copy(isLoading = false) }

            } catch (e: Exception) {
                showErrorToast()
                logRepository.logError("ACCOUNT_CREATE", "Unerwarteter Fehler", exception = e)
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
                        _uiState.update {
                            it.copy(
                                currentStep = AccountCreateStep.SUCCESS,
                                createdAccount = result.account,
                                progressPercent = 1f,
                                duplicateUserIdInfo = null
                            )
                        }
                    }
                    is BackupResult.PartialSuccess -> {
                        _uiState.update {
                            it.copy(
                                currentStep = AccountCreateStep.SUCCESS,
                                createdAccount = result.account,
                                progressPercent = 1f,
                                duplicateUserIdInfo = null
                            )
                        }
                    }
                    is BackupResult.DuplicateUserId -> {
                        // Should not happen with forceDuplicate=true
                        _uiState.update {
                            it.copy(
                                currentStep = AccountCreateStep.ERROR,
                                errorMessage = "User ID bereits als '${result.existingAccountName}' vorhanden."
                            )
                        }
                    }
                    is BackupResult.Failure -> {
                        handleError("Force Backup erstellen", Exception(result.error))
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
