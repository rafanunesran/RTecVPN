package br.com.rtec.vpn

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import br.com.rtec.vpn.ui.AddServerSheet
import br.com.rtec.vpn.ui.Cyan
import br.com.rtec.vpn.ui.DetailsScreen
import br.com.rtec.vpn.ui.FreeServersState
import br.com.rtec.vpn.ui.HomeScreen
import br.com.rtec.vpn.ui.Inter
import br.com.rtec.vpn.ui.LocalRTec
import br.com.rtec.vpn.ui.OnboardingScreen
import br.com.rtec.vpn.ui.RTecTheme
import br.com.rtec.vpn.ui.ServersScreen
import br.com.rtec.vpn.ui.SettingsScreen
import br.com.rtec.vpn.ui.SettingsUiState
import br.com.rtec.vpn.ui.SplitTunnelScreen
import br.com.rtec.vpn.ui.WifiScreen
import br.com.rtec.vpn.ui.WifiUiState
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private var showAddSheet by mutableStateOf(false)
    private var showUpdateDialog by mutableStateOf(false)
    private var vgServers by mutableStateOf<List<VgServer>>(emptyList())
    private var vgLoading by mutableStateOf(false)
    private var vgError by mutableStateOf<String?>(null)
    /** Ação que espera a permissão de VPN (conectar ou ligar o automático). */
    private var afterVpnPermission: (() -> Unit)? = null
    private var afterLocation: (() -> Unit)? = null

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (VpnService.prepare(this) == null) afterVpnPermission?.invoke() else toast("Permissão de VPN negada")
        afterVpnPermission = null
    }

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasLocation()) afterLocation?.invoke() else toast("Sem permissão de localização não dá para identificar o Wi‑Fi")
        afterLocation = null
        Prefs.version.value++
    }

    private val scanQr = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { addServer(it) }
    }

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        val text = runCatching { contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
        val name = uri.lastPathSegment?.substringAfterLast('/')?.removeSuffix(".conf")
        text?.let { addServer(it, name) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = Prefs(this)
        lifecycleScope.launch { runCatching { TunnelManager.refresh(this@MainActivity) } }
        vgServers = VpnGate.cached(this)
        refreshVpnGate()
        checkUpdates(silent = true)
        runCatching { AutoConnectService.sync(this) }
        setContent {
            val version by Prefs.version.collectAsState()
            @Suppress("UNUSED_EXPRESSION") version // relê as preferências a cada alteração
            RTecTheme(prefs.theme) {
                if (!prefs.onboardingDone) {
                    OnboardingScreen { prefs.onboardingDone = true }
                } else {
                    AppScaffold()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Prefs.version.value++ // Wi-Fi atual / permissões podem ter mudado
    }

    @Composable
    private fun AppScaffold() {
        val t = LocalRTec.current
        val nav = rememberNavController()
        val state by VpnController.state.collectAsState()
        val since by VpnController.connectedSince.collectAsState()
        val ovpnState by OpenVpnController.state.collectAsState()
        val updState by Updater.state.collectAsState()
        val updateStatus = when (val u = updState) {
            UpdateState.Checking -> "Verificando…"
            UpdateState.UpToDate -> "Você está na versão mais recente"
            is UpdateState.Available -> "Nova versão ${u.update.version}: toque para atualizar"
            is UpdateState.Downloading -> "Baixando ${(u.progress * 100).toInt()}%"
            UpdateState.Installing -> "Instalando…"
            is UpdateState.Failed -> u.message
            UpdateState.Idle -> null
        }
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        val tabs = listOf(
            Tab("home", "Início", Icons.Rounded.Home), Tab("servers", "Servidores", Icons.Rounded.Dns),
            Tab("wifi", "Wi‑Fi", Icons.Rounded.Wifi), Tab("settings", "Ajustes", Icons.Rounded.Settings),
        )
        var ssid by remember { mutableStateOf<String?>(null) }
        val version by Prefs.version.collectAsState()
        LaunchedEffect(version) { ssid = WifiUtils.currentSsid(this@MainActivity) }

        Scaffold(
            containerColor = t.background,
            bottomBar = {
                if (tabs.any { it.route == route } || route == null) {
                    Column {
                        HorizontalDivider(color = t.border)
                        NavigationBar(containerColor = t.surface, modifier = Modifier.height(68.dp + 24.dp)) {
                            tabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = route == tab.route,
                                    onClick = {
                                        nav.navigate(tab.route) {
                                            popUpTo("home") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(tab.icon, null, Modifier.size(20.dp)) },
                                    label = { Text(tab.label, fontSize = 11.sp, fontFamily = Inter) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Cyan, selectedTextColor = t.text, indicatorColor = t.surface2,
                                        unselectedIconColor = t.muted, unselectedTextColor = t.muted,
                                    ),
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().background(t.background).padding(padding)) {
                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            state = state, connectedSince = since, profile = prefs.selectedProfile,
                            vg = if (prefs.usingVpnGate) prefs.selectedVg else null,
                            onToggle = { if (state == VpnState.DOWN) requestConnect() else doDisconnect() },
                            onOpenServers = { nav.navigate("servers") { launchSingleTop = true } },
                            onOpenSettings = { nav.navigate("settings") { launchSingleTop = true } },
                            onOpenDetails = { nav.navigate("details") },
                            onAddServer = { showAddSheet = true },
                        )
                    }
                    composable("servers") {
                        ServersScreen(
                            profiles = prefs.profiles,
                            selectedId = if (prefs.usingVpnGate) prefs.selectedId else prefs.selectedProfile?.id,
                            activeId = when {
                                state != VpnState.UP -> null
                                ovpnState == VpnState.UP -> prefs.selectedId
                                else -> TunnelManager.activeProfileId
                            },
                            onSelect = { selectServer(it, state) },
                            onAdd = { showAddSheet = true },
                            onRename = { p, name -> prefs.updateProfile(p.copy(name = name.ifBlank { p.name })) },
                            onEditConfig = { p, text ->
                                runCatching { TunnelManager.parse(text) }
                                    .onSuccess { prefs.updateProfile(p.copy(config = text)); toast("Configuração salva") }
                                    .onFailure { toast("Configuração inválida: ${it.message}") }
                            },
                            onDelete = { p ->
                                if (p.id == TunnelManager.activeProfileId) doDisconnect()
                                prefs.deleteProfile(p.id)
                            },
                            free = FreeServersState(vgServers, vgLoading, vgError),
                            onSelectFree = { srv -> selectFree(srv, state) },
                            onRefreshFree = { refreshVpnGate() },
                        )
                    }
                    composable("wifi") {
                        WifiScreen(
                            WifiUiState(
                                autoEnabled = prefs.autoConnectEnabled, currentSsid = ssid, hasLocation = hasLocation(),
                                ssids = prefs.trustedSsids.sorted(), disabled = prefs.disabledSsids,
                                disconnectOnLeave = prefs.disconnectOnLeave, connectOnOpen = prefs.connectOnOpenWifi,
                            ),
                            onToggleAuto = { on -> if (on) enableAuto() else { prefs.autoConnectEnabled = false; AutoConnectService.sync(this@MainActivity) } },
                            onAddCurrent = { ssid?.let { prefs.trustedSsids = prefs.trustedSsids + it } },
                            onAddManual = { prefs.trustedSsids = prefs.trustedSsids + it },
                            onToggleSsid = { s, on -> prefs.disabledSsids = if (on) prefs.disabledSsids - s else prefs.disabledSsids + s },
                            onRemoveSsid = { s -> prefs.trustedSsids = prefs.trustedSsids - s; prefs.disabledSsids = prefs.disabledSsids - s },
                            onDisconnectOnLeave = { prefs.disconnectOnLeave = it },
                            onConnectOnOpen = { prefs.connectOnOpenWifi = it },
                            onRequestLocation = { requestLocation {} },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            SettingsUiState(
                                theme = prefs.theme, connectOnBoot = prefs.connectOnBoot, customDns = prefs.customDns,
                                excludedCount = prefs.excludedApps.size, version = BuildConfig.VERSION_NAME, updateStatus = updateStatus,
                            ),
                            onKillSwitch = { openKillSwitch() },
                            onConnectOnBoot = { prefs.connectOnBoot = it },
                            onSplitTunnel = { nav.navigate("split") },
                            onDns = { dns ->
                                if (dns.isBlank() || dns.split(',').all { it.trim().matches(Regex("[0-9a-fA-F:.]+")) }) prefs.customDns = dns
                                else toast("Use IPs separados por vírgula, ex.: 1.1.1.1, 9.9.9.9")
                            },
                            onTheme = { prefs.theme = it },
                            onGithub = { openUrl("https://github.com/rafanunesran/RTecVPN") },
                            onCheckUpdates = { if (updState is UpdateState.Available) showUpdateDialog = true else checkUpdates() },
                        )
                    }
                    composable("details") { DetailsScreen(prefs.selectedProfile) { nav.popBackStack() } }
                    composable("split") {
                        SplitTunnelScreen(prefs.excludedApps, { pkg, on ->
                            prefs.excludedApps = if (on) prefs.excludedApps + pkg else prefs.excludedApps - pkg
                        }) { nav.popBackStack() }
                    }
                }
            }
        }
        UpdateDialog()
        if (showAddSheet) {
            AddServerSheet(
                onDismiss = { showAddSheet = false },
                onScan = {
                    showAddSheet = false
                    scanQr.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false).setPrompt("Aponte para o QR code do add-client.sh"))
                },
                onFile = { showAddSheet = false; pickFile.launch(arrayOf("*/*")) },
                onPaste = { showAddSheet = false; addServer(it) },
            )
        }
    }

    private data class Tab(val route: String, val label: String, val icon: ImageVector)

    // ------------------------------------------------------------------ ações
    private fun addServer(text: String, suggestedName: String? = null) {
        try {
            val cfg = TunnelManager.parse(text)
            val host = cfg.peers.firstOrNull()?.endpoint?.orElse(null)?.host
            val name = suggestedName?.takeIf { it.isNotBlank() } ?: host ?: "Servidor ${prefs.profiles.size + 1}"
            val p = prefs.addProfile(name, text)
            prefs.selectedId = p.id
            toast("Servidor \"$name\" adicionado")
        } catch (e: Exception) {
            toast("Configuração inválida: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun selectServer(p: Profile, state: VpnState) {
        val wasVg = prefs.usingVpnGate
        prefs.selectedId = p.id
        // Se estiver conectado em outro servidor, troca na hora
        if (state == VpnState.UP && (wasVg || TunnelManager.activeProfileId != p.id)) requestConnect()
    }

    private fun selectFree(srv: VgServer, state: VpnState) {
        val changed = prefs.selectedId != srv.id
        prefs.selectVg(srv)
        if (state == VpnState.UP && changed) requestConnect()
    }

    private fun refreshVpnGate() {
        if (vgLoading) return
        vgLoading = true
        lifecycleScope.launch {
            try {
                vgServers = VpnGate.fetch(this@MainActivity)
                vgError = null
            } catch (e: Exception) {
                vgError = e.message ?: e.javaClass.simpleName
            } finally {
                vgLoading = false
            }
            // Sem nenhum servidor escolhido: já deixa o melhor gratuito pronto para conectar
            if (!prefs.hasAnyServer) vgServers.firstOrNull()?.let { prefs.selectVg(it) }
        }
    }

    private fun requestConnect() {
        val intent = VpnService.prepare(this)
        if (intent != null) { afterVpnPermission = { doConnect() }; vpnPermission.launch(intent) } else doConnect()
    }

    private fun doConnect() = lifecycleScope.launch {
        try {
            VpnController.connect(this@MainActivity)
            prefs.autoStarted = false // ligado manualmente: não desligar ao sair do Wi-Fi
            prefs.pausedSsid = null
        } catch (e: Exception) {
            toast("Erro: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun doDisconnect() = lifecycleScope.launch {
        try {
            VpnController.disconnect(this@MainActivity)
            prefs.autoStarted = false
            prefs.pausedSsid = WifiUtils.currentSsid(this@MainActivity)
        } catch (e: Exception) {
            toast("Erro: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun enableAuto() {
        when {
            !prefs.hasAnyServer -> toast("Escolha um servidor primeiro")
            !hasLocation() -> requestLocation { enableAuto() }
            !prefs.usingVpnGate && VpnService.prepare(this) != null -> {
                afterVpnPermission = { enableAuto() }
                vpnPermission.launch(VpnService.prepare(this))
            }
            else -> {
                prefs.autoConnectEnabled = true
                AutoConnectService.sync(this)
            }
        }
    }

    private fun hasLocation() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocation(then: () -> Unit) {
        afterLocation = then
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        locationPermission.launch(perms.toTypedArray())
    }

    private fun openKillSwitch() {
        toast("Ative \"VPN sempre ativa\" e \"Bloquear conexões sem VPN\" para o RTecVPN")
        runCatching { startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
            .onFailure { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
    }

    private fun openUrl(url: String) = runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    private fun checkUpdates(silent: Boolean = false) {
        lifecycleScope.launch {
            val r = Updater.check()
            if (r is UpdateState.Available) showUpdateDialog = true
            else if (!silent && r is UpdateState.UpToDate) toast("Você está na versão mais recente")
        }
    }

    private fun startUpdate(update: AppUpdate) {
        if (!Updater.canInstall(this)) {
            toast("Permita que o RTecVPN instale atualizações e toque em Atualizar de novo")
            Updater.openInstallPermission(this)
            return
        }
        lifecycleScope.launch { Updater.downloadAndInstall(this@MainActivity, update) }
    }

    @Composable
    private fun UpdateDialog() {
        val st by Updater.state.collectAsState()
        val upd = when (val x = st) {
            is UpdateState.Available -> x.update
            is UpdateState.Downloading -> x.update
            else -> null
        }
        if (!showUpdateDialog || (upd == null && st !is UpdateState.Installing)) return
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (st !is UpdateState.Downloading) showUpdateDialog = false },
            title = { Text("Nova versão ${upd?.version.orEmpty()}") },
            text = {
                Column {
                    when (val x = st) {
                        is UpdateState.Downloading -> {
                            Text("Baixando… ${(x.progress * 100).toInt()}%")
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { x.progress }, color = Cyan,
                                modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                            )
                        }
                        UpdateState.Installing -> Text("Confirme a instalação na tela do Android.")
                        else -> Text(upd?.notes?.take(400)?.ifBlank { null } ?: "Uma nova versão do RTecVPN está disponível.")
                    }
                }
            },
            confirmButton = {
                if (st is UpdateState.Available) {
                    androidx.compose.material3.TextButton(onClick = { upd?.let { startUpdate(it) } }) { Text("Atualizar") }
                }
            },
            dismissButton = {
                if (st is UpdateState.Available) {
                    androidx.compose.material3.TextButton(onClick = { showUpdateDialog = false }) { Text("Depois") }
                }
            },
        )
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
