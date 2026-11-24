package com.nexa.ai.presentation.agents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexa.ai.core.error.AppError
import com.nexa.ai.core.result.AppResult
import com.nexa.ai.domain.model.AgentInfo
import com.nexa.ai.domain.repository.AgentRepository
import com.nexa.ai.domain.repository.SettingsRepository
import com.nexa.ai.domain.usecase.RefreshCatalogUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AgentUiState(
    val agents: List<AgentInfo> = emptyList(),
    val selectedAgentId: String? = null,
    val isLoading: Boolean = true,
    val error: AppError? = null,
) {
    /** `build` stays the default whenever it exists, matching the API's own default. */
    val effectiveSelectedId: String
        get() = selectedAgentId ?: agents.firstOrNull { it.id == "build" }?.id ?: "build"

    val selectedAgent: AgentInfo?
        get() = agents.firstOrNull { it.id == effectiveSelectedId }
}

@HiltViewModel
class AgentSelectionViewModel @Inject constructor(
    private val agentRepository: AgentRepository,
    private val settingsRepository: SettingsRepository,
    private val refreshCatalogUseCase: RefreshCatalogUseCase,
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    private val _error = MutableStateFlow<AppError?>(null)

    val uiState: StateFlow<AgentUiState> = combine(
        agentRepository.agents,
        settingsRepository.selectedAgentId,
        _isLoading,
        _error,
    ) { agents, selectedId, loading, error ->
        AgentUiState(
            agents = agents,
            selectedAgentId = selectedId,
            isLoading = loading,
            error = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AgentUiState(),
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = refreshCatalogUseCase()
            _error.value = (result as? AppResult.Failure)?.error
            _isLoading.value = false
        }
    }

    fun select(agentId: String) {
        viewModelScope.launch { settingsRepository.selectAgent(agentId) }
    }

    fun clearError() {
        _error.value = null
    }
}
