package com.nexa.ai.domain.model

data class ChatSession(
    val id: String,
    val title: String,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val agent: String? = null,
    val preview: String? = null,
    val messageCount: Int = 0,
    val isActive: Boolean = false,
) {
    val displayTitle: String
        get() = title.ifBlank { "New conversation" }
}

/** An OpenCode agent. [id] is the value sent in the `agent` field of a message request. */
data class AgentInfo(
    val id: String,
    val name: String,
    val description: String? = null,
    val model: String? = null,
    val mode: String? = null,
    val isBuiltIn: Boolean = false,
) {
    val displayName: String
        get() = name.ifBlank { id }
}

/** A concrete model exposed by a provider. [providerId] + [id] form the request payload. */
data class ModelInfo(
    val providerId: String,
    val id: String,
    val name: String,
    val providerName: String? = null,
) {
    val displayName: String
        get() = name.ifBlank { id }

    val stableKey: String
        get() = "$providerId/$id"
}

data class ProviderInfo(
    val id: String,
    val name: String,
    val models: List<ModelInfo> = emptyList(),
) {
    val displayName: String
        get() = name.ifBlank { id }
}

data class HealthInfo(
    val status: String,
    val version: String? = null,
    val latencyMillis: Long = 0L,
) {
    val isHealthy: Boolean
        get() = status.equals("ok", ignoreCase = true) ||
            status.equals("healthy", ignoreCase = true) ||
            status.equals("up", ignoreCase = true)
}
