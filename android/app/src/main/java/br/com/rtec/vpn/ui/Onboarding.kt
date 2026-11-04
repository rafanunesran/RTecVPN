package br.com.rtec.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class Step(val icon: ImageVector, val title: String, val text: String)

private val steps = listOf(
    Step(Icons.Rounded.Shield, "Privacidade com WireGuard", "Criptografia moderna e rápida protegendo tudo o que você faz em redes públicas."),
    Step(Icons.Rounded.Dns, "Use seu servidor", "Sem cadastro e sem limite: você roda o servidor grátis (ex.: Oracle Cloud) e escaneia um QR code."),
    Step(Icons.Rounded.Wifi, "Auto-conexão no Wi‑Fi", "Escolha as redes onde a VPN liga sozinha, e ela desliga quando você sai delas."),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val t = LocalRTec.current
    var page by remember { mutableIntStateOf(0) }
    val step = steps[page]
    Column(
        Modifier.fillMaxSize().background(t.background).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (page < steps.lastIndex) TextButton(onClick = onDone) { Text("Pular", color = t.muted) }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(180.dp).clip(RoundedCornerShape(32.dp)).background(BrandGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(step.icon, null, tint = Color(0xFF0B1020), modifier = Modifier.size(84.dp))
        }
        Spacer(Modifier.height(40.dp))
        Text(step.title, style = Type.headline, color = t.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(step.text, style = Type.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = t.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.indices.forEach { i ->
                Box(
                    Modifier.height(6.dp).width(if (i == page) 24.dp else 6.dp).clip(RoundedCornerShape(999.dp))
                        .background(if (i == page) Cyan else t.surface2)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        GradientButton(if (page == steps.lastIndex) "Começar" else "Próximo") {
            if (page == steps.lastIndex) onDone() else page++
        }
    }
}
