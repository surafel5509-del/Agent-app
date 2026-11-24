package com.nexa.ai.data.repository

import com.nexa.ai.core.AppConstants
import com.nexa.ai.core.clock.Clock
import com.nexa.ai.core.dispatcher.DispatcherProvider
import com.nexa.ai.core.error.AppError
import com.nexa.ai.core.error.ErrorKind
import com.nexa.ai.core.error.toAppError
import com.nexa.ai.core.result.AppResult
import com.nexa.ai.data.remote.api.NexaApi
import com.nexa.ai.data.remote.parser.RemoteParser
import com.nexa.ai.domain.model.ChatMessage
import com.nexa.ai.domain.model.MessageRole
import com.nexa.ai.domain.polling.BackoffPolicy
import com.nexa.ai.domain.polling.PollResult
import com.nexa.ai.domain.polling.PollingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Polls the gateway until an agent run settles.
 *
 * The backend exposes no streaming endpoint, so this loop is the source of truth for
 * "is the agent still working":
 *
 *  1. `GET /sessions/active`  -> is this session executing right now?
 *  2. `GET /sessions/{id}/messages?order=asc` -> what has been produced so far?
 *
 * Every snapshot is pushed to the caller (which persists it), so the UI renders real
 * progress as tools run. Polling stops only when the session is idle **and** the reply has
 * landed, or when a hard limit (timeout / repeated failures) is reached.
 */
@Singleton
class SessionPoller @Inject constructor(
    private val api: NexaApi,
    private val parser: RemoteParser,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
    private val config: PollingConfig,
) {

    suspend fun poll(
        sessionId: String,
        knownMessageIds: Set<String>,
        onSnapshot: suspend (List<ChatMessage>) -> Unit,
    ): PollResult = withContext(dispatchers.io) {
        val deadline = clock.nowMillis() + config.maxWaitMillis
        var failures = 0
        var idleRounds = 0
        var delayMillis = config.initialDelayMillis
        var outcome: PollResult? = null

        while (outcome == null) {
            currentCoroutineContext().ensureActive()

            if (clock.nowMillis() >= deadline) {
                outcome = PollResult.TimedOut(config.maxWaitMillis)
                break
            }

            val activeResult = fetchActiveSessionIds()
            val messagesResult = fetchMessages(sessionId)

            val failure = messagesResult.errorOrNull()
            if (failure != null) {
                if (failure.kind == ErrorKind.UNAUTHORIZED) {
                    // Auth is handled globally; abort immediately instead of retrying.
                    outcome = PollResult.Failed(failure, failures)
                    break
                }
                failures++
                if (failures >= config.maxConsecutiveFailures) {
                    outcome = PollResult.Failed(failure, failures)
                    break
                }
                delayMillis = BackoffPolicy.delayFor(
                    attempt = failures,
                    initialDelayMillis = config.initialDelayMillis,
                    maxDelayMillis = config.maxDelayMillis,
                )
                delay(delayMillis)
                continue
            }

            val messages = messagesResult.getOrNull().orEmpty()
            onSnapshot(messages)

            val isActive = when (activeResult) {
                is AppResult.Success -> activeResult.value.contains(sessionId)
                // If activity is unknown, assume the run is still going rather than
                // declaring victory early.
                is AppResult.Failure -> true
            }

            val newAssistant = messages.filter {
                it.role == MessageRole.ASSISTANT && it.id !in knownMessageIds
            }

            if (isActive) {
                failures = 0
                idleRounds = 0
                delayMillis = config.initialDelayMillis
                delay(delayMillis)
                continue
            }

            if (newAssistant.isNotEmpty()) {
                outcome = PollResult.Completed(newAssistant.map { it.id })
                break
            }

            // Idle but no reply yet: the assistant row can land one tick after the session
            // flips to idle, so allow a couple of grace polls before giving up.
            idleRounds++
            if (idleRounds >= config.idleGraceRounds) {
                outcome = PollResult.CompletedEmpty
                break
            }
            delay(delayMillis)
        }

        outcome ?: PollResult.TimedOut(config.maxWaitMillis)
    }

    private suspend fun fetchActiveSessionIds(): AppResult<Set<String>> {
        val response = try {
            api.activeSessions()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            return AppResult.Failure(throwable.toAppError())
        }
        if (!response.isSuccessful) {
            return AppResult.Failure(
                AppError.fromHttp(response.code(), response.errorBody()?.string()?.take(200)),
            )
        }
        val body = response.body() ?: return AppResult.Success(emptySet())
        return AppResult.Success(parser.parseActiveSessionIds(body))
    }

    private suspend fun fetchMessages(sessionId: String): AppResult<List<ChatMessage>> {
        val response = try {
            api.messages(
                sessionId = sessionId,
                limit = AppConstants.MESSAGE_PAGE_SIZE,
                order = "asc",
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            return AppResult.Failure(throwable.toAppError())
        }
        if (!response.isSuccessful) {
            return AppResult.Failure(
                AppError.fromHttp(response.code(), response.errorBody()?.string()?.take(200)),
            )
        }
        val body = response.body() ?: return AppResult.Success(emptyList())
        return AppResult.Success(parser.parseMessages(body, sessionId))
    }
}
