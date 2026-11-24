package com.nexa.ai.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexa.ai.core.error.AppError
import com.nexa.ai.core.network.BaseUrlHolder
import com.nexa.ai.core.network.BaseUrlNormalizer
import com.nexa.ai.core.result.AppResult
import com.nexa.ai.domain.model.AgentInfo
import com.nexa.ai.domain.model.ConnectionStatus
import com.nexa.ai.domain.model.ModelInfo
import com.nexa.ai.domain.model.ThemeMode
import com.nexa.ai.domain.repository.AgentRepository
import com.nexa.ai.domain.repository.SettingsRepository
import com.nexa.ai.domain.usecase.ClearLocalCacheUseCase
import com.nexa.ai.domain.usecase.RefreshSessionsUseCase
import com.nexa.ai.domain.usecase.SignOutUseCase
import com.nexa.ai.domain.usecase.TestConnectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val apiUrl: String = "",
    val username: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val selectedAgentId: String? = null,
    val agents: List<AgentInfo> = emptyList(),
    val models: List<ModelInfo> = emptyList(),
    val selectedModel: ModelInfo? = null,
    val connection: ConnectionStatus = ConnectionStatus.Idle,
    val isClearing: Boolean = false,
    val isSigningOut: Boolean = false,
    val cacheCleared: Boolean = false,
    val urlError: AppError? = null,
) {
    val selectedAgent: AgentInfo?
        get() = agents.firstOrNull { it.id == selectedAgentId }
            ?: agents.firstOrNull { it.id == "build" }
            ?: agents.firstOrNull()

    val isConnected: Boolean
        get() = connection is ConnectionStatus.Online
}

private data class TransientState(
    val isClearing: Boolean = false,
    val isSigningOut: Boolean = false,
    val cacheCleared: Boolean = false,
    val urlError: AppError? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val agentRepository: AgentRepository,
    private val testConnectionUseCase: TestConnectionUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val clearLocalCacheUseCase: ClearLocalCacheUseCase,
    private val refreshSessionsUseCase: RefreshSessionsUseCase,
    private val baseUrlHolder: BaseUrlHolder,
) : ViewModel() {

    private val connection = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Idle)
    private val transient = MutableStateFlow(TransientState())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.preferences,
        agentRepository.agents,
        agentRepository.models,
        connection,
        transient,
    ) { preferences, agents, models, connectionState, transientState ->
        SettingsUiState(
            apiUrl = preferences.apiUrl,
            username = preferences.username,
            themeMode = preferences.themeMode,
            dynamicColor = preferences.dynamicColor,
            selectedAgentId = preferences.selectedAgentId,
            agents = agents,
            models = models,
            selectedModel = preferences.selectedModel,
            connection = connectionState,
            isClearing = transientState.isClearing,
            isSigningOut = transientState.isSigningOut,
            cacheCleared = transientState.cacheCleared,
            urlError = transientState.urlError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    init {
        testConnection()
    }

    fun testConnection() {
        viewModelScope.launch {
            connection.value = ConnectionStatus.Checking
            connection.value = when (val result = testConnectionUseCase()) {
                is AppResult.Success -> ConnectionStatus.Online(result.value)
                is AppResult.Failure -> ConnectionStatus.Failed(result.error)
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun selectAgent(agentId: String) {
        viewModelScope.launch { settingsRepository.selectAgent(agentId) }
    }

    fun selectModel(model: ModelInfo?) {
        viewModelScope.launch { settingsRepository.selectModel(model) }
    }

    /** Applies a new gateway URL after validating it, then re-probes `/health`. */
    fun saveApiUrl(rawUrl: String): Boolean {
        return when (val validation = BaseUrlNormalizer.validate(rawUrl)) {
            is BaseUrlNormalizer.Validation.Invalid -> {
                transient.update { it.copy(urlError = AppError.Validation(validation.reason.name)) }
                false
            }
            is BaseUrlNormalizer.Validation.Valid -> {
                viewModelScope.launch {
                    baseUrlHolder.update(validation.url)
                    settingsRepository.setApiUrl(validation.url)
                    transient.update { it.copy(urlError = null) }
                    testConnection()
                }
                true
            }
        }
    }

    fun clearUrlError() {
        transient.update { it.copy(urlError = null) }
    }

    fun clearCache() {
        viewModelScope.launch {
            transient.update { it.copy(isClearing = true) }
            clearLocalCacheUseCase()
            refreshSessionsUseCase()
            transient.update { it.copy(isClearing = false, cacheCleared = true) }
        }
    }

    fun consumeCacheCleared() {
        transient.update { it.copy(cacheCleared = false) }
    }

    fun signOut() {
        viewModelScope.launch {
            transient.update { it.copy(isSigningOut = true) }
            signOutUseCase()
            transient.update { it.copy(isSigningOut = false) }
        }
    }

    fun clearConnectionStatus() {
        connection.value = ConnectionStatus.Idle
    }

    fun refreshHistory() {
        viewModelScope.launch { refreshSessionsUseCase() }
    }
}
