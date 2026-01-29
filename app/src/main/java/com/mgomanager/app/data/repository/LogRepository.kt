package com.mgomanager.app.data.repository

import com.mgomanager.app.data.local.database.dao.LogDao
import com.mgomanager.app.data.local.database.entities.LogEntity
import com.mgomanager.app.data.local.preferences.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Log-related operations
 */
@Singleton
class LogRepository @Inject constructor(
    private val logDao: LogDao,
    private val settingsDataStore: SettingsDataStore
) {

    /**
     * Get all logs
     */
    fun getAllLogs(): Flow<List<LogEntity>> {
        return logDao.getAllLogsFlow()
    }

    /**
     * Get logs from last 5 sessions
     */
    fun getLastFiveSessionsLogs(): Flow<List<LogEntity>> {
        return logDao.getLastFiveSessionsLogsFlow()
    }

    /**
     * Get logs for specific session
     */
    suspend fun getLogsBySession(sessionId: String): List<LogEntity> {
        return logDao.getLogsBySession(sessionId)
    }

    /**
     * Add a new log entry
     */
    suspend fun addLog(
        level: String,
        operation: String,
        message: String,
        accountName: String? = null,
        stackTrace: String? = null
    ) {
        val sessionId = settingsDataStore.currentSessionId.first()
        val log = LogEntity(
            sessionId = sessionId,
            timestamp = System.currentTimeMillis(),
            level = level,
            operation = operation,
            accountName = accountName,
            message = message,
            stackTrace = stackTrace
        )
        logDao.insertLog(log)
    }

    /**
     * Add info log
     */
    suspend fun logInfo(operation: String, message: String, accountName: String? = null) {
        addLog("INFO", operation, message, accountName)
    }

    /**
     * Add warning log
     */
    suspend fun logWarning(operation: String, message: String, accountName: String? = null) {
        addLog("WARNING", operation, message, accountName)
    }

    /**
     * Add error log
     */
    suspend fun logError(
        operation: String,
        message: String,
        accountName: String? = null,
        exception: Exception? = null
    ) {
        addLog("ERROR", operation, message, accountName, exception?.stackTraceToString())
    }

    /**
     * Clean up old sessions (keep only last 5)
     */
    suspend fun cleanupOldSessions() {
        logDao.deleteOldSessions()
    }

    /**
     * Delete all logs
     */
    suspend fun deleteAllLogs() {
        logDao.deleteAllLogs()
    }
}
