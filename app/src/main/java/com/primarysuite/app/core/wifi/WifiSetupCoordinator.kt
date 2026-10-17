package com.primarysuite.app.core.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.primarysuite.app.R
import com.primarysuite.app.core.config.ConnectionSettings
import com.primarysuite.app.core.config.effectiveWifiPassword
import com.primarysuite.app.core.config.effectiveWifiSsid

/**
 * Gets the phone onto the event network: asks for permissions, prompts to turn
 * on WiFi and location, then connects. The order lives in [nextWifiSetupAction];
 * this class only performs each step.
 *
 * Create it while the activity is being constructed (as a property), because it
 * registers activity result launchers.
 */
class WifiSetupCoordinator(
    private val activity: ComponentActivity,
    wifiProvider: () -> WifiConnector,
    settingsProvider: () -> ConnectionSettings,
) {
    // Providers, because the app container isn't reachable until the activity is attached.
    private val wifi by lazy(wifiProvider)
    private val settings by lazy(settingsProvider)

    companion object {
        private const val TAG = "WifiSetup"

        val REQUIRED_PERMISSIONS: List<String> = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_WIFI_STATE)
            add(Manifest.permission.CHANGE_WIFI_STATE)
            add(Manifest.permission.ACCESS_NETWORK_STATE)
            add(Manifest.permission.INTERNET)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
    }

    /** The prompt on screen right now. Steps wait until its result arrives. */
    private var awaiting: WifiSetupAction? = null

    /**
     * Steps the user backed out of. They aren't prompted again on every resume,
     * only after [retry]. Without this, a declined WiFi panel would reopen forever.
     */
    private val declined = mutableSetOf<WifiSetupAction>()

    private val permissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            onPromptResult()
        }

    private val settingsPanelLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            onPromptResult()
        }

    private fun conditions() = WifiSetupConditions(
        state = wifi.connectionState.value,
        permissionsGranted = missingPermissions().isEmpty(),
        wifiEnabled = isWifiEnabled(),
        locationEnabled = isLocationEnabled(),
        ssid = settings.effectiveWifiSsid,
        password = settings.effectiveWifiPassword,
    )

    /** Takes the next setup step unless the user declined it. Call on resume. */
    fun ensureConnected() = step()

    /** Asks again for anything the user declined, e.g. when they tap the WiFi status. */
    fun retry() {
        declined.clear()
        step()
    }

    /** Drops the current network and joins again, e.g. after the SSID or password changed. */
    fun reconnect() {
        wifi.disconnect()
        declined.remove(WifiSetupAction.MissingSsid)
        step()
    }

    /** After the controller gives up, point at the likely cause without retrying in a loop. */
    fun onConnectionError() {
        if (awaiting != null) return
        when {
            !isWifiEnabled() -> perform(WifiSetupAction.EnableWifi)
            !isLocationEnabled() -> perform(WifiSetupAction.EnableLocation)
        }
    }

    private fun step() {
        if (awaiting != null) return
        val action = nextWifiSetupAction(conditions())
        Log.d(TAG, "Next step: $action")
        if (action !in declined) perform(action)
    }

    private fun onPromptResult() {
        val asked = awaiting ?: return
        awaiting = null
        val action = nextWifiSetupAction(conditions())
        if (action == asked) {
            declined += action
            explainDeclined(action)
        } else {
            step()
        }
    }

    @SuppressLint("MissingPermission") // Connect is only returned once permissions are granted.
    private fun perform(action: WifiSetupAction) {
        when (action) {
            WifiSetupAction.None -> Unit
            WifiSetupAction.RequestPermissions -> {
                awaiting = action
                permissionLauncher.launch(missingPermissions().toTypedArray())
            }
            WifiSetupAction.EnableWifi -> {
                awaiting = action
                promptEnableWifi()
            }
            WifiSetupAction.EnableLocation -> {
                awaiting = action
                promptEnableLocation()
            }
            WifiSetupAction.MissingSsid -> {
                declined += action
                toast(R.string.toast_wifi_ssid_not_configured, long = true)
            }
            is WifiSetupAction.Connect -> wifi.connect(action.ssid, action.password)
        }
    }

    private fun explainDeclined(action: WifiSetupAction) {
        when (action) {
            WifiSetupAction.RequestPermissions -> explainDeniedPermission()
            WifiSetupAction.EnableWifi -> toast(R.string.toast_wifi_required, long = true)
            WifiSetupAction.EnableLocation -> toast(R.string.toast_location_required, long = true)
            else -> Unit
        }
    }

    private fun missingPermissions(): List<String> = REQUIRED_PERMISSIONS.filter {
        ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
    }

    private fun explainDeniedPermission() {
        // shouldShowRequestPermissionRationale is false after "Don't ask again": only App Settings can fix it.
        if (!activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            toast(R.string.toast_location_permission_settings, long = true)
            activity.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
            )
        } else {
            toast(R.string.toast_location_permission_essential, long = true)
        }
    }

    private fun promptEnableWifi() {
        toast(R.string.toast_enable_wifi, long = true)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        settingsPanelLauncher.launch(intent)
    }

    private fun promptEnableLocation() {
        toast(R.string.toast_location_services_required, long = true)
        settingsPanelLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    private fun isWifiEnabled(): Boolean =
        (activity.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?)?.isWifiEnabled == true

    private fun isLocationEnabled(): Boolean {
        val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            try {
                Settings.Secure.getInt(activity.contentResolver, Settings.Secure.LOCATION_MODE) != Settings.Secure.LOCATION_MODE_OFF
            } catch (_: Settings.SettingNotFoundException) {
                false
            }
        }
    }

    private fun toast(resId: Int, long: Boolean = false) {
        Toast.makeText(activity, resId, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
}
