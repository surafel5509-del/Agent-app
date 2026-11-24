package com.nexa.ai

import com.nexa.ai.core.markdown.InlineType
import com.nexa.ai.core.markdown.MarkdownBlock
import com.nexa.ai.core.markdown.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `fenced code blocks keep their language and content`() {
        val blocks = MarkdownParser.parse(
            """
            Here you go:

            ```kotlin
            fun main() {
                println("hi")
            }
            ```

            Done.
            """.trimIndent(),
        )

        assertEquals(3, blocks.size)
        val code = blocks[1] as MarkdownBlock.CodeBlock
        assertEquals("kotlin", code.language)
        assertTrue(code.code.contains("println(\"hi\")"))
        assertTrue(blocks[2] is MarkdownBlock.Paragraph)
    }

    @Test
    fun `unclosed code fence still renders the code`() {
        val blocks = MarkdownParser.parse("```bash\necho hello")
        assertEquals(1, blocks.size)
        val code = blocks[0] as MarkdownBlock.CodeBlock
        assertEquals("echo hello", code.code)
    }

    @Test
    fun `headings lists quotes and dividers are recognised`() {
        val blocks = MarkdownParser.parse(
            """
            # Title
            ## Subtitle

            - one
            - two

            1. first
            2. second

            > quoted text

            ---
            """.trimIndent(),
        )

        assertTrue(blocks[0] is MarkdownBlock.Heading && (blocks[0] as MarkdownBlock.Heading).level == 1)
        assertTrue(blocks[1] is MarkdownBlock.Heading && (blocks[1] as MarkdownBlock.Heading).level == 2)
        val bullets = blocks[2] as MarkdownBlock.Bullets
        assertEquals(2, bullets.items.size)
        val numbered = blocks[3] as MarkdownBlock.Numbered
        assertEquals(2, numbered.items.size)
        assertTrue(blocks[4] is MarkdownBlock.Quote)
        assertEquals(MarkdownBlock.Divider, blocks[5])
    }

    @Test
    fun `markdown tables are parsed with header and rows`() {
        val blocks = MarkdownParser.parse(
            """
            | File | Lines |
            |------|-------|
            | A.kt | 10 |
            | B.kt | 20 |
            """.trimIndent(),
        )

        val table = blocks.single() as MarkdownBlock.Table
        assertEquals(listOf("File", "Lines"), table.header)
        assertEquals(2, table.rows.size)
        assertEquals("A.kt", table.rows[0][0])
        assertEquals("20", table.rows[1][1])
    }

    @Test
    fun `inline emphasis code and links are parsed`() {
        val spans = MarkdownParser.parseInline("Plain **bold** *italic* `code` [docs](https://example.com)")

        assertEquals(8, spans.size)
        assertEquals(InlineType.TEXT, spans[0].type)
        assertEquals("Plain ", spans[0].text)
        assertEquals(InlineType.BOLD, spans[1].type)
        assertEquals("bold", spans[1].text)
        assertEquals(InlineType.TEXT, spans[2].type)
        assertEquals(" ", spans[2].text)
        assertEquals(InlineType.ITALIC, spans[3].type)
        assertEquals("italic", spans[3].text)
        assertEquals(InlineType.TEXT, spans[4].type)
        assertEquals(InlineType.CODE, spans[5].type)
        assertEquals("code", spans[5].text)
        assertEquals(InlineType.TEXT, spans[6].type)
        assertEquals(InlineType.LINK, spans[7].type)
        assertEquals("https://example.com", spans[7].url)
    }

    @Test
    fun `unbalanced markers stay literal text`() {
        val spans = MarkdownParser.parseInline("2 * 3 = 6 and _under")
        assertEquals("2 * 3 = 6 and _under", spans.joinToString("") { it.text })
    }

    @Test
    fun `blank input produces no blocks`() {
        assertTrue(MarkdownParser.parse("   ").isEmpty())
    }
}
