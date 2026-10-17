package com.batmobilerc

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.PatternMatcher

/**
 * Se conecta a la WiFi del Batmobile (SSID tipo "XXXX-BATMOBILE", clave por defecto "BATMOBILE1") y
 * entrega la [Network] para atar el socket UDP a ella aunque no tenga internet.
 *
 * Modo automático: Android pide confirmación y se conecta él solo.
 * Modo manual: el usuario ya se ha conectado desde Ajustes y aquí solo se localiza esa red.
 */
class WifiConnector(context: Context) {

    interface Listener {
        fun onReady(network: Network)
        fun onLost()
        fun onFailed(message: String)
    }

    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun connect(manual: Boolean, ssid: String, password: String, listener: Listener) {
        release()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (!manual) {
            val spec = WifiNetworkSpecifier.Builder()
            if (ssid.isNotBlank()) {
                spec.setSsid(ssid.trim())
            } else {
                spec.setSsidPattern(PatternMatcher(".*-BATMOBILE", PatternMatcher.PATTERN_ADVANCED_GLOB))
            }
            spec.setWpa2Passphrase(password)
            request.setNetworkSpecifier(spec.build())
        }

        val cb = object : ConnectivityManager.NetworkCallback() {
            private var ready = false

            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
                if (ready) return
                val isBatmobile = lp.linkAddresses.any {
                    it.address.hostAddress?.startsWith("192.168.201.") == true
                }
                if (isBatmobile) {
                    ready = true
                    listener.onReady(network)
                } else if (manual) {
                    listener.onFailed("La WiFi actual no es la del Batmobile. Conéctate a XXXX-BATMOBILE desde Ajustes.")
                }
            }

            override fun onLost(network: Network) {
                val wasReady = ready
                ready = false
                if (wasReady) listener.onLost()
            }

            override fun onUnavailable() {
                listener.onFailed("No se pudo conectar con el Batmobile (¿encendido, con la clave por defecto y sin otro móvil conectado?)")
            }
        }
        callback = cb
        try {
            cm.requestNetwork(request.build(), cb, if (manual) 15_000 else 60_000)
        } catch (e: Exception) {
            callback = null
            listener.onFailed("Error al pedir la red: ${e.message}")
        }
    }

    fun release() {
        callback?.let {
            try {
                cm.unregisterNetworkCallback(it)
            } catch (_: Exception) {
            }
        }
        callback = null
    }
}
