package com.nexa.ai.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

/**
 * `POST /auth/login` response.
 *
 * The primary contract is snake_case (`access_token`), but camelCase is accepted too so the
 * app keeps working if the gateway is re-serialized.
 */
@Serializable
data class LoginResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("accessToken") val accessTokenCamel: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("tokenType") val tokenTypeCamel: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("expiresIn") val expiresInCamel: Long? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
    @SerialName("expiresAt") val expiresAtCamel: Long? = null,
    val username: String? = null,
) {
    val resolvedAccessToken: String?
        get() = accessToken ?: accessTokenCamel

    val resolvedTokenType: String
        get() = (tokenType ?: tokenTypeCamel)?.ifBlank { null } ?: "Bearer"

    val resolvedExpiresIn: Long?
        get() = expiresIn ?: expiresInCamel

    val resolvedExpiresAt: Long?
        get() = expiresAt ?: expiresAtCamel
}

@Serializable
data class CreateSessionRequest(
    val title: String,
)

/**
 * Optional model override. Field names are part of the API contract and must stay as-is.
 */
@Serializable
data class ModelSelection(
    @SerialName("providerID") val providerID: String,
    val id: String,
)

@Serializable
data class SendMessageRequest(
    val message: String,
    val agent: String? = null,
    val model: ModelSelection? = null,
)
