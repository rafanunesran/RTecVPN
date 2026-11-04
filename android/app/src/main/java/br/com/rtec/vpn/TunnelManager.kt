package br.com.rtec.vpn

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class VpnState { DOWN, CONNECTING, UP }

/** Ponto único que liga/desliga o túnel WireGuard. */
object TunnelManager {
    private var backend: GoBackend? = null
    private val mutex = Mutex()
    private val _state = MutableStateFlow(VpnState.DOWN)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    private val tunnel = object : Tunnel {
        override fun getName() = "rtecvpn"
        override fun onStateChange(newState: Tunnel.State) {
            _state.value = if (newState == Tunnel.State.UP) VpnState.UP else VpnState.DOWN
        }
    }

    private fun backend(context: Context): GoBackend =
        backend ?: GoBackend(context.applicationContext).also { backend = it }

    fun parse(text: String): Config = Config.parse(text.byteInputStream())

    /** Lança exceção se a config for inválida ou a permissão de VPN não tiver sido concedida. */
    suspend fun connect(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val text = Prefs(context).configText ?: error("Nenhuma configuração salva")
            _state.value = VpnState.CONNECTING
            try {
                backend(context).setState(tunnel, Tunnel.State.UP, parse(text))
                _state.value = VpnState.UP
            } catch (e: Exception) {
                _state.value = VpnState.DOWN
                throw e
            }
        }
    }

    suspend fun disconnect(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            backend(context).setState(tunnel, Tunnel.State.DOWN, null)
            _state.value = VpnState.DOWN
        }
    }

    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        val s = backend(context).getState(tunnel)
        _state.value = if (s == Tunnel.State.UP) VpnState.UP else VpnState.DOWN
    }
}
