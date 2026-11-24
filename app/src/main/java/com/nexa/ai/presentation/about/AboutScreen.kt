package com.nexa.ai.presentation.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexa.ai.BuildConfig
import com.nexa.ai.R
import com.nexa.ai.domain.model.ConnectionStatus
import com.nexa.ai.presentation.components.NexaMark

/**
 * About screen: version, stack, security model and an honest list of what this release does
 * and does not implement (the gateway exposes text + tool calls only).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val health by viewModel.health.collectAsStateWithLifecycle()

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
                        text = stringResource(R.string.about_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            NexaMark(size = 72.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.about_backend),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.about_backend_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (health) {
                                is ConnectionStatus.Online -> stringResource(
                                    R.string.about_server_status_ok,
                                    (health as ConnectionStatus.Online).health.latencyMillis,
                                )
                                is ConnectionStatus.Failed -> stringResource(R.string.about_server_status_down)
                                is ConnectionStatus.Checking -> stringResource(R.string.about_server_status_checking)
                                is ConnectionStatus.Idle -> stringResource(R.string.settings_connection_idle)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = when (health) {
                                is ConnectionStatus.Online -> MaterialTheme.colorScheme.tertiary
                                is ConnectionStatus.Failed -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::checkServer) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.action_test_connection))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            AboutList(
                title = stringResource(R.string.about_supported),
                items = listOf(
                    stringResource(R.string.about_feature_chat),
                    stringResource(R.string.about_feature_history),
                    stringResource(R.string.about_feature_agents),
                    stringResource(R.string.about_feature_tools),
                    stringResource(R.string.about_feature_offline),
                    stringResource(R.string.about_feature_security),
                ),
                icon = Icons.Rounded.Check,
                iconTint = MaterialTheme.colorScheme.tertiary,
            )

            Spacer(Modifier.height(16.dp))
            AboutList(
                title = stringResource(R.string.about_not_supported),
                items = listOf(
                    stringResource(R.string.about_missing_images),
                    stringResource(R.string.about_missing_files),
                    stringResource(R.string.about_missing_voice),
                    stringResource(R.string.about_missing_streaming),
                ),
                icon = Icons.Rounded.Close,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            AboutList(
                title = stringResource(R.string.about_stack),
                items = listOf(
                    "Kotlin · Jetpack Compose · Material 3",
                    "MVVM + Clean Architecture · Hilt",
                    "Retrofit · OkHttp · kotlinx.serialization",
                    "Room · DataStore · Android Keystore",
                ),
                icon = Icons.Rounded.Check,
                iconTint = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun AboutList(
    title: String,
    items: List<String>,
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            items.forEach { item ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(15.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
