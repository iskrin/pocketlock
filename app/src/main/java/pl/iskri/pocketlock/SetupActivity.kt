package pl.iskri.pocketlock

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.TextView

class SetupActivity : Activity() {

    private val enabledListener = CompoundButton.OnCheckedChangeListener { _, checked ->
        Prefs.setEnabled(this, checked)
        if (checked) LockService.start(this) else LockService.stop(this)
        updateStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }
        }

        findViewById<Button>(R.id.btnNotification).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        findViewById<Switch>(R.id.swEnabled).setOnCheckedChangeListener(enabledListener)

        findViewById<Button>(R.id.btnTest).setOnClickListener {
            LockActivity.launch(this)
        }

        if (Prefs.isEnabled(this)) {
            LockService.start(this)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val overlay = Settings.canDrawOverlays(this)
        val notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        val battery = power.isIgnoringBatteryOptimizations(packageName)

        findViewById<TextView>(R.id.tvOverlayStatus).text =
            getString(R.string.status_overlay) + ": " + yesNo(overlay)
        findViewById<TextView>(R.id.tvNotificationStatus).text =
            getString(R.string.status_notifications) + ": " + yesNo(notifications)
        findViewById<TextView>(R.id.tvBatteryStatus).text =
            getString(R.string.status_battery) + ": " + yesNo(battery)

        val sw = findViewById<Switch>(R.id.swEnabled)
        val enabled = Prefs.isEnabled(this)
        if (sw.isChecked != enabled) {
            sw.setOnCheckedChangeListener(null)
            sw.isChecked = enabled
            sw.setOnCheckedChangeListener(enabledListener)
        }

        findViewById<TextView>(R.id.tvDebug).text =
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "Ostatnie wejście: " + Prefs.lastKey(this)
    }

    private fun yesNo(value: Boolean): String =
        if (value) getString(R.string.yes) else getString(R.string.no)
}
