package com.mgomanager.app.ui.screens.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mgomanager.app.data.local.database.entities.LogEntity
import com.mgomanager.app.data.repository.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogUiState(
    val logs: List<LogEntity> = emptyList(),
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

            logRepository.getAllLogs()
                .collect { logs ->
                    _uiState.update { it.copy(logs = logs.sortedByDescending { it.timestamp }, isLoading = false) }
                }
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            logRepository.deleteAllLogs()
        }
    }
}
