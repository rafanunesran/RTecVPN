package br.com.rtec.vpn

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Liga/desliga o servidor selecionado, seja WireGuard próprio ou VPN Gate (OpenVPN). */
object VpnController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val state: StateFlow<VpnState> = combine(TunnelManager.state, OpenVpnController.state) { wg, ovpn ->
        when {
            wg == VpnState.UP || ovpn == VpnState.UP -> VpnState.UP
            wg == VpnState.CONNECTING || ovpn == VpnState.CONNECTING -> VpnState.CONNECTING
            else -> VpnState.DOWN
        }
    }.stateIn(scope, SharingStarted.Eagerly, VpnState.DOWN)

    val connectedSince: StateFlow<Long?> = combine(TunnelManager.connectedSince, OpenVpnController.connectedSince) { a, b -> a ?: b }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** true quando o servidor selecionado é do VPN Gate (OpenVPN embutido). */
    fun needsOpenVpn(context: Context) = Prefs(context).usingVpnGate

    suspend fun connect(context: Context) {
        val prefs = Prefs(context)
        val vg = prefs.selectedVg
        if (prefs.usingVpnGate && vg != null) {
            if (TunnelManager.state.value != VpnState.DOWN) TunnelManager.disconnect(context)
            OpenVpnController.connect(context, vg.config)
        } else {
            if (OpenVpnController.state.value != VpnState.DOWN) OpenVpnController.disconnect(context)
            TunnelManager.connect(context)
        }
    }

    suspend fun disconnect(context: Context) {
        if (TunnelManager.state.value != VpnState.DOWN) TunnelManager.disconnect(context)
        if (OpenVpnController.state.value != VpnState.DOWN) OpenVpnController.disconnect(context)
    }
}
