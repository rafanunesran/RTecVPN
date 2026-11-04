package br.com.rtec.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Religa o monitor de Wi-Fi depois que o celular reinicia. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Android 14+ pode bloquear serviços de localização iniciados no boot; nesse caso
        // o monitor volta na próxima vez que o app for aberto.
        runCatching { AutoConnectService.sync(context) }
            .onFailure { Log.w("BootReceiver", "Não foi possível iniciar o monitor", it) }
    }
}
