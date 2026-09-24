package pl.iskri.pocketlock

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class LockDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i("PocketLock", "device admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.i("PocketLock", "device admin disabled")
    }
}
