package br.com.rtec.vpn

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Um servidor voluntário do VPN Gate (Universidade de Tsukuba). */
data class VgServer(
    val host: String,
    val ip: String,
    val score: Long,
    val pingMs: Int,
    val speedBps: Long,
    val country: String,
    val countryCode: String,
    val sessions: Int,
    val config: String,
) {
    val id get() = "vg:$ip"
    val flag: String get() = flagOf(countryCode)
    /** Porta e protocolo da configuração (ex.: "TCP 443"). */
    val transport: String get() {
        val proto = Regex("(?m)^proto\\s+(\\w+)").find(config)?.groupValues?.get(1)?.uppercase() ?: "?"
        val port = Regex("(?m)^remote\\s+\\S+\\s+(\\d+)").find(config)?.groupValues?.get(1) ?: "?"
        return "$proto $port"
    }
}

fun flagOf(code: String): String =
    if (code.length != 2) "🌐" else code.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")

object VpnGate {
    private val urls = listOf("https://www.vpngate.net/api/iphone/", "http://www.vpngate.net/api/iphone/")

    private fun cacheFile(context: Context) = File(context.filesDir, "vpngate.csv")

    /** Lista em cache (última baixada), para abrir o app sem esperar a rede. */
    fun cached(context: Context): List<VgServer> =
        runCatching { parse(cacheFile(context).readText()) }.getOrDefault(emptyList())

    suspend fun fetch(context: Context): List<VgServer> = withContext(Dispatchers.IO) {
        var last: Exception? = null
        for (u in urls) {
            try {
                val conn = URL(u).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", "RTecVPN")
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val list = parse(text)
                if (list.isNotEmpty()) {
                    cacheFile(context).writeText(text)
                    return@withContext list
                }
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("Lista do VPN Gate vazia")
    }

    /**
     * Formato: linha "*vpn_servers", cabeçalho começando com "#HostName,...", linhas CSV e "*" no fim.
     * A última coluna é a configuração OpenVPN em base64.
     */
    fun parse(text: String): List<VgServer> = text.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("*") && !it.startsWith("#") }
        .mapNotNull { line ->
            val c = line.split(',')
            if (c.size < 15) return@mapNotNull null
            runCatching {
                val config = String(Base64.decode(c[14].trim(), Base64.DEFAULT))
                VgServer(
                    host = c[0], ip = c[1], score = c[2].toLongOrNull() ?: 0, pingMs = c[3].toIntOrNull() ?: 0,
                    speedBps = c[4].toLongOrNull() ?: 0, country = c[5], countryCode = c[6],
                    sessions = c[7].toIntOrNull() ?: 0, config = withModernCiphers(config),
                )
            }.getOrNull()
        }
        .filter { it.config.contains("remote ") }
        .sortedByDescending { it.score }
        .toList()

    /** OpenVPN 2.6 só aceita AES-128-CBC (usado pelo VPN Gate) se estiver em data-ciphers. */
    fun withModernCiphers(config: String): String {
        if (config.contains("data-ciphers")) return config
        val extra = "data-ciphers AES-256-GCM:AES-128-GCM:CHACHA20-POLY1305:AES-128-CBC\ndata-ciphers-fallback AES-128-CBC\n"
        return config.replace("\r\n", "\n").let { cfg ->
            val idx = cfg.indexOf("\n<ca>")
            if (idx >= 0) cfg.substring(0, idx + 1) + extra + cfg.substring(idx + 1) else cfg + "\n" + extra
        }
    }
}
