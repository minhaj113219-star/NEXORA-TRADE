package com.nexora.trade.alerts

import android.content.Context

class AlertPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun isSoundEnabled(): Boolean = prefs.getBoolean(KEY_SOUND_ENABLED, true)

    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
    }

    fun isVibrationEnabled(): Boolean = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)

    fun setVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
    }

    fun hasPromptedNotificationPermission(): Boolean =
        prefs.getBoolean(KEY_PROMPTED_NOTIFICATION_PERMISSION, false)

    fun markNotificationPermissionPrompted() {
        prefs.edit().putBoolean(KEY_PROMPTED_NOTIFICATION_PERMISSION, true).apply()
    }

    private companion object {
        const val PREFS_NAME = "nexora_alert_preferences"
        const val KEY_SOUND_ENABLED = "sound_enabled"
        const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        const val KEY_PROMPTED_NOTIFICATION_PERMISSION = "prompted_notification_permission"
    }
}