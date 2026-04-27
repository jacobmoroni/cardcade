package com.cardcade.app.games.scum.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Wraps Android NSD so the Scum LAN lobby can advertise and discover peers on
 * the same wifi network.
 */
class LanDiscovery(context: Context) {
    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    private var registrationListener: NsdManager.RegistrationListener? = null

    private val _registered = MutableStateFlow<String?>(null)
    val registeredName: StateFlow<String?> = _registered.asStateFlow()

    fun register(displayName: String, port: Int) {
        unregister()
        val info = NsdServiceInfo().apply {
            serviceName = displayName
            serviceType = SERVICE_TYPE
            this.port = port
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) { _registered.value = info.serviceName }
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) { _registered.value = null }
            override fun onServiceUnregistered(info: NsdServiceInfo) { _registered.value = null }
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun unregister() {
        registrationListener?.let { runCatching { nsd.unregisterService(it) } }
        registrationListener = null
    }

    fun discoverPeers(): Flow<List<Peer>> = callbackFlow {
        val peers = mutableMapOf<String, Peer>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(type: String, code: Int) { close() }
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                resolve(service) { peer ->
                    peers[peer.name] = peer
                    trySend(peers.values.toList())
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                peers.remove(service.serviceName)
                trySend(peers.values.toList())
            }
        }
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }

    private fun resolve(service: NsdServiceInfo, onResolved: (Peer) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            nsd.registerServiceInfoCallback(service, Runnable::run,
                object : NsdManager.ServiceInfoCallback {
                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}
                    override fun onServiceInfoCallbackUnregistered() {}
                    override fun onServiceLost() {}
                    override fun onServiceUpdated(info: NsdServiceInfo) {
                        val host = info.hostAddresses.firstOrNull()?.hostAddress ?: return
                        onResolved(Peer(info.serviceName, host, info.port))
                    }
                })
        } else {
            @Suppress("DEPRECATION")
            nsd.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceResolved(info: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    val host = info.host?.hostAddress ?: return
                    onResolved(Peer(info.serviceName, host, info.port))
                }
            })
        }
    }

    data class Peer(val name: String, val host: String, val port: Int)

    companion object {
        const val SERVICE_TYPE = "_scum._tcp."
    }
}
