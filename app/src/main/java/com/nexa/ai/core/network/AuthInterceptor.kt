package com.nexa.ai.core.network

import com.nexa.ai.core.clock.Clock
import com.nexa.ai.data.local.TokenStore
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches `Authorization: Bearer <token>` to protected calls and reports 401s.
 *
 * Security rules enforced here, in one place, for every outgoing request:
 *  - the token is never added to `POST /auth/login`;
 *  - an expired token is not sent at all (the session is invalidated instead);
 *  - nothing is ever logged, and the header is never echoed into error messages.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
    private val sessionInvalidator: SessionInvalidator,
    private val clock: Clock,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val isLoginCall = request.url.encodedPath.endsWith(LOGIN_PATH)

        val token = if (isLoginCall) null else tokenStore.currentOrNull()
        val builder = request.newBuilder()

        if (token != null) {
            if (clock.nowMillis() >= token.expiresAt) {
                sessionInvalidator.invalidate()
            } else {
                val scheme = token.tokenType.ifBlank { BEARER }
                builder.header(AUTHORIZATION, "$scheme ${token.accessToken}")
            }
        }

        val response = chain.proceed(builder.build())

        if (response.code == 401 && !isLoginCall) {
            sessionInvalidator.invalidate()
        }
        return response
    }

    private companion object {
        const val LOGIN_PATH = "/auth/login"
        const val AUTHORIZATION = "Authorization"
        const val BEARER = "Bearer"
    }
}
