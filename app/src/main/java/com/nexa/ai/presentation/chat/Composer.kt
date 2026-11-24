package com.nexa.ai.presentation.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexa.ai.R
import com.nexa.ai.domain.model.AgentInfo

private val MentionRegex = Regex("(?:^|\\s)@([A-Za-z0-9_-]*)$")

/**
 * Multiline pill composer with an inline agent chip, `@`-mention quick agent switcher
 * (populated strictly from `GET /agents`), and a single send/stop control.
 * While the agent is running the button becomes Stop, so a tap can never send twice.
 */
@Composable
fun MessageComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isBusy: Boolean,
    agentLabel: String?,
    onAgentClick: () -> Unit,
    modifier: Modifier = Modifier,
    agents: List<AgentInfo> = emptyList(),
    selectedAgentId: String? = null,
    onSelectAgent: ((String) -> Unit)? = null,
    enabled: Boolean = true,
) {
    val mentionQuery = remember(value) {
        MentionRegex.find(value)?.groupValues?.getOrNull(1)
    }
    val matchingAgents = remember(mentionQuery, agents) {
        if (mentionQuery == null || agents.isEmpty()) {
            emptyList()
        } else {
            agents.filter { agent ->
                mentionQuery.isEmpty() ||
                    agent.id.contains(mentionQuery, ignoreCase = true) ||
                    agent.displayName.contains(mentionQuery, ignoreCase = true)
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = matchingAgents.isNotEmpty() && onSelectAgent != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            AgentMentionMenu(
                agents = matchingAgents,
                selectedAgentId = selectedAgentId,
                onSelect = { agent ->
                    onSelectAgent?.invoke(agent.id)
                    val stripped = MentionRegex.replace(value) { match ->
                        if (match.value.startsWith("@")) "" else " "
                    }.trimStart()
                    onValueChange(stripped)
                },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (agentLabel != null) {
                    AssistChip(
                        onClick = onAgentClick,
                        label = {
                            Text(
                                text = "@$agentLabel",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.SmartToy,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                    )
                }
                Row(
                    modifier = Modifier.padding(start = 6.dp, end = 10.dp, top = 2.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    TextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.composer_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                        minLines = 1,
                        maxLines = 6,
                        enabled = enabled,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Default,
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                    )
                    Box {
                        if (isBusy) {
                            FilledIconButton(
                                onClick = onStop,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Stop,
                                    contentDescription = stringResource(R.string.action_stop),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        } else {
                            FilledIconButton(
                                onClick = onSend,
                                enabled = enabled && value.isNotBlank(),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowUpward,
                                    contentDescription = stringResource(R.string.action_send),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentMentionMenu(
    agents: List<AgentInfo>,
    selectedAgentId: String?,
    onSelect: (AgentInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 8.dp,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp),
        ) {
            items(items = agents, key = { it.id }) { agent ->
                val isSelected = agent.id == selectedAgentId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(agent) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SmartToy,
                        contentDescription = null,
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "@${agent.id}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        agent.description?.let { description ->
                            Text(
                                text = description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.agents_selected),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}
