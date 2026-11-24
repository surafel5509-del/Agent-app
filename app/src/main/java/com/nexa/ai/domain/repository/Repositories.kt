package com.nexa.ai.domain.repository

import com.nexa.ai.core.result.AppResult
import com.nexa.ai.domain.model.AgentInfo
import com.nexa.ai.domain.model.AuthState
import com.nexa.ai.domain.model.ChatMessage
import com.nexa.ai.domain.model.ChatSession
import com.nexa.ai.domain.model.HealthInfo
import com.nexa.ai.domain.model.MessageStatus
import com.nexa.ai.domain.model.ModelInfo
import com.nexa.ai.domain.model.ProviderInfo
import com.nexa.ai.domain.model.ThemeMode
import com.nexa.ai.domain.polling.PollResult
import kotlinx.coroutines.flow.Flow

/** Authentication, token lifecycle and session invalidation. */
interface AuthRepository {
    val authState: Flow<AuthState>

    /** Restores the persisted token (if any) and resolves the initial auth state. */
    suspend fun restore(): AuthState

    suspend fun login(username: String, password: String, apiUrl: String): AppResult<AuthState.LoggedIn>

    suspend fun signOut(): AppResult<Unit>

    val currentUsername: String?
}

/** Sessions, messages, drafts and the agent polling loop. */
interface SessionRepository {
    fun observeSessions(): Flow<List<ChatSession>>
    fun observeSession(sessionId: String): Flow<ChatSession?>
    fun observeMessages(sessionId: String): Flow<List<ChatMessage>>

    suspend fun getMessageIds(sessionId: String): Set<String>
    suspend fun getMessages(sessionId: String): List<ChatMessage>

    suspend fun refreshSessions(): AppResult<List<ChatSession>>
    suspend fun createSession(title: String): AppResult<ChatSession>
    suspend fun deleteSession(sessionId: String): AppResult<Unit>
    suspend fun refreshMessages(sessionId: String): AppResult<List<ChatMessage>>
    suspend fun sendMessage(
        sessionId: String,
        text: String,
        agent: String?,
        model: ModelInfo?,
    ): AppResult<Unit>

    suspend fun interrupt(sessionId: String): AppResult<Unit>
    suspend fun fetchActiveSessionIds(): AppResult<Set<String>>

    /**
     * Polls the gateway until the assistant reply lands or the run ends.
     * Snapshots are persisted through the repository as they arrive.
     */
    suspend fun pollForResponse(sessionId: String, knownMessageIds: Set<String>): PollResult

    suspend fun appendLocalUserMessage(
        sessionId: String,
        messageId: String,
        text: String,
        createdAt: Long,
    )

    suspend fun markMessageStatus(
        messageId: String,
        status: MessageStatus,
        error: String? = null,
    )

    /**
     * Marks in-flight assistant messages as complete. Called when the gateway reports the
     * session as idle, which is the authoritative "the agent is done" signal.
     */
    suspend fun markAssistantSettled(sessionId: String)

    suspend fun saveDraft(sessionId: String, text: String)
    suspend fun getDraft(sessionId: String): String?
    fun observeDraft(sessionId: String): Flow<String?>
    suspend fun clearDraft(sessionId: String)

    suspend fun clearLocalCache()
}

/** Agent and provider discovery. */
interface AgentRepository {
    val agents: Flow<List<AgentInfo>>
    val models: Flow<List<ModelInfo>>
    val providers: Flow<List<ProviderInfo>>

    suspend fun refresh(): AppResult<Unit>
    suspend fun currentAgents(): List<AgentInfo>
    suspend fun currentModels(): List<ModelInfo>
}

/** Theme, server URL, agent/model selection, active conversation. */
interface SettingsRepository {
    val preferences: Flow<SettingsSnapshot>
    val activeSessionId: Flow<String?>
    val selectedAgentId: Flow<String?>
    val selectedModel: Flow<ModelInfo?>
    val themeMode: Flow<ThemeMode>
    val isDynamicColorEnabled: Flow<Boolean>
    val apiUrl: Flow<String>
    val username: Flow<String?>

    suspend fun setApiUrl(url: String)
    suspend fun setUsername(username: String?)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun selectAgent(agentId: String?)
    suspend fun selectModel(model: ModelInfo?)
    suspend fun selectSession(sessionId: String?)
    suspend fun clearPreferences()
}

data class SettingsSnapshot(
    val apiUrl: String,
    val username: String?,
    val themeMode: ThemeMode,
    val dynamicColor: Boolean,
    val selectedAgentId: String?,
    val selectedModel: ModelInfo?,
    val activeSessionId: String?,
)

/** `GET /health` probing. */
interface HealthRepository {
    suspend fun check(): AppResult<HealthInfo>
}
