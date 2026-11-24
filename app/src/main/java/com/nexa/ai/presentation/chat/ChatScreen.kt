package com.nexa.ai.presentation.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexa.ai.R
import com.nexa.ai.core.error.AppError
import com.nexa.ai.domain.model.MessageRole
import com.nexa.ai.presentation.components.ErrorPanel
import com.nexa.ai.presentation.components.NexaMark
import com.nexa.ai.presentation.components.TypingIndicator
import com.nexa.ai.presentation.components.formatElapsed
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch

private val SUGGESTIONS = listOf(
    "Review this project and list the three most important issues.",
    "Explain the architecture of the codebase and how data flows.",
    "Write unit tests for the authentication flow.",
    "Refactor the repository layer and explain what changed.",
)

/**
 * Main conversation screen: top bar, message list, working indicator, error/retry area and
 * the multiline composer. Auto-scroll follows new tokens but yields as soon as the user
 * scrolls back through the conversation.
 */
@Composable
fun ChatScreen(
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOpenAgents: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var autoScroll by remember { mutableStateOf(true) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onAppResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isNearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= info.totalItemsCount - 2
        }
    }
    val showScrollButton by remember {
        derivedStateOf { !isNearBottom && state.messages.size > 1 }
    }

    // Any manual scroll away from the bottom pauses auto-scroll until the user returns.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (scrolling) {
                    val info = listState.layoutInfo
                    val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                    if (lastVisible < info.totalItemsCount - 3) autoScroll = false
                }
            }
    }

    val lastMessage = state.messages.lastOrNull()
    LaunchedEffect(
        state.messages.size,
        lastMessage?.id,
        lastMessage?.content?.length,
        lastMessage?.toolCalls?.joinToString { it.status.name },
    ) {
        if (autoScroll && state.messages.isNotEmpty()) {
            listState.animateScrollToItem(index = state.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                title = state.title,
                subtitle = state.selectedAgent?.displayName,
                onMenu = onOpenDrawer,
                onNewChat = onNewChat,
                onAgents = onOpenAgents,
                onSettings = onOpenSettings,
                isBusy = state.isBusy,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            if (!state.isOnline) {
                OfflineBanner()
            }

            Box(modifier = Modifier.weight(1f)) {
                if (state.messages.isEmpty()) {
                    EmptyChatState(
                        agentName = state.selectedAgent?.displayName,
                        onSuggestion = viewModel::onInputChanged,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 0.dp,
                            end = 0.dp,
                            top = 12.dp,
                            bottom = 12.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(
                            items = state.messages,
                            key = { message -> message.id },
                        ) { message ->
                            when (message.role) {
                                MessageRole.USER -> UserMessageBubble(message = message)
                                MessageRole.ASSISTANT -> AssistantMessageBubble(message = message)
                                MessageRole.SYSTEM -> Unit
                            }
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showScrollButton,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 },
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                autoScroll = true
                                scope.launch {
                                    listState.animateScrollToItem(state.messages.lastIndex)
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.action_scroll_to_bottom),
                            )
                        }
                    }
                }

                if (state.isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }

            AnimatedVisibility(visible = state.isBusy) {
                WorkingBanner(
                    status = state.status,
                    workingToolCount = state.workingToolCount,
                    onStop = viewModel::stop,
                )
            }

            state.error?.let { error ->
                ErrorPanel(
                    error = error.toAppError(),
                    onRetry = viewModel::retry,
                    onDismiss = viewModel::consumeError,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            MessageComposer(
                value = input,
                onValueChange = viewModel::onInputChanged,
                onSend = viewModel::send,
                onStop = viewModel::stop,
                isBusy = state.isBusy,
                agentLabel = state.selectedAgent?.displayName
                    ?: state.effectiveAgentId.ifBlank { null },
                onAgentClick = onOpenAgents,
                agents = state.agents,
                selectedAgentId = state.effectiveAgentId,
                onSelectAgent = viewModel::selectAgent,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    title: String,
    subtitle: String?,
    isBusy: Boolean,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    onAgents: () -> Unit,
    onSettings: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onMenu) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = stringResource(R.string.action_open_drawer),
                )
            }
        },
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = if (isBusy) "$subtitle · running" else subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isBusy) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onAgents) {
                Icon(
                    imageVector = Icons.Rounded.SmartToy,
                    contentDescription = stringResource(R.string.action_agents),
                )
            }
            IconButton(onClick = onNewChat) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.action_new_chat),
                )
            }
            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.action_settings),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun WorkingBanner(
    status: ChatStatus,
    workingToolCount: Int,
    onStop: () -> Unit,
) {
    val elapsed = (status as? ChatStatus.Running)?.elapsedSeconds ?: 0

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TypingIndicator()
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (status is ChatStatus.Sending) {
                        stringResource(R.string.status_sending)
                    } else {
                        stringResource(R.string.status_working)
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                if (workingToolCount > 0) {
                    Text(
                        text = stringResource(R.string.status_tools_running, workingToolCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (status is ChatStatus.Running) {
                Text(
                    text = formatElapsed(elapsed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = onStop) {
                Icon(
                    imageVector = Icons.Rounded.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.action_stop))
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.banner_offline),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyChatState(
    agentName: String?,
    onSuggestion: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NexaMark(size = 64.dp)
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (agentName != null) {
                stringResource(R.string.empty_subtitle_with_agent, agentName)
            } else {
                stringResource(R.string.empty_subtitle)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SUGGESTIONS.forEach { suggestion ->
                Surface(
                    onClick = { onSuggestion(suggestion) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

private fun ChatError.toAppError(): AppError = when (this) {
    is ChatError.Send -> error
    is ChatError.Poll -> error
    is ChatError.Interrupt -> error
    is ChatError.Validation -> AppError.Validation(message)
    ChatError.Empty -> AppError.EmptyResponse()
    is ChatError.Timeout -> AppError.Timeout()
}
