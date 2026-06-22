package com.enmooy.deepseno.service

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class NsdDevice(
    val name: String,
    val host: String = "",
    val port: Int = 0,
)

@Singleton
class NsdBrowser @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _devices = MutableStateFlow<List<NsdDevice>>(emptyList())
    val devices: StateFlow<List<NsdDevice>> = _devices

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun startBrowsing() {
        stopBrowsing()
        _devices.value = emptyList()
        _isSearching.value = true

        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) { _isSearching.value = false }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { _isSearching.value = false }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onServiceFound(info: NsdServiceInfo) {
                nsdManager?.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val device = NsdDevice(
                            name = info.serviceName,
                            host = info.host?.hostAddress ?: "",
                            port = info.port,
                        )
                        _devices.value = _devices.value + device
                    }
                })
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                _devices.value = _devices.value.filter { it.name != info.serviceName }
            }
        }

        nsdManager?.discoverServices("_korteqo._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopBrowsing() {
        try {
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (_: Exception) {}
        discoveryListener = null
        _isSearching.value = false
    }
}
