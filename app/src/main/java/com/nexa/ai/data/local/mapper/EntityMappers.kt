package com.nexa.ai.data.local.mapper

import com.nexa.ai.data.local.db.entity.DraftEntity
import com.nexa.ai.data.local.db.entity.MessageEntity
import com.nexa.ai.data.local.db.entity.MessageWithTools
import com.nexa.ai.data.local.db.entity.SessionEntity
import com.nexa.ai.data.local.db.entity.ToolCallEntity
import com.nexa.ai.domain.model.ChatMessage
import com.nexa.ai.domain.model.ChatSession
import com.nexa.ai.domain.model.MessageRole
import com.nexa.ai.domain.model.MessageStatus
import com.nexa.ai.domain.model.ToolCall
import com.nexa.ai.domain.model.ToolStatus

private fun String.toRole(): MessageRole = runCatching { MessageRole.valueOf(this) }
    .getOrDefault(MessageRole.ASSISTANT)

private fun String.toMessageStatus(): MessageStatus = runCatching { MessageStatus.valueOf(this) }
    .getOrDefault(MessageStatus.COMPLETE)

private fun String.toToolStatus(): ToolStatus = runCatching { ToolStatus.valueOf(this) }
    .getOrDefault(ToolStatus.COMPLETED)

fun SessionEntity.toDomain(isActive: Boolean = false): ChatSession = ChatSession(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    agent = agent,
    preview = preview,
    messageCount = messageCount,
    isActive = isActive,
)

fun ChatSession.toEntity(): SessionEntity = SessionEntity(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    agent = agent,
    preview = preview,
    messageCount = messageCount,
)

fun MessageWithTools.toDomain(): ChatMessage = message.toDomain(toolCalls)

fun MessageEntity.toDomain(toolCalls: List<ToolCallEntity> = emptyList()): ChatMessage = ChatMessage(
    id = id,
    sessionId = sessionId,
    role = role.toRole(),
    content = content,
    toolCalls = toolCalls
        .sortedBy { it.orderIndex }
        .map { entity ->
            ToolCall(
                id = entity.id,
                name = entity.name,
                status = entity.status.toToolStatus(),
                input = entity.input,
                output = entity.output,
                title = entity.title,
                startedAt = entity.startedAt,
                completedAt = entity.completedAt,
            )
        },
    status = status.toMessageStatus(),
    createdAt = createdAt,
    completedAt = completedAt,
    model = model,
    provider = provider,
    error = error,
    isLocal = isLocal,
)

fun ChatMessage.toEntity(): MessageEntity = MessageEntity(
    id = id,
    sessionId = sessionId,
    role = role.name,
    content = content,
    status = status.name,
    createdAt = createdAt,
    completedAt = completedAt,
    model = model,
    provider = provider,
    error = error,
    isLocal = isLocal,
)

fun ToolCall.toEntity(messageId: String, orderIndex: Int): ToolCallEntity = ToolCallEntity(
    id = id,
    messageId = messageId,
    name = name,
    status = status.name,
    input = input,
    output = output,
    title = title,
    startedAt = startedAt,
    completedAt = completedAt,
    orderIndex = orderIndex,
)

fun DraftEntity.toText(): String? = text.takeIf { it.isNotBlank() }
