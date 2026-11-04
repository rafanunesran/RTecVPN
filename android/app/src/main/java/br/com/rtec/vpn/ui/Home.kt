package br.com.rtec.vpn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.rtec.vpn.Profile
import br.com.rtec.vpn.TunnelManager
import br.com.rtec.vpn.TunnelStats
import br.com.rtec.vpn.VpnState
import kotlinx.coroutines.delay

fun endpointOf(p: Profile?): String? = p?.let {
    runCatching { TunnelManager.parse(it.config).peers.firstOrNull()?.endpoint?.orElse(null)?.toString() }.getOrNull()
}

@Composable
fun HomeScreen(
    state: VpnState,
    connectedSince: Long?,
    profile: Profile?,
    onToggle: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDetails: () -> Unit,
    onAddServer: () -> Unit,
) {
    val t = LocalRTec.current
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var stats by remember { mutableStateOf<TunnelStats?>(null) }
    LaunchedEffect(state) {
        while (true) {
            now = System.currentTimeMillis()
            stats = TunnelManager.stats(context)
            delay(1000)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBox(Icons.Rounded.Shield, size = 36.dp, gradient = true)
            Spacer(Modifier.width(10.dp))
            Text("RTecVPN", style = Type.title, color = t.text, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "Ajustes", tint = t.muted) }
        }
        Spacer(Modifier.height(24.dp))
        ConnectButton(state, enabled = profile != null, onClick = onToggle)
        Spacer(Modifier.height(16.dp))
        Text(
            when (state) { VpnState.UP -> "Conectado"; VpnState.CONNECTING -> "Conectando…"; VpnState.DOWN -> "Desconectado" },
            style = Type.display, color = stateColor(state, t.text),
        )
        Text(
            if (state == VpnState.UP && connectedSince != null) formatDuration(now - connectedSince)
            else if (profile == null) "Adicione um servidor para começar" else "Toque para proteger sua conexão",
            style = Type.mono, color = t.muted, modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(24.dp))

        if (profile == null) {
            GradientButton("Adicionar servidor", icon = Icons.Rounded.Add, onClick = onAddServer)
        } else {
            RCard(onClick = onOpenServers) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBox(Icons.Rounded.Dns)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.name, style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = t.text)
                        Text(endpointOf(profile) ?: "—", style = Type.mono.copy(fontSize = 12.sp), color = t.muted)
                    }
                    stats?.let { s ->
                        if (s.lastHandshakeMillis > 0) Chip(formatAgo(s.lastHandshakeMillis, now), Success)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = t.muted)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Download", stats?.let { formatBytes(it.rxBytes) } ?: "—", Modifier.weight(1f))
                StatTile("Upload", stats?.let { formatBytes(it.txBytes) } ?: "—", Modifier.weight(1f))
                StatTile("Sessão", if (state == VpnState.UP && connectedSince != null) formatDuration(now - connectedSince).dropLast(3) else "—", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onOpenDetails) { Text("Ver detalhes da conexão", color = Cyan) }
        }
        Spacer(Modifier.size(8.dp))
    }
}
