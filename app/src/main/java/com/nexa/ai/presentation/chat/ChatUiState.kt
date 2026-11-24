package com.nexa.ai.presentation.chat

import com.nexa.ai.core.AppConstants
import com.nexa.ai.core.error.AppError
import com.nexa.ai.domain.model.AgentInfo
import com.nexa.ai.domain.model.ChatMessage
import com.nexa.ai.domain.model.ModelInfo

/** Coarse agent-run state driving the working indicator and the send/stop button. */
sealed interface ChatStatus {
    data object Idle : ChatStatus
    data object Sending : ChatStatus
    data class Running(val elapsedSeconds: Int = 0) : ChatStatus
    data object Stopped : ChatStatus
}

/** Failures surfaced to the user, with the recovery action each one implies. */
sealed interface ChatError {
    data class Validation(val message: String) : ChatError
    data class Send(val error: AppError) : ChatError
    data class Poll(val error: AppError) : ChatError
    data class Interrupt(val error: AppError) : ChatError

    /** The session went idle without producing an assistant message. */
    data object Empty : ChatError

    data class Timeout(val waitedSeconds: Int) : ChatError
}

data class ChatUiState(
    val sessionId: String? = null,
    val title: String = "Nexa AI",
    val messages: List<ChatMessage> = emptyList(),
    val agents: List<AgentInfo> = emptyList(),
    val models: List<ModelInfo> = emptyList(),
    val selectedAgentId: String? = null,
    val selectedModel: ModelInfo? = null,
    val status: ChatStatus = ChatStatus.Idle,
    val error: ChatError? = null,
    val isOnline: Boolean = true,
    val isRefreshing: Boolean = false,
) {
    val isBusy: Boolean
        get() = status is ChatStatus.Sending || status is ChatStatus.Running

    val hasMessages: Boolean
        get() = messages.isNotEmpty()

    /** The agent actually sent with the next message. */
    val effectiveAgentId: String
        get() = selectedAgentId
            ?: agents.firstOrNull { it.id == AppConstants.DEFAULT_AGENT }?.id
            ?: AppConstants.DEFAULT_AGENT

    val selectedAgent: AgentInfo?
        get() = agents.firstOrNull { it.id == effectiveAgentId }

    val workingToolCount: Int
        get() = messages.sumOf { message -> message.toolCalls.count { it.isRunning } }
}
