package com.nexa.ai.presentation.chat

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import com.nexa.ai.R
import com.nexa.ai.core.markdown.InlineNode
import com.nexa.ai.core.markdown.InlineType
import com.nexa.ai.core.markdown.MarkdownBlock
import com.nexa.ai.core.markdown.MarkdownParser
import com.nexa.ai.core.markdown.SyntaxHighlighter
import com.nexa.ai.core.markdown.TokenType
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy

/** Theme-aware colours used by the code highlighter. */
@Immutable
data class CodeColors(
    val comment: Color,
    val keyword: Color,
    val string: Color,
    val number: Color,
    val function: Color,
    val type: Color,
    val annotation: Color,
    val property: Color,
    val plain: Color,
    val punctuation: Color,
) {
    fun colorFor(tokenType: TokenType): Color = when (tokenType) {
        TokenType.COMMENT -> comment
        TokenType.KEYWORD -> keyword
        TokenType.STRING -> string
        TokenType.NUMBER -> number
        TokenType.FUNCTION -> function
        TokenType.TYPE -> this.type
        TokenType.ANNOTATION -> annotation
        TokenType.PROPERTY -> property
        TokenType.PUNCTUATION -> punctuation
        TokenType.PLAIN -> plain
    }
}

@Composable
fun rememberCodeColors(): CodeColors {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        CodeColors(
            comment = scheme.onSurfaceVariant.copy(alpha = 0.65f),
            keyword = scheme.primary,
            string = scheme.tertiary,
            number = scheme.secondary,
            function = scheme.primary.copy(alpha = 0.85f),
            type = scheme.secondary,
            annotation = scheme.tertiary,
            property = scheme.primary,
            plain = scheme.onSurface,
            punctuation = scheme.onSurfaceVariant,
        )
    }
}

/**
 * Renders assistant Markdown: headings, emphasis, inline code, lists, quotes, tables and
 * fenced code blocks with language-aware syntax highlighting and per-block copy.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    onCopyCode: (String) -> Unit = {},
) {
    val blocks = remember(text) { MarkdownParser.parse(text) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.forEach { block -> MarkdownBlockView(block, fontSize, onCopyCode) }
    }
}

@Composable
private fun MarkdownBlockView(
    block: MarkdownBlock,
    fontSize: TextUnit,
    onCopyCode: (String) -> Unit,
) {
    when (block) {
        is MarkdownBlock.Paragraph -> InlineText(
            spans = block.spans,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = fontSize,
        )
        is MarkdownBlock.Heading -> {
            val style = when (block.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleLarge
                3 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            }
            InlineText(
                spans = block.spans,
                style = style.copy(fontWeight = FontWeight.Bold),
                fontSize = fontSize,
            )
        }
        is MarkdownBlock.CodeBlock -> CodeBlockView(
            language = block.language,
            code = block.code,
            onCopy = onCopyCode,
        )
        is MarkdownBlock.Bullets -> BulletListView(items = block.items, fontSize = fontSize)
        is MarkdownBlock.Numbered -> NumberedListView(items = block.items, fontSize = fontSize)
        is MarkdownBlock.Quote -> QuoteView(spans = block.spans, fontSize = fontSize)
        is MarkdownBlock.Table -> TableView(table = block)
        MarkdownBlock.Divider -> HorizontalDivider(
            modifier = Modifier.padding(vertical = 2.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Inline text
// ---------------------------------------------------------------------------

private data class LinkRange(val start: Int, val end: Int, val url: String)

private data class RichText(val annotated: AnnotatedString, val links: List<LinkRange>)

@Composable
private fun InlineText(
    spans: List<InlineNode>,
    modifier: Modifier = Modifier,
    style: TextStyle,
    fontSize: TextUnit = TextUnit.Unspecified,
) {
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val codeColor = MaterialTheme.colorScheme.primary
    val linkColor = MaterialTheme.colorScheme.primary
    val richText = remember(spans, codeBackground, codeColor, linkColor) {
        buildRichText(spans, codeBackground, codeColor, linkColor)
    }

    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val cannotOpenLink = stringResource(R.string.msg_cannot_open_link)

    Text(
        text = richText.annotated,
        modifier = modifier.pointerInput(richText.links) {
            detectTapGestures { offset ->
                val layout = layoutResult.value ?: return@detectTapGestures
                val textOffset = layout.getOffsetForPosition(offset)
                val link = richText.links.firstOrNull { textOffset in it.start until it.end }
                if (link != null) {
                    val opened = runCatching { uriHandler.openUri(link.url) }.isSuccess
                    if (!opened) {
                        Toast.makeText(context, cannotOpenLink, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        },
        color = Color.Unspecified,
        fontSize = fontSize,
        style = style,
        onTextLayout = { layoutResult.value = it },
    )
}

private fun buildRichText(
    spans: List<InlineNode>,
    codeBackground: Color,
    codeColor: Color,
    linkColor: Color,
): RichText {
    val links = ArrayList<LinkRange>()
    val annotated = buildAnnotatedString {
        fun appendNodes(
            nodes: List<InlineNode>,
            bold: Boolean,
            italic: Boolean,
            strike: Boolean,
        ) {
            nodes.forEach { node ->
                when (node.type) {
                    InlineType.TEXT -> {
                        val fontWeight = if (bold) FontWeight.Bold else null
                        val fontStyle = if (italic) FontStyle.Italic else null
                        val decoration = if (strike) TextDecoration.LineThrough else null
                        if (fontWeight != null || fontStyle != null || decoration != null) {
                            withStyle(
                                SpanStyle(
                                    fontWeight = fontWeight,
                                    fontStyle = fontStyle,
                                    textDecoration = decoration,
                                ),
                            ) { append(node.text) }
                        } else {
                            append(node.text)
                        }
                    }
                    InlineType.BOLD -> appendNodes(
                        node.children.ifEmpty { listOf(InlineNode(InlineType.TEXT, node.text)) },
                        bold = true,
                        italic = italic,
                        strike = strike,
                    )
                    InlineType.ITALIC -> appendNodes(
                        node.children.ifEmpty { listOf(InlineNode(InlineType.TEXT, node.text)) },
                        bold = bold,
                        italic = true,
                        strike = strike,
                    )
                    InlineType.STRIKE -> appendNodes(
                        node.children.ifEmpty { listOf(InlineNode(InlineType.TEXT, node.text)) },
                        bold = bold,
                        italic = italic,
                        strike = true,
                    )
                    InlineType.CODE -> withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                            color = codeColor,
                            fontSize = 13.sp,
                        ),
                    ) {
                        append(" ")
                        append(node.text)
                        append(" ")
                    }
                    InlineType.LINK -> {
                        val start = length
                        withStyle(
                            SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium,
                            ),
                        ) {
                            append(node.text)
                        }
                        node.url?.let { url -> links += LinkRange(start, length, url) }
                    }
                }
            }
        }
        appendNodes(spans, bold = false, italic = false, strike = false)
    }
    return RichText(annotated, links)
}

// ---------------------------------------------------------------------------
// Lists, quotes, tables
// ---------------------------------------------------------------------------

@Composable
private fun BulletListView(items: List<List<InlineNode>>, fontSize: TextUnit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { spans ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(16.dp),
                )
                InlineText(
                    spans = spans,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = fontSize,
                )
            }
        }
    }
}

@Composable
private fun NumberedListView(items: List<List<InlineNode>>, fontSize: TextUnit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { index, spans ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(20.dp),
                )
                InlineText(
                    spans = spans,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = fontSize,
                )
            }
        }
    }
}

@Composable
private fun QuoteView(spans: List<InlineNode>, fontSize: TextUnit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(10.dp))
        InlineText(
            spans = spans,
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            fontSize = fontSize,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TableView(table: MarkdownBlock.Table) {
    val columnCount = maxOf(
        table.header.size,
        table.rows.maxOfOrNull { it.size } ?: 0,
    )
    if (columnCount == 0) return

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row {
                table.header.forEach { cell ->
                    Text(
                        text = cell,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp),
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            table.rows.forEach { row ->
                Row {
                    repeat(columnCount) { index ->
                        Text(
                            text = row.getOrElse(index) { "" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Code block
// ---------------------------------------------------------------------------

@Composable
fun CodeBlockView(
    language: String?,
    code: String,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberCodeColors()
    val clipboard = LocalClipboardManager.current
    val copiedMessage = stringResource(R.string.msg_copied)
    val context = LocalContext.current

    val trimmedCode = remember(code) { code.trimEnd() }
    val totalLines = remember(trimmedCode) { trimmedCode.lineSequence().count() }
    var expanded by remember(trimmedCode) {
        mutableStateOf(totalLines <= COLLAPSED_LINE_LIMIT)
    }
    val visibleCode = if (expanded) {
        trimmedCode
    } else {
        trimmedCode.lineSequence().take(COLLAPSED_LINE_LIMIT).joinToString("\n")
    }

    val tokens = remember(visibleCode, language) {
        SyntaxHighlighter.tokenize(visibleCode, language)
    }
    val annotated = remember(tokens, colors) {
        buildAnnotatedString {
            tokens.forEach { token ->
                withStyle(SpanStyle(color = colors.colorFor(token.type))) { append(token.text) }
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = language?.uppercase().orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (totalLines > COLLAPSED_LINE_LIMIT) {
                    TextButton(
                        onClick = { expanded = !expanded },
                    ) {
                        Text(
                            text = if (expanded) {
                                stringResource(R.string.action_show_less)
                            } else {
                                stringResource(R.string.action_show_more)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(trimmedCode))
                    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                    onCopy(trimmedCode)
                }) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.action_copy_code),
                        modifier = Modifier.padding(2.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SelectionContainer {
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    ),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                )
            }
        }
    }
}

private const val COLLAPSED_LINE_LIMIT = 28
