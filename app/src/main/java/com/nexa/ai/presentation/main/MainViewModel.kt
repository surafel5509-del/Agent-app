package com.nexa.ai.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexa.ai.core.error.AppError
import com.nexa.ai.core.result.AppResult
import com.nexa.ai.domain.model.ChatSession
import com.nexa.ai.domain.repository.SessionRepository
import com.nexa.ai.domain.repository.SettingsRepository
import com.nexa.ai.domain.usecase.DeleteSessionUseCase
import com.nexa.ai.domain.usecase.RefreshCatalogUseCase
import com.nexa.ai.domain.usecase.RefreshSessionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val sessions: List<ChatSession> = emptyList(),
    val selectedSessionId: String? = null,
    val isLoading: Boolean = true,
    val error: AppError? = null,
)

/** Owns conversation history and the persisted "current conversation" pointer. */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val deleteSessionUseCase: DeleteSessionUseCase,
    private val refreshSessionsUseCase: RefreshSessionsUseCase,
    private val refreshCatalogUseCase: RefreshCatalogUseCase,
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    private val _error = MutableStateFlow<AppError?>(null)

    val uiState: StateFlow<MainUiState> = combine(
        sessionRepository.observeSessions(),
        settingsRepository.activeSessionId,
        _isLoading,
        _error,
    ) { sessions, selectedId, loading, error ->
        MainUiState(
            sessions = sessions,
            selectedSessionId = selectedId,
            isLoading = loading,
            error = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    init {
        refresh()
        viewModelScope.launch { refreshCatalogUseCase() }
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = refreshSessionsUseCase()
            _error.value = (result as? AppResult.Failure)?.error
            _isLoading.value = false
        }
    }

    fun startNewChat() {
        viewModelScope.launch { settingsRepository.selectSession(null) }
    }

    fun selectSession(sessionId: String) {
        viewModelScope.launch { settingsRepository.selectSession(sessionId) }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            deleteSessionUseCase(sessionId)
            if (uiState.value.selectedSessionId == sessionId) {
                settingsRepository.selectSession(null)
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
