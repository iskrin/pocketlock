package pl.iskri.pocketlock

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import kotlin.math.roundToInt

class SetupActivity : Activity() {

    private var tab = TAB_OPTIONS

    private val enabledListener = CompoundButton.OnCheckedChangeListener { _, checked ->
        Prefs.setEnabled(this, checked)
        if (checked) LockService.start(this) else LockService.stop(this)
        updateStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        findViewById<Button>(R.id.btnTabPermissions).setOnClickListener { setTab(TAB_PERMISSIONS) }
        findViewById<Button>(R.id.btnTabOptions).setOnClickListener { setTab(TAB_OPTIONS) }

        findViewById<ImageButton>(R.id.btnInfo).setOnClickListener { showInstructions() }

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

        findViewById<Button>(R.id.btnAppearance).setOnClickListener {
            startActivity(Intent(this, AppearanceActivity::class.java))
        }

        val cbSound = findViewById<CheckBox>(R.id.cbSound)
        cbSound.isChecked = Prefs.isSoundEnabled(this)
        cbSound.setOnCheckedChangeListener { _, checked -> Prefs.setSoundEnabled(this, checked) }

        val cbVibration = findViewById<CheckBox>(R.id.cbVibration)
        cbVibration.isChecked = Prefs.isVibrationEnabled(this)
        cbVibration.setOnCheckedChangeListener { _, checked ->
            Prefs.setVibrationEnabled(this, checked)
        }

        val cbNotification = findViewById<CheckBox>(R.id.cbNotification)
        cbNotification.isChecked = Prefs.isNotificationEnabled(this)
        cbNotification.setOnCheckedChangeListener { _, checked ->
            Prefs.setNotificationEnabled(this, checked)
            if (Prefs.isEnabled(this)) {
                LockService.start(this)
            }
        }

        if (Prefs.isEnabled(this)) {
            LockService.start(this)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val content = findViewById<FrameLayout>(R.id.setup_content)
            content.setOnApplyWindowInsetsListener { view, insets ->
                val gestureBottom = insets.getInsets(WindowInsets.Type.systemGestures()).bottom
                view.setPadding(
                    view.paddingLeft,
                    view.paddingTop,
                    view.paddingRight,
                    dp(72) + gestureBottom
                )
                insets
            }
        }

        setTab(if (Settings.canDrawOverlays(this)) TAB_OPTIONS else TAB_PERMISSIONS)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun setTab(newTab: Int) {
        tab = newTab
        findViewById<View>(R.id.tab_permissions).visibility =
            if (newTab == TAB_PERMISSIONS) View.VISIBLE else View.GONE
        findViewById<View>(R.id.tab_options).visibility =
            if (newTab == TAB_OPTIONS) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnTabPermissions).alpha =
            if (newTab == TAB_PERMISSIONS) 1f else 0.5f
        findViewById<Button>(R.id.btnTabOptions).alpha =
            if (newTab == TAB_OPTIONS) 1f else 0.5f
    }

    private fun showInstructions() {
        AlertDialog.Builder(this)
            .setTitle(R.string.instructions_title)
            .setMessage(R.string.instructions)
            .setPositiveButton(android.R.string.ok, null)
            .show()
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

        findViewById<TextView>(R.id.tvServiceStatus).text =
            getString(R.string.status_service) + ": " +
            if (LockService.isRunning) getString(R.string.yes) else getString(R.string.no)

        val sw = findViewById<Switch>(R.id.swEnabled)
        val enabled = Prefs.isEnabled(this)
        if (sw.isChecked != enabled) {
            sw.setOnCheckedChangeListener(null)
            sw.isChecked = enabled
            sw.setOnCheckedChangeListener(enabledListener)
        }
    }

    private fun yesNo(value: Boolean): String =
        if (value) getString(R.string.yes) else getString(R.string.no)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val TAB_PERMISSIONS = 0
        const val TAB_OPTIONS = 1
    }
}
