package com.nexora.trade.alerts

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AppSettingsStore {
    val darkThemeFlow: StateFlow<Boolean>
    fun isDarkTheme(): Boolean
    fun setDarkTheme(enabled: Boolean)
    fun isSoundEnabled(): Boolean
    fun setSoundEnabled(enabled: Boolean)
    fun isVibrationEnabled(): Boolean
    fun setVibrationEnabled(enabled: Boolean)
    fun hasPromptedNotificationPermission(): Boolean
    fun markNotificationPermissionPrompted()
    fun getLastPayoutPercent(): String
    fun setLastPayoutPercent(percent: String)
}

class AlertPreferences(context: Context) : AppSettingsStore {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _darkThemeFlow = MutableStateFlow(prefs.getBoolean(KEY_DARK_THEME, true))
    override val darkThemeFlow: StateFlow<Boolean> = _darkThemeFlow.asStateFlow()

    override fun isDarkTheme(): Boolean = _darkThemeFlow.value

    override fun setDarkTheme(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, enabled).apply()
        _darkThemeFlow.value = enabled
    }

    override fun isSoundEnabled(): Boolean = prefs.getBoolean(KEY_SOUND_ENABLED, true)

    override fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
    }

    override fun isVibrationEnabled(): Boolean = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)

    override fun setVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
    }

    override fun hasPromptedNotificationPermission(): Boolean =
        prefs.getBoolean(KEY_PROMPTED_NOTIFICATION_PERMISSION, false)

    override fun markNotificationPermissionPrompted() {
        prefs.edit().putBoolean(KEY_PROMPTED_NOTIFICATION_PERMISSION, true).apply()
    }

    override fun getLastPayoutPercent(): String =
        prefs.getString(KEY_LAST_PAYOUT_PERCENT, DEFAULT_PAYOUT_PERCENT) ?: DEFAULT_PAYOUT_PERCENT

    override fun setLastPayoutPercent(percent: String) {
        prefs.edit().putString(KEY_LAST_PAYOUT_PERCENT, percent).apply()
    }

    private companion object {
        const val PREFS_NAME = "nexora_alert_preferences"
        const val KEY_DARK_THEME = "dark_theme_enabled"
        const val KEY_SOUND_ENABLED = "sound_enabled"
        const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        const val KEY_PROMPTED_NOTIFICATION_PERMISSION = "prompted_notification_permission"
        const val KEY_LAST_PAYOUT_PERCENT = "last_payout_percent"
        const val DEFAULT_PAYOUT_PERCENT = "85"
    }
}