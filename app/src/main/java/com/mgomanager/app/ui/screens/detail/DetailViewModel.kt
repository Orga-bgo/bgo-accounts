package com.mgomanager.app.ui.screens.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mgomanager.app.data.model.Account
import com.mgomanager.app.data.model.RestoreResult
import com.mgomanager.app.data.model.SusLevel
import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.BackupRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.util.RootUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val account: Account? = null,
    val isLoading: Boolean = false,
    val showEditDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showRestoreDialog: Boolean = false,
    val restoreResult: RestoreResult? = null,
    val showRestoreSuccessDialog: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountRepository: AccountRepository,
    private val backupRepository: BackupRepository,
    private val logRepository: LogRepository,
    private val rootUtil: RootUtil
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun loadAccount(accountId: Long) {
        viewModelScope.launch {
            accountRepository.getAccountByIdFlow(accountId).collect { account ->
                _uiState.update { it.copy(account = account) }
            }
        }
    }

    fun showRestoreDialog() {
        _uiState.update { it.copy(showRestoreDialog = true) }
    }

    fun hideRestoreDialog() {
        _uiState.update { it.copy(showRestoreDialog = false, restoreResult = null) }
    }

    fun restoreAccount() {
        viewModelScope.launch {
            val account = _uiState.value.account ?: return@launch
            _uiState.update { it.copy(isLoading = true) }

            try {
                logRepository.logInfo("DETAIL", "Starte Restore für ${account.fullName}")

                val result = backupRepository.restoreBackup(account.id)
                val isSuccess = result is RestoreResult.Success

                if (isSuccess) {
                    logRepository.logInfo("DETAIL", "Restore erfolgreich für ${account.fullName}")
                } else {
                    val error = (result as? RestoreResult.Failure)?.error ?: "Unbekannter Fehler"
                    logRepository.logError("DETAIL", "Restore fehlgeschlagen: $error", account.fullName)
                    showErrorToast()
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        restoreResult = if (!isSuccess) result else null,
                        showRestoreDialog = false,
                        showRestoreSuccessDialog = isSuccess
                    )
                }
            } catch (e: Exception) {
                logRepository.logError("DETAIL", "Restore Exception: ${e.message}", account.fullName, e)
                showErrorToast()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        showRestoreDialog = false
                    )
                }
            }
        }
    }

    private fun showErrorToast() {
        Toast.makeText(context, "Da ist etwas schief gelaufen.. Prüfe den Log", Toast.LENGTH_LONG).show()
    }

    fun hideRestoreSuccessDialog() {
        _uiState.update { it.copy(showRestoreSuccessDialog = false) }
    }

    fun showEditDialog() {
        _uiState.update { it.copy(showEditDialog = true) }
    }

    fun hideEditDialog() {
        _uiState.update { it.copy(showEditDialog = false) }
    }

    fun updateAccount(
        name: String,
        susLevel: SusLevel,
        hasError: Boolean,
        fbUsername: String?,
        fbPassword: String?,
        fb2FA: String?,
        fbTempMail: String?
    ) {
        viewModelScope.launch {
            val account = _uiState.value.account ?: return@launch
            val updated = account.copy(
                accountName = name,
                susLevel = susLevel,
                hasError = hasError,
                fbUsername = fbUsername,
                fbPassword = fbPassword,
                fb2FA = fb2FA,
                fbTempMail = fbTempMail
            )
            accountRepository.updateAccount(updated)
            _uiState.update { it.copy(showEditDialog = false) }
        }
    }

    fun showDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun hideDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun deleteAccount(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val account = _uiState.value.account ?: return@launch

            try {
                logRepository.logInfo("DETAIL", "Starte Löschung für ${account.fullName}")

                // Step 1: Delete backup folder first (important order per P5 spec)
                val backupPath = account.backupPath
                if (backupPath.isNotBlank()) {
                    val deleteResult = rootUtil.executeCommand("rm -rf \"$backupPath\"")
                    if (deleteResult.isFailure) {
                        logRepository.logWarning("DETAIL", "Backup-Ordner konnte nicht gelöscht werden: $backupPath")
                        // Continue with DB deletion even if folder deletion fails
                    } else {
                        logRepository.logInfo("DETAIL", "Backup-Ordner gelöscht: $backupPath")
                    }
                }

                // Step 2: Delete DB entry
                accountRepository.deleteAccount(account)
                logRepository.logInfo("DETAIL", "Account aus DB gelöscht: ${account.fullName}")

                _uiState.update { it.copy(showDeleteDialog = false) }
                onDeleted()

            } catch (e: Exception) {
                logRepository.logError("DETAIL", "Löschung fehlgeschlagen: ${e.message}", account.fullName, e)
                showErrorToast()
                _uiState.update { it.copy(showDeleteDialog = false) }
            }
        }
    }

    /**
     * Copy value to clipboard and show toast
     */
    fun copyToClipboard(label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, value)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Kopiert", Toast.LENGTH_SHORT).show()
    }

    /**
     * Update all account fields (Edit functionality per P5 spec)
     * Changes only affect DB, no side effects
     */
    fun updateAccountFull(
        name: String,
        userId: String,
        ssaid: String,
        gaid: String,
        deviceToken: String,
        appSetId: String,
        susLevel: SusLevel,
        hasError: Boolean,
        hasFacebookLink: Boolean,
        fbUsername: String?,
        fbPassword: String?,
        fb2FA: String?,
        fbTempMail: String?,
        backupPath: String
    ) {
        viewModelScope.launch {
            val account = _uiState.value.account ?: return@launch

            val updated = account.copy(
                accountName = name,
                userId = userId,
                ssaid = ssaid,
                gaid = gaid,
                deviceToken = deviceToken,
                appSetId = appSetId,
                susLevel = susLevel,
                hasError = hasError,
                hasFacebookLink = hasFacebookLink,
                fbUsername = fbUsername,
                fbPassword = fbPassword,
                fb2FA = fb2FA,
                fbTempMail = fbTempMail,
                backupPath = backupPath
            )

            accountRepository.updateAccount(updated)
            logRepository.logInfo("DETAIL", "Account aktualisiert: ${updated.fullName}")
            _uiState.update { it.copy(showEditDialog = false) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
