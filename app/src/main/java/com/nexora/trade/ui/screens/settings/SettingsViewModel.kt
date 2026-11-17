package com.nexora.trade.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nexora.trade.BuildConfig
import com.nexora.trade.alerts.AppSettingsStore
import com.nexora.trade.alerts.NotificationStatusProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsUiState(
    val appVersion: String = BuildConfig.VERSION_NAME,
    val isDarkTheme: Boolean = true,
    val isSoundEnabled: Boolean = true,
    val isVibrationEnabled: Boolean = true,
    val hasNotificationPermission: Boolean = true,
    val areSystemNotificationsEnabled: Boolean = true
)

class SettingsViewModel(
    private val settingsStore: AppSettingsStore,
    private val notificationStatusProvider: NotificationStatusProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(readCurrentState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun toggleDarkTheme(enabled: Boolean) {
        settingsStore.setDarkTheme(enabled)
        _uiState.update { it.copy(isDarkTheme = enabled) }
    }

    fun toggleSound(enabled: Boolean) {
        settingsStore.setSoundEnabled(enabled)
        _uiState.update { it.copy(isSoundEnabled = enabled) }
    }

    fun toggleVibration(enabled: Boolean) {
        settingsStore.setVibrationEnabled(enabled)
        _uiState.update { it.copy(isVibrationEnabled = enabled) }
    }

    fun refreshNotificationStatus() {
        _uiState.update {
            it.copy(
                hasNotificationPermission = notificationStatusProvider.hasNotificationPermission(),
                areSystemNotificationsEnabled = notificationStatusProvider.areSystemNotificationsEnabled()
            )
        }
    }

    private fun readCurrentState(): SettingsUiState {
        return SettingsUiState(
            appVersion = BuildConfig.VERSION_NAME,
            isDarkTheme = settingsStore.isDarkTheme(),
            isSoundEnabled = settingsStore.isSoundEnabled(),
            isVibrationEnabled = settingsStore.isVibrationEnabled(),
            hasNotificationPermission = notificationStatusProvider.hasNotificationPermission(),
            areSystemNotificationsEnabled = notificationStatusProvider.areSystemNotificationsEnabled()
        )
    }

    companion object {
        fun provideFactory(
            settingsStore: AppSettingsStore,
            notificationStatusProvider: NotificationStatusProvider
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                        return SettingsViewModel(settingsStore, notificationStatusProvider) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
