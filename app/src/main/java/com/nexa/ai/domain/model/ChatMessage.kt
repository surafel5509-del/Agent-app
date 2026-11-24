package com.nexa.ai.domain.model

enum class MessageRole { USER, ASSISTANT, SYSTEM }

/** Lifecycle of a single message row. */
enum class MessageStatus {
    /** Optimistic local echo that has not been accepted by the gateway yet. */
    PENDING,

    /** Accepted by the gateway. */
    SENT,

    /** Assistant message whose parts are still being produced by the agent. */
    STREAMING,

    COMPLETE,

    ERROR,

    INTERRUPTED,
}

enum class ToolStatus { PENDING, RUNNING, COMPLETED, FAILED }

data class ToolCall(
    val id: String,
    val name: String,
    val status: ToolStatus = ToolStatus.PENDING,
    val input: String? = null,
    val output: String? = null,
    val title: String? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
) {
    val isRunning: Boolean
        get() = status == ToolStatus.RUNNING || status == ToolStatus.PENDING

    val isFailed: Boolean
        get() = status == ToolStatus.FAILED
}

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val status: MessageStatus = MessageStatus.COMPLETE,
    val createdAt: Long = 0L,
    val completedAt: Long? = null,
    val model: String? = null,
    val provider: String? = null,
    val error: String? = null,
    val isLocal: Boolean = false,
) {
    val isUser: Boolean
        get() = role == MessageRole.USER

    val isAssistant: Boolean
        get() = role == MessageRole.ASSISTANT

    val isPending: Boolean
        get() = status == MessageStatus.PENDING

    /** True while the backend is still working on this message or one of its tools. */
    val isWorking: Boolean
        get() = status == MessageStatus.STREAMING || toolCalls.any { it.isRunning }

    val hasVisibleContent: Boolean
        get() = content.isNotBlank() || toolCalls.isNotEmpty()
}
