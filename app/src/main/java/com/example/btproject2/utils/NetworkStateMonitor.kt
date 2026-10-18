package com.example.btproject2.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper

/**
 * Lifecycle-safe network connectivity monitor for KinTrace.
 * Detects ONLINE, WEAK, and OFFLINE network states in real time.
 */
object NetworkStateMonitor {

    enum class NetworkState {
        ONLINE,
        WEAK,
        OFFLINE
    }

    private var currentState: NetworkState = NetworkState.ONLINE
    private val listeners = mutableSetOf<(NetworkState) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRegistered = false

    private var connectivityManager: ConnectivityManager? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateStateFromActiveNetwork()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            updateStateFromActiveNetwork()
        }

        override fun onLost(network: Network) {
            mainHandler.postDelayed({
                updateStateFromActiveNetwork()
            }, 300L)
        }
    }

    /**
     * Start monitoring connectivity state. Safe to call multiple times.
     */
    @Synchronized
    fun startMonitoring(context: Context) {
        if (isRegistered) return
        val appContext = context.applicationContext
        connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val cm = connectivityManager ?: return

        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, networkCallback)
            isRegistered = true
        } catch (_: Exception) {
            // Fallback for restricted environments
            try {
                cm.registerDefaultNetworkCallback(networkCallback)
                isRegistered = true
            } catch (_: Exception) {}
        }

        updateStateFromActiveNetwork()
    }

    /**
     * Stop monitoring to release resources when application shuts down.
     */
    @Synchronized
    fun stopMonitoring() {
        if (!isRegistered) return
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {}
        isRegistered = false
    }

    /**
     * Synchronously evaluate the current network state.
     */
    fun checkCurrentState(context: Context): NetworkState {
        val cm = (context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
            ?: connectivityManager
            ?: return NetworkState.OFFLINE

        val activeNetwork = cm.activeNetwork ?: return NetworkState.OFFLINE
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return NetworkState.OFFLINE

        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return NetworkState.OFFLINE
        }

        val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val downstreamKbps = caps.linkDownstreamBandwidthKbps

        return when {
            !isValidated -> NetworkState.WEAK
            downstreamKbps in 1..800 -> NetworkState.WEAK
            else -> NetworkState.ONLINE
        }
    }

    fun getCurrentState(): NetworkState = currentState

    /**
     * Register a listener for network state changes. Immediately invokes with current state.
     */
    fun addListener(listener: (NetworkState) -> Unit) {
        synchronized(listeners) {
            listeners.add(listener)
        }
        mainHandler.post { listener(currentState) }
    }

    /**
     * Unregister a listener.
     */
    fun removeListener(listener: (NetworkState) -> Unit) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    private fun updateStateFromActiveNetwork() {
        val cm = connectivityManager ?: return
        val activeNetwork = cm.activeNetwork
        val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }

        val newState = when {
            activeNetwork == null || caps == null -> NetworkState.OFFLINE
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkState.OFFLINE
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> NetworkState.WEAK
            caps.linkDownstreamBandwidthKbps in 1..800 -> NetworkState.WEAK
            else -> NetworkState.ONLINE
        }

        if (newState != currentState) {
            currentState = newState
            mainHandler.post {
                synchronized(listeners) {
                    listeners.forEach { it(newState) }
                }
            }
        }
    }
}
