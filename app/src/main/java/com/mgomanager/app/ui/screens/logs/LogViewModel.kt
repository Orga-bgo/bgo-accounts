package com.mgomanager.app.ui.screens.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mgomanager.app.data.local.database.entities.LogEntity
import com.mgomanager.app.data.repository.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionLogs(
    val sessionId: String,
    val logs: List<LogEntity>
)

data class LogUiState(
    val sessionLogs: List<SessionLogs> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class LogViewModel @Inject constructor(
    private val logRepository: LogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogUiState())
    val uiState: StateFlow<LogUiState> = _uiState.asStateFlow()

    init {
        loadLogs()
    }

    private fun loadLogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            logRepository.getLastFiveSessionsLogs()
                .map { logs ->
                    // Group logs by session
                    logs.groupBy { it.sessionId }
                        .map { (sessionId, sessionLogs) ->
                            SessionLogs(sessionId, sessionLogs.sortedBy { it.timestamp })
                        }
                        .sortedByDescending { it.logs.firstOrNull()?.timestamp ?: 0 }
                }
                .collect { sessionLogs ->
                    _uiState.update { it.copy(sessionLogs = sessionLogs, isLoading = false) }
                }
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            logRepository.deleteAllLogs()
        }
    }
}
