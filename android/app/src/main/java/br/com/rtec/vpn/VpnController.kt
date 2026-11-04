package br.com.rtec.vpn

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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

    private var vgJob: Job? = null
    private val _notice = MutableStateFlow<String?>(null)
    /** Aviso para a tela (troca automática de servidor ou falha). */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** Tempo para um servidor do VPN Gate conectar antes de tentar o próximo. */
    private const val VG_TIMEOUT_MS = 25_000L
    private const val VG_ATTEMPTS = 5

    /** true quando o servidor selecionado é do VPN Gate (OpenVPN embutido). */
    fun needsOpenVpn(context: Context) = Prefs(context).usingVpnGate

    suspend fun connect(context: Context) {
        val prefs = Prefs(context)
        val vg = prefs.selectedVg
        if (prefs.usingVpnGate && vg != null) {
            if (TunnelManager.state.value != VpnState.DOWN) TunnelManager.disconnect(context)
            vgJob?.cancel()
            _notice.value = null
            val app = context.applicationContext
            vgJob = scope.launch { connectVpnGate(app, vg) }
        } else {
            if (OpenVpnController.state.value != VpnState.DOWN) OpenVpnController.disconnect(context)
            TunnelManager.connect(context)
        }
    }

    /**
     * Muitos servidores voluntários do VPN Gate ficam fora do ar ou bloqueados. Se o escolhido
     * não conectar em [VG_TIMEOUT_MS], tenta os próximos melhores (primeiro do mesmo país).
     */
    private suspend fun connectVpnGate(context: Context, first: VgServer) {
        val prefs = Prefs(context)
        val others = VpnGate.cached(context).filter { it.id != first.id }
        val candidates = (listOf(first) + others.filter { it.countryCode == first.countryCode } +
            others.filter { it.countryCode != first.countryCode }).take(VG_ATTEMPTS)
        for ((i, srv) in candidates.withIndex()) {
            if (i > 0) {
                _notice.value = "Servidor sem resposta. Tentando ${srv.flag} ${srv.country} (${i + 1}/${candidates.size})…"
                prefs.selectVg(srv)
                Prefs.version.value++
            }
            try {
                OpenVpnController.connect(context, srv.config)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                OpenVpnController.disconnect(context)
                continue // configuração que o motor não aceita: pula para o próximo
            }
            val up = withTimeoutOrNull(VG_TIMEOUT_MS) { OpenVpnController.state.first { it == VpnState.UP } }
            if (up != null) {
                _notice.value = null
                return
            }
            OpenVpnController.disconnect(context)
        }
        val why = OpenVpnController.lastError?.let { " Último erro: $it" } ?: ""
        _notice.value = "Nenhum servidor gratuito respondeu. Verifique a internet ou escolha outro país.$why"
    }

    fun clearNotice() { _notice.value = null }

    suspend fun disconnect(context: Context) {
        vgJob?.cancel()
        vgJob = null
        if (TunnelManager.state.value != VpnState.DOWN) TunnelManager.disconnect(context)
        if (OpenVpnController.state.value != VpnState.DOWN) OpenVpnController.disconnect(context)
    }
}
