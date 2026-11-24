package com.nexa.ai.presentation.navigation

import androidx.navigation.NavHostController

/** Route names for the main graph (chat is the start destination). */
object Routes {
    const val CHAT = "chat"
    const val AGENTS = "agents"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
}

/** Navigates without growing the back stack when the destination is already on top. */
fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) { launchSingleTop = true }
}
