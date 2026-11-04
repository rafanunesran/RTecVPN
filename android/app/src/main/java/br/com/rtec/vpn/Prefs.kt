package br.com.rtec.vpn

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Um servidor (perfil WireGuard) salvo no app. */
data class Profile(val id: String, val name: String, val config: String)

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** Preferências locais do app. Toda escrita incrementa [version] para a UI se atualizar. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("rtecvpn", Context.MODE_PRIVATE)

    init {
        migrate()
    }

    private fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        sp.edit().apply(block).apply()
        version.value++
    }

    // ------------------------------------------------------------ servidores
    var profiles: List<Profile>
        get() = runCatching {
            val arr = JSONArray(sp.getString(KEY_PROFILES, "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Profile(o.getString("id"), o.getString("name"), o.getString("config"))
            }
        }.getOrDefault(emptyList())
        set(list) {
            val arr = JSONArray()
            list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("config", it.config)) }
            edit { putString(KEY_PROFILES, arr.toString()) }
        }

    var selectedId: String?
        get() = sp.getString(KEY_SELECTED, null)
        set(v) = edit { putString(KEY_SELECTED, v) }

    /** Servidor WireGuard próprio selecionado (null se o selecionado for do VPN Gate). */
    val selectedProfile: Profile?
        get() {
            if (selectedId?.startsWith("vg:") == true && selectedVg != null) return null
            return profiles.let { list -> list.firstOrNull { it.id == selectedId } ?: list.firstOrNull() }
        }

    /** Servidor do VPN Gate escolhido (guardado inteiro, porque a lista pública muda). */
    var selectedVg: VgServer?
        get() = runCatching {
            val o = JSONObject(sp.getString(KEY_VG, null)!!)
            VgServer(o.getString("host"), o.getString("ip"), o.optLong("score"), o.optInt("ping"), o.optLong("speed"),
                o.getString("country"), o.getString("code"), o.optInt("sessions"), o.getString("config"))
        }.getOrNull()
        set(v) = edit {
            if (v == null) remove(KEY_VG) else putString(KEY_VG, JSONObject()
                .put("host", v.host).put("ip", v.ip).put("score", v.score).put("ping", v.pingMs).put("speed", v.speedBps)
                .put("country", v.country).put("code", v.countryCode).put("sessions", v.sessions).put("config", v.config).toString())
        }

    val usingVpnGate: Boolean get() = selectedId?.startsWith("vg:") == true && selectedVg != null
    val hasAnyServer: Boolean get() = usingVpnGate || selectedProfile != null

    fun selectVg(s: VgServer) {
        selectedVg = s
        selectedId = s.id
    }

    fun addProfile(name: String, config: String): Profile {
        val p = Profile(UUID.randomUUID().toString(), name, config)
        profiles = profiles + p
        if (selectedId == null || profiles.size == 1) selectedId = p.id
        return p
    }

    fun updateProfile(p: Profile) {
        profiles = profiles.map { if (it.id == p.id) p else it }
    }

    fun deleteProfile(id: String) {
        profiles = profiles.filterNot { it.id == id }
        if (selectedId == id) selectedId = profiles.firstOrNull()?.id
    }

    // ------------------------------------------------------------ Wi-Fi automático
    /** Liga/desliga a regra de conexão automática por Wi-Fi. */
    var autoConnectEnabled: Boolean
        get() = sp.getBoolean(KEY_AUTO, false)
        set(v) = edit { putBoolean(KEY_AUTO, v) }

    /** Nomes (SSID) das redes cadastradas. */
    var trustedSsids: Set<String>
        get() = sp.getStringSet(KEY_SSIDS, emptySet())!!.toSet()
        set(v) = edit { putStringSet(KEY_SSIDS, v) }

    /** Redes cadastradas mas com a chave desligada. */
    var disabledSsids: Set<String>
        get() = sp.getStringSet(KEY_SSIDS_OFF, emptySet())!!.toSet()
        set(v) = edit { putStringSet(KEY_SSIDS_OFF, v) }

    val activeSsids: Set<String> get() = trustedSsids - disabledSsids

    var disconnectOnLeave: Boolean
        get() = sp.getBoolean(KEY_DISCONNECT_LEAVE, true)
        set(v) = edit { putBoolean(KEY_DISCONNECT_LEAVE, v) }

    /** Também conectar em Wi-Fi aberto (sem senha), mesmo fora da lista. */
    var connectOnOpenWifi: Boolean
        get() = sp.getBoolean(KEY_OPEN_WIFI, false)
        set(v) = edit { putBoolean(KEY_OPEN_WIFI, v) }

    /** true quando a VPN atual foi ligada pela regra automática (só então desligamos ao sair da rede). */
    var autoStarted: Boolean
        get() = sp.getBoolean(KEY_AUTO_STARTED, false)
        set(v) = edit { putBoolean(KEY_AUTO_STARTED, v) }

    /** Rede em que o usuário desligou a VPN manualmente; não religamos até ele sair dela. */
    var pausedSsid: String?
        get() = sp.getString(KEY_PAUSED, null)
        set(v) = edit { putString(KEY_PAUSED, v) }

    // ------------------------------------------------------------ ajustes
    var onboardingDone: Boolean
        get() = sp.getBoolean(KEY_ONBOARDING, false)
        set(v) = edit { putBoolean(KEY_ONBOARDING, v) }

    var theme: ThemeMode
        get() = runCatching { ThemeMode.valueOf(sp.getString(KEY_THEME, null)!!) }.getOrDefault(ThemeMode.SYSTEM)
        set(v) = edit { putString(KEY_THEME, v.name) }

    var connectOnBoot: Boolean
        get() = sp.getBoolean(KEY_BOOT, false)
        set(v) = edit { putBoolean(KEY_BOOT, v) }

    /** DNS que substitui o da configuração (vazio = usar o do servidor). */
    var customDns: String
        get() = sp.getString(KEY_DNS, "")!!
        set(v) = edit { putString(KEY_DNS, v.trim()) }

    /** Apps que não passam pela VPN (split tunneling). */
    var excludedApps: Set<String>
        get() = sp.getStringSet(KEY_EXCLUDED, emptySet())!!.toSet()
        set(v) = edit { putStringSet(KEY_EXCLUDED, v) }

    private fun migrate() {
        // v0.1 guardava só uma configuração em "config"
        val old = sp.getString(KEY_OLD_CONFIG, null) ?: return
        if (profiles.isEmpty()) {
            val p = Profile(UUID.randomUUID().toString(), "Meu servidor", old)
            val arr = JSONArray().put(JSONObject().put("id", p.id).put("name", p.name).put("config", p.config))
            sp.edit().putString(KEY_PROFILES, arr.toString()).putString(KEY_SELECTED, p.id).putBoolean(KEY_ONBOARDING, true).apply()
        }
        sp.edit().remove(KEY_OLD_CONFIG).apply()
    }

    companion object {
        val version = MutableStateFlow(0)

        private const val KEY_OLD_CONFIG = "config"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_SELECTED = "selected_profile"
        private const val KEY_VG = "selected_vpngate"
        private const val KEY_AUTO = "auto_connect"
        private const val KEY_SSIDS = "trusted_ssids"
        private const val KEY_SSIDS_OFF = "disabled_ssids"
        private const val KEY_DISCONNECT_LEAVE = "disconnect_on_leave"
        private const val KEY_OPEN_WIFI = "connect_open_wifi"
        private const val KEY_AUTO_STARTED = "auto_started"
        private const val KEY_PAUSED = "paused_ssid"
        private const val KEY_ONBOARDING = "onboarding_done"
        private const val KEY_THEME = "theme"
        private const val KEY_BOOT = "connect_on_boot"
        private const val KEY_DNS = "custom_dns"
        private const val KEY_EXCLUDED = "excluded_apps"
    }
}
