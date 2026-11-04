package br.com.rtec.vpn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.rtec.vpn.ThemeMode

data class SettingsUiState(
    val theme: ThemeMode,
    val connectOnBoot: Boolean,
    val customDns: String,
    val excludedCount: Int,
    val version: String,
    val updateStatus: String?,
)

@Composable
fun SettingsScreen(
    s: SettingsUiState,
    onKillSwitch: () -> Unit,
    onConnectOnBoot: (Boolean) -> Unit,
    onSplitTunnel: () -> Unit,
    onDns: (String) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onGithub: () -> Unit,
    onCheckUpdates: () -> Unit,
) {
    val t = LocalRTec.current
    var editingDns by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ajustes", style = Type.title, color = t.text, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        Overline("Conexão")
        RCard {
            SettingRow("Kill switch", "Bloquear internet sem VPN (configurado no Android)", Icons.Rounded.Lock, onClick = onKillSwitch) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = t.muted)
            }
            SettingRow("Conectar ao ligar o celular", "Sobe a VPN depois de reiniciar", Icons.Rounded.PowerSettingsNew) {
                GradientSwitch(s.connectOnBoot, onConnectOnBoot)
            }
            SettingRow(
                "Split tunneling",
                if (s.excludedCount == 0) "Todos os apps usam a VPN" else "${s.excludedCount} app(s) fora da VPN",
                Icons.AutoMirrored.Rounded.CallSplit, onClick = onSplitTunnel,
            ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = t.muted) }
            SettingRow("DNS personalizado", s.customDns.ifBlank { "Usar o do servidor" }, Icons.Rounded.Dns, onClick = { editingDns = true }) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = t.muted)
            }
        }
        Overline("Aparência")
        RCard {
            SettingRow("Tema", icon = Icons.Rounded.DarkMode)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ThemeMode.DARK to "Escuro", ThemeMode.LIGHT to "Claro", ThemeMode.SYSTEM to "Sistema").forEach { (mode, label) ->
                    FilterChip(
                        selected = s.theme == mode, onClick = { onTheme(mode) }, label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Cyan.copy(alpha = 0.18f), selectedLabelColor = t.text),
                    )
                }
            }
        }
        Overline("Sobre")
        RCard {
            SettingRow("RTecVPN", "Versão ${s.version}", Icons.Rounded.Info)
            SettingRow("Código no GitHub", "rafanunesran/RTecVPN", Icons.Rounded.Code, onClick = onGithub) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = t.muted)
            }
            SettingRow("Verificar atualizações", s.updateStatus, Icons.Rounded.SystemUpdate, onClick = onCheckUpdates) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = t.muted)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (editingDns) {
        TextDialog("DNS personalizado (ex.: 1.1.1.1, 9.9.9.9)", s.customDns, singleLine = true, onDismiss = { editingDns = false }) {
            editingDns = false
            onDns(it)
        }
    }
}
