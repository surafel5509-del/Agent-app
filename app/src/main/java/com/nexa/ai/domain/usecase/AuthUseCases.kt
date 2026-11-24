package com.nexa.ai.domain.usecase

import com.nexa.ai.core.error.AppError
import com.nexa.ai.core.result.AppResult
import com.nexa.ai.domain.model.AuthState
import com.nexa.ai.domain.model.HealthInfo
import com.nexa.ai.domain.repository.AgentRepository
import com.nexa.ai.domain.repository.AuthRepository
import com.nexa.ai.domain.repository.HealthRepository
import com.nexa.ai.domain.repository.SessionRepository
import javax.inject.Inject

/** Result of a sign-in attempt, including the (non-fatal) post-login health probe. */
sealed interface LoginOutcome {
    data class Success(
        val state: AuthState.LoggedIn,
        val health: HealthInfo?,
        val warning: AppError?,
    ) : LoginOutcome

    data class Failed(val error: AppError) : LoginOutcome
}

/**
 * Signs in, then warms the caches the chat screen needs immediately:
 * `GET /health`, `GET /agents` and `GET /sessions`.
 *
 * A failing health probe does **not** fail the login: the token may still be valid while a
 * transient gateway error occurs, so it is surfaced as a warning instead.
 */
class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val healthRepository: HealthRepository,
    private val agentRepository: AgentRepository,
    private val sessionRepository: SessionRepository,
) {
    suspend operator fun invoke(
        username: String,
        password: String,
        apiUrl: String,
    ): LoginOutcome {
        val login = authRepository.login(username, password, apiUrl)
        if (login is AppResult.Failure) return LoginOutcome.Failed(login.error)

        val health = healthRepository.check()
        agentRepository.refresh()
        sessionRepository.refreshSessions()

        return LoginOutcome.Success(
            state = login.value,
            health = health.getOrNull(),
            warning = (health as? AppResult.Failure)?.error,
        )
    }
}

class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
) {
    suspend operator fun invoke(): AppResult<Unit> {
        sessionRepository.clearLocalCache()
        return authRepository.signOut()
    }
}

class TestConnectionUseCase @Inject constructor(
    private val healthRepository: HealthRepository,
) {
    suspend operator fun invoke() = healthRepository.check()
}
