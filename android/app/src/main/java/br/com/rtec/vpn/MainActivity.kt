package br.com.rtec.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var server: TextView
    private lateinit var toggle: MaterialButton

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (VpnService.prepare(this) == null) doConnect() else toast(getString(R.string.vpn_denied))
    }

    private val scanQr = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { saveConfig(it) }
    }

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        text?.let { saveConfig(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        status = findViewById(R.id.status)
        server = findViewById(R.id.server)
        toggle = findViewById(R.id.toggle)

        toggle.setOnClickListener {
            if (TunnelManager.state.value == VpnState.DOWN) requestConnect() else doDisconnect()
        }
        findViewById<MaterialButton>(R.id.scan).setOnClickListener {
            scanQr.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false).setPrompt(""))
        }
        findViewById<MaterialButton>(R.id.importFile).setOnClickListener { pickFile.launch(arrayOf("*/*")) }
        findViewById<MaterialButton>(R.id.paste).setOnClickListener { showPasteDialog() }
        findViewById<MaterialButton>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TunnelManager.state.collect { render(it) }
            }
        }
        lifecycleScope.launch { runCatching { TunnelManager.refresh(this@MainActivity) } }
        // Mantém o monitor de Wi-Fi rodando se a regra estiver ligada
        runCatching { AutoConnectService.sync(this) }
        renderServer()
    }

    private fun render(state: VpnState) {
        status.setText(
            when (state) {
                VpnState.DOWN -> R.string.status_off
                VpnState.CONNECTING -> R.string.status_connecting
                VpnState.UP -> R.string.status_on
            }
        )
        toggle.setText(if (state == VpnState.DOWN) R.string.connect else R.string.disconnect)
        toggle.isEnabled = state != VpnState.CONNECTING && prefs.configText != null
    }

    private fun renderServer() {
        val endpoint = prefs.configText?.let { text ->
            runCatching { TunnelManager.parse(text).peers.firstOrNull()?.endpoint?.orElse(null)?.host }.getOrNull()
        }
        server.text = endpoint?.let { getString(R.string.server_label, it) } ?: getString(R.string.no_config)
        render(TunnelManager.state.value)
    }

    private fun saveConfig(text: String) {
        try {
            TunnelManager.parse(text)
            prefs.configText = text
            toast(getString(R.string.config_saved))
            renderServer()
        } catch (e: Exception) {
            toast(getString(R.string.config_invalid, e.message ?: e.javaClass.simpleName))
        }
    }

    private fun showPasteDialog() {
        val input = EditText(this).apply { minLines = 6; setText(prefs.configText.orEmpty()) }
        AlertDialog.Builder(this)
            .setTitle(R.string.paste_config)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ -> saveConfig(input.text.toString()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun requestConnect() {
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermission.launch(intent) else doConnect()
    }

    private fun doConnect() = lifecycleScope.launch {
        try {
            TunnelManager.connect(this@MainActivity)
            prefs.autoStarted = false // ligado manualmente: não desligar ao sair do Wi-Fi
        } catch (e: Exception) {
            toast(getString(R.string.error, e.message ?: e.javaClass.simpleName))
        }
    }

    private fun doDisconnect() = lifecycleScope.launch {
        try {
            TunnelManager.disconnect(this@MainActivity)
            prefs.autoStarted = false
            prefs.pausedSsid = WifiUtils.currentSsid(this@MainActivity)
        } catch (e: Exception) {
            toast(getString(R.string.error, e.message ?: e.javaClass.simpleName))
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
