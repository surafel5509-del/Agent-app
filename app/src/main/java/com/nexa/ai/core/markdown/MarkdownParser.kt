package com.nexa.ai.core.markdown

/** Inline emphasis / code / link types supported by the renderer. */
enum class InlineType { TEXT, BOLD, ITALIC, CODE, LINK, STRIKE }

data class InlineNode(
    val type: InlineType,
    val text: String,
    val children: List<InlineNode> = emptyList(),
    val url: String? = null,
)

/** Block-level document model produced by [MarkdownParser]. */
sealed interface MarkdownBlock {
    data class Paragraph(val spans: List<InlineNode>) : MarkdownBlock
    data class Heading(val level: Int, val spans: List<InlineNode>) : MarkdownBlock
    data class CodeBlock(val language: String?, val code: String) : MarkdownBlock
    data class Bullets(val items: List<List<InlineNode>>) : MarkdownBlock
    data class Numbered(val items: List<List<InlineNode>>) : MarkdownBlock
    data class Quote(val spans: List<InlineNode>) : MarkdownBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MarkdownBlock
    data object Divider : MarkdownBlock
}

/**
 * Small, dependency-free Markdown parser tuned for assistant output:
 * headings, emphasis, inline code, fenced code with a language tag, lists,
 * block quotes, GFM tables and horizontal rules.
 */
object MarkdownParser {

    private val headingRegex = Regex("^(#{1,6})\\s+(.*)$")
    private val bulletRegex = Regex("^\\s{0,3}[-*+]\\s+(.*)$")
    private val orderedRegex = Regex("^\\s{0,3}\\d{1,3}[.)]\\s+(.*)$")
    private val dividerRegex = Regex("^\\s{0,3}([-*_])\\s*(?:\\1\\s*){2,}$")
    private val tableSeparatorRegex = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")

    fun parse(text: String): List<MarkdownBlock> {
        if (text.isBlank()) return emptyList()

        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val blocks = ArrayList<MarkdownBlock>()
        val paragraph = ArrayList<String>()

        var index = 0
        while (index < lines.size) {
            val line = lines[index]

            // Fenced code block -------------------------------------------------------------
            if (line.trimStart().startsWith("```")) {
                flushParagraph(paragraph, blocks)
                val language = line.trimStart().removePrefix("```").trim().take(24).ifBlank { null }
                val code = StringBuilder()
                index++
                var closed = false
                while (index < lines.size) {
                    val current = lines[index]
                    if (current.trimStart().startsWith("```")) {
                        closed = true
                        index++
                        break
                    }
                    code.append(current).append('\n')
                    index++
                }
                if (!closed) index = lines.size
                blocks += MarkdownBlock.CodeBlock(language, code.toString().trimEnd('\n'))
                continue
            }

            // Blank line --------------------------------------------------------------------
            if (line.isBlank()) {
                flushParagraph(paragraph, blocks)
                index++
                continue
            }

            // Heading -----------------------------------------------------------------------
            val heading = headingRegex.find(line)
            if (heading != null) {
                flushParagraph(paragraph, blocks)
                blocks += MarkdownBlock.Heading(
                    level = heading.groupValues[1].length,
                    spans = parseInline(heading.groupValues[2].trim()),
                )
                index++
                continue
            }

            // Horizontal rule ---------------------------------------------------------------
            if (dividerRegex.matches(line)) {
                flushParagraph(paragraph, blocks)
                blocks += MarkdownBlock.Divider
                index++
                continue
            }

            // Table --------------------------------------------------------------------------
            val tableCandidate = isTableStart(lines, index)
            if (tableCandidate != null) {
                flushParagraph(paragraph, blocks)
                val (table, nextIndex) = tableCandidate
                blocks += table
                index = nextIndex
                continue
            }

            // Block quote ---------------------------------------------------------------------
            if (line.trimStart().startsWith(">")) {
                flushParagraph(paragraph, blocks)
                val quoted = ArrayList<String>()
                while (index < lines.size && lines[index].trimStart().startsWith(">")) {
                    quoted += lines[index].trimStart().removePrefix(">").removePrefix(" ")
                    index++
                }
                blocks += MarkdownBlock.Quote(spans = parseInline(quoted.joinToString(" ")))
                continue
            }

            // Lists ---------------------------------------------------------------------------
            val bullet = bulletRegex.find(line)
            if (bullet != null) {
                flushParagraph(paragraph, blocks)
                val items = ArrayList<List<InlineNode>>()
                while (index < lines.size) {
                    val current = lines[index]
                    val match = bulletRegex.find(current) ?: break
                    items += parseInline(match.groupValues[1].trim())
                    index++
                }
                blocks += MarkdownBlock.Bullets(items)
                continue
            }

            val ordered = orderedRegex.find(line)
            if (ordered != null) {
                flushParagraph(paragraph, blocks)
                val items = ArrayList<List<InlineNode>>()
                while (index < lines.size) {
                    val current = lines[index]
                    val match = orderedRegex.find(current) ?: break
                    items += parseInline(match.groupValues[1].trim())
                    index++
                }
                blocks += MarkdownBlock.Numbered(items)
                continue
            }

            paragraph += line.trim()
            index++
        }

        flushParagraph(paragraph, blocks)
        return blocks
    }

    private fun flushParagraph(paragraph: MutableList<String>, blocks: MutableList<MarkdownBlock>) {
        if (paragraph.isEmpty()) return
        blocks += MarkdownBlock.Paragraph(spans = parseInline(paragraph.joinToString(" ")))
        paragraph.clear()
    }

    private fun isTableStart(lines: List<String>, index: Int): Pair<MarkdownBlock.Table, Int>? {
        val headerLine = lines.getOrNull(index) ?: return null
        val separatorLine = lines.getOrNull(index + 1) ?: return null
        if (!headerLine.contains('|')) return null
        if (!tableSeparatorRegex.matches(separatorLine)) return null

        val header = splitRow(headerLine)
        if (header.isEmpty()) return null

        var cursor = index + 2
        val rows = ArrayList<List<String>>()
        while (cursor < lines.size) {
            val row = lines[cursor]
            if (row.isBlank() || !row.contains('|')) break
            rows += splitRow(row)
            cursor++
        }
        return MarkdownBlock.Table(header = header, rows = rows) to cursor
    }

    private fun splitRow(line: String): List<String> {
        var trimmed = line.trim()
        if (trimmed.startsWith("|")) trimmed = trimmed.substring(1)
        if (trimmed.endsWith("|")) trimmed = trimmed.substring(0, trimmed.length - 1)
        return trimmed.split('|').map { it.trim() }
    }

    // ------------------------------------------------------------------------------------
    // Inline parsing
    // ------------------------------------------------------------------------------------

    fun parseInline(text: String): List<InlineNode> {
        if (text.isEmpty()) return emptyList()

        val nodes = ArrayList<InlineNode>()
        val buffer = StringBuilder()

        fun flushText() {
            if (buffer.isNotEmpty()) {
                nodes += InlineNode(InlineType.TEXT, buffer.toString())
                buffer.clear()
            }
        }

        var i = 0
        while (i < text.length) {
            val ch = text[i]

            // Escaped character
            if (ch == '\\' && i + 1 < text.length) {
                buffer.append(text[i + 1])
                i += 2
                continue
            }

            // Inline code
            if (ch == '`') {
                val ticks = runLength(text, i, '`')
                val closing = findClosingRun(text, i + ticks, '`', ticks)
                if (closing != null && closing > i + ticks) {
                    flushText()
                    nodes += InlineNode(InlineType.CODE, text.substring(i + ticks, closing))
                    i = closing + ticks
                    continue
                }
            }

            // Bold / italic
            if (ch == '*' || ch == '_') {
                val count = runLength(text, i, ch)
                if (count >= 2) {
                    val closing = findClosingRun(text, i + count, ch, count)
                    if (closing != null && closing > i + count) {
                        val innerText = text.substring(i + count, closing)
                        flushText()
                        nodes += InlineNode(
                            type = InlineType.BOLD,
                            text = innerText,
                            children = parseInline(innerText),
                        )
                        i = closing + count
                        continue
                    }
                } else {
                    val closing = findClosingRun(text, i + 1, ch, 1)
                    if (closing != null && closing > i + 1) {
                        val innerText = text.substring(i + 1, closing)
                        flushText()
                        nodes += InlineNode(
                            type = InlineType.ITALIC,
                            text = innerText,
                            children = parseInline(innerText),
                        )
                        i = closing + 1
                        continue
                    }
                }
                buffer.append(ch)
                i += count
                continue
            }

            // Strikethrough
            if (ch == '~' && i + 1 < text.length && text[i + 1] == '~') {
                val closing = text.indexOf("~~", i + 2)
                if (closing > i + 2) {
                    val innerText = text.substring(i + 2, closing)
                    flushText()
                    nodes += InlineNode(
                        type = InlineType.STRIKE,
                        text = innerText,
                        children = parseInline(innerText),
                    )
                    i = closing + 2
                    continue
                }
            }

            // Link: [label](url)
            if (ch == '[') {
                val labelEnd = text.indexOf(']', i + 1)
                if (labelEnd > i && labelEnd + 1 < text.length && text[labelEnd + 1] == '(') {
                    val urlEnd = text.indexOf(')', labelEnd + 2)
                    if (urlEnd > labelEnd + 2) {
                        val label = text.substring(i + 1, labelEnd)
                        val url = text.substring(labelEnd + 2, urlEnd).trim()
                        flushText()
                        nodes += InlineNode(
                            type = InlineType.LINK,
                            text = label,
                            children = parseInline(label),
                            url = url,
                        )
                        i = urlEnd + 1
                        continue
                    }
                }
            }

            buffer.append(ch)
            i++
        }

        flushText()
        return nodes
    }

    private fun runLength(text: String, start: Int, char: Char): Int {
        var count = 0
        var cursor = start
        while (cursor < text.length && text[cursor] == char) {
            count++
            cursor++
        }
        return count
    }

    /**
     * Finds the next run of exactly [count] occurrences of [char] starting at [from].
     * A longer run (e.g. `***`) is skipped so `**bold**` and `*italic*` do not collide.
     */
    private fun findClosingRun(text: String, from: Int, char: Char, count: Int): Int? {
        var cursor = from
        while (cursor < text.length) {
            val next = text.indexOf(char, cursor)
            if (next < 0) return null
            val length = runLength(text, next, char)
            if (length == count) return next
            cursor = next + length
        }
        return null
    }
}
