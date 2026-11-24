package com.nexa.ai.data.remote.parser

import com.nexa.ai.domain.model.AgentInfo
import com.nexa.ai.domain.model.ChatMessage
import com.nexa.ai.domain.model.ChatSession
import com.nexa.ai.domain.model.HealthInfo
import com.nexa.ai.domain.model.MessageRole
import com.nexa.ai.domain.model.MessageStatus
import com.nexa.ai.domain.model.ModelInfo
import com.nexa.ai.domain.model.ProviderInfo
import com.nexa.ai.domain.model.ToolCall
import com.nexa.ai.domain.model.ToolStatus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant

/**
 * Tolerant decoder for gateway payloads.
 *
 * The gateway wraps an OpenCode server, so envelopes differ between builds:
 * `GET /sessions` may answer a bare array or `{sessions: [...]}`, and messages carry
 * OpenCode `parts` arrays (`text`, `tool`, `reasoning`, `step-start`, ...).
 *
 * This parser accepts the shapes observed in the wild and degrades instead of crashing:
 * unknown part types are ignored, missing ids are synthesised deterministically, and a
 * missing `role` is inferred. Every branch is covered by `RemoteParserTest`.
 */
object RemoteParser {

    // ------------------------------------------------------------------
    // Json helpers
    // ------------------------------------------------------------------

    private fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement.primitiveString(): String? = (this as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.string(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> this[key]?.primitiveString() }

    /**
     * Parses epoch milliseconds, 10-digit Unix seconds, fractional seconds, or ISO-8601 strings.
     * Small integers (< 1_000_000_000) are kept verbatim so synthetic test values stay exact.
     */
    private fun JsonObject.timestamp(vararg keys: String): Long? =
        keys.firstNotNullOfOrNull { key ->
            val primitive = this[key] as? JsonPrimitive ?: return@firstNotNullOfOrNull null
            primitive.longOrNull?.let { value ->
                return@firstNotNullOfOrNull if (value in 1_000_000_000L..9_999_999_999L) {
                    value * 1_000L
                } else {
                    value
                }
            }
            primitive.doubleOrNull?.let { value ->
                return@firstNotNullOfOrNull if (value >= 1_000_000_000.0 && value < 10_000_000_000.0) {
                    (value * 1_000.0).toLong()
                } else {
                    value.toLong()
                }
            }
            primitive.contentOrNull?.takeIf { it.isNotBlank() }?.let { raw ->
                runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
            }
        }

    private fun JsonObject.int(vararg keys: String): Int? =
        keys.firstNotNullOfOrNull { key -> (this[key] as? JsonPrimitive)?.intOrNull }

    private fun JsonObject.boolean(vararg keys: String): Boolean? =
        keys.firstNotNullOfOrNull { key -> (this[key] as? JsonPrimitive)?.booleanOrNull }

    private fun JsonObject.obj(vararg keys: String): JsonObject? =
        keys.firstNotNullOfOrNull { key -> this[key] as? JsonObject }

    /**
     * Returns the list behind the first matching envelope key. Handles three layouts:
     * a bare array, `{key: [ ... ]}` and `{key: {id1: {...}, id2: {...}}}`.
     */
    private fun JsonElement.toItemElements(vararg keys: String): List<JsonElement> = when (this) {
        is JsonArray -> this.toList()
        is JsonObject -> {
            val array = keys.firstNotNullOfOrNull { key -> this[key] as? JsonArray }
            if (array != null) {
                array.toList()
            } else {
                val map = keys.firstNotNullOfOrNull { key -> this[key] as? JsonObject }
                map?.values?.toList().orEmpty()
            }
        }
        else -> emptyList()
    }

    private fun JsonElement.singleObject(vararg keys: String): JsonObject? = when (this) {
        is JsonObject -> keys.firstNotNullOfOrNull { key -> this[key] as? JsonObject }
        else -> null
    }

    private fun JsonElement.elementToString(): String? = when (this) {
        is JsonPrimitive -> contentOrNull
        is JsonObject, is JsonArray -> toString()
        else -> null
    }

    // ------------------------------------------------------------------
    // Health
    // ------------------------------------------------------------------

    fun parseHealth(element: JsonElement): HealthInfo {
        val status = when (element) {
            is JsonObject -> element.string("status", "state", "health")
                ?: if (element.boolean("ok", "healthy") == true) "ok" else null
            is JsonPrimitive -> element.contentOrNull
            else -> null
        } ?: "unknown"

        val version = (element as? JsonObject)?.string("version", "build", "release")
        return HealthInfo(status = status, version = version)
    }

    // ------------------------------------------------------------------
    // Sessions
    // ------------------------------------------------------------------

    fun parseSessions(element: JsonElement): List<ChatSession> =
        element.toItemElements("sessions", "items", "data", "results")
            .mapNotNull { it.asObjectOrNull() }
            .mapNotNull { parseSession(it) }

    fun parseSession(element: JsonElement): ChatSession? {
        val obj = element.asObjectOrNull() ?: return null
        val target = obj.singleObject("session", "data", "item") ?: obj

        val id = target.string(
            "id",
            "sessionID",
            "sessionId",
            "session_id",
            "_id",
        ) ?: return null

        val time = target.obj("time")
        val createdAt = time?.timestamp("created", "createdAt")
            ?: target.timestamp("createdAt", "created_at", "created")
        val updatedAt = time?.timestamp("updated", "updatedAt")
            ?: target.timestamp("updatedAt", "updated_at", "updated")

        return ChatSession(
            id = id,
            title = target.string("title", "name", "summary")?.trim().orEmpty(),
            createdAt = createdAt ?: 0L,
            updatedAt = updatedAt ?: createdAt ?: 0L,
            agent = target.string("agent", "agentID", "agentId"),
            preview = target.string("preview", "lastMessage", "last_message", "subtitle"),
            messageCount = target.int("messageCount", "message_count", "messages") ?: 0,
        )
    }

    fun parseSessionId(element: JsonElement): String? = parseSession(element)?.id

    fun parseActiveSessionIds(element: JsonElement): Set<String> {
        val items = element.toItemElements("sessions", "active", "activeSessions", "items", "data")
        if (items.isEmpty() && element is JsonObject) {
            element.string("sessionId", "sessionID", "session_id", "id")?.let { return setOf(it) }
        }
        return items.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull
                is JsonObject -> parseSession(item)?.id
                    ?: item.string("sessionId", "sessionID", "session_id", "id")
                else -> null
            }
        }.filter { it.isNotBlank() }.toSet()
    }

    // ------------------------------------------------------------------
    // Messages
    // ------------------------------------------------------------------

    fun parseMessages(element: JsonElement, sessionId: String): List<ChatMessage> =
        element.toItemElements("messages", "items", "data", "results")
            .mapNotNull { it.asObjectOrNull() }
            .mapIndexedNotNull { index, obj -> parseMessage(obj, sessionId, index) }

    fun parseMessageId(element: JsonElement): String? {
        val obj = element.asObjectOrNull() ?: return null
        val target = obj.singleObject("message", "data") ?: obj
        return target.string("id", "messageID", "message_id", "messageId")
    }

    private fun parseMessage(obj: JsonObject, sessionId: String, index: Int): ChatMessage? {
        val parts = parseParts(obj)
        val textParts = parts.filterIsInstance<ParsedPart.Text>()
        val toolCalls = parts.filterIsInstance<ParsedPart.Tool>().mapIndexed { partIndex, part ->
            part.toToolCall(sessionId = sessionId, messageIndex = index, partIndex = partIndex)
        }

        val content = when {
            textParts.isNotEmpty() ->
                textParts.map { it.text.trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")
            else -> extractPlainText(obj)
        }

        val role = resolveRole(obj, hasTools = toolCalls.isNotEmpty())
        if (content.isBlank() && toolCalls.isEmpty() && role != MessageRole.ASSISTANT) return null

        val time = obj.obj("time")
        val createdAt = time?.timestamp("created", "createdAt")
            ?: obj.timestamp("createdAt", "created_at", "timestamp")
        val completedAt = time?.timestamp("completed", "updatedAt")
            ?: obj.timestamp("completedAt", "completed_at")

        val error = obj.string("error") ?: obj.obj("error")?.string("message", "name")

        val status = when {
            error != null -> MessageStatus.ERROR
            role == MessageRole.ASSISTANT && completedAt == null -> MessageStatus.STREAMING
            else -> MessageStatus.COMPLETE
        }

        val id = obj.string("id", "messageID", "message_id", "messageId")
            ?: "synthetic-$sessionId-$index-${(content + toolCalls.joinToString { it.name }).hashCode().toUInt()}"

        return ChatMessage(
            id = id,
            sessionId = sessionId,
            role = role,
            content = content,
            toolCalls = toolCalls,
            status = status,
            createdAt = createdAt ?: 0L,
            completedAt = completedAt,
            model = obj.string("modelID", "modelId", "model"),
            provider = obj.string("providerID", "providerId", "provider"),
            error = error,
        )
    }

    private fun resolveRole(obj: JsonObject, hasTools: Boolean): MessageRole {
        val raw = obj.string("role", "type", "author", "sender")?.lowercase()
        return when {
            raw == "user" || raw == "human" -> MessageRole.USER
            raw == "system" || raw == "tool" -> MessageRole.SYSTEM
            raw != null -> MessageRole.ASSISTANT
            hasTools -> MessageRole.ASSISTANT
            else -> MessageRole.ASSISTANT
        }
    }

    private fun extractPlainText(obj: JsonObject): String {
        obj["content"]?.let { content ->
            when (content) {
                is JsonPrimitive -> content.contentOrNull?.let { return it }
                is JsonArray -> {
                    val joined = content.mapNotNull { element ->
                        element.asObjectOrNull()?.string("text", "content")
                            ?: (element as? JsonPrimitive)?.contentOrNull
                    }.filter { it.isNotBlank() }.joinToString("\n\n")
                    if (joined.isNotBlank()) return joined
                }
                else -> Unit
            }
        }
        return obj.string("text", "message").orEmpty()
    }

    // ------------------------------------------------------------------
    // Parts (OpenCode shape)
    // ------------------------------------------------------------------

    private sealed interface ParsedPart {
        data class Text(val text: String) : ParsedPart

        data class Tool(
            val id: String?,
            val name: String,
            val status: ToolStatus,
            val input: String?,
            val output: String?,
            val title: String?,
            val startedAt: Long?,
            val completedAt: Long?,
        ) : ParsedPart {
            fun toToolCall(sessionId: String, messageIndex: Int, partIndex: Int): ToolCall = ToolCall(
                id = id ?: "tool-$sessionId-$messageIndex-$partIndex-$name",
                name = name,
                status = status,
                input = input,
                output = output,
                title = title,
                startedAt = startedAt,
                completedAt = completedAt,
            )
        }
    }

    private fun parseParts(obj: JsonObject): List<ParsedPart> {
        val container = obj["parts"] as? JsonArray ?: obj["content"] as? JsonArray ?: return emptyList()

        return container.mapIndexedNotNull { _, element ->
            val part = element.asObjectOrNull()
                ?: return@mapIndexedNotNull (element as? JsonPrimitive)?.contentOrNull?.let { ParsedPart.Text(it) }

            when (part.string("type")?.lowercase()) {
                "text", "paragraph", "output_text" ->
                    ParsedPart.Text(part.string("text", "content", "value").orEmpty())
                "tool", "tool_call", "tool-call", "tool_use", "function" ->
                    parseToolPart(part)
                // reasoning / step-start / file / snapshot / patch are not rendered for now
                else -> null
            }
        }
    }

    private fun parseToolPart(part: JsonObject): ParsedPart.Tool {
        val state = part.obj("state")

        val name = part.string("tool", "name", "toolName", "tool_name")
            ?: part.obj("function")?.string("name")
            ?: "tool"

        val statusRaw = state?.string("status") ?: part.string("status", "state")
        val status = when (statusRaw?.lowercase()) {
            "pending", "queued" -> ToolStatus.PENDING
            "running", "in_progress", "in-progress", "started" -> ToolStatus.RUNNING
            "completed", "complete", "done", "success", "ok" -> ToolStatus.COMPLETED
            "error", "failed", "failure", "cancelled", "canceled" -> ToolStatus.FAILED
            else -> ToolStatus.PENDING
        }

        val input = state?.get("input")?.elementToString()
            ?: part["input"]?.elementToString()
            ?: part["arguments"]?.elementToString()
        val output = state?.get("output")?.elementToString()
            ?: state?.get("result")?.elementToString()
            ?: part["output"]?.elementToString()
            ?: part["result"]?.elementToString()

        val startedAt = state?.obj("time")?.timestamp("start", "started")
            ?: state?.timestamp("startedAt", "started_at")
        val completedAt = state?.obj("time")?.timestamp("end", "completed")
            ?: state?.timestamp("completedAt", "completed_at")

        return ParsedPart.Tool(
            id = part.string("callID", "callId", "call_id", "id", "toolCallID", "tool_call_id"),
            name = name,
            status = status,
            input = input,
            output = output,
            title = state?.string("title") ?: part.string("title", "label"),
            startedAt = startedAt,
            completedAt = completedAt,
        )
    }

    // ------------------------------------------------------------------
    // Agents
    // ------------------------------------------------------------------

    fun parseAgents(element: JsonElement): List<AgentInfo> =
        element.toItemElements("agents", "items", "data", "results")
            .mapNotNull { it.asObjectOrNull() }
            .mapNotNull { obj ->
                val id = obj.string("name", "id", "agent") ?: return@mapNotNull null
                val model = obj.string("model")
                    ?: obj.obj("model")?.let { modelObject ->
                        listOfNotNull(
                            modelObject.string("providerID", "providerId"),
                            modelObject.string("modelID", "modelId", "id"),
                        ).joinToString("/").ifBlank { null }
                    }
                AgentInfo(
                    id = id,
                    name = obj.string("name", "title", "label") ?: id,
                    description = obj.string("description", "summary")?.takeIf { it.isNotBlank() },
                    model = model,
                    mode = obj.string("mode"),
                    isBuiltIn = obj.boolean("built-in", "builtin", "builtIn", "isBuiltIn") ?: false,
                )
            }

    // ------------------------------------------------------------------
    // Providers / models
    // ------------------------------------------------------------------

    fun parseProviders(element: JsonElement): List<ProviderInfo> =
        element.toItemElements("providers", "items", "data", "results")
            .mapNotNull { it.asObjectOrNull() }
            .mapNotNull { obj ->
                val id = obj.string("id", "providerID", "providerId", "provider_id") ?: return@mapNotNull null
                val models = when (val modelsElement = obj["models"]) {
                    is JsonArray -> modelsElement.mapNotNull { parseModel(it, id, null) }
                    is JsonObject -> modelsElement.mapNotNull { (key, value) -> parseModel(value, id, key) }
                    else -> emptyList()
                }
                ProviderInfo(
                    id = id,
                    name = obj.string("name", "label") ?: id,
                    models = models,
                )
            }

    private fun parseModel(element: JsonElement, providerId: String, key: String?): ModelInfo? = when (element) {
        is JsonObject -> {
            val id = element.string("id", "modelID", "modelId", "name") ?: key ?: return null
            ModelInfo(
                providerId = element.string("providerID", "providerId") ?: providerId,
                id = id,
                name = element.string("name", "label") ?: id,
            )
        }
        is JsonPrimitive -> key?.let { ModelInfo(providerId = providerId, id = it, name = it) }
        else -> null
    }

    /** Flattened models for the Settings selector, preserving provider order. */
    fun parseModels(element: JsonElement): List<ModelInfo> =
        parseProviders(element).flatMap { provider ->
            provider.models.map { it.copy(providerName = provider.displayName) }
        }
}
