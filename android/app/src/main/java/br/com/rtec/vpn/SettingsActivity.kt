package br.com.rtec.vpn

import android.Manifest
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

/** Configura a conexão automática por rede Wi-Fi. */
class SettingsActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var autoSwitch: MaterialSwitch
    private lateinit var adapter: ArrayAdapter<String>

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasLocation()) enableAuto() else {
            autoSwitch.isChecked = false
            toast(getString(R.string.location_needed))
        }
    }

    private val askVpn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (VpnService.prepare(this) == null) enableAuto() else {
            autoSwitch.isChecked = false
            toast(getString(R.string.vpn_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)
        autoSwitch = findViewById(R.id.autoSwitch)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        val list = findViewById<ListView>(R.id.ssidList)
        list.adapter = adapter
        refreshList()

        autoSwitch.isChecked = prefs.autoConnectEnabled
        autoSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) startEnableFlow() else {
                prefs.autoConnectEnabled = false
                AutoConnectService.sync(this)
            }
        }

        findViewById<MaterialButton>(R.id.addCurrent).setOnClickListener {
            if (!hasLocation()) { requestLocation(); return@setOnClickListener }
            val ssid = WifiUtils.currentSsid(this)
            if (ssid == null) toast(getString(R.string.no_wifi)) else addSsid(ssid)
        }
        findViewById<MaterialButton>(R.id.addManual).setOnClickListener {
            val input = EditText(this)
            AlertDialog.Builder(this)
                .setTitle(R.string.add_manual)
                .setView(input)
                .setPositiveButton(android.R.string.ok) { _, _ -> input.text.toString().trim().takeIf { it.isNotEmpty() }?.let(::addSsid) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        list.setOnItemClickListener { _, _, pos, _ ->
            val ssid = adapter.getItem(pos) ?: return@setOnItemClickListener
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.remove_ssid, ssid))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    prefs.trustedSsids = prefs.trustedSsids - ssid
                    refreshList()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun addSsid(ssid: String) {
        prefs.trustedSsids = prefs.trustedSsids + ssid
        refreshList()
    }

    private fun refreshList() {
        adapter.clear()
        adapter.addAll(prefs.trustedSsids.sorted())
    }

    private fun startEnableFlow() {
        when {
            prefs.configText == null -> { autoSwitch.isChecked = false; toast(getString(R.string.no_config)) }
            !hasLocation() -> requestLocation()
            VpnService.prepare(this) != null -> askVpn.launch(VpnService.prepare(this))
            else -> enableAuto()
        }
    }

    private fun enableAuto() {
        if (VpnService.prepare(this) != null) { askVpn.launch(VpnService.prepare(this)); return }
        prefs.autoConnectEnabled = true
        AutoConnectService.sync(this)
    }

    private fun hasLocation() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocation() {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        AlertDialog.Builder(this)
            .setMessage(R.string.location_rationale)
            .setPositiveButton(android.R.string.ok) { _, _ -> askPermissions.launch(perms.toTypedArray()) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> autoSwitch.isChecked = prefs.autoConnectEnabled }
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
