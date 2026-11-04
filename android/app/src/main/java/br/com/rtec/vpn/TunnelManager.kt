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

data class TunnelStats(val rxBytes: Long, val txBytes: Long, val lastHandshakeMillis: Long)

/** Ponto único que liga/desliga o túnel WireGuard. */
object TunnelManager {
    private var backend: GoBackend? = null
    private val mutex = Mutex()
    private val _state = MutableStateFlow(VpnState.DOWN)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    /** Momento em que o túnel subiu (para o cronômetro da sessão). */
    private val _connectedSince = MutableStateFlow<Long?>(null)
    val connectedSince: StateFlow<Long?> = _connectedSince.asStateFlow()

    /** Servidor em uso no túnel ativo. */
    var activeProfileId: String? = null
        private set

    private val tunnel = object : Tunnel {
        override fun getName() = "rtecvpn"
        override fun onStateChange(newState: Tunnel.State) = setUp(newState == Tunnel.State.UP)
    }

    private fun setUp(up: Boolean) {
        _state.value = if (up) VpnState.UP else VpnState.DOWN
        if (up && _connectedSince.value == null) _connectedSince.value = System.currentTimeMillis()
        if (!up) _connectedSince.value = null
    }

    fun backend(context: Context): GoBackend =
        backend ?: GoBackend(context.applicationContext).also { backend = it }

    fun parse(text: String): Config = Config.parse(text.byteInputStream())

    /** Aplica os ajustes do app (DNS personalizado e apps fora da VPN) ao texto da configuração. */
    fun applyOverrides(text: String, customDns: String, excludedApps: Set<String>): String {
        val out = StringBuilder()
        var inInterface = false
        var existingExcluded = emptySet<String>()
        var hasIncluded = false
        val lines = text.lines()
        for (line in lines) {
            val t = line.trim()
            if (t.startsWith("[")) {
                if (inInterface) out.append(extraLines(customDns, excludedApps, existingExcluded, hasIncluded))
                inInterface = t.equals("[Interface]", ignoreCase = true)
                out.appendLine(line)
                continue
            }
            if (inInterface) {
                val key = t.substringBefore('=').trim().lowercase()
                when {
                    key == "dns" && customDns.isNotBlank() -> continue
                    key == "excludedapplications" -> {
                        existingExcluded = t.substringAfter('=').split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                        continue
                    }
                    key == "includedapplications" -> hasIncluded = true
                }
            }
            out.appendLine(line)
        }
        if (inInterface) out.append(extraLines(customDns, excludedApps, existingExcluded, hasIncluded))
        return out.toString()
    }

    private fun extraLines(dns: String, excluded: Set<String>, existing: Set<String>, hasIncluded: Boolean): String {
        val sb = StringBuilder()
        if (dns.isNotBlank()) sb.appendLine("DNS = $dns")
        // WireGuard não aceita Included e Excluded ao mesmo tempo
        val all = if (hasIncluded) emptySet() else existing + excluded
        if (all.isNotEmpty()) sb.appendLine("ExcludedApplications = ${all.joinToString(", ")}")
        return sb.toString()
    }

    /** Lança exceção se a config for inválida ou a permissão de VPN não tiver sido concedida. */
    suspend fun connect(context: Context, profileId: String? = null) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefs = Prefs(context)
            val profile = profileId?.let { id -> prefs.profiles.firstOrNull { it.id == id } }
                ?: prefs.selectedProfile ?: error("Nenhum servidor configurado")
            val config = parse(applyOverrides(profile.config, prefs.customDns, prefs.excludedApps))
            _state.value = VpnState.CONNECTING
            try {
                backend(context).setState(tunnel, Tunnel.State.UP, config)
                activeProfileId = profile.id
                setUp(true)
            } catch (e: Exception) {
                setUp(false)
                throw e
            }
        }
    }

    suspend fun disconnect(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            backend(context).setState(tunnel, Tunnel.State.DOWN, null)
            activeProfileId = null
            setUp(false)
        }
    }

    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        setUp(backend(context).getState(tunnel) == Tunnel.State.UP)
    }

    suspend fun stats(context: Context): TunnelStats? = withContext(Dispatchers.IO) {
        if (_state.value != VpnState.UP) return@withContext null
        runCatching {
            val s = backend(context).getStatistics(tunnel)
            val hs = s.peers().maxOfOrNull { s.peer(it)?.latestHandshakeEpochMillis() ?: 0L } ?: 0L
            TunnelStats(s.totalRx(), s.totalTx(), hs)
        }.getOrNull()
    }
}
