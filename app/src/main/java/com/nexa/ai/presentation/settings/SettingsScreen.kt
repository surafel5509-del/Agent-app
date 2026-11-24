package com.nexa.ai.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexa.ai.BuildConfig
import com.nexa.ai.R
import com.nexa.ai.domain.model.ConnectionStatus
import com.nexa.ai.domain.model.ThemeMode
import com.nexa.ai.presentation.components.ErrorPanel
import com.nexa.ai.presentation.components.SectionHeader
import com.nexa.ai.presentation.components.errorMessage
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAgents: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showUrlDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var urlInput by rememberSaveable { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    val cacheClearedMessage = stringResource(R.string.settings_cache_cleared)

    LaunchedEffect(state.cacheCleared) {
        if (state.cacheCleared) {
            snackbarHostState.showSnackbar(cacheClearedMessage)
            viewModel.consumeCacheCleared()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(
                text = stringResource(R.string.settings_section_account),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.Person,
                    title = stringResource(R.string.settings_username),
                    value = state.username ?: stringResource(R.string.settings_unknown),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(
                    icon = Icons.Rounded.Dns,
                    title = stringResource(R.string.settings_api_url),
                    value = state.apiUrl,
                    onClick = {
                        viewModel.clearUrlError()
                        urlInput = state.apiUrl
                        showUrlDialog = true
                    },
                    trailing = {
                        Text(
                            text = stringResource(R.string.action_edit),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ConnectionRow(
                    status = state.connection,
                    onTest = viewModel::testConnection,
                )
            }

            SectionHeader(
                text = stringResource(R.string.settings_section_agent),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.SmartToy,
                    title = stringResource(R.string.settings_agent),
                    value = state.selectedAgent?.displayName
                        ?: stringResource(R.string.settings_agent_default),
                    onClick = onOpenAgents,
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(
                    icon = Icons.Rounded.Code,
                    title = stringResource(R.string.settings_model),
                    value = state.selectedModel?.displayName
                        ?: stringResource(R.string.settings_model_automatic),
                    onClick = { showModelDialog = true },
                    enabled = state.models.isNotEmpty(),
                    supporting = if (state.models.isEmpty()) {
                        stringResource(R.string.settings_model_unavailable)
                    } else {
                        null
                    },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }

            SectionHeader(
                text = stringResource(R.string.settings_section_appearance),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_theme),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = mode == state.themeMode,
                                onClick = { viewModel.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ThemeMode.entries.size,
                                ),
                            ) {
                                Text(
                                    text = when (mode) {
                                        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                        ThemeMode.DARK -> stringResource(R.string.theme_dark)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_dynamic_color),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = stringResource(R.string.settings_dynamic_color_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.dynamicColor,
                            onCheckedChange = viewModel::setDynamicColor,
                        )
                    }
                }
            }

            SectionHeader(
                text = stringResource(R.string.settings_section_data),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.Refresh,
                    title = stringResource(R.string.settings_refresh_history),
                    value = stringResource(R.string.settings_refresh_history_hint),
                    onClick = viewModel::refreshHistory,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(
                    icon = Icons.Rounded.DeleteOutline,
                    title = stringResource(R.string.settings_clear_cache),
                    value = stringResource(R.string.settings_clear_cache_hint),
                    onClick = { showClearCacheDialog = true },
                    destructive = true,
                )
            }

            SectionHeader(
                text = stringResource(R.string.settings_section_session),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.Logout,
                    title = stringResource(R.string.settings_sign_out),
                    value = stringResource(R.string.settings_sign_out_hint),
                    onClick = { showSignOutDialog = true },
                    destructive = true,
                )
            }

            SectionHeader(
                text = stringResource(R.string.settings_section_about),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.app_name),
                    value = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    onClick = onOpenAbout,
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text(stringResource(R.string.settings_api_url)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.settings_api_url_dialog_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = {
                            urlInput = it
                            if (state.urlError != null) viewModel.clearUrlError()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.field_api_url)) },
                        isError = state.urlError != null,
                        supportingText = state.urlError?.let { error ->
                            {
                                Text(
                                    text = errorMessage(error),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (viewModel.saveApiUrl(urlInput)) {
                        showUrlDialog = false
                    }
                }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.clearUrlError()
                    showUrlDialog = false
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showModelDialog) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text(stringResource(R.string.settings_model)) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    item {
                        ModelRow(
                            title = stringResource(R.string.settings_model_automatic),
                            subtitle = stringResource(R.string.settings_model_automatic_hint),
                            selected = state.selectedModel == null,
                            onClick = {
                                viewModel.selectModel(null)
                                showModelDialog = false
                            },
                        )
                    }
                    items(items = state.models, key = { model -> model.stableKey }) { model ->
                        ModelRow(
                            title = model.displayName,
                            subtitle = model.providerName ?: model.providerId,
                            selected = state.selectedModel?.stableKey == model.stableKey,
                            onClick = {
                                viewModel.selectModel(model)
                                showModelDialog = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text(stringResource(R.string.settings_clear_cache)) },
            text = { Text(stringResource(R.string.settings_clear_cache_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheDialog = false
                        viewModel.clearCache()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.action_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(stringResource(R.string.settings_sign_out)) },
            text = { Text(stringResource(R.string.settings_sign_out_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        viewModel.signOut()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.settings_sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String?,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val supportingText = listOfNotNull(value, supporting).joinToString(" · ")
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    destructive -> MaterialTheme.colorScheme.error
                    enabled -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        supportingContent = if (supportingText.isBlank()) {
            null
        } else {
            {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp),
            )
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                },
            ),
    )
}

@Composable
private fun ConnectionRow(
    status: ConnectionStatus,
    onTest: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Dns,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_connection),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = connectionLabel(status),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (status) {
                        is ConnectionStatus.Online -> MaterialTheme.colorScheme.tertiary
                        is ConnectionStatus.Failed -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (status is ConnectionStatus.Checking) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onTest) {
                    Text(stringResource(R.string.action_test_connection))
                }
            }
        }
        if (status is ConnectionStatus.Failed) {
            Spacer(Modifier.height(8.dp))
            ErrorPanel(error = status.error, onRetry = onTest)
        }
    }
}

@Composable
private fun connectionLabel(status: ConnectionStatus): String = when (status) {
    is ConnectionStatus.Idle -> stringResource(R.string.settings_connection_idle)
    is ConnectionStatus.Checking -> stringResource(R.string.settings_connection_checking)
    is ConnectionStatus.Online -> stringResource(
        R.string.settings_connection_online,
        status.health.status,
        status.health.latencyMillis,
    )
    is ConnectionStatus.Failed -> stringResource(R.string.settings_connection_failed)
}

@Composable
private fun ModelRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = if (selected) {
            {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.agents_selected),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            null
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick),
    )
}
