package com.nexa.ai.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import com.nexa.ai.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Connectivity surface consumed by ViewModels; implemented by [ConnectivityObserver]. */
interface ConnectivityMonitor {
    val isOnline: StateFlow<Boolean>
}

/**
 * Publishes coarse connectivity so the UI can show an offline banner and refresh
 * conversation history as soon as the device is back online.
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope appScope: CoroutineScope,
) : ConnectivityMonitor {

    private val _isOnline = MutableStateFlow(isCurrentlyOnline())
    override val isOnline: StateFlow<Boolean> = _isOnline

    init {
        appScope.launch {
            networkChanges()
                .conflate()
                .collect { _isOnline.value = isCurrentlyOnline() }
        }
    }

    private fun isCurrentlyOnline(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network: Network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun networkChanges() = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            close()
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(Unit)
            }

            override fun onLost(network: Network) {
                trySend(Unit)
            }

            override fun onUnavailable() {
                trySend(Unit)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        runCatching { manager.registerNetworkCallback(request, callback) }
        trySend(Unit)

        awaitClose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }
}
