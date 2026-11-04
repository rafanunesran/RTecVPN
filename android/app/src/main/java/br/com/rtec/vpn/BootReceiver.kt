package br.com.rtec.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Ao ligar o celular: religa o monitor de Wi-Fi e, se escolhido, conecta a VPN. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Android 14+ pode bloquear serviços de localização iniciados no boot; nesse caso
        // o monitor volta na próxima vez que o app for aberto.
        runCatching { AutoConnectService.sync(context) }
            .onFailure { Log.w(TAG, "Não foi possível iniciar o monitor", it) }

        val prefs = Prefs(context)
        if (prefs.connectOnBoot && prefs.selectedProfile != null && VpnService.prepare(context) == null) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { TunnelManager.connect(context.applicationContext) }
                    .onFailure { Log.w(TAG, "Falha ao conectar no boot", it) }
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
