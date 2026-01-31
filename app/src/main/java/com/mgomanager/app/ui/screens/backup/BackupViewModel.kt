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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Wizard steps for Tab 1 (Account sichern)
 */
enum class BackupWizardStep {
    NAME_INPUT,
    SOCIAL_MEDIA,
    FACEBOOK_DETAILS,
    BACKUP_PROGRESS,
    SUCCESS,
    ERROR
}

/**
 * UI State for Backup screen
 */
data class BackupUiState(
    // Wizard state
    val currentStep: BackupWizardStep = BackupWizardStep.NAME_INPUT,
    val selectedTab: Int = 0, // 0 = Account sichern, 1 = Account erstellen

    // Input fields
    val accountName: String = "",
    val hasFacebookLink: Boolean = false,
    val fbUsername: String = "",
    val fbPassword: String = "",
    val fb2FA: String = "",
    val fbTempMail: String = "",

    // Progress state
    val isLoading: Boolean = false,
    val progressMessage: String = "",

    // Result state
    val backupResult: BackupResult? = null,
    val createdAccount: Account? = null,
    val errorMessage: String? = null,

    // Duplicate handling
    val duplicateUserIdInfo: DuplicateInfo? = null,

    // Settings
    val accountPrefix: String = "MGO_"
)

data class DuplicateInfo(
    val userId: String,
    val existingAccountName: String
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupRepository: BackupRepository,
    private val appStateRepository: AppStateRepository,
    private val restoreBackupUseCase: RestoreBackupUseCase,
    private val logRepository: LogRepository,
    private val rootUtil: RootUtil
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

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
    // Tab Navigation
    // ============================================================

    fun selectTab(index: Int) {
        _uiState.update {
            it.copy(
                selectedTab = index,
                currentStep = BackupWizardStep.NAME_INPUT,
                accountName = "",
                hasFacebookLink = false,
                fbUsername = "",
                fbPassword = "",
                fb2FA = "",
                fbTempMail = "",
                backupResult = null,
                errorMessage = null
            )
        }
    }

    // ============================================================
    // Wizard Navigation (Tab 1: Account sichern)
    // ============================================================

    fun onAccountNameChange(name: String) {
        _uiState.update { it.copy(accountName = name) }
    }

    fun onHasFacebookLinkChange(hasLink: Boolean) {
        _uiState.update { it.copy(hasFacebookLink = hasLink) }
    }

    fun onFbUsernameChange(username: String) {
        _uiState.update { it.copy(fbUsername = username) }
    }

    fun onFbPasswordChange(password: String) {
        _uiState.update { it.copy(fbPassword = password) }
    }

    fun onFb2FAChange(code: String) {
        _uiState.update { it.copy(fb2FA = code) }
    }

    fun onFbTempMailChange(mail: String) {
        _uiState.update { it.copy(fbTempMail = mail) }
    }

    fun proceedFromNameInput() {
        if (_uiState.value.accountName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Bitte gib einen Account-Namen ein") }
            return
        }
        _uiState.update { it.copy(currentStep = BackupWizardStep.SOCIAL_MEDIA, errorMessage = null) }
    }

    fun proceedFromSocialMedia() {
        if (_uiState.value.hasFacebookLink) {
            _uiState.update { it.copy(currentStep = BackupWizardStep.FACEBOOK_DETAILS) }
        } else {
            startBackup()
        }
    }

    fun proceedFromFacebookDetails() {
        startBackup()
    }

    fun goBack() {
        val newStep = when (_uiState.value.currentStep) {
            BackupWizardStep.SOCIAL_MEDIA -> BackupWizardStep.NAME_INPUT
            BackupWizardStep.FACEBOOK_DETAILS -> BackupWizardStep.SOCIAL_MEDIA
            else -> _uiState.value.currentStep
        }
        _uiState.update { it.copy(currentStep = newStep) }
    }

    // ============================================================
    // Backup Execution
    // ============================================================

    private fun startBackup() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    currentStep = BackupWizardStep.BACKUP_PROGRESS,
                    isLoading = true,
                    progressMessage = "Backup wird erstellt...",
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
                    hasFacebookLink = _uiState.value.hasFacebookLink,
                    fbUsername = if (_uiState.value.hasFacebookLink) _uiState.value.fbUsername else null,
                    fbPassword = if (_uiState.value.hasFacebookLink) _uiState.value.fbPassword else null,
                    fb2FA = if (_uiState.value.hasFacebookLink) _uiState.value.fb2FA else null,
                    fbTempMail = if (_uiState.value.hasFacebookLink) _uiState.value.fbTempMail else null
                )

                val result = backupRepository.createBackup(request)

                when (result) {
                    is BackupResult.Success -> {
                        _uiState.update {
                            it.copy(
                                currentStep = BackupWizardStep.SUCCESS,
                                isLoading = false,
                                backupResult = result,
                                createdAccount = result.account
                            )
                        }
                    }
                    is BackupResult.PartialSuccess -> {
                        _uiState.update {
                            it.copy(
                                currentStep = BackupWizardStep.SUCCESS,
                                isLoading = false,
                                backupResult = result,
                                createdAccount = result.account
                            )
                        }
                    }
                    is BackupResult.DuplicateUserId -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                duplicateUserIdInfo = DuplicateInfo(
                                    userId = result.userId,
                                    existingAccountName = result.existingAccountName
                                ),
                                currentStep = BackupWizardStep.ERROR,
                                errorMessage = "User ID bereits als '${result.existingAccountName}' vorhanden."
                            )
                        }
                    }
                    is BackupResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                currentStep = BackupWizardStep.ERROR,
                                isLoading = false,
                                errorMessage = "Da ist etwas schief gelaufen.. Prüfe den Log"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                logRepository.logError("BACKUP_VM", "Backup fehlgeschlagen", exception = e)
                _uiState.update {
                    it.copy(
                        currentStep = BackupWizardStep.ERROR,
                        isLoading = false,
                        errorMessage = "Da ist etwas schief gelaufen.. Prüfe den Log"
                    )
                }
            }
        }
    }

    fun retryBackup() {
        _uiState.update {
            it.copy(
                currentStep = BackupWizardStep.NAME_INPUT,
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
                    currentStep = BackupWizardStep.BACKUP_PROGRESS,
                    isLoading = true,
                    progressMessage = "Backup wird erstellt (forceDuplicate)...",
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
                    hasFacebookLink = _uiState.value.hasFacebookLink,
                    fbUsername = if (_uiState.value.hasFacebookLink) _uiState.value.fbUsername else null,
                    fbPassword = if (_uiState.value.hasFacebookLink) _uiState.value.fbPassword else null,
                    fb2FA = if (_uiState.value.hasFacebookLink) _uiState.value.fb2FA else null,
                    fbTempMail = if (_uiState.value.hasFacebookLink) _uiState.value.fbTempMail else null
                )

                // Call with forceDuplicate = true
                val result = backupRepository.createBackup(request, forceDuplicate = true)

                when (result) {
                    is BackupResult.Success -> {
                        _uiState.update {
                            it.copy(
                                currentStep = BackupWizardStep.SUCCESS,
                                isLoading = false,
                                backupResult = result,
                                createdAccount = result.account,
                                duplicateUserIdInfo = null
                            )
                        }
                    }
                    is BackupResult.PartialSuccess -> {
                        _uiState.update {
                            it.copy(
                                currentStep = BackupWizardStep.SUCCESS,
                                isLoading = false,
                                backupResult = result,
                                createdAccount = result.account,
                                duplicateUserIdInfo = null
                            )
                        }
                    }
                    is BackupResult.DuplicateUserId -> {
                        // Should not happen with forceDuplicate=true, but handle just in case
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                currentStep = BackupWizardStep.ERROR,
                                errorMessage = "User ID bereits als '${result.existingAccountName}' vorhanden."
                            )
                        }
                    }
                    is BackupResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                currentStep = BackupWizardStep.ERROR,
                                isLoading = false,
                                errorMessage = "Da ist etwas schief gelaufen.. Prüfe den Log"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                logRepository.logError("BACKUP_VM", "Force backup fehlgeschlagen", exception = e)
                _uiState.update {
                    it.copy(
                        currentStep = BackupWizardStep.ERROR,
                        isLoading = false,
                        errorMessage = "Da ist etwas schief gelaufen.. Prüfe den Log"
                    )
                }
            }
        }
    }

    // ============================================================
    // Success Actions
    // ============================================================

    /**
     * Navigate to account detail
     * Returns the account ID to navigate to
     */
    fun getCreatedAccountId(): Long? {
        return _uiState.value.createdAccount?.id
    }

    /**
     * Start Monopoly Go with the created account (Restore + Launch)
     */
    fun launchMonopolyGoWithCreatedAccount() {
        val account = _uiState.value.createdAccount ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                logRepository.logInfo("BACKUP_VM", "Starte Monopoly GO mit neuem Account: ${account.fullName}")

                // Use RestoreBackupUseCase to set SSAID and copy data
                val restoreResult = restoreBackupUseCase.execute(account.id)

                when (restoreResult) {
                    is RestoreResult.Success -> {
                        // Start Monopoly GO
                        val launchResult = rootUtil.executeCommand(
                            "am start -n com.scopely.monopolygo/.MainActivity"
                        )

                        if (launchResult.isSuccess) {
                            logRepository.logInfo("BACKUP_VM", "Monopoly GO erfolgreich gestartet")
                        } else {
                            showErrorToast("Da lief etwas schief .. Prüfe den Log.")
                            logRepository.logError(
                                "BACKUP_VM",
                                "Fehler beim Starten von Monopoly GO: ${launchResult.exceptionOrNull()?.message}"
                            )
                        }
                    }

                    is RestoreResult.Failure -> {
                        showErrorToast("Da lief etwas schief .. Prüfe den Log.")
                        logRepository.logError("BACKUP_VM", "Restore fehlgeschlagen: ${restoreResult.error}")
                    }
                }

                _uiState.update { it.copy(isLoading = false) }

            } catch (e: Exception) {
                showErrorToast("Da lief etwas schief .. Prüfe den Log.")
                logRepository.logError("BACKUP_VM", "Unerwarteter Fehler", exception = e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Reset to start a new backup
     */
    fun resetForNewBackup() {
        _uiState.update {
            BackupUiState(accountPrefix = it.accountPrefix)
        }
    }

    private fun showErrorToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
