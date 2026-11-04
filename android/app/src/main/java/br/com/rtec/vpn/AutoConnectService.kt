package br.com.rtec.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Serviço em primeiro plano que observa o Wi-Fi.
 * Ao entrar numa rede da lista, liga a VPN; ao sair dela (ou o Wi-Fi cair), desliga
 * — mas só se tiver sido este serviço que ligou.
 */
class AutoConnectService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var cm: ConnectivityManager
    private lateinit var prefs: Prefs
    /** Redes Wi-Fi atuais e seus SSIDs. */
    private val wifiNetworks = mutableMapOf<Network, String?>()

    private inner class WifiCallback : ConnectivityManager.NetworkCallback {
        constructor() : super()
        @RequiresApi(Build.VERSION_CODES.S)
        constructor(flags: Int) : super(flags)

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val ssid = WifiUtils.ssidFrom(caps, this@AutoConnectService)
            scope.launch { wifiNetworks[network] = ssid; evaluate() }
        }

        override fun onLost(network: Network) {
            scope.launch { wifiNetworks.remove(network); evaluate() }
        }
    }

    private val callback by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            WifiCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO)
        } else {
            WifiCallback()
        }
    }

    override fun onCreate() {
        super.onCreate()
        cm = getSystemService(ConnectivityManager::class.java)
        prefs = Prefs(this)
        startInForeground()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        cm.registerNetworkCallback(request, callback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!prefs.autoConnectEnabled) stopSelf()
        return START_STICKY
    }

    private fun evaluate() {
        val trusted = prefs.trustedSsids
        val current = wifiNetworks.values.filterNotNull().toSet()
        // Usuário desligou na mão nesta rede: respeita até trocar de rede
        prefs.pausedSsid?.let { if (it !in current) prefs.pausedSsid = null }
        val paused = prefs.pausedSsid != null
        val onTrusted = current.any { it in trusted }
        val state = TunnelManager.state.value
        scope.launch {
            try {
                if (onTrusted && !paused && state == VpnState.DOWN) {
                    if (VpnService.prepare(this@AutoConnectService) != null) {
                        Log.w(TAG, "Permissão de VPN ausente; abra o app para conceder")
                        return@launch
                    }
                    TunnelManager.connect(this@AutoConnectService)
                    prefs.autoStarted = true
                } else if (!onTrusted && state != VpnState.DOWN && prefs.autoStarted) {
                    TunnelManager.disconnect(this@AutoConnectService)
                    prefs.autoStarted = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Falha na conexão automática", e)
            }
        }
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.auto_channel), NotificationManager.IMPORTANCE_MIN)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(getString(R.string.auto_notification))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    override fun onDestroy() {
        runCatching { cm.unregisterNetworkCallback(callback) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AutoConnect"
        private const val CHANNEL = "auto_connect"
        private const val NOTIFICATION_ID = 1

        fun sync(context: Context) {
            val intent = Intent(context, AutoConnectService::class.java)
            if (Prefs(context).autoConnectEnabled) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.stopService(intent)
            }
        }
    }
}
