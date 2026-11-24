package com.nexa.ai.data.repository

import androidx.room.withTransaction
import com.nexa.ai.core.AppConstants
import com.nexa.ai.core.clock.Clock
import com.nexa.ai.core.dispatcher.DispatcherProvider
import com.nexa.ai.core.error.AppError
import com.nexa.ai.core.error.toAppError
import com.nexa.ai.core.result.AppResult
import com.nexa.ai.data.local.db.NexaDatabase
import com.nexa.ai.data.local.db.dao.DraftDao
import com.nexa.ai.data.local.db.dao.MessageDao
import com.nexa.ai.data.local.db.dao.SessionDao
import com.nexa.ai.data.local.db.entity.DraftEntity
import com.nexa.ai.data.local.db.entity.MessageEntity
import com.nexa.ai.data.local.mapper.toDomain
import com.nexa.ai.data.local.mapper.toEntity
import com.nexa.ai.data.local.mapper.toText
import com.nexa.ai.data.remote.api.NexaApi
import com.nexa.ai.data.remote.dto.CreateSessionRequest
import com.nexa.ai.data.remote.dto.ModelSelection
import com.nexa.ai.data.remote.dto.SendMessageRequest
import com.nexa.ai.data.remote.parser.RemoteParser
import com.nexa.ai.domain.model.ChatMessage
import com.nexa.ai.domain.model.ChatSession
import com.nexa.ai.domain.model.MessageRole
import com.nexa.ai.domain.model.MessageStatus
import com.nexa.ai.domain.model.ModelInfo
import com.nexa.ai.domain.polling.PollResult
import com.nexa.ai.domain.repository.SessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val api: NexaApi,
    private val database: NexaDatabase,
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val draftDao: DraftDao,
    private val parser: RemoteParser,
    private val poller: SessionPoller,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : SessionRepository {

    override fun observeSessions(): Flow<List<ChatSession>> =
        sessionDao.observeSessions().map { sessions -> sessions.map { it.toDomain() } }

    override fun observeSession(sessionId: String): Flow<ChatSession?> =
        sessionDao.observeSession(sessionId).map { it?.toDomain() }

    override fun observeMessages(sessionId: String): Flow<List<ChatMessage>> =
        messageDao.observeMessages(sessionId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getMessageIds(sessionId: String): Set<String> = withContext(dispatchers.io) {
        messageDao.getMessageIds(sessionId).toSet()
    }

    override suspend fun getMessages(sessionId: String): List<ChatMessage> =
        withContext(dispatchers.io) {
            messageDao.getMessages(sessionId).map { entity ->
                entity.toDomain(messageDao.getToolCalls(entity.id))
            }
        }

    override suspend fun refreshSessions(): AppResult<List<ChatSession>> = withContext(dispatchers.io) {
        val response = try {
            api.sessions()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            return@withContext AppResult.Failure(throwable.toAppError())
        }
        if (!response.isSuccessful) {
            return@withContext AppResult.Failure(
                AppError.fromHttp(response.code(), response.errorBody()?.string()?.take(200)),
            )
        }
        val body = response.body() ?: return@withContext AppResult.Success(emptyList())
        val sessions = parser.parseSessions(body)

        database.withTransaction {
            val remoteIds = sessions.map { it.id }.toSet()
            val stale = sessionDao.getAll().map { it.id }.filter { it !in remoteIds }
            if (stale.isNotEmpty()) sessionDao.deleteByIds(stale)
            sessionDao.upsertAll(sessions.map { it.toEntity() })
        }

        AppResult.Success(sessions)
    }

    override suspend fun createSession(title: String): AppResult<ChatSession> = withContext(dispatchers.io) {
        val response = try {
            api.createSession(CreateSessionRequest(title.ifBlank { DEFAULT_TITLE }))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            return@withContext AppResult.Failure(throwable.toAppError())
        }
        if (!response.isSuccessful) {
            return@withContext AppResult.Failure(
                AppError.fromHttp(response.code(), response.errorBody()?.string()?.take(200)),
            )
        }
        val body = response.body()
            ?: return@withContext AppResult.Failure(AppError.EmptyResponse("Empty session response"))
        val session = parser.parseSession(body)
            ?: return@withContext AppResult.Failure(AppError.EmptyResponse("Session response had no id"))

        sessionDao.upsert(session.toEntity())
        AppResult.Success(session)
    }

    override suspend fun deleteSession(sessionId: String): AppResult<Unit> = withContext(dispatchers.io) {
        val response = try {
            api.deleteSession(sessionId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            return@withContext AppResult.Failure(throwable.toAppError())
        }
        if (!response.isSuccessful && response.code() != 404) {
            return@withContext AppResult.Failure(
                AppError.fromHttp(response.code(), response.errorBody()?.string()?.take(200)),
            )
        }
        database.withTransaction {
            // Messages and tool calls cascade from the session row.
            sessionDao.delete(sessionId)
            draftDao.delete(sessionId)
        }
        AppResult.Success(Unit)
    }

    override suspend fun refreshMessages(sessionId: String): AppResult<List<ChatMessage>> =
        withContext(dispatchers.io) {
            val response = try {
                api.messages(
                    sessionId = sessionId,
                    limit = AppConstants.MESSAGE_PAGE_SIZE,
                    order = "asc",
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                return@withContext AppResult.Failure(throwable.toAppError())
            }
            if (!response.isSuccessful) {
                return@withContext AppResult.Failure(
                    AppError.fromHttp(response.code(), response.errorBody()?.string()?.take(200)),
                )
            }
            val body = response.body() ?: return@withContext AppResult.Success(emptyList())
            val messages = parser.parseMessages(body, sessionId)
            persistRemoteMessages(sessionId, messages)
            AppResult.Success(messages)
        }

    override suspend fun sendMessage(
        sessionId: String,
        text: String,
        agent: String?,
        model: ModelInfo?,
    ): AppResult<Unit> = withContext(dispatchers.io) {
        val request = SendMessageRequest(
            message = text,
            agent = agent?.takeIf { it.isNotBlank() },
            model = model?.let { ModelSelection(providerID = it.providerId, id = it.id) },
        )

        val response = try {
            api.sendMessage(sessionId, request)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            return@withContext AppResult.Failure(throwable.toAppError())
        }
        if (!response.isSuccessful) {
            return@withContext AppResult.Failure(
                AppError.fromHttp(response.code(), response.errorBody()?.string()?.take(200)),
            )
        }

        // Pull the server copy so local ids/order match the backend before polling starts.
        // A failed follow-up refresh must not mask a successful send: the poller retries it.
        refreshMessages(sessionId)
        AppResult.Success(Unit)
    }

    override suspend fun interrupt(sessionId: String): AppResult<Unit> = withContext(dispatchers.io) {
        val response = try {
            api.interrupt(sessionId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            return@withContext AppResult.Failure(throwable.toAppError())
        }
        if (!response.isSuccessful && response.code() != 404) {
            return@withContext AppResult.Failure(
                AppError.fromHttp(response.code(), response.errorBody()?.string()?.take(200)),
            )
        }
        // Best effort: whatever the agent produced before interruption is kept.
        refreshMessages(sessionId)
        AppResult.Success(Unit)
    }

    override suspend fun fetchActiveSessionIds(): AppResult<Set<String>> = withContext(dispatchers.io) {
        val response = try {
            api.activeSessions()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            return@withContext AppResult.Failure(throwable.toAppError())
        }
        if (!response.isSuccessful) {
            return@withContext AppResult.Failure(
                AppError.fromHttp(response.code(), response.errorBody()?.string()?.take(200)),
            )
        }
        val body = response.body() ?: return@withContext AppResult.Success(emptySet())
        AppResult.Success(parser.parseActiveSessionIds(body))
    }

    override suspend fun pollForResponse(sessionId: String, knownMessageIds: Set<String>): PollResult =
        poller.poll(sessionId, knownMessageIds) { messages ->
            persistRemoteMessages(sessionId, messages)
        }

    override suspend fun appendLocalUserMessage(
        sessionId: String,
        messageId: String,
        text: String,
        createdAt: Long,
    ) = withContext(dispatchers.io) {
        sessionDao.getById(sessionId)?.let { existing ->
            sessionDao.updateSummary(
                sessionId = sessionId,
                title = existing.title,
                preview = text.take(120),
                messageCount = existing.messageCount + 1,
                updatedAt = clock.nowMillis(),
            )
        }
        messageDao.upsertMessages(
            listOf(
                MessageEntity(
                    id = messageId,
                    sessionId = sessionId,
                    role = MessageRole.USER.name,
                    content = text,
                    status = MessageStatus.PENDING.name,
                    createdAt = createdAt,
                    completedAt = null,
                    model = null,
                    provider = null,
                    error = null,
                    isLocal = true,
                ),
            ),
        )
    }

    override suspend fun markMessageStatus(
        messageId: String,
        status: MessageStatus,
        error: String?,
    ) = withContext(dispatchers.io) {
        messageDao.updateStatus(messageId, status.name, error)
    }

    override suspend fun markAssistantSettled(sessionId: String) = withContext(dispatchers.io) {
        messageDao.updateAssistantStatus(
            sessionId = sessionId,
            fromStatus = MessageStatus.STREAMING.name,
            toStatus = MessageStatus.COMPLETE.name,
        )
    }

    override suspend fun saveDraft(sessionId: String, text: String) = withContext(dispatchers.io) {
        if (text.isBlank()) {
            draftDao.delete(sessionId)
        } else {
            draftDao.save(DraftEntity(sessionId = sessionId, text = text, updatedAt = clock.nowMillis()))
        }
    }

    override suspend fun getDraft(sessionId: String): String? = withContext(dispatchers.io) {
        draftDao.get(sessionId)?.toText()
    }

    override fun observeDraft(sessionId: String): Flow<String?> =
        draftDao.observe(sessionId).map { it?.toText() }

    override suspend fun clearDraft(sessionId: String) = withContext(dispatchers.io) {
        draftDao.delete(sessionId)
    }

    override suspend fun clearLocalCache() = withContext(dispatchers.io) {
        database.withTransaction {
            messageDao.deleteAll()
            sessionDao.deleteAll()
            draftDao.deleteAll()
        }
    }

    // ------------------------------------------------------------------
    // Persistence helpers
    // ------------------------------------------------------------------

    private suspend fun persistRemoteMessages(sessionId: String, remote: List<ChatMessage>) {
        val messageEntities = remote.map { it.toEntity() }
        val toolCallEntities = remote.flatMap { message ->
            message.toolCalls.mapIndexed { index, tool -> tool.toEntity(message.id, index) }
        }

        database.withTransaction {
            messageDao.deleteRemoteMessages(sessionId)
            messageDao.upsertMessages(messageEntities)
            if (toolCallEntities.isNotEmpty()) {
                messageDao.upsertToolCalls(toolCallEntities)
            }
        }

        reconcileLocalEchoes(sessionId, remote)
        updateSessionSummary(sessionId, remote)
    }

    /**
     * The optimistic user echo is replaced by the server copy once it exists, which keeps
     * ordering stable and avoids duplicate bubbles after a refresh.
     */
    private suspend fun reconcileLocalEchoes(sessionId: String, remote: List<ChatMessage>) {
        val local = messageDao.getMessages(sessionId).filter { it.isLocal }
        if (local.isEmpty()) return
        val remoteUserTexts = remote
            .filter { it.role == MessageRole.USER }
            .map { it.content.trim() }
            .toSet()
        val ids = local.filter { it.content.trim() in remoteUserTexts }.map { it.id }
        if (ids.isNotEmpty()) messageDao.deleteByIds(ids)
    }

    private suspend fun updateSessionSummary(sessionId: String, remote: List<ChatMessage>) {
        val existing = sessionDao.getById(sessionId) ?: return
        val last = remote.lastOrNull()
        val firstUser = remote.firstOrNull { it.role == MessageRole.USER }
        val derivedTitle = firstUser?.content
            ?.trim()
            ?.lineSequence()
            ?.firstOrNull()
            ?.take(48)
            .orEmpty()

        val title = when {
            existing.title.isBlank() || existing.title == DEFAULT_TITLE ->
                derivedTitle.ifBlank { DEFAULT_TITLE }
            else -> existing.title
        }

        sessionDao.updateSummary(
            sessionId = sessionId,
            title = title,
            preview = last?.content?.trim()?.take(120),
            messageCount = remote.size,
            updatedAt = maxOf(existing.updatedAt, last?.createdAt ?: 0L, clock.nowMillis()),
        )
    }

    private companion object {
        const val DEFAULT_TITLE = "New conversation"
    }
}
