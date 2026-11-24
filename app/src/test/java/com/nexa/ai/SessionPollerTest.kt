package com.nexa.ai

import com.nexa.ai.core.clock.Clock
import com.nexa.ai.core.dispatcher.DispatcherProvider
import com.nexa.ai.core.error.ErrorKind
import com.nexa.ai.data.remote.api.NexaApi
import com.nexa.ai.data.remote.parser.RemoteParser
import com.nexa.ai.data.repository.SessionPoller
import com.nexa.ai.domain.model.ChatMessage
import com.nexa.ai.domain.polling.PollResult
import com.nexa.ai.domain.polling.PollingConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * The polling loop is the app's source of truth for "is the agent still working", so its
 * terminal conditions are pinned down against a real HTTP server.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionPollerTest {

    private lateinit var server: MockWebServer
    private lateinit var api: NexaApi
    private val dispatcher = StandardTestDispatcher()

    private class TestDispatcherProvider(private val dispatcher: CoroutineDispatcher) : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        api = Retrofit.Builder()
            .baseUrl(server.url("/v1/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NexaApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun config(maxWait: Long = 60_000L) = PollingConfig(
        initialDelayMillis = 100L,
        maxDelayMillis = 1_000L,
        maxWaitMillis = maxWait,
        maxConsecutiveFailures = 6,
        idleGraceRounds = 2,
    )

    private fun clock(testScheduler: kotlinx.coroutines.test.TestScheduler) = object : Clock {
        override fun nowMillis(): Long = testScheduler.currentTime
    }

    private fun userOnly() = MockResponse().setBody(
        """{"messages":[{"id":"msg_u","role":"user","content":"Analyze this project"}]}""",
    )

    private fun withAssistant() = MockResponse().setBody(
        """
        {"messages":[
          {"id":"msg_u","role":"user","content":"Analyze this project"},
          {"id":"msg_a","role":"assistant","content":"Here is the analysis.","time":{"completed":5}}
        ]}
        """.trimIndent(),
    )

    @Test
    fun `completes once the session is idle and a new assistant message exists`() = runTest(dispatcher.scheduler) {
        server.enqueue(MockResponse().setBody("""{"sessions":[{"id":"ses_1"}]}"""))
        server.enqueue(userOnly())
        server.enqueue(MockResponse().setBody("""[]"""))
        server.enqueue(withAssistant())

        val snapshots = mutableListOf<List<ChatMessage>>()
        val poller = SessionPoller(api, RemoteParser, clock(testScheduler), TestDispatcherProvider(dispatcher), config())

        val result = poller.poll("ses_1", emptySet()) { snapshots += it }

        assertEquals(PollResult.Completed(listOf("msg_a")), result)
        assertEquals(2, snapshots.size)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `idle without any reply is reported as an empty completion`() = runTest(dispatcher.scheduler) {
        repeat(3) {
            server.enqueue(MockResponse().setBody("""[]"""))
            server.enqueue(userOnly())
        }

        val poller = SessionPoller(api, RemoteParser, clock(testScheduler), TestDispatcherProvider(dispatcher), config())

        val result = poller.poll("ses_1", emptySet()) { }

        assertEquals(PollResult.CompletedEmpty, result)
    }

    @Test
    fun `temporary server errors are retried with backoff`() = runTest(dispatcher.scheduler) {
        server.enqueue(MockResponse().setBody("""{"sessions":[{"id":"ses_1"}]}"""))
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        server.enqueue(MockResponse().setBody("""[]"""))
        server.enqueue(withAssistant())

        val poller = SessionPoller(api, RemoteParser, clock(testScheduler), TestDispatcherProvider(dispatcher), config())

        val result = poller.poll("ses_1", emptySet()) { }

        assertEquals(PollResult.Completed(listOf("msg_a")), result)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `authentication failures abort immediately instead of retrying`() = runTest(dispatcher.scheduler) {
        server.enqueue(MockResponse().setBody("""{"sessions":[{"id":"ses_1"}]}"""))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"expired"}"""))

        val poller = SessionPoller(api, RemoteParser, clock(testScheduler), TestDispatcherProvider(dispatcher), config())

        val result = poller.poll("ses_1", emptySet()) { }

        assertTrue(result is PollResult.Failed)
        assertEquals(ErrorKind.UNAUTHORIZED, (result as PollResult.Failed).error.kind)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `repeated failures stop polling with the last error`() = runTest(dispatcher.scheduler) {
        repeat(6) {
            server.enqueue(MockResponse().setBody("""{"sessions":[{"id":"ses_1"}]}"""))
            server.enqueue(MockResponse().setResponseCode(503).setBody("overloaded"))
        }

        val poller = SessionPoller(api, RemoteParser, clock(testScheduler), TestDispatcherProvider(dispatcher), config())

        val result = poller.poll("ses_1", emptySet()) { }

        assertTrue(result is PollResult.Failed)
        assertEquals(ErrorKind.SERVER, (result as PollResult.Failed).error.kind)
    }

    @Test
    fun `a session that never settles times out`() = runTest(dispatcher.scheduler) {
        repeat(20) {
            server.enqueue(MockResponse().setBody("""{"sessions":[{"id":"ses_1"}]}"""))
            server.enqueue(userOnly())
        }

        val poller = SessionPoller(api, RemoteParser, clock(testScheduler), TestDispatcherProvider(dispatcher), config(maxWait = 1_000L))

        val result = poller.poll("ses_1", emptySet()) { }

        assertTrue(result is PollResult.TimedOut)
    }

    @Test
    fun `snapshots are pushed as they arrive so tool calls render live`() = runTest(dispatcher.scheduler) {
        server.enqueue(MockResponse().setBody("""{"sessions":[{"id":"ses_1"}]}"""))
        server.enqueue(
            MockResponse().setBody(
                """
                {"messages":[{"id":"msg_a","role":"assistant","parts":[
                  {"type":"tool","callID":"c1","tool":"bash","state":{"status":"running"}}
                ]}]}
                """.trimIndent(),
            ),
        )
        server.enqueue(MockResponse().setBody("""[]"""))
        server.enqueue(withAssistant())

        val snapshots = mutableListOf<List<ChatMessage>>()
        val poller = SessionPoller(api, RemoteParser, clock(testScheduler), TestDispatcherProvider(dispatcher), config())

        poller.poll("ses_1", emptySet()) { snapshots += it }

        assertTrue(snapshots.first().first().toolCalls.isNotEmpty())
        assertEquals("bash", snapshots.first().first().toolCalls.first().name)
    }
}
