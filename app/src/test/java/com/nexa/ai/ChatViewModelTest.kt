package com.nexa.ai

import com.nexa.ai.core.clock.Clock
import com.nexa.ai.core.error.AppError
import com.nexa.ai.core.network.ConnectivityMonitor
import com.nexa.ai.core.result.AppResult
import com.nexa.ai.domain.model.AgentInfo
import com.nexa.ai.domain.model.ChatMessage
import com.nexa.ai.domain.model.ChatSession
import com.nexa.ai.domain.model.MessageRole
import com.nexa.ai.domain.model.MessageStatus
import com.nexa.ai.domain.model.ModelInfo
import com.nexa.ai.domain.model.ProviderInfo
import com.nexa.ai.domain.model.ThemeMode
import com.nexa.ai.domain.polling.PollResult
import com.nexa.ai.domain.repository.AgentRepository
import com.nexa.ai.domain.repository.SessionRepository
import com.nexa.ai.domain.repository.SettingsRepository
import com.nexa.ai.domain.repository.SettingsSnapshot
import com.nexa.ai.domain.usecase.CreateSessionUseCase
import com.nexa.ai.domain.usecase.InterruptSessionUseCase
import com.nexa.ai.domain.usecase.MarkSessionSettledUseCase
import com.nexa.ai.domain.usecase.PollSessionUseCase
import com.nexa.ai.domain.usecase.RefreshMessagesUseCase
import com.nexa.ai.domain.usecase.RefreshSessionsUseCase
import com.nexa.ai.domain.usecase.SendMessageUseCase
import com.nexa.ai.presentation.chat.ChatError
import com.nexa.ai.presentation.chat.ChatStatus
import com.nexa.ai.presentation.chat.ChatViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * Chat state machine tests: optimistic echo, single-flight send protection, polling outcomes,
 * stop/interrupt and draft restoration.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private data class SentCall(
        val sessionId: String,
        val text: String,
        val agent: String?,
        val model: ModelInfo?,
    )

    private class FakeSessionRepository : SessionRepository {
        val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
        val sessionMeta = MutableStateFlow<ChatSession?>(null)
        val drafts = ConcurrentHashMap<String, String>()

        var sendResult: AppResult<Unit> = AppResult.Success(Unit)
        var interruptResult: AppResult<Unit> = AppResult.Success(Unit)
        var pollResult: PollResult = PollResult.CompletedEmpty
        var activeResult: AppResult<Set<String>> = AppResult.Success(emptySet())
        var pollGate: CompletableDeferred<Unit>? = null

        var sendCalls = 0
        var interruptCalls = 0
        var createdSessions = 0
        var lastSend: SentCall? = null

        override fun observeSessions(): Flow<List<ChatSession>> = flowOf(emptyList())
        override fun observeSession(sessionId: String): Flow<ChatSession?> = sessionMeta
        override fun observeMessages(sessionId: String): Flow<List<ChatMessage>> = messages
        override suspend fun getMessageIds(sessionId: String): Set<String> =
            messages.value.map { it.id }.toSet()

        override suspend fun getMessages(sessionId: String): List<ChatMessage> = messages.value
        override suspend fun refreshSessions(): AppResult<List<ChatSession>> =
            AppResult.Success(emptyList())

        override suspend fun createSession(title: String): AppResult<ChatSession> {
            createdSessions++
            val session = ChatSession(id = SESSION_ID, title = title, createdAt = 1L, updatedAt = 1L)
            sessionMeta.value = session
            return AppResult.Success(session)
        }

        override suspend fun deleteSession(sessionId: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun refreshMessages(sessionId: String): AppResult<List<ChatMessage>> =
            AppResult.Success(messages.value)

        override suspend fun sendMessage(
            sessionId: String,
            text: String,
            agent: String?,
            model: ModelInfo?,
        ): AppResult<Unit> {
            sendCalls++
            lastSend = SentCall(sessionId, text, agent, model)
            if (sendResult is AppResult.Success) {
                messages.value = messages.value + ChatMessage(
                    id = "srv_user_$sendCalls",
                    sessionId = sessionId,
                    role = MessageRole.USER,
                    content = text,
                    status = MessageStatus.SENT,
                )
            }
            return sendResult
        }

        override suspend fun interrupt(sessionId: String): AppResult<Unit> {
            interruptCalls++
            return interruptResult
        }

        override suspend fun fetchActiveSessionIds(): AppResult<Set<String>> = activeResult

        override suspend fun pollForResponse(sessionId: String, knownMessageIds: Set<String>): PollResult {
            pollGate?.await()
            if (pollResult is PollResult.Completed) {
                messages.value = messages.value + ChatMessage(
                    id = "srv_assistant",
                    sessionId = sessionId,
                    role = MessageRole.ASSISTANT,
                    content = "real answer",
                    status = MessageStatus.COMPLETE,
                )
            }
            return pollResult
        }

        override suspend fun appendLocalUserMessage(
            sessionId: String,
            messageId: String,
            text: String,
            createdAt: Long,
        ) {
            messages.value = messages.value + ChatMessage(
                id = messageId,
                sessionId = sessionId,
                role = MessageRole.USER,
                content = text,
                status = MessageStatus.PENDING,
                isLocal = true,
            )
        }

        override suspend fun markMessageStatus(messageId: String, status: MessageStatus, error: String?) {
            messages.value = messages.value.map {
                if (it.id == messageId) it.copy(status = status, error = error) else it
            }
        }

        override suspend fun markAssistantSettled(sessionId: String) {
            messages.value = messages.value.map {
                if (it.role == MessageRole.ASSISTANT && it.status == MessageStatus.STREAMING) {
                    it.copy(status = MessageStatus.COMPLETE)
                } else {
                    it
                }
            }
        }

        override suspend fun saveDraft(sessionId: String, text: String) {
            if (text.isBlank()) drafts.remove(sessionId) else drafts[sessionId] = text
        }

        override suspend fun getDraft(sessionId: String): String? = drafts[sessionId]
        override fun observeDraft(sessionId: String): Flow<String?> = flowOf(drafts[sessionId])
        override suspend fun clearDraft(sessionId: String) {
            drafts.remove(sessionId)
        }

        override suspend fun clearLocalCache() {
            messages.value = emptyList()
        }

        companion object {
            const val SESSION_ID = "ses_test"
        }
    }

    private class FakeSettingsRepository : SettingsRepository {
        val activeSession = MutableStateFlow<String?>(null)
        val selectedAgent = MutableStateFlow<String?>(null)
        val selectedModelFlow = MutableStateFlow<ModelInfo?>(null)

        override val preferences: Flow<SettingsSnapshot> = flowOf(
            SettingsSnapshot(
                apiUrl = "https://example.com/v1",
                username = "tester",
                themeMode = ThemeMode.SYSTEM,
                dynamicColor = true,
                selectedAgentId = null,
                selectedModel = null,
                activeSessionId = null,
            ),
        )
        override val activeSessionId: Flow<String?> = activeSession
        override val selectedAgentId: Flow<String?> = selectedAgent
        override val selectedModel: Flow<ModelInfo?> = selectedModelFlow
        override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
        override val isDynamicColorEnabled = MutableStateFlow(true)
        override val apiUrl = MutableStateFlow("https://example.com/v1")
        override val username = MutableStateFlow<String?>("tester")

        override suspend fun setApiUrl(url: String) = Unit
        override suspend fun setUsername(username: String?) = Unit
        override suspend fun setThemeMode(mode: ThemeMode) {
            themeMode.value = mode
        }

        override suspend fun setDynamicColor(enabled: Boolean) {
            isDynamicColorEnabled.value = enabled
        }

        override suspend fun selectAgent(agentId: String?) {
            selectedAgent.value = agentId
        }

        override suspend fun selectModel(model: ModelInfo?) {
            selectedModelFlow.value = model
        }

        override suspend fun selectSession(sessionId: String?) {
            activeSession.value = sessionId
        }

        override suspend fun clearPreferences() = Unit
    }

    private class FakeAgentRepository : AgentRepository {
        override val agents = MutableStateFlow(
            listOf(
                AgentInfo(id = "build", name = "Build", description = "Default coding agent"),
                AgentInfo(id = "plan", name = "Plan", description = "Read-only planning"),
            ),
        )
        override val models = MutableStateFlow(emptyList<ModelInfo>())
        override val providers = MutableStateFlow(emptyList<ProviderInfo>())

        override suspend fun refresh(): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun currentAgents(): List<AgentInfo> = agents.value
        override suspend fun currentModels(): List<ModelInfo> = models.value
    }

    private class FakeConnectivity : ConnectivityMonitor {
        override val isOnline = MutableStateFlow(true)
    }

    private fun TestScope.observeState(vm: ChatViewModel) {
        backgroundScope.launch { vm.uiState.collect() }
    }

    private fun createViewModel(
        repo: FakeSessionRepository,
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ) = ChatViewModel(
        sessionRepository = repo,
        agentRepository = FakeAgentRepository(),
        settingsRepository = settings,
        connectivityMonitor = FakeConnectivity(),
        sendMessageUseCase = SendMessageUseCase(repo),
        pollSessionUseCase = PollSessionUseCase(repo),
        interruptUseCase = InterruptSessionUseCase(repo),
        createSessionUseCase = CreateSessionUseCase(repo),
        refreshMessagesUseCase = RefreshMessagesUseCase(repo),
        refreshSessionsUseCase = RefreshSessionsUseCase(repo),
        markSessionSettledUseCase = MarkSessionSettledUseCase(repo),
        clock = object : Clock {
            override fun nowMillis(): Long = 1_000L
        },
    )

    @Test
    fun `send creates a session delivers the message and polls for the reply`() =
        runTest(dispatcher.scheduler) {
            val repo = FakeSessionRepository().apply {
                pollResult = PollResult.Completed(listOf("srv_assistant"))
            }
            val settings = FakeSettingsRepository()
            val vm = createViewModel(repo, settings)
            observeState(vm)
            advanceUntilIdle()

            vm.onInputChanged("Analyze this project")
            vm.send()
            advanceUntilIdle()

            assertEquals(1, repo.createdSessions)
            assertEquals(1, repo.sendCalls)
            assertEquals(FakeSessionRepository.SESSION_ID, repo.lastSend?.sessionId)
            assertEquals("Analyze this project", repo.lastSend?.text)
            assertEquals("build", repo.lastSend?.agent)
            assertEquals(FakeSessionRepository.SESSION_ID, settings.activeSession.value)
            assertTrue(repo.messages.value.any { it.role == MessageRole.ASSISTANT })
            assertEquals(ChatStatus.Idle, vm.uiState.value.status)
        }

    @Test
    fun `a second send while the agent is running is ignored`() =
        runTest(dispatcher.scheduler) {
            val gate = CompletableDeferred<Unit>()
            val repo = FakeSessionRepository().apply { pollGate = gate }
            val vm = createViewModel(repo)
            observeState(vm)
            advanceUntilIdle()

            vm.onInputChanged("first")
            vm.send()
            runCurrent()
            assertEquals(1, repo.sendCalls)

            vm.onInputChanged("second")
            vm.send()
            runCurrent()

            assertEquals("second", vm.input.value)
            assertEquals(1, repo.sendCalls)
            assertTrue(vm.uiState.value.isBusy)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(ChatStatus.Idle, vm.uiState.value.status)
        }

    @Test
    fun `a failed send restores the draft and flags the local message`() =
        runTest(dispatcher.scheduler) {
            val repo = FakeSessionRepository().apply {
                sendResult = AppResult.Failure(AppError.Server(502, "bad gateway"))
            }
            val vm = createViewModel(repo)
            observeState(vm)
            advanceUntilIdle()

            vm.onInputChanged("hello")
            vm.send()
            advanceUntilIdle()

            assertEquals("hello", vm.input.value)
            assertTrue(vm.uiState.value.error is ChatError.Send)
            assertTrue(repo.messages.value.any { it.status == MessageStatus.ERROR })
            assertEquals(ChatStatus.Idle, vm.uiState.value.status)
        }

    @Test
    fun `polling failures surface an error and retry resumes polling`() =
        runTest(dispatcher.scheduler) {
            val repo = FakeSessionRepository().apply {
                pollResult = PollResult.Failed(AppError.Server(500, "boom"), 2)
                activeResult = AppResult.Success(setOf(FakeSessionRepository.SESSION_ID))
            }
            val vm = createViewModel(repo)
            observeState(vm)
            advanceUntilIdle()

            vm.onInputChanged("hi")
            vm.send()
            advanceUntilIdle()

            assertTrue(vm.uiState.value.error is ChatError.Poll)

            repo.pollResult = PollResult.Completed(listOf("srv_assistant"))
            vm.retry()
            advanceUntilIdle()

            assertEquals(ChatStatus.Idle, vm.uiState.value.status)
            assertNull(vm.uiState.value.error)
            assertTrue(repo.messages.value.any { it.role == MessageRole.ASSISTANT })
        }

    @Test
    fun `stop cancels polling and interrupts the agent on the server`() =
        runTest(dispatcher.scheduler) {
            val gate = CompletableDeferred<Unit>()
            val repo = FakeSessionRepository().apply { pollGate = gate }
            val vm = createViewModel(repo)
            observeState(vm)
            advanceUntilIdle()

            vm.onInputChanged("long task")
            vm.send()
            runCurrent()

            vm.stop()
            advanceUntilIdle()

            assertEquals(1, repo.interruptCalls)
            assertEquals(ChatStatus.Stopped, vm.uiState.value.status)
        }

    @Test
    fun `the draft of the restored conversation is loaded on start`() =
        runTest(dispatcher.scheduler) {
            val repo = FakeSessionRepository()
            val settings = FakeSettingsRepository().apply {
                activeSession.value = FakeSessionRepository.SESSION_ID
            }
            repo.drafts[FakeSessionRepository.SESSION_ID] = "unsent text"

            val vm = createViewModel(repo, settings)
            observeState(vm)
            advanceUntilIdle()

            assertEquals("unsent text", vm.input.value)
        }

    @Test
    fun `empty input is rejected before touching the network`() =
        runTest(dispatcher.scheduler) {
            val repo = FakeSessionRepository()
            val vm = createViewModel(repo)
            observeState(vm)
            advanceUntilIdle()

            vm.onInputChanged("   ")
            vm.send()
            advanceUntilIdle()

            assertEquals(0, repo.sendCalls)
            assertTrue(vm.uiState.value.error is ChatError.Validation)
        }
}
