package com.nexa.ai

import com.nexa.ai.data.remote.dto.LoginResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The gateway documents snake_case (`access_token`, `expires_in`), but a re-serialized
 * deployment may answer camelCase. Both must resolve to the same values.
 */
class LoginResponseParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Test
    fun `snake_case payload is decoded`() {
        val payload = """
            {
              "access_token": "jwt.snake.case",
              "token_type": "Bearer",
              "expires_in": 604800
            }
        """.trimIndent()

        val response = json.decodeFromString(LoginResponse.serializer(), payload)

        assertEquals("jwt.snake.case", response.resolvedAccessToken)
        assertEquals("Bearer", response.resolvedTokenType)
        assertEquals(604800L, response.resolvedExpiresIn)
        assertNull(response.resolvedExpiresAt)
    }

    @Test
    fun `camel_case payload is decoded`() {
        val payload = """
            {
              "accessToken": "jwt.camel.case",
              "tokenType": "bearer",
              "expiresAt": 1735689600000,
              "username": "surafel"
            }
        """.trimIndent()

        val response = json.decodeFromString(LoginResponse.serializer(), payload)

        assertEquals("jwt.camel.case", response.resolvedAccessToken)
        assertEquals("bearer", response.resolvedTokenType)
        assertEquals(1735689600000L, response.resolvedExpiresAt)
        assertEquals("surafel", response.username)
    }

    @Test
    fun `missing token type falls back to bearer`() {
        val response = json.decodeFromString(
            LoginResponse.serializer(),
            """{"access_token":"abc"}""",
        )
        assertEquals("Bearer", response.resolvedTokenType)
    }

    @Test
    fun `unknown fields are ignored so extra gateway metadata cannot break login`() {
        val response = json.decodeFromString(
            LoginResponse.serializer(),
            """{"access_token":"abc","user":{"id":1},"scopes":["agent"]}""",
        )
        assertEquals("abc", response.resolvedAccessToken)
    }
}
