package com.mgomanager.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appStateDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_state")

/**
 * Repository for managing app state via DataStore
 * Handles onboarding status, system checks, and configuration
 */
@Singleton
class AppStateRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Onboarding Keys
        val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val ONBOARDING_STEP = intPreferencesKey("onboarding_step")

        // Setup Keys
        val DEFAULT_PREFIX = stringPreferencesKey("default_prefix")
        val BACKUP_DIRECTORY = stringPreferencesKey("backup_directory")

        // SSH Keys
        val SSH_ENABLED = booleanPreferencesKey("ssh_enabled")
        val SSH_HOST = stringPreferencesKey("ssh_host")
        val SSH_PORT = intPreferencesKey("ssh_port")
        val SSH_USERNAME = stringPreferencesKey("ssh_username")
        val SSH_PASSWORD = stringPreferencesKey("ssh_password")

        // System Status Keys
        val MONOPOLY_GO_UID = intPreferencesKey("monopoly_go_uid")
        val MONOPOLY_GO_INSTALLED = booleanPreferencesKey("monopoly_go_installed")
        val ROOT_ACCESS_GRANTED = booleanPreferencesKey("root_access_granted")
        val DATA_DATA_PERMISSIONS = booleanPreferencesKey("data_data_permissions")
        val LAST_SYSTEM_CHECK = longPreferencesKey("last_system_check")

        // Search & Sort Keys (for P2)
        val LAST_SEARCH_QUERY = stringPreferencesKey("last_search_query")
        val ACCOUNTS_SORT_OPTION = stringPreferencesKey("accounts_sort_option")
        val ACCOUNTS_SORT_DIRECTION = stringPreferencesKey("accounts_sort_direction")
    }

    private val dataStore = context.appStateDataStore

    // ============================================================
    // Onboarding
    // ============================================================

    suspend fun isFirstLaunch(): Boolean {
        return dataStore.data.first()[IS_FIRST_LAUNCH] ?: true
    }

    fun isFirstLaunchFlow(): Flow<Boolean> {
        return dataStore.data.map { it[IS_FIRST_LAUNCH] ?: true }
    }

    suspend fun setFirstLaunch(isFirst: Boolean) {
        dataStore.edit { it[IS_FIRST_LAUNCH] = isFirst }
    }

    suspend fun isOnboardingCompleted(): Boolean {
        return dataStore.data.first()[ONBOARDING_COMPLETED] ?: false
    }

    fun isOnboardingCompletedFlow(): Flow<Boolean> {
        return dataStore.data.map { it[ONBOARDING_COMPLETED] ?: false }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit {
            it[ONBOARDING_COMPLETED] = completed
            if (completed) {
                it[IS_FIRST_LAUNCH] = false
            }
        }
    }

    suspend fun getOnboardingStep(): Int {
        return dataStore.data.first()[ONBOARDING_STEP] ?: 0
    }

    suspend fun setOnboardingStep(step: Int) {
        dataStore.edit { it[ONBOARDING_STEP] = step }
    }

    // ============================================================
    // Setup - Prefix
    // ============================================================

    suspend fun getDefaultPrefix(): String? {
        return dataStore.data.first()[DEFAULT_PREFIX]
    }

    fun getDefaultPrefixFlow(): Flow<String?> {
        return dataStore.data.map { it[DEFAULT_PREFIX] }
    }

    suspend fun setDefaultPrefix(prefix: String) {
        dataStore.edit { it[DEFAULT_PREFIX] = prefix }
    }

    // ============================================================
    // Setup - Backup Directory
    // ============================================================

    suspend fun getBackupDirectory(): String? {
        return dataStore.data.first()[BACKUP_DIRECTORY]
    }

    fun getBackupDirectoryFlow(): Flow<String?> {
        return dataStore.data.map { it[BACKUP_DIRECTORY] }
    }

    suspend fun setBackupDirectory(directory: String) {
        dataStore.edit { it[BACKUP_DIRECTORY] = directory }
    }

    // ============================================================
    // SSH Configuration
    // ============================================================

    suspend fun isSshEnabled(): Boolean {
        return dataStore.data.first()[SSH_ENABLED] ?: false
    }

    fun isSshEnabledFlow(): Flow<Boolean> {
        return dataStore.data.map { it[SSH_ENABLED] ?: false }
    }

    suspend fun setSshEnabled(enabled: Boolean) {
        dataStore.edit { it[SSH_ENABLED] = enabled }
    }

    suspend fun getSshHost(): String? {
        return dataStore.data.first()[SSH_HOST]
    }

    suspend fun getSshPort(): Int {
        return dataStore.data.first()[SSH_PORT] ?: 22
    }

    suspend fun getSshUsername(): String? {
        return dataStore.data.first()[SSH_USERNAME]
    }

    suspend fun getSshPassword(): String? {
        return dataStore.data.first()[SSH_PASSWORD]
    }

    suspend fun setSshConfig(
        enabled: Boolean,
        host: String,
        port: Int,
        username: String,
        password: String
    ) {
        dataStore.edit { prefs ->
            prefs[SSH_ENABLED] = enabled
            prefs[SSH_HOST] = host
            prefs[SSH_PORT] = port
            prefs[SSH_USERNAME] = username
            prefs[SSH_PASSWORD] = password
        }
    }

    data class SshConfig(
        val enabled: Boolean,
        val host: String?,
        val port: Int,
        val username: String?,
        val password: String?
    )

    suspend fun getSshConfig(): SshConfig {
        val prefs = dataStore.data.first()
        return SshConfig(
            enabled = prefs[SSH_ENABLED] ?: false,
            host = prefs[SSH_HOST],
            port = prefs[SSH_PORT] ?: 22,
            username = prefs[SSH_USERNAME],
            password = prefs[SSH_PASSWORD]
        )
    }

    // ============================================================
    // System Status
    // ============================================================

    suspend fun getMonopolyGoUid(): Int? {
        return dataStore.data.first()[MONOPOLY_GO_UID]
    }

    suspend fun setMonopolyGoUid(uid: Int) {
        dataStore.edit { it[MONOPOLY_GO_UID] = uid }
    }

    suspend fun getMonopolyGoInstalled(): Boolean {
        return dataStore.data.first()[MONOPOLY_GO_INSTALLED] ?: false
    }

    fun getMonopolyGoInstalledFlow(): Flow<Boolean> {
        return dataStore.data.map { it[MONOPOLY_GO_INSTALLED] ?: false }
    }

    suspend fun setMonopolyGoInstalled(installed: Boolean) {
        dataStore.edit { it[MONOPOLY_GO_INSTALLED] = installed }
    }

    suspend fun isRootAccessGranted(): Boolean {
        return dataStore.data.first()[ROOT_ACCESS_GRANTED] ?: false
    }

    suspend fun setRootAccessGranted(granted: Boolean) {
        dataStore.edit { it[ROOT_ACCESS_GRANTED] = granted }
    }

    suspend fun hasDataDataPermissions(): Boolean {
        return dataStore.data.first()[DATA_DATA_PERMISSIONS] ?: false
    }

    suspend fun setDataDataPermissions(granted: Boolean) {
        dataStore.edit { it[DATA_DATA_PERMISSIONS] = granted }
    }

    suspend fun setSystemStatus(
        rootGranted: Boolean,
        dataDataPermissions: Boolean,
        monopolyGoInstalled: Boolean,
        monopolyGoUid: Int?
    ) {
        dataStore.edit { prefs ->
            prefs[ROOT_ACCESS_GRANTED] = rootGranted
            prefs[DATA_DATA_PERMISSIONS] = dataDataPermissions
            prefs[MONOPOLY_GO_INSTALLED] = monopolyGoInstalled
            monopolyGoUid?.let { prefs[MONOPOLY_GO_UID] = it }
        }
    }

    suspend fun getLastSystemCheckTimestamp(): Long {
        return dataStore.data.first()[LAST_SYSTEM_CHECK] ?: 0L
    }

    suspend fun setLastSystemCheckTimestamp(timestamp: Long) {
        dataStore.edit { it[LAST_SYSTEM_CHECK] = timestamp }
    }

    // ============================================================
    // Search & Sort (for P2)
    // ============================================================

    suspend fun getLastSearchQuery(): String? {
        return dataStore.data.first()[LAST_SEARCH_QUERY]
    }

    fun getLastSearchQueryFlow(): Flow<String?> {
        return dataStore.data.map { it[LAST_SEARCH_QUERY] }
    }

    suspend fun setLastSearchQuery(query: String) {
        dataStore.edit { it[LAST_SEARCH_QUERY] = query }
    }

    suspend fun getSortOption(): String {
        return dataStore.data.first()[ACCOUNTS_SORT_OPTION] ?: "lastPlayedAt"
    }

    fun getSortOptionFlow(): Flow<String> {
        return dataStore.data.map { it[ACCOUNTS_SORT_OPTION] ?: "lastPlayedAt" }
    }

    suspend fun setSortOption(option: String) {
        dataStore.edit { it[ACCOUNTS_SORT_OPTION] = option }
    }

    suspend fun getSortDirection(): String {
        return dataStore.data.first()[ACCOUNTS_SORT_DIRECTION] ?: "DESC"
    }

    fun getSortDirectionFlow(): Flow<String> {
        return dataStore.data.map { it[ACCOUNTS_SORT_DIRECTION] ?: "DESC" }
    }

    suspend fun setSortDirection(direction: String) {
        dataStore.edit { it[ACCOUNTS_SORT_DIRECTION] = direction }
    }

    // ============================================================
    // Clear All Data (for testing/reset)
    // ============================================================

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}
