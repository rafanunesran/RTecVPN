package br.com.rtec.vpn

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdate(val version: String, val apkUrl: String, val notes: String, val sizeBytes: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AppUpdate) : UpdateState
    data class Downloading(val update: AppUpdate, val progress: Float) : UpdateState
    data object Installing : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Atualiza o app a partir do último release do GitHub, sem passar pelo navegador. */
object Updater {
    private const val API = "https://api.github.com/repos/rafanunesran/RTecVPN/releases/latest"
    private const val TAG = "Updater"
    const val ACTION_INSTALL_STATUS = "br.com.rtec.vpn.INSTALL_STATUS"

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Compara versões "1.2.3" número a número. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        val l = local.removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    suspend fun check(): UpdateState = withContext(Dispatchers.IO) {
        _state.value = UpdateState.Checking
        val result = try {
            val conn = URL(API).openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val version = json.getString("tag_name").removePrefix("v")
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") }
            when {
                apk == null || !isNewer(version, BuildConfig.VERSION_NAME) -> UpdateState.UpToDate
                else -> UpdateState.Available(
                    AppUpdate(version, apk.getString("browser_download_url"), json.optString("body"), apk.optLong("size"))
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao verificar", e)
            UpdateState.Failed("Não foi possível verificar: ${e.message ?: e.javaClass.simpleName}")
        }
        _state.value = result
        result
    }

    /** O Android exige que o usuário libere "instalar apps desconhecidos" para o RTecVPN uma vez. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    suspend fun downloadAndInstall(context: Context, update: AppUpdate) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, "update.apk")
            _state.value = UpdateState.Downloading(update, 0f)
            val conn = URL(update.apkUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: update.sizeBytes
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) _state.value = UpdateState.Downloading(update, done.toFloat() / total)
                    }
                }
            }
            _state.value = UpdateState.Installing
            install(context, file)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao atualizar", e)
            _state.value = UpdateState.Failed("Falha ao baixar: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("rtecvpn.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallReceiver::class.java).setAction(ACTION_INSTALL_STATUS)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, id, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    internal fun onInstallResult(context: Context, status: Int, message: String?) {
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> UpdateState.UpToDate
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, PackageInstaller.STATUS_FAILURE_CONFLICT ->
                UpdateState.Failed("Esta versão foi assinada com outra chave. Desinstale o RTecVPN uma vez e instale a nova pelo site.")
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateState.Idle
            else -> UpdateState.Failed("A instalação falhou: ${message ?: status}")
        }
        if (status != PackageInstaller.STATUS_SUCCESS && status != PackageInstaller.STATUS_FAILURE_ABORTED) {
            Toast.makeText(context, (state.value as? UpdateState.Failed)?.message, Toast.LENGTH_LONG).show()
        }
    }
}

/** Recebe o resultado da instalação; quando o Android pede confirmação, abre a tela dele. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { context.startActivity(it) }
            return
        }
        Updater.onInstallResult(context, status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
