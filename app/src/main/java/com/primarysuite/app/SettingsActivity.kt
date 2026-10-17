package com.primarysuite.app

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.primarysuite.app.core.config.AppPreferences
import com.primarysuite.app.core.config.ServerConfig
import com.primarysuite.app.core.config.resetToDefaults

/**
 * Simple settings screen that lets the user override WiFi and WebSocket connection defaults.
 * Clearing a field resets that value to the BuildConfig default on next use.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var appPreferences: AppPreferences

    private lateinit var editWifiSsid: EditText
    private lateinit var editWifiPassword: EditText
    private lateinit var editWsIp: EditText
    private lateinit var editWsPort: EditText
    private lateinit var buttonSave: MaterialButton
    private lateinit var buttonResetDefaults: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        title = getString(R.string.title_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        appPreferences = AppPreferences.getInstance(this)

        editWifiSsid = findViewById(R.id.editWifiSsid)
        editWifiPassword = findViewById(R.id.editWifiPassword)
        editWsIp = findViewById(R.id.editWsIp)
        editWsPort = findViewById(R.id.editWsPort)
        buttonSave = findViewById(R.id.buttonSave)
        buttonResetDefaults = findViewById(R.id.buttonResetDefaults)

        loadCurrentValues()

        buttonSave.setOnClickListener { saveSettings() }
        buttonResetDefaults.setOnClickListener { resetDefaults() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadCurrentValues() {
        // Show current effective values; leave blank if it's the default (hint shows default)
        editWifiSsid.setText(appPreferences.wifiSsidOverride ?: "")
        editWifiPassword.setText(appPreferences.wifiPasswordOverride ?: "")
        editWsIp.setText(appPreferences.serverHostOverride ?: "")
        editWsPort.setText(appPreferences.serverPortOverride ?: "")

        // Show default values as helper text below each field
        val defaults = appPreferences.defaults
        setHelperText(R.id.editWifiSsid, defaults.wifiSsid)
        setHelperText(R.id.editWifiPassword, defaults.wifiPassword)
        setHelperText(R.id.editWsIp, defaults.serverHost)
        setHelperText(R.id.editWsPort, defaults.serverPort)
    }

    private fun setHelperText(editTextId: Int, defaultValue: String) {
        val editText = findViewById<EditText>(editTextId)
        val layout = editText.parent?.parent as? TextInputLayout
        layout?.helperText = getString(R.string.hint_default_value, defaultValue)
    }

    private fun saveSettings() {
        // Validate port if provided
        val portText = editWsPort.text.toString().trim()
        if (portText.isNotBlank()) {
            if (!ServerConfig.isValidPort(portText)) {
                editWsPort.error = getString(R.string.settings_port_invalid)
                editWsPort.requestFocus()
                return
            }
        }

        appPreferences.wifiSsidOverride = editWifiSsid.text.toString().ifBlank { null }
        appPreferences.wifiPasswordOverride = editWifiPassword.text.toString().ifBlank { null }
        appPreferences.serverHostOverride = editWsIp.text.toString().trim().ifBlank { null }
        appPreferences.serverPortOverride = portText.ifBlank { null }

        Toast.makeText(this, R.string.toast_settings_saved, Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    private fun resetDefaults() {
        appPreferences.resetToDefaults()

        editWifiSsid.setText("")
        editWifiPassword.setText("")
        editWsIp.setText("")
        editWsPort.setText("")

        Toast.makeText(this, R.string.toast_settings_reset, Toast.LENGTH_SHORT).show()
    }
}
