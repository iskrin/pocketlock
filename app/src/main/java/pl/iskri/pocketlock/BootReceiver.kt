package pl.iskri.pocketlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.isEnabled(context)) {
            LockService.start(context)
        }
    }
}
