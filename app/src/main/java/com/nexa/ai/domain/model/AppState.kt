package com.nexa.ai.domain.model

import com.nexa.ai.core.error.AppError

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/** Session-level authentication state consumed by the root navigation graph. */
sealed interface AuthState {
    data object Checking : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(
        val username: String?,
        val expiresAt: Long?,
    ) : AuthState
}

/** Result of probing `GET /health` from the login screen or Settings. */
sealed interface ConnectionStatus {
    data object Idle : ConnectionStatus
    data object Checking : ConnectionStatus
    data class Online(val health: HealthInfo) : ConnectionStatus
    data class Failed(val error: AppError) : ConnectionStatus
}
