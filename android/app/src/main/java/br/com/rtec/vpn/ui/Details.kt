package br.com.rtec.vpn.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.rtec.vpn.Profile
import br.com.rtec.vpn.TunnelManager
import br.com.rtec.vpn.TunnelStats
import kotlinx.coroutines.delay

@Composable
fun DetailsScreen(profile: Profile?, onBack: () -> Unit) {
    val t = LocalRTec.current
    val context = LocalContext.current
    var stats by remember { mutableStateOf<TunnelStats?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { stats = TunnelManager.stats(context); now = System.currentTimeMillis(); delay(1000) }
    }
    val config = remember(profile?.config) { profile?.let { runCatching { TunnelManager.parse(it.config) }.getOrNull() } }
    val peer = config?.peers?.firstOrNull()
    val pubKey = config?.`interface`?.keyPair?.publicKey?.toBase64()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", tint = t.text) }
            Text("Detalhes da conexão", style = Type.title, color = t.text)
        }
        RCard {
            DetailRow("Interface", "rtecvpn")
            DetailRow("Servidor", profile?.name ?: "—")
            DetailRow("Chave pública", pubKey?.let { it.take(10) + "…" + it.takeLast(6) } ?: "—", mono = true,
                onCopy = pubKey?.let { k -> { copy(context, k) } })
            DetailRow("Endpoint", peer?.endpoint?.orElse(null)?.toString() ?: "—", mono = true)
            DetailRow("Endereço", config?.`interface`?.addresses?.joinToString() ?: "—", mono = true)
            DetailRow("Allowed IPs", peer?.allowedIps?.joinToString() ?: "—", mono = true)
            DetailRow("Último handshake", stats?.let { formatAgo(it.lastHandshakeMillis, now) } ?: "desconectado")
            DetailRow("Tráfego", stats?.let { "↓ ${formatBytes(it.rxBytes)}  ↑ ${formatBytes(it.txBytes)}" } ?: "—")
            DetailRow("MTU", config?.`interface`?.mtu?.orElse(null)?.toString() ?: "automático", last = true)
        }
    }
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("chave", text))
    Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
}

@Composable
private fun DetailRow(label: String, value: String, mono: Boolean = false, last: Boolean = false, onCopy: (() -> Unit)? = null) {
    val t = LocalRTec.current
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.bodySmall, color = t.muted, modifier = Modifier.weight(1f))
        Text(value, style = if (mono) Type.mono else Type.bodySmall, color = t.text, textAlign = TextAlign.End,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.6f))
        if (onCopy != null) IconButton(onClick = onCopy) { Icon(Icons.Rounded.ContentCopy, "Copiar", tint = Cyan) }
    }
    if (!last) HorizontalDivider(color = t.border)
}
