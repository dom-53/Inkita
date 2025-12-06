package net.dom53.inkita.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.Constraints
import androidx.work.NetworkType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.dom53.inkita.core.storage.AppPreferences
import java.util.concurrent.atomic.AtomicReference

enum class ConnectionType { Wifi, Cellular, Ethernet, Other, None }

data class NetworkStatus(
    val isOnline: Boolean,
    val connectionType: ConnectionType,
    val isMetered: Boolean,
    val isRoaming: Boolean,
    val offlineMode: Boolean,
) {
    val isOnlineAllowed: Boolean
        get() = isOnline && !offlineMode
}

/**
 * Shared connectivity monitor for UI and background work. Supports manual offline mode
 * to suppress network usage and debounces rapid connectivity changes.
 */
class NetworkMonitor private constructor(
    context: Context,
    private val preferences: AppPreferences,
) {
    private val appContext = context.applicationContext
    private val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val connectivityFlow =
        callbackFlow {
            val initial = snapshot()
            trySend(initial)

            if (cm == null) {
                close()
                return@callbackFlow
            }

            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        trySend(snapshot())
                    }

                    override fun onLost(network: Network) {
                        trySend(snapshot())
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities,
                    ) {
                        trySend(snapshot(networkCapabilities))
                    }
                }
            val request =
                NetworkRequest
                    .Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
            try {
                cm.registerNetworkCallback(request, callback)
            } catch (_: SecurityException) {
                trySend(NetworkStatus(false, ConnectionType.None, true, false, offlineMode = false))
            }

            awaitClose {
                runCatching { cm.unregisterNetworkCallback(callback) }
            }
        }.catch { emit(snapshot()) }

    @Suppress("MagicNumber")
    val status: StateFlow<NetworkStatus> =
        combine(connectivityFlow, preferences.offlineModeFlow) { net, offline ->
            net.copy(offlineMode = offline)
        }.debounce(300)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                snapshot().copy(offlineMode = runBlocking { preferences.offlineModeFlow.first() }),
            )

    fun isOnline(): Boolean = status.value.isOnlineAllowed

    fun shouldDeferNetworkWork(): Boolean = !status.value.isOnlineAllowed

    /**
     * Build WorkManager constraints for network work, respecting offline mode and metered preference.
     * Callers should still skip enqueuing when offlineMode is true.
     */
    fun buildConstraints(
        allowMetered: Boolean,
        requireBatteryNotLow: Boolean = false,
    ): Constraints {
        val builder =
            Constraints
                .Builder()
                .setRequiresBatteryNotLow(requireBatteryNotLow)
        if (status.value.offlineMode) {
            builder.setRequiredNetworkType(NetworkType.NOT_REQUIRED)
        } else {
            builder.setRequiredNetworkType(if (allowMetered) NetworkType.CONNECTED else NetworkType.UNMETERED)
        }
        return builder.build()
    }

    private fun snapshot(caps: NetworkCapabilities? = null): NetworkStatus {
        val capabilities =
            caps ?: cm?.activeNetwork?.let { net -> cm.getNetworkCapabilities(net) }
                ?: return NetworkStatus(false, ConnectionType.None, true, false, offlineMode = false)
        val hasInternet =
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val connection =
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.Wifi
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.Cellular
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.Ethernet
                else -> ConnectionType.Other
            }
        val metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val roaming = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
        val online = hasInternet && connection != ConnectionType.None
        return NetworkStatus(
            isOnline = online,
            connectionType = connection,
            isMetered = metered,
            isRoaming = roaming,
            offlineMode = false,
        )
    }

    companion object {
        private val cached = AtomicReference<NetworkMonitor?>()

        fun getInstance(
            context: Context,
            preferences: AppPreferences? = null,
        ): NetworkMonitor {
            val existing = cached.get()
            if (existing != null) return existing
            val prefs = preferences ?: AppPreferences(context.applicationContext)
            val created = NetworkMonitor(context, prefs)
            cached.compareAndSet(null, created)
            return cached.get() ?: created
        }
    }

    fun cancel() {
        scope.cancel()
    }
}
