package com.nexa.ai

import com.nexa.ai.core.markdown.SyntaxHighlighter
import com.nexa.ai.core.markdown.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlighterTest {

    @Test
    fun `kotlin keywords strings and comments are classified`() {
        val tokens = SyntaxHighlighter.tokenize(
            """
            // entry point
            val message = "hello"
            fun main() = println(message)
            """.trimIndent(),
            "kotlin",
        )

        assertTrue(tokens.any { it.type == TokenType.COMMENT && it.text.contains("entry point") })
        assertTrue(tokens.any { it.type == TokenType.KEYWORD && it.text == "val" })
        assertTrue(tokens.any { it.type == TokenType.STRING && it.text.contains("hello") })
        assertTrue(tokens.any { it.type == TokenType.FUNCTION && it.text == "println" })
    }

    @Test
    fun `python and shell use hash comments`() {
        val python = SyntaxHighlighter.tokenize("# note\nx = 1", "py")
        assertTrue(python.any { it.type == TokenType.COMMENT && it.text.contains("note") })
        assertTrue(python.any { it.type == TokenType.NUMBER && it.text == "1" })

        val shell = SyntaxHighlighter.tokenize("# install\ncurl https://example.com", "sh")
        assertTrue(shell.any { it.type == TokenType.COMMENT })
    }

    @Test
    fun `json highlights property keys and literals`() {
        val tokens = SyntaxHighlighter.tokenize("""{"name": "Nexa", "ok": true}""", "json")

        assertTrue(tokens.any { it.type == TokenType.PROPERTY && it.text.contains("name") })
        assertTrue(tokens.any { it.type == TokenType.STRING && it.text.contains("Nexa") })
        assertTrue(tokens.any { it.type == TokenType.KEYWORD && it.text == "true" })
    }

    @Test
    fun `sql uses double dash comments and keywords`() {
        val tokens = SyntaxHighlighter.tokenize("-- top users\nSELECT id FROM users", "sql")
        assertTrue(tokens.any { it.type == TokenType.COMMENT })
        assertTrue(tokens.any { it.type == TokenType.KEYWORD && it.text.equals("SELECT", true) })
    }

    @Test
    fun `unknown language falls back to a generic tokenizer without crashing`() {
        val tokens = SyntaxHighlighter.tokenize("def strange = 1", "brainfuck")
        assertEquals("def strange = 1", tokens.joinToString("") { it.text })
    }

    @Test
    fun `rejoining tokens reproduces the original code exactly`() {
        val code = """
            class Demo {
                fun run(): Int = 42 // done
            }
        """.trimIndent()
        val rejoined = SyntaxHighlighter.tokenize(code, "kotlin").joinToString("") { it.text }
        assertEquals(code, rejoined)
    }

    @Test
    fun `empty input produces no tokens`() {
        assertTrue(SyntaxHighlighter.tokenize("", "kotlin").isEmpty())
    }
}
