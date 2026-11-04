package br.com.rtec.vpn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.rtec.vpn.VpnState

fun stateColor(state: VpnState, idle: Color) = when (state) {
    VpnState.DOWN -> idle
    VpnState.CONNECTING -> Warning
    VpnState.UP -> Success
}

/** Botão central de conexão: anel de 3dp, halo pulsando e anel girando ao conectar. */
@Composable
fun ConnectButton(state: VpnState, enabled: Boolean, onClick: () -> Unit) {
    val t = LocalRTec.current
    val color by animateColorAsState(stateColor(state, t.idle), tween(400), label = "cor")
    val infinite = rememberInfiniteTransition(label = "anim")
    val pulse by infinite.animateFloat(
        0.85f, 1.1f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulso"
    )
    val spin by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "giro")
    val haloAlpha by animateFloatAsState(if (state == VpnState.DOWN) 0f else 0.35f, label = "halo")

    Box(Modifier.size(224.dp), contentAlignment = Alignment.Center) {
        // Halo
        Box(
            Modifier.size(190.dp).scale(if (state == VpnState.DOWN) 1f else pulse).alpha(haloAlpha)
                .blur(24.dp).background(color, CircleShape)
        )
        // Anel
        Canvas(Modifier.size(200.dp).rotate(if (state == VpnState.CONNECTING) spin else 0f)) {
            val brush = if (state == VpnState.CONNECTING) {
                Brush.sweepGradient(listOf(Color.Transparent, color))
            } else {
                Brush.linearGradient(listOf(color, color))
            }
            drawCircle(brush, style = Stroke(width = 3.dp.toPx()))
        }
        Box(
            Modifier.size(160.dp).clip(CircleShape).background(t.surface)
                .border(1.dp, t.border, CircleShape)
                .clickable(enabled = enabled && state != VpnState.CONNECTING, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PowerSettingsNew, contentDescription = if (state == VpnState.UP) "Desconectar" else "Conectar",
                tint = color, modifier = Modifier.size(56.dp))
        }
    }
}

@Composable
fun GradientButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    Box(
        modifier.height(56.dp).fillMaxWidth().alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(16.dp)).background(BrandGradient)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, null, tint = Color(0xFF0B1020), modifier = Modifier.size(20.dp))
            Text(text, color = Color(0xFF0B1020), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = Inter)
        }
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    val t = LocalRTec.current
    Box(
        modifier.height(52.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(t.surface)
            .border(1.dp, t.border, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (icon != null) IconBox(icon, size = 32.dp)
            Text(text, style = Type.body, color = t.text)
        }
    }
}

@Composable
fun RCard(modifier: Modifier = Modifier, border: BorderStroke? = null, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalRTec.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(t.surface)
            .border(border ?: BorderStroke(1.dp, t.border), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun IconBox(icon: ImageVector, size: androidx.compose.ui.unit.Dp = 44.dp, gradient: Boolean = false) {
    val t = LocalRTec.current
    Box(
        Modifier.size(size).clip(RoundedCornerShape(if (size > 36.dp) 16.dp else 12.dp))
            .background(if (gradient) BrandGradient else Brush.linearGradient(listOf(t.surface2, t.surface2))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (gradient) Color(0xFF0B1020) else Cyan, modifier = Modifier.size(size * 0.48f))
    }
}

/** Switch 48×28 com trilho em gradiente quando ligado. */
@Composable
fun GradientSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val t = LocalRTec.current
    val offset by animateFloatAsState(if (checked) 1f else 0f, tween(180), label = "thumb")
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier.width(48.dp).height(28.dp).alpha(if (enabled) 1f else 0.5f).clip(shape)
            .background(if (checked) BrandGradient else Brush.linearGradient(listOf(t.surface2, t.surface2)))
            .border(1.dp, if (checked) Color.Transparent else t.border, shape)
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
    ) {
        Box(
            Modifier.padding(4.dp).offset(x = 20.dp * offset).size(20.dp)
                .background(if (checked) t.background else t.muted, CircleShape)
        )
    }
}

@Composable
fun SettingRow(title: String, subtitle: String? = null, icon: ImageVector? = null, onClick: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    val t = LocalRTec.current
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { IconBox(icon, size = 36.dp); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.body, color = t.text)
            if (subtitle != null) Text(subtitle, style = Type.bodySmall, color = t.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
fun Overline(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = Type.overline, color = LocalRTec.current.muted, modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp))
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val t = LocalRTec.current
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(t.surface).border(1.dp, t.border, RoundedCornerShape(16.dp)).padding(12.dp)
    ) {
        Text(label, fontSize = 11.sp, color = t.muted, fontFamily = Inter)
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = t.text, fontFamily = Inter,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"), maxLines = 1)
    }
}

@Composable
fun PermissionNotice(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Warning.copy(alpha = 0.10f))
            .border(1.dp, Warning.copy(alpha = 0.30f), shape)
            .then(if (onAction != null) Modifier.clickable(onClick = onAction) else Modifier).padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Rounded.WarningAmber, null, tint = Warning, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(text, style = Type.bodySmall, color = LocalRTec.current.text)
            if (action != null) Text(action, style = Type.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = Warning, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun Chip(text: String, color: Color) {
    Text(
        text, fontSize = 11.sp, fontFamily = Inter, fontWeight = FontWeight.SemiBold, color = color,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

fun formatBytes(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.1f KB".format(b / 1024.0)
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / 1024.0 / 1024)
    else -> "%.2f GB".format(b / 1024.0 / 1024 / 1024)
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
}

fun formatAgo(epochMs: Long, now: Long): String {
    if (epochMs <= 0) return "nunca"
    val s = (now - epochMs) / 1000
    return when {
        s < 60 -> "há $s s"
        s < 3600 -> "há ${s / 60} min"
        else -> "há ${s / 3600} h"
    }
}
