package pl.iskri.pocketlock

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

class SetupActivity : Activity() {

    private var tab = TAB_OPTIONS

    // Values of the "Screen off after" spinner, in seconds; 0 means "never".
    private val screenTimeoutValues = intArrayOf(5, 10, 15, 30, 60, 120, 0)

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

        findViewById<Button>(R.id.btnAdmin).setOnClickListener { requestAdmin() }

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

        val timeoutSpinner = findViewById<Spinner>(R.id.spScreenTimeout)
        val timeoutLabels = screenTimeoutValues.map { seconds ->
            when {
                seconds <= 0 -> getString(R.string.screen_timeout_never)
                seconds < 60 -> getString(R.string.screen_timeout_seconds, seconds)
                else -> getString(R.string.screen_timeout_minutes, seconds / 60)
            }
        }
        timeoutSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, timeoutLabels).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        val timeoutIndex = screenTimeoutValues.indexOf(Prefs.screenOffSeconds(this))
        timeoutSpinner.setSelection(
            if (timeoutIndex >= 0) timeoutIndex else screenTimeoutValues.lastIndex
        )
        timeoutSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val seconds = screenTimeoutValues[position]
                if (Prefs.screenOffSeconds(this@SetupActivity) == seconds) return
                Prefs.setScreenOffSeconds(this@SetupActivity, seconds)
                if (seconds > 0 && !ScreenTimeout.isAdminActive(this@SetupActivity)) {
                    Toast.makeText(
                        this@SetupActivity,
                        R.string.screen_timeout_admin_needed,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
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

    private fun requestAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(
                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                ComponentName(this@SetupActivity, LockDeviceAdminReceiver::class.java)
            )
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                getString(R.string.device_admin_explanation)
            )
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
        }
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

        findViewById<TextView>(R.id.tvAdminStatus).text =
            getString(R.string.status_device_admin) + ": " +
            yesNo(ScreenTimeout.isAdminActive(this))

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
