package com.nexa.ai.domain.polling

import com.nexa.ai.core.error.AppError

/**
 * Tunables for the message polling loop. Injected so tests can run with millisecond delays.
 *
 * The backend has no streaming endpoint, so the client polls `GET /sessions/active` and
 * `GET /sessions/{id}/messages` on a fixed cadence (1.5s, per the API contract) and only
 * backs off when a request actually fails.
 */
data class PollingConfig(
    val initialDelayMillis: Long = 1_500L,
    val maxDelayMillis: Long = 15_000L,
    val maxWaitMillis: Long = 10 * 60_000L,
    val maxConsecutiveFailures: Int = 6,
    /** Extra polls after the session goes idle, in case the assistant row lands slightly late. */
    val idleGraceRounds: Int = 3,
) {
    companion object {
        val Default = PollingConfig()
    }
}

/** Exponential backoff with a hard cap. */
object BackoffPolicy {

    fun delayFor(
        attempt: Int,
        initialDelayMillis: Long,
        maxDelayMillis: Long,
    ): Long {
        val safeAttempt = attempt.coerceIn(0, 20)
        val raw = initialDelayMillis * (1L shl safeAttempt)
        return if (raw <= 0 || raw > maxDelayMillis) maxDelayMillis else raw
    }
}

/** Terminal outcome of a polling run. */
sealed interface PollResult {
    /** The assistant produced at least one new message and the session is idle. */
    data class Completed(val assistantMessageIds: List<String>) : PollResult

    /** The session went idle without ever producing an assistant message. */
    data object CompletedEmpty : PollResult

    data object Interrupted : PollResult

    data class TimedOut(val waitedMillis: Long) : PollResult

    data class Failed(val error: AppError, val attempts: Int) : PollResult

    data object Cancelled : PollResult
}
