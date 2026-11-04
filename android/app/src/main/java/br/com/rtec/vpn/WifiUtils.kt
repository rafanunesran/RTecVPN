package br.com.rtec.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build

object WifiUtils {
    /** Remove aspas e descarta o valor "<unknown ssid>" que o Android devolve sem permissão de localização. */
    fun clean(raw: String?): String? {
        val s = raw?.trim()?.removeSurrounding("\"") ?: return null
        return if (s.isEmpty() || s == WifiManager.UNKNOWN_SSID.removeSurrounding("\"") || s == "<unknown ssid>") null else s
    }

    fun ssidFrom(caps: NetworkCapabilities?, context: Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val info = caps?.transportInfo as? WifiInfo
            clean(info?.ssid)?.let { return it }
        }
        @Suppress("DEPRECATION")
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        return clean(wm.connectionInfo?.ssid)
    }

    /** Wi-Fi sem senha (aberto ou OWE). Só dá para saber a partir do Android 12. */
    fun isOpen(caps: NetworkCapabilities?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val info = caps?.transportInfo as? WifiInfo ?: return false
        return info.currentSecurityType == WifiInfo.SECURITY_TYPE_OPEN ||
            info.currentSecurityType == WifiInfo.SECURITY_TYPE_OWE
    }

    /** SSID da rede Wi-Fi atual, ou null se não estiver em Wi-Fi ou faltar permissão. */
    fun currentSsid(context: Context): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return null
        return ssidFrom(caps, context)
    }
}
