package com.nexa.ai.presentation.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexa.ai.BuildConfig
import com.nexa.ai.R
import com.nexa.ai.core.error.AppError
import com.nexa.ai.core.error.ErrorKind
import com.nexa.ai.presentation.components.GradientButton
import com.nexa.ai.presentation.components.NexaMark
import com.nexa.ai.presentation.components.errorTitle

/**
 * Premium sign-in screen.
 *
 * The API URL is prefilled with the gateway and editable; username/password are collected
 * here and only here (never persisted), then exchanged for a Keystore-encrypted token.
 */
@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colorScheme = MaterialTheme.colorScheme

    val healthWarning = state.warning
    LaunchedEffect(healthWarning) {
        if (healthWarning is AppError) {
            snackbarHostState.showSnackbar(
                message = "Signed in, but the server check failed: ${healthWarning.kind}",
            )
            viewModel.consumeWarning()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colorScheme.primaryContainer.copy(alpha = 0.55f),
                        colorScheme.background,
                        colorScheme.background,
                        colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            NexaMark(size = 76.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.login_title),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surface.copy(alpha = 0.94f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NexaTextField(
                        value = state.apiUrl,
                        onValueChange = viewModel::onApiUrlChanged,
                        label = stringResource(R.string.field_api_url),
                        leadingIcon = Icons.Rounded.Dns,
                        keyboardType = KeyboardType.Uri,
                        enabled = !state.isSubmitting,
                        singleLine = true,
                    )
                    NexaTextField(
                        value = state.username,
                        onValueChange = viewModel::onUsernameChanged,
                        label = stringResource(R.string.field_username),
                        leadingIcon = Icons.Rounded.Person,
                        keyboardType = KeyboardType.Email,
                        enabled = !state.isSubmitting,
                        singleLine = true,
                    )
                    NexaTextField(
                        value = state.password,
                        onValueChange = viewModel::onPasswordChanged,
                        label = stringResource(R.string.field_password),
                        leadingIcon = Icons.Rounded.Lock,
                        keyboardType = KeyboardType.Password,
                        enabled = !state.isSubmitting,
                        singleLine = true,
                        visualTransformation = if (state.isPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = viewModel::onPasswordVisibilityToggled) {
                                Icon(
                                    imageVector = if (state.isPasswordVisible) {
                                        Icons.Rounded.VisibilityOff
                                    } else {
                                        Icons.Rounded.Visibility
                                    },
                                    contentDescription = stringResource(
                                        if (state.isPasswordVisible) {
                                            R.string.action_hide_password
                                        } else {
                                            R.string.action_show_password
                                        },
                                    ),
                                )
                            }
                        },
                    )

                    state.error?.let { error -> LoginErrorRow(error, viewModel::consumeError) }

                    Spacer(Modifier.height(2.dp))
                    GradientButton(
                        text = stringResource(R.string.action_sign_in),
                        onClick = viewModel::submit,
                        enabled = state.canSubmit,
                        loading = state.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.login_secure_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.login_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(24.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun NexaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    keyboardType: KeyboardType,
    enabled: Boolean,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = { Icon(leadingIcon, contentDescription = null) },
        trailingIcon = trailingIcon,
        enabled = enabled,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = KeyboardCapitalization.None,
            autoCorrect = false,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )
}

@Composable
private fun LoginErrorRow(error: LoginError, onDismiss: () -> Unit) {
    val message = when (error) {
        LoginError.EmptyUsername -> stringResource(R.string.error_empty_username)
        LoginError.EmptyPassword -> stringResource(R.string.error_empty_password)
        LoginError.InvalidUrl -> stringResource(R.string.error_invalid_url)
        LoginError.HttpsRequired -> stringResource(R.string.error_https_required)
        is LoginError.RequestFailed -> loginRequestFailedMessage(error.error)
    }
    AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_dismiss),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun loginRequestFailedMessage(error: AppError): String = when (error.kind) {
    ErrorKind.UNAUTHORIZED -> stringResource(R.string.error_login_bad_credentials)
    else -> "${errorTitle(error)}. ${error.detail?.take(120).orEmpty()}".trim().trimEnd('.')
}
