package com.mgomanager.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mgomanager.app.data.repository.AppStateRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Integration tests for App Start flow
 * Tests the complete flow from first launch through onboarding to normal operation
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppStartIntegrationTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var appStateRepository: AppStateRepository

    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun firstLaunch_shouldShowOnboarding() = runTest {
        // Clear any existing state
        appStateRepository.clearAll()

        // Verify first launch conditions
        assertTrue("Should be first launch", appStateRepository.isFirstLaunch())
        assertFalse("Onboarding should not be completed", appStateRepository.isOnboardingCompleted())
    }

    @Test
    fun afterOnboardingComplete_shouldShowSystemCheck() = runTest {
        // Clear and then complete onboarding
        appStateRepository.clearAll()
        appStateRepository.setOnboardingCompleted(true)

        // Verify state
        assertFalse("Should not be first launch", appStateRepository.isFirstLaunch())
        assertTrue("Onboarding should be completed", appStateRepository.isOnboardingCompleted())
    }

    @Test
    fun prefixSetting_shouldPersist() = runTest {
        val testPrefix = "TEST_"

        appStateRepository.setDefaultPrefix(testPrefix)

        assertEquals(testPrefix, appStateRepository.getDefaultPrefix())
    }

    @Test
    fun backupDirectorySetting_shouldPersist() = runTest {
        val testPath = "/storage/emulated/0/test_backups/"

        appStateRepository.setBackupDirectory(testPath)

        assertEquals(testPath, appStateRepository.getBackupDirectory())
    }

    @Test
    fun sshConfig_shouldPersistAllFields() = runTest {
        val host = "192.168.1.100"
        val port = 2222
        val username = "testuser"
        val password = "testpass"

        appStateRepository.setSshConfig(
            enabled = true,
            host = host,
            port = port,
            username = username,
            password = password
        )

        val config = appStateRepository.getSshConfig()
        assertTrue(config.enabled)
        assertEquals(host, config.host)
        assertEquals(port, config.port)
        assertEquals(username, config.username)
        assertEquals(password, config.password)
    }

    @Test
    fun systemStatus_shouldPersistAllFields() = runTest {
        val rootGranted = true
        val dataDataPermissions = true
        val monopolyGoInstalled = true
        val monopolyGoUid = 10123

        appStateRepository.setSystemStatus(
            rootGranted = rootGranted,
            dataDataPermissions = dataDataPermissions,
            monopolyGoInstalled = monopolyGoInstalled,
            monopolyGoUid = monopolyGoUid
        )

        assertTrue(appStateRepository.isRootAccessGranted())
        assertTrue(appStateRepository.hasDataDataPermissions())
        assertTrue(appStateRepository.getMonopolyGoInstalled())
        assertEquals(monopolyGoUid, appStateRepository.getMonopolyGoUid())
    }

    @Test
    fun monopolyGoUid_shouldUpdateWhenChanged() = runTest {
        val oldUid = 10123
        val newUid = 10456

        appStateRepository.setMonopolyGoUid(oldUid)
        assertEquals(oldUid, appStateRepository.getMonopolyGoUid())

        appStateRepository.setMonopolyGoUid(newUid)
        assertEquals(newUid, appStateRepository.getMonopolyGoUid())
    }

    @Test
    fun lastSystemCheckTimestamp_shouldPersist() = runTest {
        val timestamp = System.currentTimeMillis()

        appStateRepository.setLastSystemCheckTimestamp(timestamp)

        assertEquals(timestamp, appStateRepository.getLastSystemCheckTimestamp())
    }

    @Test
    fun sortSettings_shouldPersist() = runTest {
        val sortOption = "accountName"
        val sortDirection = "ASC"

        appStateRepository.setSortOption(sortOption)
        appStateRepository.setSortDirection(sortDirection)

        assertEquals(sortOption, appStateRepository.getSortOption())
        assertEquals(sortDirection, appStateRepository.getSortDirection())
    }

    @Test
    fun lastSearchQuery_shouldPersist() = runTest {
        val query = "test search"

        appStateRepository.setLastSearchQuery(query)

        assertEquals(query, appStateRepository.getLastSearchQuery())
    }

    @Test
    fun clearAll_shouldResetAllSettings() = runTest {
        // Set various settings
        appStateRepository.setOnboardingCompleted(true)
        appStateRepository.setDefaultPrefix("TEST_")
        appStateRepository.setBackupDirectory("/test/path")
        appStateRepository.setSshConfig(true, "host", 22, "user", "pass")
        appStateRepository.setSystemStatus(true, true, true, 10123)

        // Clear all
        appStateRepository.clearAll()

        // Verify defaults
        assertTrue(appStateRepository.isFirstLaunch())
        assertFalse(appStateRepository.isOnboardingCompleted())
        assertNull(appStateRepository.getDefaultPrefix())
        assertNull(appStateRepository.getBackupDirectory())
        assertFalse(appStateRepository.isSshEnabled())
    }

    @Test
    fun onboardingSteps_shouldTrackProgress() = runTest {
        appStateRepository.setOnboardingStep(0)
        assertEquals(0, appStateRepository.getOnboardingStep())

        appStateRepository.setOnboardingStep(3)
        assertEquals(3, appStateRepository.getOnboardingStep())

        appStateRepository.setOnboardingStep(6)
        assertEquals(6, appStateRepository.getOnboardingStep())
    }
}
