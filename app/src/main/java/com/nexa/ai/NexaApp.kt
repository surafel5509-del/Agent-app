package com.nexa.ai

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Nexa AI – a native Android client for a self-hosted OpenCode agent.
 *
 * There is deliberately no eager work here: the token is restored by [com.nexa.ai.data.repository.AuthRepositoryImpl]
 * and the theme is resolved in Compose, so cold start stays fast.
 */
@HiltAndroidApp
class NexaApp : Application()
