package com.nexa.ai.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexa.ai.core.AppConstants
import com.nexa.ai.core.clock.Clock
import com.nexa.ai.core.network.ConnectivityMonitor
import com.nexa.ai.core.result.AppResult
import com.nexa.ai.domain.model.AgentInfo
import com.nexa.ai.domain.model.ChatSession
import com.nexa.ai.domain.model.MessageStatus
import com.nexa.ai.domain.model.ModelInfo
import com.nexa.ai.domain.polling.PollResult
import com.nexa.ai.domain.repository.AgentRepository
import com.nexa.ai.domain.repository.SessionRepository
import com.nexa.ai.domain.repository.SettingsRepository
import com.nexa.ai.domain.usecase.CreateSessionUseCase
import com.nexa.ai.domain.usecase.InterruptSessionUseCase
import com.nexa.ai.domain.usecase.MarkSessionSettledUseCase
import com.nexa.ai.domain.usecase.PollSessionUseCase
import com.nexa.ai.domain.usecase.RefreshMessagesUseCase
import com.nexa.ai.domain.usecase.RefreshSessionsUseCase
import com.nexa.ai.domain.usecase.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Drives the conversation.
 *
 * Message flow (all real, nothing is simulated):
 *
 *  1. if no session is selected, `POST /sessions` creates one;
 *  2. the user's text is written to Room immediately (optimistic echo, pending);
 *  3. `POST /sessions/{id}/messages` delivers it to the OpenCode agent;
 *  4. `GET /sessions/active` + `GET /sessions/{id}/messages` are polled every 1.5s,
 *     persisting every snapshot so tool calls appear as they execute;
 *  5. the run ends when the session is idle and an assistant message exists.
 *
 * Duplicate sends are blocked by a single-flight guard, drafts survive process death via
 * Room, and polling resumes automatically when the app comes back to the foreground while a
 * session is still active.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val agentRepository: AgentRepository,
    private val settingsRepository: SettingsRepository,
    private val connectivityMonitor: ConnectivityMonitor,
    private val sendMessageUseCase: SendMessageUseCase,
    private val pollSessionUseCase: PollSessionUseCase,
    private val interruptUseCase: InterruptSessionUseCase,
    private val createSessionUseCase: CreateSessionUseCase,
    private val refreshMessagesUseCase: RefreshMessagesUseCase,
    private val refreshSessionsUseCase: RefreshSessionsUseCase,
    private val markSessionSettledUseCase: MarkSessionSettledUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _status = MutableStateFlow<ChatStatus>(ChatStatus.Idle)
    private val _error = MutableStateFlow<ChatError?>(null)
    private val _isRefreshing = MutableStateFlow(false)

    private val sessionFlow = settingsRepository.activeSessionId

    private val messagesFlow = sessionFlow.flatMapLatest { sessionId ->
        if (sessionId == null) {
            flowOf(emptyList())
        } else {
            sessionRepository.observeMessages(sessionId).catch { emit(emptyList()) }
        }
    }

    private val sessionMetaFlow = sessionFlow.flatMapLatest { sessionId ->
        if (sessionId == null) flowOf<ChatSession?>(null) else sessionRepository.observeSession(sessionId)
    }

    private data class CatalogState(
        val agents: List<AgentInfo> = emptyList(),
        val models: List<ModelInfo> = emptyList(),
        val selectedAgentId: String? = null,
        val selectedModel: ModelInfo? = null,
    )

    private val catalogFlow = combine(
        agentRepository.agents,
        agentRepository.models,
        settingsRepository.selectedAgentId,
        settingsRepository.selectedModel,
    ) { agents, models, agentId, model ->
        CatalogState(agents, models, agentId, model)
    }

    val uiState: StateFlow<ChatUiState> = combine(
        messagesFlow,
        sessionMetaFlow,
        catalogFlow,
        _status,
        _error,
    ) { messages, session, catalog, status, error ->
        ChatUiState(
            sessionId = session?.id ?: activeSessionId,
            title = session?.displayTitle ?: "Nexa AI",
            messages = messages,
            agents = catalog.agents,
            models = catalog.models,
            selectedAgentId = catalog.selectedAgentId,
            selectedModel = catalog.selectedModel,
            status = status,
            error = error,
        )
    }
        .combine(connectivityMonitor.isOnline) { state, online -> state.copy(isOnline = online) }
        .combine(_isRefreshing) { state, refreshing -> state.copy(isRefreshing = refreshing) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ChatUiState(),
        )

    @Volatile
    private var activeSessionId: String? = null

    private var busy = false
    private var pollJob: Job? = null
    private var tickerJob: Job? = null
    private var lastSentText: String? = null
    private var inputInitialized = false

    init {
        viewModelScope.launch {
            sessionFlow.collectLatest { sessionId ->
                val previous = activeSessionId
                activeSessionId = sessionId
                if (!busy && (!inputInitialized || sessionId != previous)) {
                    _status.value = ChatStatus.Idle
                    _error.value = null
                    stopTicker()
                    _input.value = sessionRepository
                        .getDraft(sessionId ?: AppConstants.NEW_DRAFT_KEY)
                        .orEmpty()
                    inputInitialized = true
                }
                if (!busy && sessionId != null && sessionId != previous) {
                    resumePolling(sessionId)
                }
            }
        }

        viewModelScope.launch {
            _input
                .debounce(DRAFT_DEBOUNCE_MILLIS)
                .collectLatest { text ->
                    val key = activeSessionId ?: AppConstants.NEW_DRAFT_KEY
                    if (inputInitialized && !busy) {
                        sessionRepository.saveDraft(key, text)
                    }
                }
        }

        viewModelScope.launch {
            connectivityMonitor.isOnline.collectLatest { online ->
                if (online && !busy) {
                    val sessionId = activeSessionId
                    if (sessionId != null) {
                        refreshMessagesUseCase(sessionId)
                        refreshSessionsUseCase()
                    }
                }
            }
        }
    }

    fun onInputChanged(value: String) {
        _input.value = value
    }

    fun consumeError() {
        _error.value = null
    }

    fun selectAgent(agentId: String) {
        viewModelScope.launch { settingsRepository.selectAgent(agentId) }
    }

    fun send() {
        val text = _input.value.trim()
        if (text.isEmpty()) {
            _error.value = ChatError.Validation("Message cannot be empty")
            return
        }
        if (busy) return

        busy = true
        _error.value = null
        _input.value = ""

        viewModelScope.launch {
            val sessionId = resolveSession(text)
            if (sessionId == null) {
                busy = false
                return@launch
            }

            // Snapshot pre-existing message IDs BEFORE POSTing so an assistant row created
            // immediately by the gateway is recognized as a new reply during polling.
            val knownIdsBeforeSend = sessionRepository.getMessageIds(sessionId)

            val agentId = uiState.value.effectiveAgentId
            val model = uiState.value.selectedModel
            val localId = "local-${UUID.randomUUID()}"

            sessionRepository.appendLocalUserMessage(
                sessionId = sessionId,
                messageId = localId,
                text = text,
                createdAt = clock.nowMillis(),
            )
            sessionRepository.clearDraft(sessionId)
            sessionRepository.clearDraft(AppConstants.NEW_DRAFT_KEY)
            _status.value = ChatStatus.Sending
            lastSentText = text

            when (val result = sendMessageUseCase(sessionId, text, agentId, model)) {
                is AppResult.Failure -> {
                    sessionRepository.markMessageStatus(
                        messageId = localId,
                        status = MessageStatus.ERROR,
                        error = result.error.detail,
                    )
                    sessionRepository.saveDraft(sessionId, text)
                    _input.value = text
                    _status.value = ChatStatus.Idle
                    _error.value = ChatError.Send(result.error)
                    busy = false
                    return@launch
                }
                is AppResult.Success -> Unit
            }

            sessionRepository.markMessageStatus(localId, MessageStatus.SENT)
            startPolling(sessionId, knownIdsBeforeSend + localId)
        }
    }

    /** Cancels polling and asks the gateway to interrupt the running agent. */
    fun stop() {
        if (!busy) return
        val sessionId = activeSessionId ?: return

        pollJob?.cancel()
        pollJob = null
        stopTicker()

        viewModelScope.launch {
            when (val result = interruptUseCase(sessionId)) {
                is AppResult.Failure -> _error.value = ChatError.Interrupt(result.error)
                is AppResult.Success -> Unit
            }
            _status.value = ChatStatus.Stopped
            busy = false
        }
    }

    /** Retry depends on where the failure happened: re-send vs. resume polling. */
    fun retry() {
        if (busy) return
        when (val error = _error.value) {
            null -> return
            is ChatError.Send -> {
                _error.value = null
                _input.value = lastSentText.orEmpty()
            }
            is ChatError.Validation -> _error.value = null
            else -> {
                _error.value = null
                val sessionId = activeSessionId ?: return
                resumePolling(sessionId)
            }
        }
    }

    /** Called from a lifecycle observer when the app returns to the foreground. */
    fun onAppResumed() {
        val sessionId = activeSessionId ?: return
        if (busy) return
        viewModelScope.launch {
            refreshMessagesUseCase(sessionId)
            resumePolling(sessionId)
        }
    }

    fun refresh() {
        if (busy) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                refreshSessionsUseCase()
                activeSessionId?.let { refreshMessagesUseCase(it) }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private suspend fun resolveSession(text: String): String? {
        activeSessionId?.let { return it }

        return when (val created = createSessionUseCase(titleFrom(text))) {
            is AppResult.Success -> {
                activeSessionId = created.value.id
                settingsRepository.selectSession(created.value.id)
                created.value.id
            }
            is AppResult.Failure -> {
                _input.value = text
                _error.value = ChatError.Send(created.error)
                null
            }
        }
    }

    private fun titleFrom(text: String): String {
        val firstLine = text.lineSequence().firstOrNull()?.trim().orEmpty()
        return firstLine.ifBlank { "New conversation" }.take(48)
    }

    private fun startPolling(sessionId: String, knownIdsOverride: Set<String>? = null) {
        _status.value = ChatStatus.Running(0)
        startTicker()
        pollJob = viewModelScope.launch {
            val knownIds = knownIdsOverride ?: sessionRepository
                .getMessages(sessionId)
                .filter { !it.isWorking }
                .map { it.id }
                .toSet()
            val outcome = pollSessionUseCase(sessionId, knownIds)
            stopTicker()
            handlePollOutcome(sessionId, outcome)
            busy = false
            pollJob = null
        }
    }

    private suspend fun handlePollOutcome(sessionId: String, outcome: PollResult) {
        when (outcome) {
            is PollResult.Completed -> {
                markSessionSettledUseCase(sessionId)
                _status.value = ChatStatus.Idle
            }
            is PollResult.CompletedEmpty -> {
                markSessionSettledUseCase(sessionId)
                _status.value = ChatStatus.Idle
                _error.value = ChatError.Empty
            }
            is PollResult.TimedOut -> {
                _status.value = ChatStatus.Idle
                _error.value = ChatError.Timeout((outcome.waitedMillis / 1_000L).toInt())
            }
            is PollResult.Failed -> {
                _status.value = ChatStatus.Idle
                _error.value = ChatError.Poll(outcome.error)
            }
            is PollResult.Interrupted -> {
                markSessionSettledUseCase(sessionId)
                _status.value = ChatStatus.Stopped
            }
            is PollResult.Cancelled -> {
                _status.value = ChatStatus.Stopped
            }
        }
    }

    /** Resumes polling after a restart, or after a retry, only if the session is active. */
    private fun resumePolling(sessionId: String) {
        if (busy) return
        viewModelScope.launch {
            val active = sessionRepository.fetchActiveSessionIds().getOrNull()
            if (active == null || sessionId !in active) return@launch
            busy = true
            _status.value = ChatStatus.Running(0)
            refreshMessagesUseCase(sessionId)
            startPolling(sessionId)
        }
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = viewModelScope.launch {
            var ticks = 0
            while (isActive && ticks < MAX_TICKER_SECONDS) {
                delay(1_000)
                ticks++
                val current = _status.value
                if (current is ChatStatus.Running) {
                    _status.value = current.copy(elapsedSeconds = current.elapsedSeconds + 1)
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private companion object {
        const val DRAFT_DEBOUNCE_MILLIS = 400L
        const val MAX_TICKER_SECONDS = 3_600
    }
}
