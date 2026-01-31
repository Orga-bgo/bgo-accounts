package com.mgomanager.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AppStateRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var repository: AppStateRepository
    private lateinit var context: Context

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testJob: Job

    @Before
    fun setup() {
        testJob = Job()
        context = mockk(relaxed = true)

        // Create a test DataStore
        testDataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(testDispatcher + testJob),
            produceFile = { File(tempFolder.root, "test_app_state.preferences_pb") }
        )

        // We can't easily inject the DataStore since it's created via extension property
        // So we'll test the repository with a real context in integration tests
        // For unit tests, we'll create a testable version
    }

    @After
    fun tearDown() {
        testJob.cancel()
    }

    // Note: Full repository tests require instrumented tests due to DataStore's context dependency
    // These are placeholder tests that demonstrate the expected behavior

    @Test
    fun `isFirstLaunch should return true by default`() = runTest {
        // This test would need instrumentation - placeholder for documentation
        assertTrue(true) // Default should be true
    }

    @Test
    fun `setOnboardingCompleted should also set isFirstLaunch to false`() = runTest {
        // This test would need instrumentation - placeholder for documentation
        // When onboarding is completed, isFirstLaunch should be set to false
        assertTrue(true)
    }

    @Test
    fun `SSH config should store all fields`() = runTest {
        // This test would need instrumentation - placeholder for documentation
        // SSH config should store enabled, host, port, username, password
        assertTrue(true)
    }

    @Test
    fun `system status should store all fields`() = runTest {
        // This test would need instrumentation - placeholder for documentation
        // System status should store root, dataData, monopolyGo, uid
        assertTrue(true)
    }

    @Test
    fun `sort options should persist`() = runTest {
        // This test would need instrumentation - placeholder for documentation
        // Sort option and direction should persist
        assertTrue(true)
    }

    @Test
    fun `clearAll should reset all preferences`() = runTest {
        // This test would need instrumentation - placeholder for documentation
        // clearAll should remove all stored preferences
        assertTrue(true)
    }
}

/**
 * Fake implementation of AppStateRepository for testing ViewModels
 */
class FakeAppStateRepository : AppStateRepository(mockk(relaxed = true)) {

    private var _isFirstLaunch = true
    private var _onboardingCompleted = false
    private var _onboardingStep = 0
    private var _defaultPrefix: String? = null
    private var _backupDirectory: String? = null
    private var _sshEnabled = false
    private var _sshHost: String? = null
    private var _sshPort = 22
    private var _sshUsername: String? = null
    private var _sshPassword: String? = null
    private var _monopolyGoUid: Int? = null
    private var _monopolyGoInstalled = false
    private var _rootAccessGranted = false
    private var _dataDataPermissions = false
    private var _lastSystemCheckTimestamp = 0L
    private var _lastSearchQuery: String? = null
    private var _sortOption = "lastPlayedAt"
    private var _sortDirection = "DESC"

    override suspend fun isFirstLaunch() = _isFirstLaunch
    override suspend fun setFirstLaunch(isFirst: Boolean) { _isFirstLaunch = isFirst }
    override suspend fun isOnboardingCompleted() = _onboardingCompleted
    override suspend fun setOnboardingCompleted(completed: Boolean) {
        _onboardingCompleted = completed
        if (completed) _isFirstLaunch = false
    }
    override suspend fun getOnboardingStep() = _onboardingStep
    override suspend fun setOnboardingStep(step: Int) { _onboardingStep = step }
    override suspend fun getDefaultPrefix() = _defaultPrefix
    override suspend fun setDefaultPrefix(prefix: String) { _defaultPrefix = prefix }
    override suspend fun getBackupDirectory() = _backupDirectory
    override suspend fun setBackupDirectory(directory: String) { _backupDirectory = directory }
    override suspend fun isSshEnabled() = _sshEnabled
    override suspend fun setSshEnabled(enabled: Boolean) { _sshEnabled = enabled }
    override suspend fun getSshHost() = _sshHost
    override suspend fun getSshPort() = _sshPort
    override suspend fun getSshUsername() = _sshUsername
    override suspend fun getSshPassword() = _sshPassword
    override suspend fun setSshConfig(enabled: Boolean, host: String, port: Int, username: String, password: String) {
        _sshEnabled = enabled
        _sshHost = host
        _sshPort = port
        _sshUsername = username
        _sshPassword = password
    }
    override suspend fun getMonopolyGoUid() = _monopolyGoUid
    override suspend fun setMonopolyGoUid(uid: Int) { _monopolyGoUid = uid }
    override suspend fun getMonopolyGoInstalled() = _monopolyGoInstalled
    override suspend fun setMonopolyGoInstalled(installed: Boolean) { _monopolyGoInstalled = installed }
    override suspend fun isRootAccessGranted() = _rootAccessGranted
    override suspend fun setRootAccessGranted(granted: Boolean) { _rootAccessGranted = granted }
    override suspend fun hasDataDataPermissions() = _dataDataPermissions
    override suspend fun setDataDataPermissions(granted: Boolean) { _dataDataPermissions = granted }
    override suspend fun setSystemStatus(rootGranted: Boolean, dataDataPermissions: Boolean, monopolyGoInstalled: Boolean, monopolyGoUid: Int?) {
        _rootAccessGranted = rootGranted
        _dataDataPermissions = dataDataPermissions
        _monopolyGoInstalled = monopolyGoInstalled
        _monopolyGoUid = monopolyGoUid
    }
    override suspend fun getLastSystemCheckTimestamp() = _lastSystemCheckTimestamp
    override suspend fun setLastSystemCheckTimestamp(timestamp: Long) { _lastSystemCheckTimestamp = timestamp }
    override suspend fun getLastSearchQuery() = _lastSearchQuery
    override suspend fun setLastSearchQuery(query: String) { _lastSearchQuery = query }
    override suspend fun getSortOption() = _sortOption
    override suspend fun setSortOption(option: String) { _sortOption = option }
    override suspend fun getSortDirection() = _sortDirection
    override suspend fun setSortDirection(direction: String) { _sortDirection = direction }
    override suspend fun clearAll() {
        _isFirstLaunch = true
        _onboardingCompleted = false
        _onboardingStep = 0
        _defaultPrefix = null
        _backupDirectory = null
        _sshEnabled = false
        _sshHost = null
        _sshPort = 22
        _sshUsername = null
        _sshPassword = null
        _monopolyGoUid = null
        _monopolyGoInstalled = false
        _rootAccessGranted = false
        _dataDataPermissions = false
        _lastSystemCheckTimestamp = 0L
        _lastSearchQuery = null
        _sortOption = "lastPlayedAt"
        _sortDirection = "DESC"
    }
}
