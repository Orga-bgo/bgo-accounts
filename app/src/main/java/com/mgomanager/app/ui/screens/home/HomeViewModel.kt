package com.mgomanager.app.ui.screens.home

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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sort options for account list
 */
enum class SortOption(val displayName: String) {
    NAME("Name"),
    LAST_PLAYED("Zuletzt gespielt"),
    CREATED_AT("Erstellt am"),
    USER_ID("User ID")
}

/**
 * Sort direction
 */
enum class SortDirection {
    ASC, DESC
}

/**
 * UI State for Home/Accounts screen
 */
data class HomeUiState(
    val accounts: List<Account> = emptyList(),
    val currentAccount: Account? = null,

    // Search and Sort
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.LAST_PLAYED,
    val sortDirection: SortDirection = SortDirection.DESC,

    // Statistics
    val totalCount: Int = 0,
    val errorCount: Int = 0,
    val susCount: Int = 0,

    // Loading and Error states
    val isLoading: Boolean = false,
    val errorMessage: String? = null,

    // Dialog states
    val showBackupDialog: Boolean = false,
    val backupResult: BackupResult? = null,
    val restoreResult: RestoreResult? = null,
    val showRestoreConfirm: Long? = null,
    val showRestoreSuccessDialog: Boolean = false,
    val duplicateUserIdDialog: DuplicateUserIdInfo? = null,

    // Prefix
    val accountPrefix: String = "MGO_"
)

data class DuplicateUserIdInfo(
    val userId: String,
    val existingAccountName: String,
    val pendingRequest: BackupRequest
)

@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountRepository: AccountRepository,
    private val backupRepository: BackupRepository,
    private val appStateRepository: AppStateRepository,
    private val logRepository: LogRepository,
    private val restoreBackupUseCase: RestoreBackupUseCase,
    private val rootUtil: RootUtil
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        loadInitialState()
        observeAccounts()
        loadStatistics()
        observeSearchQuery()
    }

    /**
     * Load initial state from DataStore
     */
    private fun loadInitialState() {
        viewModelScope.launch {
            val savedQuery = appStateRepository.getLastSearchQuery() ?: ""
            val savedSortOption = try {
                SortOption.valueOf(appStateRepository.getSortOption())
            } catch (e: Exception) {
                SortOption.LAST_PLAYED
            }
            val savedSortDirection = try {
                SortDirection.valueOf(appStateRepository.getSortDirection())
            } catch (e: Exception) {
                SortDirection.DESC
            }
            val prefix = appStateRepository.getDefaultPrefix() ?: "MGO_"

            _searchQuery.value = savedQuery
            _uiState.update {
                it.copy(
                    searchQuery = savedQuery,
                    sortOption = savedSortOption,
                    sortDirection = savedSortDirection,
                    accountPrefix = prefix
                )
            }
        }
    }

    /**
     * Observe accounts with search and sort applied
     */
    private fun observeAccounts() {
        viewModelScope.launch {
            combine(
                accountRepository.getAllAccounts(),
                _searchQuery,
                _uiState.map { Pair(it.sortOption, it.sortDirection) }.distinctUntilChanged()
            ) { accounts, query, (sortOption, sortDirection) ->
                Triple(accounts, query, Pair(sortOption, sortDirection))
            }.collect { (accounts, query, sortPair) ->
                val filtered = filterAccounts(accounts, query)
                val sorted = sortAccounts(filtered, sortPair.first, sortPair.second)
                val currentAccount = findCurrentAccount(accounts)

                _uiState.update {
                    it.copy(
                        accounts = sorted,
                        currentAccount = currentAccount,
                        searchQuery = query
                    )
                }
            }
        }
    }

    /**
     * Observe search query changes and persist with debounce
     */
    private fun observeSearchQuery() {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .collect { query ->
                    appStateRepository.setLastSearchQuery(query)
                }
        }
    }

    /**
     * Filter accounts by search query
     */
    private fun filterAccounts(accounts: List<Account>, query: String): List<Account> {
        if (query.isBlank()) return accounts
        val lowerQuery = query.lowercase()
        return accounts.filter { account ->
            account.accountName.lowercase().contains(lowerQuery) ||
                    account.userId.lowercase().contains(lowerQuery)
        }
    }

    /**
     * Sort accounts based on sort option and direction
     */
    private fun sortAccounts(
        accounts: List<Account>,
        sortOption: SortOption,
        sortDirection: SortDirection
    ): List<Account> {
        val sorted = when (sortOption) {
            SortOption.NAME -> accounts.sortedBy { it.accountName.lowercase() }
            SortOption.LAST_PLAYED -> accounts.sortedBy { it.lastPlayedAt }
            SortOption.CREATED_AT -> accounts.sortedBy { it.createdAt }
            SortOption.USER_ID -> accounts.sortedBy { it.userId }
        }
        return if (sortDirection == SortDirection.DESC) sorted.reversed() else sorted
    }

    /**
     * Find the current account (newest lastPlayedAt > 0)
     */
    private fun findCurrentAccount(accounts: List<Account>): Account? {
        return accounts
            .filter { it.lastPlayedAt > 0 }
            .maxByOrNull { it.lastPlayedAt }
    }

    /**
     * Load statistics from repository
     */
    private fun loadStatistics() {
        viewModelScope.launch {
            combine(
                accountRepository.getAccountCount(),
                accountRepository.getErrorCount(),
                accountRepository.getSusCount()
            ) { total, error, sus ->
                Triple(total, error, sus)
            }.collect { (total, error, sus) ->
                _uiState.update {
                    it.copy(
                        totalCount = total,
                        errorCount = error,
                        susCount = sus
                    )
                }
            }
        }
    }

    // ============================================================
    // Search & Sort Actions
    // ============================================================

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSortOptionChange(option: SortOption) {
        viewModelScope.launch {
            appStateRepository.setSortOption(option.name)
            _uiState.update { it.copy(sortOption = option) }
        }
    }

    fun toggleSortDirection() {
        viewModelScope.launch {
            val newDirection = if (_uiState.value.sortDirection == SortDirection.ASC) {
                SortDirection.DESC
            } else {
                SortDirection.ASC
            }
            appStateRepository.setSortDirection(newDirection.name)
            _uiState.update { it.copy(sortDirection = newDirection) }
        }
    }

    // ============================================================
    // Launch Monopoly Go with Account State (C2)
    // ============================================================

    /**
     * Launch Monopoly GO with the specified account's state
     * 1. Set SSAID
     * 2. Copy account data to /data/data/com.scopely.monopolygo/
     * 3. Start Monopoly GO
     */
    fun launchMonopolyGoWithAccountState(accountId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                // 1. Load account from DB
                val account = accountRepository.getAccountById(accountId)
                if (account == null) {
                    showErrorToast("Account nicht gefunden")
                    logRepository.logError("LAUNCH_MGO", "Account mit ID $accountId nicht gefunden")
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }

                logRepository.logInfo("LAUNCH_MGO", "Starte Monopoly GO mit Account: ${account.accountName}")

                val restoreResult = restoreBackupUseCase.execute(accountId)

                when (restoreResult) {
                    is RestoreResult.Success -> {
                        logRepository.logInfo("LAUNCH_MGO", "Monopoly GO erfolgreich gestartet")
                    }
                    is RestoreResult.Failure -> {
                        showErrorToast("Da lief etwas schief .. Prüfe den Log.")
                        logRepository.logError("LAUNCH_MGO", "Restore fehlgeschlagen: ${restoreResult.error}")
                    }
                }

                _uiState.update { it.copy(isLoading = false) }

            } catch (e: Exception) {
                showErrorToast("Da lief etwas schief .. Prüfe den Log.")
                logRepository.logError("LAUNCH_MGO", "Unerwarteter Fehler", exception = e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun showErrorToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    // ============================================================
    // Backup Dialog Actions
    // ============================================================

    fun showBackupDialog() {
        _uiState.update { it.copy(showBackupDialog = true) }
    }

    fun hideBackupDialog() {
        _uiState.update { it.copy(showBackupDialog = false, backupResult = null) }
    }

    fun createBackup(
        accountName: String,
        hasFacebookLink: Boolean,
        fbUsername: String? = null,
        fbPassword: String? = null,
        fb2FA: String? = null,
        fbTempMail: String? = null,
        forceDuplicate: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val prefix = appStateRepository.getDefaultPrefix() ?: "MGO_"
            val backupPath = appStateRepository.getBackupDirectory() ?: "/storage/emulated/0/bgo_backups/"

            val request = BackupRequest(
                accountName = accountName,
                prefix = prefix,
                backupRootPath = backupPath,
                hasFacebookLink = hasFacebookLink,
                fbUsername = fbUsername,
                fbPassword = fbPassword,
                fb2FA = fb2FA,
                fbTempMail = fbTempMail
            )

            val result = backupRepository.createBackup(request, forceDuplicate)

            if (result is BackupResult.DuplicateUserId) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        showBackupDialog = false,
                        duplicateUserIdDialog = DuplicateUserIdInfo(
                            userId = result.userId,
                            existingAccountName = result.existingAccountName,
                            pendingRequest = request
                        )
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        backupResult = result,
                        showBackupDialog = false
                    )
                }
            }
        }
    }

    fun confirmDuplicateBackup() {
        val info = _uiState.value.duplicateUserIdDialog ?: return
        _uiState.update { it.copy(duplicateUserIdDialog = null) }
        createBackup(
            accountName = info.pendingRequest.accountName,
            hasFacebookLink = info.pendingRequest.hasFacebookLink,
            fbUsername = info.pendingRequest.fbUsername,
            fbPassword = info.pendingRequest.fbPassword,
            fb2FA = info.pendingRequest.fb2FA,
            fbTempMail = info.pendingRequest.fbTempMail,
            forceDuplicate = true
        )
    }

    fun cancelDuplicateBackup() {
        _uiState.update { it.copy(duplicateUserIdDialog = null) }
    }

    fun clearBackupResult() {
        _uiState.update { it.copy(backupResult = null) }
    }

    // ============================================================
    // Restore Actions
    // ============================================================

    fun showRestoreConfirm(accountId: Long) {
        _uiState.update { it.copy(showRestoreConfirm = accountId) }
    }

    fun hideRestoreConfirm() {
        _uiState.update { it.copy(showRestoreConfirm = null) }
    }

    fun restoreAccount(accountId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showRestoreConfirm = null) }

            val result = backupRepository.restoreBackup(accountId)
            val isSuccess = result is RestoreResult.Success
            _uiState.update {
                it.copy(
                    isLoading = false,
                    restoreResult = if (!isSuccess) result else null,
                    showRestoreSuccessDialog = isSuccess
                )
            }
        }
    }

    fun clearRestoreResult() {
        _uiState.update { it.copy(restoreResult = null) }
    }

    fun hideRestoreSuccessDialog() {
        _uiState.update { it.copy(showRestoreSuccessDialog = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
