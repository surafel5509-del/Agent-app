package com.nexa.ai

import com.nexa.ai.core.clock.Clock
import com.nexa.ai.core.network.AuthInterceptor
import com.nexa.ai.core.network.SessionInvalidator
import com.nexa.ai.data.local.StoredToken
import com.nexa.ai.data.local.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var invalidator: SessionInvalidator
    private val invalidations = mutableListOf<Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private class FakeClock(var now: Long = 1_000L) : Clock {
        override fun nowMillis(): Long = now
    }

    private class FakeTokenStore(initial: StoredToken?) : TokenStore {
        private val state = MutableStateFlow(initial)
        override val token: StateFlow<StoredToken?> = state
        override fun currentOrNull(): StoredToken? = state.value
        override suspend fun save(token: StoredToken) {
            state.value = token
        }

        override suspend fun clear() {
            state.value = null
        }

        override suspend fun awaitLoaded() = Unit
    }

    private fun token(expiresAt: Long = 999_999L) = StoredToken(
        accessToken = "secret-token",
        tokenType = "Bearer",
        expiresAt = expiresAt,
        username = "surafel",
    )

    private fun client(store: TokenStore, clock: Clock): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(store, invalidator, clock))
            .build()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        invalidator = SessionInvalidator(FakeClock())
        scope.launch { invalidator.events.collect { invalidations += it } }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun execute(client: OkHttpClient, path: String, body: String = "{}"): okhttp3.Response {
        val request = Request.Builder()
            .url(server.url(path))
            .header("Content-Type", "application/json")
            .method("POST", okhttp3.RequestBody.create(null, body))
            .build()
        return client.newCall(request).execute()
    }

    @Test
    fun `attaches the bearer token to protected requests`() {
        server.enqueue(MockResponse().setBody("""{"sessions":[]}"""))
        val response = execute(client(FakeTokenStore(token()), FakeClock()), "/v1/sessions")

        assertEquals(200, response.code)
        val recorded = server.takeRequest()
        assertEquals("Bearer secret-token", recorded.getHeader("Authorization"))
        response.close()
    }

    @Test
    fun `never sends the token to the login endpoint`() {
        server.enqueue(MockResponse().setBody("""{"access_token":"x"}"""))
        val response = execute(client(FakeTokenStore(token()), FakeClock()), "/v1/auth/login")

        assertEquals(200, response.code)
        assertNull(server.takeRequest().getHeader("Authorization"))
        response.close()
    }

    @Test
    fun `a 401 on a protected call triggers exactly one invalidation`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"expired"}"""))
        val response = execute(client(FakeTokenStore(token()), FakeClock()), "/v1/sessions")

        assertEquals(401, response.code)
        assertEquals(1, invalidations.size)
        response.close()
    }

    @Test
    fun `a 401 on login is a credentials problem not a session problem`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"bad password"}"""))
        val response = execute(client(FakeTokenStore(null), FakeClock()), "/v1/auth/login")

        assertEquals(401, response.code)
        assertEquals(0, invalidations.size)
        response.close()
    }

    @Test
    fun `an expired token is not sent and forces a sign-in`() {
        server.enqueue(MockResponse().setBody("""{"sessions":[]}"""))
        val clock = FakeClock(now = 5_000_000L)
        val response = execute(client(FakeTokenStore(token(expiresAt = 1_000L)), clock), "/v1/sessions")

        assertEquals(200, response.code)
        assertNull(server.takeRequest().getHeader("Authorization"))
        assertEquals(1, invalidations.size)
        response.close()
    }
}
