package com.nexa.ai.presentation.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nexa.ai.presentation.about.AboutScreen
import com.nexa.ai.presentation.agents.AgentSelectionScreen
import com.nexa.ai.presentation.chat.ChatScreen
import com.nexa.ai.presentation.history.ConversationHistoryDrawer
import com.nexa.ai.presentation.navigation.Routes
import com.nexa.ai.presentation.navigation.navigateSingleTop
import com.nexa.ai.presentation.settings.SettingsScreen
import kotlinx.coroutines.launch

/**
 * Authenticated shell: conversation history drawer + navigation graph.
 *
 * On wide screens (tablets / unfolded devices) the drawer becomes a permanent 320dp rail so
 * history stays visible while chatting; on phones it is a modal drawer.
 */
@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val configuration = LocalConfiguration.current
    val isWide = configuration.screenWidthDp >= 840

    fun closeDrawerOnPhone() {
        if (!isWide) {
            scope.launch { drawerState.close() }
        }
    }

    @Composable
    fun drawerContent() {
        ConversationHistoryDrawer(
            sessions = state.sessions,
            selectedSessionId = state.selectedSessionId,
            isLoading = state.isLoading,
            error = state.error,
            onNewChat = {
                viewModel.startNewChat()
                navController.navigateSingleTop(Routes.CHAT)
                closeDrawerOnPhone()
            },
            onSelect = { sessionId ->
                viewModel.selectSession(sessionId)
                navController.navigateSingleTop(Routes.CHAT)
                closeDrawerOnPhone()
            },
            onDelete = viewModel::deleteSession,
            onRefresh = viewModel::refresh,
            onDismissError = viewModel::clearError,
            onOpenAgents = {
                navController.navigateSingleTop(Routes.AGENTS)
                closeDrawerOnPhone()
            },
            onOpenSettings = {
                navController.navigateSingleTop(Routes.SETTINGS)
                closeDrawerOnPhone()
            },
            onOpenAbout = {
                navController.navigateSingleTop(Routes.ABOUT)
                closeDrawerOnPhone()
            },
        )
    }

    if (isWide) {
        Row(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                drawerContent()
            }
            Box(modifier = Modifier.fillMaxSize()) {
                NexaNavHost(
                    navController = navController,
                    onMenuClick = {},
                    onStartNewChat = viewModel::startNewChat,
                )
            }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = { drawerContent() },
        ) {
            NexaNavHost(
                navController = navController,
                onMenuClick = { scope.launch { drawerState.open() } },
                onStartNewChat = viewModel::startNewChat,
            )
        }
    }
}

@Composable
private fun NexaNavHost(
    navController: NavHostController,
    onMenuClick: () -> Unit,
    onStartNewChat: () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.CHAT,
    ) {
        composable(Routes.CHAT) {
            ChatScreen(
                onOpenDrawer = onMenuClick,
                onNewChat = {
                    onStartNewChat()
                    navController.navigateSingleTop(Routes.CHAT)
                },
                onOpenAgents = { navController.navigateSingleTop(Routes.AGENTS) },
                onOpenSettings = { navController.navigateSingleTop(Routes.SETTINGS) },
            )
        }
        composable(Routes.AGENTS) {
            AgentSelectionScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAgents = { navController.navigateSingleTop(Routes.AGENTS) },
                onOpenAbout = { navController.navigateSingleTop(Routes.ABOUT) },
            )
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
