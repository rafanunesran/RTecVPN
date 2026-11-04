package br.com.rtec.vpn.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class WifiUiState(
    val autoEnabled: Boolean,
    val currentSsid: String?,
    val hasLocation: Boolean,
    val ssids: List<String>,
    val disabled: Set<String>,
    val disconnectOnLeave: Boolean,
    val connectOnOpen: Boolean,
)

@Composable
fun WifiScreen(
    s: WifiUiState,
    onToggleAuto: (Boolean) -> Unit,
    onAddCurrent: () -> Unit,
    onAddManual: (String) -> Unit,
    onToggleSsid: (String, Boolean) -> Unit,
    onRemoveSsid: (String) -> Unit,
    onDisconnectOnLeave: (Boolean) -> Unit,
    onConnectOnOpen: (Boolean) -> Unit,
    onRequestLocation: () -> Unit,
) {
    val t = LocalRTec.current
    var manual by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Wi‑Fi automático", style = Type.title, color = t.text, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        RCard {
            SettingRow("Conectar automaticamente em redes Wi‑Fi escolhidas", "A VPN liga ao entrar nessas redes") {
                GradientSwitch(s.autoEnabled, onToggleAuto)
            }
        }
        if (!s.hasLocation) {
            PermissionNotice(
                "O Android só informa o nome do Wi‑Fi para apps com permissão de localização. Nada sai do seu aparelho.",
                action = "Conceder permissão", onAction = onRequestLocation,
            )
        }

        Overline("Rede atual")
        RCard(border = if (s.currentSsid != null) BorderStroke(1.dp, Cyan.copy(alpha = 0.5f)) else null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBox(if (s.currentSsid != null) Icons.Rounded.Wifi else Icons.Rounded.WifiOff)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.currentSsid ?: "Sem Wi‑Fi", style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = t.text)
                    Text(
                        when {
                            s.currentSsid == null -> "Conecte-se a um Wi‑Fi para adicioná-lo"
                            s.currentSsid in s.ssids -> "Já está na lista"
                            else -> "Não está na lista"
                        }, style = Type.caption, color = t.muted,
                    )
                }
            }
            if (s.currentSsid != null && s.currentSsid !in s.ssids) {
                Spacer(Modifier.height(12.dp))
                GradientButton("Adicionar rede atual", icon = Icons.Rounded.Add, onClick = onAddCurrent)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline("Redes escolhidas", Modifier.weight(1f))
            TextButton(onClick = { manual = true }) { Text("Adicionar pelo nome", color = Cyan) }
        }
        if (s.ssids.isEmpty()) {
            Text("Nenhuma rede ainda.", style = Type.bodySmall, color = t.muted, modifier = Modifier.padding(start = 4.dp))
        } else {
            RCard {
                s.ssids.forEach { ssid ->
                    SettingRow(ssid, icon = Icons.Rounded.Wifi) {
                        GradientSwitch(ssid !in s.disabled, { onToggleSsid(ssid, it) })
                        IconButton(onClick = { onRemoveSsid(ssid) }) { Icon(Icons.Rounded.Delete, "Remover $ssid", tint = t.muted) }
                    }
                }
            }
        }

        Overline("Comportamento")
        RCard {
            SettingRow("Desconectar ao sair dessas redes", "Só quando foi a regra automática que ligou") {
                GradientSwitch(s.disconnectOnLeave, onDisconnectOnLeave)
            }
            SettingRow("Conectar em redes abertas", "Liga em qualquer Wi‑Fi sem senha (Android 12+)") {
                GradientSwitch(s.connectOnOpen, onConnectOnOpen)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (manual) {
        TextDialog("Nome da rede (SSID)", "", singleLine = true, onDismiss = { manual = false }) {
            manual = false
            if (it.isNotBlank()) onAddManual(it.trim())
        }
    }
}
