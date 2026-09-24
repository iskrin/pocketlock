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
    private const val KEY_SCREEN_OFF_SECONDS = "screen_off_seconds"
    private const val DEFAULT_SCREEN_OFF_SECONDS = 10

    private const val KEY_BG_ENABLED = "bg_enabled"
    private const val KEY_BG_SCALE = "bg_scale"
    private const val KEY_BG_OFFSET_X = "bg_offset_x"
    private const val KEY_BG_OFFSET_Y = "bg_offset_y"
    private const val KEY_DOT_SCALE = "dot_scale"
    private const val KEY_DOT_SPACING = "dot_spacing"
    private const val KEY_DOT_CENTER_X = "dot_center_x"
    private const val KEY_DOT_CENTER_Y = "dot_center_y"
    private const val KEY_DOT_ACTIVE_COLOR = "dot_active_color"
    private const val KEY_DOT_INACTIVE_COLOR = "dot_inactive_color"

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

    fun screenOffSeconds(context: Context): Int =
        sp(context).getInt(KEY_SCREEN_OFF_SECONDS, DEFAULT_SCREEN_OFF_SECONDS)

    fun setScreenOffSeconds(context: Context, seconds: Int) {
        sp(context).edit().putInt(KEY_SCREEN_OFF_SECONDS, seconds).apply()
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

    fun isBackgroundEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_BG_ENABLED, false)

    fun setBackgroundEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_BG_ENABLED, enabled).apply()
    }

    fun backgroundScale(context: Context): Float = sp(context).getFloat(KEY_BG_SCALE, 1f)

    fun setBackgroundScale(context: Context, value: Float) {
        sp(context).edit().putFloat(KEY_BG_SCALE, value).apply()
    }

    fun backgroundOffsetX(context: Context): Float = sp(context).getFloat(KEY_BG_OFFSET_X, 0f)

    fun backgroundOffsetY(context: Context): Float = sp(context).getFloat(KEY_BG_OFFSET_Y, 0f)

    fun setBackgroundOffset(context: Context, x: Float, y: Float) {
        sp(context).edit().putFloat(KEY_BG_OFFSET_X, x).putFloat(KEY_BG_OFFSET_Y, y).apply()
    }

    fun dotScale(context: Context): Float = sp(context).getFloat(KEY_DOT_SCALE, 1.2f)

    fun setDotScale(context: Context, value: Float) {
        sp(context).edit().putFloat(KEY_DOT_SCALE, value).apply()
    }

    fun dotSpacing(context: Context): Float = sp(context).getFloat(KEY_DOT_SPACING, 1f)

    fun setDotSpacing(context: Context, value: Float) {
        sp(context).edit().putFloat(KEY_DOT_SPACING, value).apply()
    }

    fun dotCenterX(context: Context): Float = sp(context).getFloat(KEY_DOT_CENTER_X, 0.5f)

    fun dotCenterY(context: Context): Float = sp(context).getFloat(KEY_DOT_CENTER_Y, 0.5f)

    fun setDotCenter(context: Context, x: Float, y: Float) {
        sp(context).edit().putFloat(KEY_DOT_CENTER_X, x).putFloat(KEY_DOT_CENTER_Y, y).apply()
    }

    fun dotActiveColor(context: Context): Int =
        sp(context).getInt(KEY_DOT_ACTIVE_COLOR, 0xFFFFFFFF.toInt())

    fun setDotActiveColor(context: Context, color: Int) {
        sp(context).edit().putInt(KEY_DOT_ACTIVE_COLOR, color).apply()
    }

    fun dotInactiveColor(context: Context): Int =
        sp(context).getInt(KEY_DOT_INACTIVE_COLOR, 0x26FFFFFF)

    fun setDotInactiveColor(context: Context, color: Int) {
        sp(context).edit().putInt(KEY_DOT_INACTIVE_COLOR, color).apply()
    }

    fun resetAppearance(context: Context) {
        sp(context).edit()
            .remove(KEY_BG_SCALE)
            .remove(KEY_BG_OFFSET_X)
            .remove(KEY_BG_OFFSET_Y)
            .remove(KEY_DOT_SCALE)
            .remove(KEY_DOT_SPACING)
            .remove(KEY_DOT_CENTER_X)
            .remove(KEY_DOT_CENTER_Y)
            .remove(KEY_DOT_ACTIVE_COLOR)
            .remove(KEY_DOT_INACTIVE_COLOR)
            .apply()
    }

    fun resetBackgroundTransform(context: Context) {
        sp(context).edit()
            .remove(KEY_BG_SCALE)
            .remove(KEY_BG_OFFSET_X)
            .remove(KEY_BG_OFFSET_Y)
            .apply()
    }

    fun resetDotTransform(context: Context) {
        sp(context).edit()
            .remove(KEY_DOT_SCALE)
            .remove(KEY_DOT_SPACING)
            .remove(KEY_DOT_CENTER_X)
            .remove(KEY_DOT_CENTER_Y)
            .apply()
    }
}
