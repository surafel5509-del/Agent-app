package com.nexa.ai.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexa.ai.domain.model.AuthState
import com.nexa.ai.domain.repository.AgentRepository
import com.nexa.ai.domain.repository.AuthRepository
import com.nexa.ai.domain.repository.SessionRepository
import com.nexa.ai.domain.repository.SettingsRepository
import com.nexa.ai.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RootState(
    val authState: AuthState = AuthState.Checking,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
)

@HiltViewModel
class RootViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val agentRepository: AgentRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val authState = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Checking)

    val state: StateFlow<RootState> = combine(
        authState,
        settingsRepository.themeMode,
        settingsRepository.isDynamicColorEnabled,
    ) { auth, themeMode, dynamicColor ->
        RootState(authState = auth, themeMode = themeMode, dynamicColor = dynamicColor)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RootState())

    init {
        viewModelScope.launch {
            val restored = authRepository.restore()
            if (restored is AuthState.LoggedIn) {
                // Warm the catalog and conversation list so the first screen is never empty.
                agentRepository.refresh()
                sessionRepository.refreshSessions()
            }
        }
    }
}
