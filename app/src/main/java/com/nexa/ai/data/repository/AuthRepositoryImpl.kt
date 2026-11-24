package com.nexa.ai.data.repository

import com.nexa.ai.core.clock.Clock
import com.nexa.ai.core.dispatcher.DispatcherProvider
import com.nexa.ai.core.error.AppError
import com.nexa.ai.core.error.toAppError
import com.nexa.ai.core.network.BaseUrlHolder
import com.nexa.ai.core.network.BaseUrlNormalizer
import com.nexa.ai.core.network.SessionInvalidator
import com.nexa.ai.core.result.AppResult
import com.nexa.ai.data.local.StoredToken
import com.nexa.ai.data.local.TokenStore
import com.nexa.ai.data.remote.api.NexaApi
import com.nexa.ai.data.remote.dto.LoginRequest
import com.nexa.ai.di.ApplicationScope
import com.nexa.ai.domain.model.AuthState
import com.nexa.ai.domain.repository.AuthRepository
import com.nexa.ai.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: NexaApi,
    private val tokenStore: TokenStore,
    private val settingsRepository: SettingsRepository,
    private val baseUrlHolder: BaseUrlHolder,
    private val sessionInvalidator: SessionInvalidator,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
    @ApplicationScope appScope: CoroutineScope,
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Checking)
    override val authState: Flow<AuthState> = _authState.asStateFlow()

    init {
        appScope.launch {
            // Any 401 (or an expired token discovered by the interceptor) funnels through
            // SessionInvalidator, which is the single place that forces a sign-out.
            sessionInvalidator.events.collect {
                tokenStore.clear()
                _authState.value = AuthState.LoggedOut
            }
        }
    }

    override val currentUsername: String?
        get() = tokenStore.currentOrNull()?.username

    override suspend fun restore(): AuthState {
        _authState.value = AuthState.Checking
        tokenStore.awaitLoaded()
        val token = tokenStore.currentOrNull()
        val persistedUrl = settingsRepository.apiUrl.firstOrNull()
        if (!persistedUrl.isNullOrBlank()) {
            baseUrlHolder.update(persistedUrl)
        }
        val resolved = if (token != null) {
            AuthState.LoggedIn(token.username, token.expiresAt)
        } else {
            AuthState.LoggedOut
        }
        _authState.value = resolved
        return resolved
    }

    override suspend fun login(
        username: String,
        password: String,
        apiUrl: String,
    ): AppResult<AuthState.LoggedIn> = withContext(dispatchers.io) {
        val normalizedUrl = when (val validation = BaseUrlNormalizer.validate(apiUrl)) {
            is BaseUrlNormalizer.Validation.Invalid -> {
                return@withContext AppResult.Failure(
                    AppError.Validation(validation.reason.name),
                )
            }
            is BaseUrlNormalizer.Validation.Valid -> validation.url
        }

        if (username.isBlank() || password.isBlank()) {
            return@withContext AppResult.Failure(AppError.Validation("Credentials are required"))
        }

        baseUrlHolder.update(normalizedUrl)

        val response = try {
            api.login(LoginRequest(username = username.trim(), password = password))
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
            ?: return@withContext AppResult.Failure(AppError.EmptyResponse("Empty login response"))
        val accessToken = body.resolvedAccessToken
            ?: return@withContext AppResult.Failure(AppError.EmptyResponse("Login response had no access token"))

        val rawExpiresAt = body.resolvedExpiresAt
        val normalizedExpiresAt = when {
            rawExpiresAt == null -> null
            rawExpiresAt in 1_000_000_000L..9_999_999_999L -> rawExpiresAt * 1_000L
            else -> rawExpiresAt
        }
        val expiresAt = normalizedExpiresAt
            ?: clock.nowMillis() + (body.resolvedExpiresIn ?: DEFAULT_EXPIRY_SECONDS) * 1_000L
        val resolvedUsername = body.username?.takeIf { it.isNotBlank() } ?: username.trim()

        val stored = StoredToken(
            accessToken = accessToken,
            tokenType = body.resolvedTokenType,
            expiresAt = expiresAt,
            username = resolvedUsername,
        )
        tokenStore.save(stored)
        settingsRepository.setApiUrl(normalizedUrl)
        settingsRepository.setUsername(resolvedUsername)

        val loggedIn = AuthState.LoggedIn(resolvedUsername, expiresAt)
        _authState.value = loggedIn
        AppResult.Success(loggedIn)
    }

    override suspend fun signOut(): AppResult<Unit> {
        tokenStore.clear()
        _authState.value = AuthState.LoggedOut
        return AppResult.Success(Unit)
    }

    private companion object {
        /** Matches the gateway's documented 7-day token lifetime. */
        const val DEFAULT_EXPIRY_SECONDS = 7 * 24 * 60 * 60L
    }
}
