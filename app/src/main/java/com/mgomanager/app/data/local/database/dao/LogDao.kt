package com.mgomanager.app.data.local.database.dao

import androidx.room.*
import com.mgomanager.app.data.local.database.entities.LogEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Log operations
 */
@Dao
interface LogDao {

    @Query("SELECT * FROM logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<LogEntity>>

    @Query("SELECT * FROM logs WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getLogsBySession(sessionId: String): List<LogEntity>

    @Query("SELECT DISTINCT sessionId FROM logs ORDER BY timestamp DESC LIMIT 5")
    suspend fun getLastFiveSessions(): List<String>

    @Query("""
        SELECT * FROM logs
        WHERE sessionId IN (
            SELECT DISTINCT sessionId FROM logs
            ORDER BY timestamp DESC
            LIMIT 5
        )
        ORDER BY timestamp DESC
    """)
    fun getLastFiveSessionsLogsFlow(): Flow<List<LogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<LogEntity>)

    @Query("DELETE FROM logs WHERE sessionId NOT IN (SELECT DISTINCT sessionId FROM logs ORDER BY timestamp DESC LIMIT 5)")
    suspend fun deleteOldSessions()

    @Query("DELETE FROM logs")
    suspend fun deleteAllLogs()

    @Query("SELECT COUNT(DISTINCT sessionId) FROM logs")
    suspend fun getSessionCount(): Int
}
