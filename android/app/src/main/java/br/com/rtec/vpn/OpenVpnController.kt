package br.com.rtec.vpn

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import de.blinkt.openvpn.core.ConfigParser
import de.blinkt.openvpn.core.ConnectionStatus
import de.blinkt.openvpn.core.IOpenVPNServiceInternal
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.ProfileManager
import de.blinkt.openvpn.core.VPNLaunchHelper
import de.blinkt.openvpn.core.VpnStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.StringReader

/**
 * Conecta nos servidores do VPN Gate (OpenVPN) com o motor do "OpenVPN for Android"
 * (ics-openvpn) embutido no próprio RTecVPN. Não precisa de nenhum app extra.
 */
object OpenVpnController {
    private val _state = MutableStateFlow(VpnState.DOWN)
    val state: StateFlow<VpnState> = _state.asStateFlow()
    private val _since = MutableStateFlow<Long?>(null)
    val connectedSince: StateFlow<Long?> = _since.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _bytes = MutableStateFlow(0L to 0L)
    /** Bytes recebidos e enviados na conexão atual. */
    val bytes: StateFlow<Pair<Long, Long>> = _bytes.asStateFlow()

    private var initialized = false

    private val stateListener = object : VpnStatus.StateListener {
        override fun updateState(state: String?, logmessage: String?, localizedResId: Int, level: ConnectionStatus?, intent: Intent?) {
            _message.value = logmessage
            val new = when (level) {
                ConnectionStatus.LEVEL_CONNECTED -> VpnState.UP
                ConnectionStatus.LEVEL_NOTCONNECTED, ConnectionStatus.LEVEL_NONETWORK,
                ConnectionStatus.LEVEL_AUTH_FAILED, ConnectionStatus.UNKNOWN_LEVEL, null -> VpnState.DOWN
                else -> if (state == "NOPROCESS" || state == "EXITING") VpnState.DOWN else VpnState.CONNECTING
            }
            if (new == VpnState.UP && _since.value == null) _since.value = System.currentTimeMillis()
            if (new == VpnState.DOWN) { _since.value = null; _bytes.value = 0L to 0L }
            _state.value = new
        }

        override fun setConnectedVPN(uuid: String?) {}
    }

    private val byteListener = VpnStatus.ByteCountListener { inBytes, outBytes, _, _ ->
        _bytes.value = inBytes to outBytes
    }

    /** Chamado no Application: passa a receber o status do motor OpenVPN. */
    fun init() {
        if (initialized) return
        initialized = true
        VpnStatus.addStateListener(stateListener)
        VpnStatus.addByteCountListener(byteListener)
    }

    /** Conecta com a config .ovpn (a permissão de VPN do Android já deve ter sido dada). */
    suspend fun connect(context: Context, config: String) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val parser = ConfigParser()
        parser.parseConfig(StringReader(config))
        val profile = parser.convertProfile().apply { mName = "VPN Gate" }
        ProfileManager.setTemporaryProfile(app, profile)
        _state.value = VpnState.CONNECTING
        VPNLaunchHelper.startOpenVpn(profile, app, "RTecVPN", true)
    }

    suspend fun disconnect(context: Context) {
        val app = context.applicationContext
        val done = CompletableDeferred<Unit>()
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                runCatching { IOpenVPNServiceInternal.Stub.asInterface(binder).stopVPN(false) }
                done.complete(Unit)
            }

            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        val intent = Intent(app, OpenVPNService::class.java).setAction(OpenVPNService.START_SERVICE)
        if (app.bindService(intent, conn, Context.BIND_AUTO_CREATE)) {
            withTimeoutOrNull(5_000) { done.await() }
            runCatching { app.unbindService(conn) }
        }
        _state.value = VpnState.DOWN
        _since.value = null
    }
}
