package pl.iskri.pocketlock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log

/**
 * Turns the screen back off when the lock screen is not unlocked within the time chosen in
 * Options. Uses the device administrator permission, because turning the display off is not
 * possible for a normal app on modern Android.
 */
object ScreenTimeout {

    private const val TAG = "PocketLock"

    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null

    private val lockRunnable = Runnable {
        val context = appContext ?: return@Runnable
        if (!isInteractive(context)) return@Runnable
        if (!isAdminActive(context)) return@Runnable
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.lockNow()
            Log.i(TAG, "screen timeout elapsed, screen off")
        } catch (t: Throwable) {
            Log.i(TAG, "screen off failed: ${t.javaClass.simpleName}")
        }
    }

    /** (Re)starts the countdown; a value of 0 means "never". */
    fun start(context: Context) {
        appContext = context.applicationContext
        handler.removeCallbacks(lockRunnable)
        val seconds = Prefs.screenOffSeconds(context)
        if (seconds <= 0) return
        if (!isInteractive(context)) return
        if (!isAdminActive(context)) return
        handler.postDelayed(lockRunnable, seconds * 1000L)
    }

    fun cancel() {
        handler.removeCallbacks(lockRunnable)
    }

    fun isAdminActive(context: Context): Boolean = try {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.isAdminActive(ComponentName(context, LockDeviceAdminReceiver::class.java))
    } catch (_: Throwable) {
        false
    }

    private fun isInteractive(context: Context): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
}
