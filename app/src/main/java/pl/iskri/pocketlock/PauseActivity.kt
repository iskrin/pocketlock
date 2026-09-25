package pl.iskri.pocketlock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import java.lang.ref.WeakReference

/**
 * Opaque, empty activity that exists only to stop the app below (onStop) when it keeps running
 * behind the translucent lock screen (apps that ignore onPause, e.g. Mupen64Plus AE). It is
 * launched on demand - never for apps that pause themselves (e.g. RetroArch), because stopping
 * and restarting those can crash their graphics driver.
 */
class PauseActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = WeakReference(this)
        launching = false
        setShowWhenLocked(true)
        @Suppress("DEPRECATION")
        window.setWindowAnimations(0)
        Log.i(TAG, "PauseActivity created")
    }

    override fun onDestroy() {
        Log.i(TAG, "PauseActivity destroyed")
        launching = false
        if (instance?.get() === this) {
            instance = null
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PocketLock"
        private var instance: WeakReference<PauseActivity>? = null
        @Volatile
        private var launching = false

        fun launch(context: Context) {
            if (instance?.get() != null || launching) return
            launching = true
            val intent = Intent(context, PauseActivity::class.java)
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            try {
                Log.i(TAG, "starting PauseActivity")
                context.startActivity(intent)
            } catch (t: Throwable) {
                launching = false
                Log.w(TAG, "PauseActivity start failed: ${t.javaClass.simpleName}")
            }
        }

        fun isRunning(): Boolean = instance?.get() != null

        fun isLaunchingOrRunning(): Boolean = launching || instance?.get() != null

        fun finishIfRunning() {
            val activity = instance?.get() ?: return
            instance = null
            try {
                activity.finish()
            } catch (_: Throwable) {
            }
        }
    }
}
