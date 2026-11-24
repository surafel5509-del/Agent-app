package com.nexa.ai.data.local

import kotlinx.coroutines.flow.StateFlow

/**
 * Access token as returned by `POST /auth/login`.
 *
 * Only [accessToken] is secret; [expiresAt] is an epoch-milli timestamp derived from
 * `expires_in` (the gateway issues 7-day tokens).
 */
data class StoredToken(
    val accessToken: String,
    val tokenType: String,
    val expiresAt: Long,
    val username: String?,
) {
    fun isExpired(nowMillis: Long): Boolean = nowMillis >= expiresAt
}

/** Persistence boundary for the access token. */
interface TokenStore {
    val token: StateFlow<StoredToken?>

    /** Synchronous snapshot used by the OkHttp interceptor (never blocks on disk). */
    fun currentOrNull(): StoredToken?

    /** Suspends until the persisted token (if any) has been restored from disk. */
    suspend fun awaitLoaded()

    suspend fun save(token: StoredToken)

    suspend fun clear()
}
