package com.nexa.ai.presentation.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexa.ai.R
import com.nexa.ai.domain.model.ToolCall
import com.nexa.ai.domain.model.ToolStatus
import com.nexa.ai.presentation.components.formatTimestamp
import androidx.compose.ui.res.stringResource

/**
 * One tool execution, exactly as reported by the backend.
 *
 * A tool shows as running only while the gateway says it is running; the card never flips to
 * "completed" on a timer. Output is expandable and scrollable so long logs stay readable.
 */
@Composable
fun ToolCallCard(
    tool: ToolCall,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(tool.id) {
        mutableStateOf(tool.isRunning || tool.status == ToolStatus.FAILED)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolStatusIcon(status = tool.status)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                ToolStatusLabel(status = tool.status)
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.action_collapse else R.string.action_expand,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    tool.title?.takeIf { it.isNotBlank() }?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    tool.input?.takeIf { it.isNotBlank() }?.let { input ->
                        ToolOutputBlock(
                            label = stringResource(R.string.label_tool_input),
                            content = input,
                        )
                    }
                    val output = tool.output
                    if (!output.isNullOrBlank()) {
                        ToolOutputBlock(
                            label = stringResource(R.string.label_tool_output),
                            content = output,
                            mono = true,
                        )
                    } else if (tool.isRunning) {
                        Text(
                            text = stringResource(R.string.label_tool_waiting),
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    tool.startedAt?.let { startedAt ->
                        Text(
                            text = formatStartedAt(startedAt, tool.completedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolStatusIcon(status: ToolStatus) {
    when (status) {
        ToolStatus.RUNNING, ToolStatus.PENDING -> CircularProgressIndicator(
            modifier = Modifier.size(15.dp),
            strokeWidth = 1.8.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        ToolStatus.COMPLETED -> Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = stringResource(R.string.label_tool_completed),
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(17.dp),
        )
        ToolStatus.FAILED -> Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = stringResource(R.string.label_tool_failed),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun ToolStatusLabel(status: ToolStatus) {
    val (label, color) = when (status) {
        ToolStatus.PENDING -> stringResource(R.string.label_tool_queued) to MaterialTheme.colorScheme.onSurfaceVariant
        ToolStatus.RUNNING -> stringResource(R.string.label_tool_running) to MaterialTheme.colorScheme.primary
        ToolStatus.COMPLETED -> stringResource(R.string.label_tool_completed) to MaterialTheme.colorScheme.tertiary
        ToolStatus.FAILED -> stringResource(R.string.label_tool_failed) to MaterialTheme.colorScheme.error
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun ToolOutputBlock(
    label: String,
    content: String,
    mono: Boolean = true,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(4.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = if (mono) FontFamily.Monospace else FontFamily.SansSerif,
                    fontSize = if (mono) 11.5.sp else 12.5.sp,
                    lineHeight = 16.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp),
            )
        }
    }
}

private fun formatStartedAt(startedAt: Long, completedAt: Long?): String {
    val start = formatTimestamp(startedAt)
    return when {
        completedAt == null || completedAt <= 0L -> start
        else -> "$start → ${formatTimestamp(completedAt)}"
    }
}
