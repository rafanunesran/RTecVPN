package br.com.rtec.vpn

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import de.blinkt.openvpn.api.IOpenVPNAPIService
import de.blinkt.openvpn.api.IOpenVPNStatusCallback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout

/**
 * Conecta nos servidores do VPN Gate (OpenVPN) pelo app gratuito "OpenVPN for Android",
 * usando a API pública dele. O RTecVPN mostra a lista e controla a conexão.
 */
object OpenVpnController {
    const val PACKAGE = "de.blinkt.openvpn"
    private const val TAG = "OpenVpn"

    private var service: IOpenVPNAPIService? = null
    private var binding: CompletableDeferred<IOpenVPNAPIService>? = null
    private val _state = MutableStateFlow(VpnState.DOWN)
    val state: StateFlow<VpnState> = _state.asStateFlow()
    private val _since = MutableStateFlow<Long?>(null)
    val connectedSince: StateFlow<Long?> = _since.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val callback = object : IOpenVPNStatusCallback.Stub() {
        override fun newStatus(uuid: String?, state: String?, message: String?, level: String?) {
            _message.value = message
            val new = when (level) {
                "LEVEL_CONNECTED" -> VpnState.UP
                "LEVEL_NOTCONNECTED", "LEVEL_NONETWORK", "LEVEL_AUTH_FAILED", null -> VpnState.DOWN
                else -> if (state == "NOPROCESS" || state == "EXITING") VpnState.DOWN else VpnState.CONNECTING
            }
            if (new == VpnState.UP && _since.value == null) _since.value = System.currentTimeMillis()
            if (new == VpnState.DOWN) _since.value = null
            _state.value = new
        }
    }

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = IOpenVPNAPIService.Stub.asInterface(binder)
            service = s
            runCatching { s.registerStatusCallback(callback) }.onFailure { Log.w(TAG, "callback", it) }
            binding?.complete(s)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            binding = null
            _state.value = VpnState.DOWN
        }
    }

    suspend fun bind(context: Context): IOpenVPNAPIService {
        service?.let { return it }
        val d = binding ?: CompletableDeferred<IOpenVPNAPIService>().also { binding = it }
        val intent = Intent(IOpenVPNAPIService::class.java.name).setPackage(PACKAGE)
        if (!context.applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            binding = null
            error("Instale o app gratuito \"OpenVPN for Android\" para usar os servidores do VPN Gate")
        }
        return withTimeout(10_000) { d.await() }
    }

    /** Intent para o usuário autorizar o RTecVPN a controlar o OpenVPN for Android (null = já autorizado). */
    suspend fun permissionIntent(context: Context): Intent? = bind(context).prepare(context.packageName)

    suspend fun connect(context: Context, config: String) {
        val s = bind(context)
        _state.value = VpnState.CONNECTING
        s.startVPN(config)
    }

    suspend fun disconnect(context: Context) {
        runCatching { bind(context).disconnect() }
        _state.value = VpnState.DOWN
    }

    /** Liga o callback de status ao abrir o app, se o OpenVPN for Android estiver instalado. */
    suspend fun attach(context: Context) {
        if (isInstalled(context)) runCatching { bind(context) }
    }
}
