package br.com.rtec.vpn

import android.util.Log
import com.wireguard.android.backend.GoBackend
import de.blinkt.openvpn.core.ICSOpenVPNApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Herda do Application do motor OpenVPN embutido: ele cria os canais de notificação e
 * repassa o status da conexão (que roda no processo ":openvpn") para o processo do app.
 */
class RTecApp : ICSOpenVPNApplication() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        OpenVpnController.init()
        // "VPN sempre ativa" do Android (kill switch): o sistema inicia o serviço e nós subimos o túnel
        GoBackend.setAlwaysOnCallback {
            scope.launch {
                runCatching { TunnelManager.connect(this@RTecApp) }
                    .onFailure { Log.e("RTecApp", "Falha ao conectar pela VPN sempre ativa", it) }
            }
        }
    }
}
