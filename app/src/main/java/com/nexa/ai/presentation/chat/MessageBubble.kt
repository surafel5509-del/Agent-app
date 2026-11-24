package com.nexa.ai.presentation.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexa.ai.R
import com.nexa.ai.domain.model.ChatMessage
import com.nexa.ai.domain.model.MessageStatus
import com.nexa.ai.presentation.components.NexaMark
import com.nexa.ai.presentation.components.TypingIndicator
import com.nexa.ai.presentation.components.formatTimestamp
import com.nexa.ai.presentation.theme.NexaGradientEnd
import com.nexa.ai.presentation.theme.NexaGradientStart

private val UserBubbleShape = RoundedCornerShape(
    topStart = 20.dp,
    topEnd = 6.dp,
    bottomEnd = 20.dp,
    bottomStart = 20.dp,
)

private val AssistantBubbleShape = RoundedCornerShape(
    topStart = 6.dp,
    topEnd = 20.dp,
    bottomEnd = 20.dp,
    bottomStart = 20.dp,
)

/** Right-aligned gradient bubble for the user's own message. */
@Composable
fun UserMessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Box(
                modifier = Modifier
                    .clip(UserBubbleShape)
                    .background(
                        brush = Brush.linearGradient(listOf(NexaGradientStart, NexaGradientEnd)),
                    ),
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = when (message.status) {
                    MessageStatus.PENDING -> stringResource(R.string.msg_sending)
                    MessageStatus.ERROR -> stringResource(R.string.msg_not_delivered)
                    else -> formatTimestamp(message.createdAt)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (message.status == MessageStatus.ERROR) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * Assistant turn: tool executions on top, Markdown body below, plus per-message actions.
 * The bubble only claims to be "generating" while the backend message is still in progress.
 */
@Composable
fun AssistantMessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.msg_copied)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        NexaMark(
            size = 28.dp,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 640.dp),
        ) {
            if (message.toolCalls.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    message.toolCalls.forEach { tool ->
                        ToolCallCard(tool = tool)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            if (message.content.isNotBlank()) {
                Surface(
                    shape = AssistantBubbleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    MarkdownText(
                        text = message.content,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        fontSize = 15.sp,
                    )
                }
            } else if (message.isWorking) {
                Surface(
                    shape = AssistantBubbleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    TypingIndicator(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (message.isWorking) {
                        stringResource(R.string.msg_generating)
                    } else {
                        formatTimestamp(message.createdAt)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                message.model?.takeIf { it.isNotBlank() }?.let { model ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "· $model",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } ?: Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(message.content))
                        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                    },
                    enabled = message.content.isNotBlank(),
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.action_copy_response),
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            message.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontStyle = FontStyle.Italic,
                )
            }
        }
    }
}
