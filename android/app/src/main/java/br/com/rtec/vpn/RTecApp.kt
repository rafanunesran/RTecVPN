package br.com.rtec.vpn

import android.app.Application
import android.util.Log
import com.wireguard.android.backend.GoBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RTecApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        // "VPN sempre ativa" do Android (kill switch): o sistema inicia o serviço e nós subimos o túnel
        GoBackend.setAlwaysOnCallback {
            scope.launch {
                runCatching { TunnelManager.connect(this@RTecApp) }
                    .onFailure { Log.e("RTecApp", "Falha ao conectar pela VPN sempre ativa", it) }
            }
        }
    }
}
