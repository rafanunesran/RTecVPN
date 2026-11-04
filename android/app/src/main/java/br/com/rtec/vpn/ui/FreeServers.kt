package br.com.rtec.vpn.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.rtec.vpn.VgServer

data class FreeServersState(
    val servers: List<VgServer>,
    val loading: Boolean,
    val error: String?,
)

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val t = LocalRTec.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(t.surface)
            .padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(999.dp))
                    .background(if (i == selected) t.surface2 else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Type.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = if (i == selected) t.text else t.muted)
            }
        }
    }
}

/** Servidores gratuitos do VPN Gate, com filtro por país. */
@Composable
fun FreeServersList(
    s: FreeServersState,
    selectedId: String?,
    activeId: String?,
    onSelect: (VgServer) -> Unit,
    onRefresh: () -> Unit,
) {
    val t = LocalRTec.current
    var country by remember { mutableStateOf<String?>(null) }
    val countries = remember(s.servers) {
        s.servers.groupBy { it.countryCode }.entries.sortedByDescending { it.value.size }.map { it.key to it.value.first() }
    }
    val shown = s.servers.filter { country == null || it.countryCode == country }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (s.servers.isEmpty()) "Lista do VPN Gate" else "${s.servers.size} servidores em ${countries.size} países",
                style = Type.caption, color = t.muted, modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            if (s.loading) CircularProgressIndicator(color = Cyan, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            else IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, "Atualizar lista", tint = Cyan) }
        }
        if (s.error != null && s.servers.isEmpty()) {
            PermissionNotice("Não foi possível baixar a lista: ${s.error}", action = "Tentar de novo", onAction = onRefresh)
        }
        if (countries.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CountryChip("Todos", country == null) { country = null }
                countries.forEach { (code, any) -> CountryChip("${any.flag} ${any.country}", country == code) { country = code } }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (shown.isNotEmpty()) {
            RCard(onClick = { onSelect(shown.first()) }, border = BorderStroke(1.dp, Cyan.copy(alpha = 0.5f))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBox(Icons.Rounded.Bolt, gradient = true)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Melhor servidor${country?.let { " em ${shown.first().country}" } ?: ""}", style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = t.text)
                        Text("Escolhe o de melhor pontuação (velocidade e estabilidade)", style = Type.caption, color = t.muted)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown, key = { it.id }) { srv ->
                FreeServerCard(srv, selected = srv.id == selectedId, active = srv.id == activeId) { onSelect(srv) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun CountryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val t = LocalRTec.current
    Text(
        label, style = Type.bodySmall, color = t.text,
        modifier = Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (selected) Cyan.copy(alpha = 0.18f) else t.surface)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun FreeServerCard(srv: VgServer, selected: Boolean, active: Boolean, onClick: () -> Unit) {
    val t = LocalRTec.current
    RCard(border = if (selected) BorderStroke(1.dp, Cyan) else null, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(t.surface2), contentAlignment = Alignment.Center) {
                Text(srv.flag, fontSize = 22.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(srv.country, style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = t.text)
                Text("${srv.host} · ${srv.transport}", style = Type.mono.copy(fontSize = 12.sp), color = t.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (srv.pingMs > 0) Chip("${srv.pingMs} ms", if (srv.pingMs < 80) Success else Warning)
                    Chip("%.0f Mbps".format(srv.speedBps / 1_000_000.0), Cyan)
                    if (active) Chip("Em uso", Success)
                }
            }
            if (selected) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(BrandGradient), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, "Selecionado", tint = Color(0xFF0B1020), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
