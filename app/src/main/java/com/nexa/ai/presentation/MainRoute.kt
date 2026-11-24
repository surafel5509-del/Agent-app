package com.nexa.ai.presentation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexa.ai.domain.model.AuthState
import com.nexa.ai.presentation.auth.LoginScreen
import com.nexa.ai.presentation.components.NexaWordmark
import com.nexa.ai.presentation.main.MainScreen
import com.nexa.ai.presentation.theme.NexaTheme

/**
 * Root of the Compose tree: resolves the auth state, applies the user's theme choice and then
 * hands off to either the login experience or the main chat shell.
 *
 * A 401 anywhere in the app clears the token through [com.nexa.ai.core.network.SessionInvalidator],
 * which flips [AuthState] to [AuthState.LoggedOut] and lands the user back on the login screen
 * with the navigation stack implicitly reset.
 */
@Composable
fun MainRoute(viewModel: RootViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    NexaTheme(
        themeMode = state.themeMode,
        dynamicColor = state.dynamicColor,
    ) {
        Crossfade(
            targetState = state.authState,
            animationSpec = tween(durationMillis = 260),
            label = "auth-crossfade",
        ) { authState ->
            when (authState) {
                is AuthState.Checking -> SplashScreen()
                is AuthState.LoggedOut -> LoginScreen()
                is AuthState.LoggedIn -> MainScreen()
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            NexaWordmark(markSize = 52.dp, subtitle = "Your agent, on the go")
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Restoring your session…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}
