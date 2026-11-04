package br.com.rtec.vpn.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.rtec.vpn.Profile
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder

@Composable
fun ServersScreen(
    profiles: List<Profile>,
    selectedId: String?,
    activeId: String?,
    onSelect: (Profile) -> Unit,
    onAdd: () -> Unit,
    onRename: (Profile, String) -> Unit,
    onEditConfig: (Profile, String) -> Unit,
    onDelete: (Profile) -> Unit,
) {
    val t = LocalRTec.current
    var renaming by remember { mutableStateOf<Profile?>(null) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    var sharing by remember { mutableStateOf<Profile?>(null) }
    var deleting by remember { mutableStateOf<Profile?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text("Servidores", style = Type.title, color = t.text, modifier = Modifier.padding(vertical = 16.dp))
            if (profiles.isEmpty()) {
                EmptyServers(onAdd)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(profiles, key = { it.id }) { p ->
                        ServerCard(
                            p, selected = p.id == selectedId, active = p.id == activeId,
                            onClick = { onSelect(p) },
                            onRename = { renaming = p }, onEdit = { editing = p },
                            onShare = { sharing = p }, onDelete = { deleting = p },
                        )
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
        if (profiles.isNotEmpty()) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(20.dp).size(56.dp).clip(RoundedCornerShape(18.dp))
                    .background(BrandGradient).clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, "Adicionar servidor", tint = Color(0xFF0B1020), modifier = Modifier.size(26.dp)) }
        }
    }

    renaming?.let { p ->
        TextDialog("Renomear servidor", p.name, singleLine = true, onDismiss = { renaming = null }) { onRename(p, it); renaming = null }
    }
    editing?.let { p ->
        TextDialog("Editar configuração", p.config, singleLine = false, onDismiss = { editing = null }) { onEditConfig(p, it); editing = null }
    }
    sharing?.let { p -> QrDialog(p) { sharing = null } }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Excluir ${p.name}?") },
            text = { Text("A configuração será apagada deste aparelho.") },
            confirmButton = { TextButton(onClick = { onDelete(p); deleting = null }) { Text("Excluir", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun EmptyServers(onAdd: () -> Unit) {
    val t = LocalRTec.current
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBox(Icons.Rounded.Dns, size = 72.dp)
        Spacer(Modifier.height(16.dp))
        Text("Nenhum servidor ainda", style = Type.title, color = t.text)
        Text(
            "Rode o add-client.sh no seu servidor e escaneie o QR code que ele mostra.",
            style = Type.bodySmall, color = t.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
        )
        GradientButton("Adicionar servidor", icon = Icons.Rounded.Add, onClick = onAdd)
    }
}

@Composable
private fun ServerCard(
    p: Profile, selected: Boolean, active: Boolean,
    onClick: () -> Unit, onRename: () -> Unit, onEdit: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit,
) {
    val t = LocalRTec.current
    var menu by remember { mutableStateOf(false) }
    RCard(border = if (selected) BorderStroke(1.dp, Cyan) else null, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBox(Icons.Rounded.Dns)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = t.text)
                Text(endpointOf(p) ?: "configuração inválida", style = Type.mono.copy(fontSize = 12.sp), color = t.muted)
                if (active) Text("Em uso agora", fontSize = 11.sp, color = Success, fontFamily = Inter, modifier = Modifier.padding(top = 2.dp))
            }
            if (selected) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(BrandGradient), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, "Selecionado", tint = Color(0xFF0B1020), modifier = Modifier.size(16.dp))
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Mais opções", tint = t.muted) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Renomear") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Editar configuração") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Compartilhar QR code") }, onClick = { menu = false; onShare() })
                    DropdownMenuItem(text = { Text("Excluir", color = ErrorRed) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
fun TextDialog(title: String, initial: String, singleLine: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = singleLine,
                textStyle = if (singleLine) Type.body else Type.mono,
                modifier = Modifier.fillMaxWidth().then(if (singleLine) Modifier else Modifier.heightIn(min = 200.dp)),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun QrDialog(p: Profile, onDismiss: () -> Unit) {
    val bitmap: Bitmap? = remember(p.id) {
        runCatching { BarcodeEncoder().encodeBitmap(p.config, BarcodeFormat.QR_CODE, 720, 720) }.getOrNull()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p.name) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                bitmap?.let {
                    Image(it.asImageBitmap(), "QR code da configuração",
                        Modifier.size(260.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(8.dp))
                }
                Text(
                    "Quem escanear este código usa a mesma chave que você. Para outro aparelho, prefira gerar um cliente novo com o add-client.sh.",
                    style = Type.caption, color = LocalRTec.current.muted, modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

/** Folha "Adicionar servidor": QR code, arquivo .conf ou colar texto. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServerSheet(onDismiss: () -> Unit, onScan: () -> Unit, onFile: () -> Unit, onPaste: (String) -> Unit) {
    val t = LocalRTec.current
    var pasting by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = t.background) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Adicionar servidor", style = Type.title, color = t.text)
            Text("Use o QR code ou o arquivo gerado pelo add-client.sh no seu servidor.", style = Type.bodySmall, color = t.muted)
            GradientButton("Escanear QR code", icon = Icons.Rounded.QrCodeScanner, onClick = onScan)
            SecondaryButton("Importar arquivo .conf", icon = Icons.Rounded.FileOpen, onClick = onFile)
            SecondaryButton("Colar configuração", icon = Icons.Rounded.ContentPaste) { pasting = true }
        }
    }
    if (pasting) {
        TextDialog("Colar configuração", "", singleLine = false, onDismiss = { pasting = false }) { pasting = false; onPaste(it) }
    }
}
