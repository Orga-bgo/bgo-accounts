package com.mgomanager.app.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.*

/**
 * Room entity for storing application logs
 */
@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sessionId: String, // UUID for each app start
    val timestamp: Long,   // Unix milliseconds
    val level: String,     // "INFO", "WARNING", "ERROR"
    val operation: String, // "BACKUP", "RESTORE", "ERROR", "APP_START"
    val accountName: String? = null,
    val message: String,
    val stackTrace: String? = null
)

/**
 * Log level enum for type safety
 */
enum class LogLevel {
    INFO,
    WARNING,
    ERROR;

    companion object {
        fun fromString(value: String): LogLevel {
            return values().find { it.name == value } ?: INFO
        }
    }
}

/**
 * Extension function to format timestamp
 */
fun LogEntity.getFormattedTimestamp(): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.GERMAN)
    return sdf.format(Date(timestamp))
}

/**
 * Extension function to get color based on level
 */
fun LogEntity.getLevelColor(): androidx.compose.ui.graphics.Color {
    return when (LogLevel.fromString(level)) {
        LogLevel.INFO -> androidx.compose.ui.graphics.Color.Gray
        LogLevel.WARNING -> com.mgomanager.app.ui.theme.StatusOrange
        LogLevel.ERROR -> com.mgomanager.app.ui.theme.StatusRed
    }
}
