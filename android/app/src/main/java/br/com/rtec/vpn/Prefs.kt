package br.com.rtec.vpn

import android.content.Context

/** Preferências locais do app (config do túnel e regras de Wi-Fi). */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("rtecvpn", Context.MODE_PRIVATE)

    var configText: String?
        get() = sp.getString(KEY_CONFIG, null)
        set(v) = sp.edit().putString(KEY_CONFIG, v).apply()

    /** Liga/desliga a regra de conexão automática por Wi-Fi. */
    var autoConnectEnabled: Boolean
        get() = sp.getBoolean(KEY_AUTO, false)
        set(v) = sp.edit().putBoolean(KEY_AUTO, v).apply()

    /** Nomes (SSID) das redes onde a VPN liga sozinha. */
    var trustedSsids: Set<String>
        get() = sp.getStringSet(KEY_SSIDS, emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet(KEY_SSIDS, v).apply()

    /** true quando a VPN atual foi ligada pela regra automática (só então desligamos ao sair da rede). */
    var autoStarted: Boolean
        get() = sp.getBoolean(KEY_AUTO_STARTED, false)
        set(v) = sp.edit().putBoolean(KEY_AUTO_STARTED, v).apply()

    private companion object {
        const val KEY_CONFIG = "config"
        const val KEY_AUTO = "auto_connect"
        const val KEY_SSIDS = "trusted_ssids"
        const val KEY_AUTO_STARTED = "auto_started"
    }
}
