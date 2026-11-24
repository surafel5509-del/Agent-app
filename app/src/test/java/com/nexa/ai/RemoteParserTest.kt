package com.nexa.ai

import com.nexa.ai.data.remote.parser.RemoteParser
import com.nexa.ai.domain.model.MessageRole
import com.nexa.ai.domain.model.MessageStatus
import com.nexa.ai.domain.model.ToolStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gateway wraps OpenCode, so envelopes vary between builds. Every shape below is one the
 * app must survive without crashing or silently losing messages.
 */
class RemoteParserTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun element(raw: String) = json.parseToJsonElement(raw)

    @Test
    fun `sessions can arrive as a bare array`() {
        val sessions = RemoteParser.parseSessions(
            element("""[{"id":"ses_1","title":"Fix auth","time":{"created":10,"updated":20}}]"""),
        )
        assertEquals(1, sessions.size)
        assertEquals("ses_1", sessions[0].id)
        assertEquals("Fix auth", sessions[0].title)
        assertEquals(10L, sessions[0].createdAt)
        assertEquals(20L, sessions[0].updatedAt)
    }

    @Test
    fun `sessions can arrive wrapped in an envelope`() {
        val sessions = RemoteParser.parseSessions(
            element("""{"sessions":[{"id":"ses_1","title":"Fix auth"}],"total":1}"""),
        )
        assertEquals(1, sessions.size)
        assertEquals("ses_1", sessions[0].id)
    }

    @Test
    fun `sessions can arrive as an object map`() {
        val sessions = RemoteParser.parseSessions(
            element("""{"sessions":{"ses_1":{"id":"ses_1","title":"One"},"ses_2":{"id":"ses_2"}}}"""),
        )
        assertEquals(2, sessions.size)
        assertTrue(sessions.any { it.id == "ses_2" })
    }

    @Test
    fun `single session response unwraps nested object`() {
        val session = RemoteParser.parseSession(
            element("""{"session":{"id":"ses_9","title":"Created"}}"""),
        )
        assertNotNull(session)
        assertEquals("ses_9", session?.id)
    }

    @Test
    fun `opencode message parts become text content`() {
        val messages = RemoteParser.parseMessages(
            element(
                """
                [
                  {"id":"msg_u","role":"user","time":{"created":1},"parts":[{"type":"text","text":"Analyze this project"}]},
                  {"id":"msg_a","role":"assistant","time":{"created":2,"completed":3},
                   "parts":[{"type":"text","text":"Here is "},{"type":"text","text":"the analysis."}]}
                ]
                """.trimIndent(),
            ),
            sessionId = "ses_1",
        )

        assertEquals(2, messages.size)
        assertEquals(MessageRole.USER, messages[0].role)
        assertEquals("Analyze this project", messages[0].content)
        assertEquals(MessageRole.ASSISTANT, messages[1].role)
        assertEquals("Here is\n\nthe analysis.", messages[1].content)
        assertEquals(MessageStatus.COMPLETE, messages[1].status)
        assertEquals(3L, messages[1].completedAt)
    }

    @Test
    fun `openai style content array is also supported`() {
        val messages = RemoteParser.parseMessages(
            element("""[{"id":"m1","role":"assistant","content":[{"type":"text","text":"Hello"}]}]"""),
            sessionId = "ses_1",
        )
        assertEquals("Hello", messages[0].content)
    }

    @Test
    fun `plain string content is supported`() {
        val messages = RemoteParser.parseMessages(
            element("""[{"id":"m1","role":"user","content":"Hi there"}]"""),
            sessionId = "ses_1",
        )
        assertEquals("Hi there", messages[0].content)
    }

    @Test
    fun `assistant message without completion timestamp is treated as streaming`() {
        val messages = RemoteParser.parseMessages(
            element("""[{"id":"m1","role":"assistant","parts":[{"type":"text","text":"Working"}]}]"""),
            sessionId = "ses_1",
        )
        assertEquals(MessageStatus.STREAMING, messages[0].status)
    }

    @Test
    fun `tool parts carry status input and output`() {
        val messages = RemoteParser.parseMessages(
            element(
                """
                [{"id":"m1","role":"assistant","parts":[
                  {"type":"tool","callID":"call_1","tool":"bash",
                   "state":{"status":"completed","input":{"command":"ls -la"},"output":"README.md","time":{"start":100,"end":200}}},
                  {"type":"tool","callID":"call_2","tool":"edit",
                   "state":{"status":"running","input":{"path":"Main.kt"}}},
                  {"type":"tool","callID":"call_3","tool":"web_fetch","state":{"status":"error","output":"timeout"}}
                ]}]
                """.trimIndent(),
            ),
            sessionId = "ses_1",
        )

        val tools = messages[0].toolCalls
        assertEquals(3, tools.size)
        assertEquals(ToolStatus.COMPLETED, tools[0].status)
        assertEquals(ToolStatus.RUNNING, tools[1].status)
        assertEquals(ToolStatus.FAILED, tools[2].status)
        assertTrue(tools[0].input?.contains("ls -la") == true)
        assertEquals("README.md", tools[0].output)
        assertEquals(100L, tools[0].startedAt)
        assertEquals(200L, tools[0].completedAt)
        assertEquals("call_1", tools[0].id)
        assertTrue(messages[0].content.isBlank())
    }

    @Test
    fun `reasoning and step parts are ignored instead of breaking the message`() {
        val messages = RemoteParser.parseMessages(
            element(
                """[{"id":"m1","role":"assistant","parts":[
                    {"type":"reasoning","text":"thinking..."},
                    {"type":"step-start"},
                    {"type":"text","text":"Answer"}
                ]}]""",
            ),
            sessionId = "ses_1",
        )
        assertEquals("Answer", messages[0].content)
        assertEquals(0, messages[0].toolCalls.size)
    }

    @Test
    fun `message without an id gets a deterministic synthetic id`() {
        val raw = """[{"role":"user","content":"Same text"}]"""
        val first = RemoteParser.parseMessages(element(raw), "ses_1")
        val second = RemoteParser.parseMessages(element(raw), "ses_1")
        assertEquals(first[0].id, second[0].id)
        assertTrue(first[0].id.startsWith("synthetic-ses_1"))
    }

    @Test
    fun `empty payload produces an empty list instead of crashing`() {
        assertTrue(RemoteParser.parseMessages(element("[]"), "ses_1").isEmpty())
        assertTrue(RemoteParser.parseMessages(element("""{"messages":[]}"""), "ses_1").isEmpty())
    }

    @Test
    fun `active sessions accepts arrays of objects ids and strings`() {
        assertEquals(
            setOf("ses_1"),
            RemoteParser.parseActiveSessionIds(element("""[{"id":"ses_1"}]""")),
        )
        assertEquals(
            setOf("ses_1", "ses_2"),
            RemoteParser.parseActiveSessionIds(element("""["ses_1","ses_2"]""")),
        )
        assertEquals(
            setOf("ses_3"),
            RemoteParser.parseActiveSessionIds(element("""{"sessions":[{"id":"ses_3"}]}""")),
        )
        assertTrue(RemoteParser.parseActiveSessionIds(element("[]")).isEmpty())
    }

    @Test
    fun `agents are parsed with description model and built-in flag`() {
        val agents = RemoteParser.parseAgents(
            element(
                """
                {"agents":[
                  {"name":"build","mode":"code","description":"Default coding agent","built-in":true},
                  {"name":"plan","mode":"plan","model":{"providerID":"openrouter","modelID":"qwen"}},
                  {"name":"explore"}
                ]}
                """.trimIndent(),
            ),
        )

        assertEquals(3, agents.size)
        assertEquals("build", agents[0].id)
        assertTrue(agents[0].isBuiltIn)
        assertEquals("code", agents[0].mode)
        assertEquals("openrouter/qwen", agents[1].model)
        assertEquals("explore", agents[2].id)
    }

    @Test
    fun `providers expose models from both object and array shapes`() {
        val providers = RemoteParser.parseProviders(
            element(
                """
                {"providers":[
                  {"id":"openrouter","name":"OpenRouter","models":{"qwen/qwen-2.5":{"id":"qwen/qwen-2.5","name":"Qwen 2.5"}}},
                  {"id":"anthropic","name":"Anthropic","models":[{"id":"claude-3-5-sonnet","name":"Sonnet"}]}
                ]}
                """.trimIndent(),
            ),
        )

        assertEquals(2, providers.size)
        assertEquals(1, providers[0].models.size)
        assertEquals("qwen/qwen-2.5", providers[0].models[0].id)
        assertEquals("Qwen 2.5", providers[0].models[0].name)
        assertEquals(1, providers[1].models.size)
        assertEquals("claude-3-5-sonnet", providers[1].models[0].id)
    }

    @Test
    fun `flattened models remember their provider name`() {
        val models = RemoteParser.parseModels(
            element("""{"providers":[{"id":"openrouter","name":"OpenRouter","models":{"m1":{"id":"m1","name":"Model One"}}}]}"""),
        )
        assertEquals(1, models.size)
        assertEquals("OpenRouter", models[0].providerName)
        assertEquals("openrouter/m1", models[0].stableKey)
    }

    @Test
    fun `health accepts status ok and boolean shapes`() {
        assertTrue(RemoteParser.parseHealth(element("""{"status":"ok"}""")).isHealthy)
        assertTrue(RemoteParser.parseHealth(element("""{"status":"healthy"}""")).isHealthy)
        assertTrue(RemoteParser.parseHealth(element("""{"ok":true}""")).isHealthy)
        assertTrue(RemoteParser.parseHealth(element("\"ok\"")).isHealthy)
        assertTrue(RemoteParser.parseHealth(element("""{"status":"ok","version":"1.4.0"}""")).version == "1.4.0")
    }

    @Test
    fun `message id is extracted from a nested send response`() {
        assertEquals(
            "msg_42",
            RemoteParser.parseMessageId(element("""{"message":{"id":"msg_42"}}""")),
        )
        assertNull(RemoteParser.parseMessageId(element("""{"ok":true}""")))
    }

    @Test
    fun `json array helper handles malformed envelopes gracefully`() {
        val empty = JsonArray(emptyList())
        assertTrue(RemoteParser.parseMessages(empty, "ses_1").isEmpty())
        assertNull(RemoteParser.parseSession(element("""{"status":"error"}""")))
    }

    @Test
    fun `timestamps accept unix seconds milliseconds and iso8601 strings`() {
        val unixSec = RemoteParser.parseSession(
            element("""{"id":"ses_s","created_at":1732400000,"updated_at":"2026-11-24T09:00:00Z"}"""),
        )
        assertNotNull(unixSec)
        assertEquals(1_732_400_000_000L, unixSec?.createdAt)
        assertTrue((unixSec?.updatedAt ?: 0L) > 1_700_000_000_000L)
    }
}
