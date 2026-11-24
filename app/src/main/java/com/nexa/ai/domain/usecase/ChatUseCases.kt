package com.nexa.ai.domain.usecase

import com.nexa.ai.core.result.AppResult
import com.nexa.ai.domain.model.ChatMessage
import com.nexa.ai.domain.model.ChatSession
import com.nexa.ai.domain.model.ModelInfo
import com.nexa.ai.domain.polling.PollResult
import com.nexa.ai.domain.repository.AgentRepository
import com.nexa.ai.domain.repository.SessionRepository
import javax.inject.Inject

/** Delivers a user turn to the real OpenCode agent via the gateway. */
class SendMessageUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    suspend operator fun invoke(
        sessionId: String,
        text: String,
        agent: String?,
        model: ModelInfo?,
    ): AppResult<Unit> = sessionRepository.sendMessage(sessionId, text, agent, model)
}

/** Polls `GET /sessions/active` + `GET /sessions/{id}/messages` until the run settles. */
class PollSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    suspend operator fun invoke(sessionId: String, knownMessageIds: Set<String>): PollResult =
        sessionRepository.pollForResponse(sessionId, knownMessageIds)
}

class InterruptSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    suspend operator fun invoke(sessionId: String): AppResult<Unit> =
        sessionRepository.interrupt(sessionId)
}

class CreateSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    suspend operator fun invoke(title: String): AppResult<ChatSession> =
        sessionRepository.createSession(title)
}

class RefreshMessagesUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    suspend operator fun invoke(sessionId: String): AppResult<List<ChatMessage>> =
        sessionRepository.refreshMessages(sessionId)
}

class RefreshSessionsUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    suspend operator fun invoke() = sessionRepository.refreshSessions()
}

class DeleteSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    suspend operator fun invoke(sessionId: String) = sessionRepository.deleteSession(sessionId)
}

class ClearLocalCacheUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    suspend operator fun invoke() = sessionRepository.clearLocalCache()
}

class MarkSessionSettledUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    /** Marks in-flight assistant messages as complete once the backend reports an idle session. */
    suspend operator fun invoke(sessionId: String) =
        sessionRepository.markAssistantSettled(sessionId)
}

class RefreshCatalogUseCase @Inject constructor(
    private val agentRepository: AgentRepository,
) {
    suspend operator fun invoke() = agentRepository.refresh()
}
