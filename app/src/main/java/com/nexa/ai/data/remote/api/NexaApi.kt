package com.nexa.ai.data.remote.api

import com.nexa.ai.data.remote.dto.CreateSessionRequest
import com.nexa.ai.data.remote.dto.LoginRequest
import com.nexa.ai.data.remote.dto.LoginResponse
import com.nexa.ai.data.remote.dto.SendMessageRequest
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit definition of the OpenCode gateway.
 *
 * Session/message/agent/provider endpoints return [JsonElement] on purpose: the gateway is a
 * thin wrapper whose envelope shape can vary (bare array vs `{sessions: [...]}`), and a strict
 * DTO would make the whole app fail on an extra wrapper key. [com.nexa.ai.data.remote.parser.RemoteParser]
 * performs the tolerant decoding and is unit-tested against several envelope shapes.
 *
 * Every non-login call is authenticated by `AuthInterceptor`.
 */
interface NexaApi {

    @GET("health")
    suspend fun health(): Response<JsonElement>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @GET("sessions")
    suspend fun sessions(): Response<JsonElement>

    @POST("sessions")
    suspend fun createSession(@Body body: CreateSessionRequest): Response<JsonElement>

    @GET("sessions/active")
    suspend fun activeSessions(): Response<JsonElement>

    @GET("sessions/{sessionId}")
    suspend fun session(@Path("sessionId") sessionId: String): Response<JsonElement>

    @DELETE("sessions/{sessionId}")
    suspend fun deleteSession(@Path("sessionId") sessionId: String): Response<Unit>

    @GET("sessions/{sessionId}/messages")
    suspend fun messages(
        @Path("sessionId") sessionId: String,
        @Query("limit") limit: Int = 50,
        @Query("order") order: String = "asc",
    ): Response<JsonElement>

    @POST("sessions/{sessionId}/messages")
    suspend fun sendMessage(
        @Path("sessionId") sessionId: String,
        @Body body: SendMessageRequest,
    ): Response<JsonElement>

    @POST("sessions/{sessionId}/interrupt")
    suspend fun interrupt(@Path("sessionId") sessionId: String): Response<JsonElement>

    @GET("agents")
    suspend fun agents(): Response<JsonElement>

    @GET("providers")
    suspend fun providers(): Response<JsonElement>
}
