package com.nexa.ai.data.local.db.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val agent: String?,
    val preview: String?,
    val messageCount: Int,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sessionId"),
        Index(value = ["sessionId", "createdAt"]),
    ],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    /** [com.nexa.ai.domain.model.MessageRole] name. */
    val role: String,
    val content: String,
    /** [com.nexa.ai.domain.model.MessageStatus] name. */
    val status: String,
    val createdAt: Long,
    val completedAt: Long?,
    val model: String?,
    val provider: String?,
    val error: String?,
    /** True for optimistic user echoes that have not been confirmed by the gateway yet. */
    val isLocal: Boolean,
)

@Entity(
    tableName = "tool_calls",
    primaryKeys = ["messageId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("messageId")],
)
data class ToolCallEntity(
    val id: String,
    val messageId: String,
    val name: String,
    /** [com.nexa.ai.domain.model.ToolStatus] name. */
    val status: String,
    val input: String?,
    val output: String?,
    val title: String?,
    val startedAt: Long?,
    val completedAt: Long?,
    val orderIndex: Int,
)

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val sessionId: String,
    val text: String,
    val updatedAt: Long,
)

data class MessageWithTools(
    @Embedded val message: MessageEntity,
    @Relation(parentColumn = "id", entityColumn = "messageId")
    val toolCalls: List<ToolCallEntity>,
)
