package com.nexa.ai.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nexa.ai.data.local.db.entity.DraftEntity
import com.nexa.ai.data.local.db.entity.MessageEntity
import com.nexa.ai.data.local.db.entity.MessageWithTools
import com.nexa.ai.data.local.db.entity.SessionEntity
import com.nexa.ai.data.local.db.entity.ToolCallEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    fun observeSession(sessionId: String): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getById(sessionId: String): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    suspend fun getAll(): List<SessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sessions: List<SessionEntity>)

    @Query("UPDATE sessions SET title = :title, preview = :preview, messageCount = :messageCount, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun updateSummary(
        sessionId: String,
        title: String,
        preview: String?,
        messageCount: Int,
        updatedAt: Long,
    )

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun delete(sessionId: String)

    @Query("DELETE FROM sessions WHERE id IN (:sessionIds)")
    suspend fun deleteByIds(sessionIds: List<String>)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()
}

@Dao
interface MessageDao {

    @Transaction
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeMessages(sessionId: String): Flow<List<MessageWithTools>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun getMessages(sessionId: String): List<MessageEntity>

    @Query("SELECT id FROM messages WHERE sessionId = :sessionId")
    suspend fun getMessageIds(sessionId: String): List<String>

    @Query("SELECT * FROM tool_calls WHERE messageId = :messageId ORDER BY orderIndex ASC")
    suspend fun getToolCalls(messageId: String): List<ToolCallEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertToolCalls(toolCalls: List<ToolCallEntity>)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId AND isLocal = 0")
    suspend fun deleteRemoteMessages(sessionId: String)

    @Query("DELETE FROM messages WHERE id IN (:messageIds)")
    suspend fun deleteByIds(messageIds: List<String>)

    @Query("UPDATE messages SET status = :status, error = :error WHERE id = :messageId")
    suspend fun updateStatus(messageId: String, status: String, error: String?)

    @Query(
        "UPDATE messages SET status = :toStatus " +
            "WHERE sessionId = :sessionId AND role = 'ASSISTANT' AND status = :fromStatus",
    )
    suspend fun updateAssistantStatus(
        sessionId: String,
        fromStatus: String,
        toStatus: String,
    )

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}

@Dao
interface DraftDao {

    @Query("SELECT * FROM drafts WHERE sessionId = :sessionId")
    fun observe(sessionId: String): Flow<DraftEntity?>

    @Query("SELECT * FROM drafts WHERE sessionId = :sessionId")
    suspend fun get(sessionId: String): DraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(draft: DraftEntity)

    @Query("DELETE FROM drafts WHERE sessionId = :sessionId")
    suspend fun delete(sessionId: String)

    @Query("DELETE FROM drafts")
    suspend fun deleteAll()
}
