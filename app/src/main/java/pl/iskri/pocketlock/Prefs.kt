package pl.iskri.pocketlock

import android.content.Context

object Prefs {

    private const val FILE = "pocketlock"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_KEY = "last_key"
    private const val KEY_SOUND = "sound_status"
    private const val KEY_SOUND_ENABLED = "sound_enabled"
    private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
    private const val KEY_NOTIFICATION_ENABLED = "notification_enabled"

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = sp(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isSoundEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_SOUND_ENABLED, true)

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
    }

    fun isVibrationEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_VIBRATION_ENABLED, true)

    fun setVibrationEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
    }

    fun isNotificationEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_NOTIFICATION_ENABLED, true)

    fun setNotificationEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_NOTIFICATION_ENABLED, enabled).apply()
    }

    fun lastKey(context: Context): String =
        sp(context).getString(KEY_LAST_KEY, "-") ?: "-"

    fun setLastKey(context: Context, value: String) {
        sp(context).edit().putString(KEY_LAST_KEY, value).apply()
    }

    fun soundStatus(context: Context): String =
        sp(context).getString(KEY_SOUND, "-") ?: "-"

    fun setSoundStatus(context: Context, value: String) {
        sp(context).edit().putString(KEY_SOUND, value).apply()
    }
}
