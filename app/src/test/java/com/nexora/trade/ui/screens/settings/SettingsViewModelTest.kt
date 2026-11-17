package com.nexora.trade.ui.screens.settings

import com.nexora.trade.alerts.AppSettingsStore
import com.nexora.trade.alerts.NotificationStatusProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {

    @Test
    fun `settings toggles update ui state and store and refresh notification status`() {
        val fakeStore = FakeSettingsStore()
        val fakeNotificationProvider = FakeNotificationStatusProvider()

        val viewModel = SettingsViewModel(fakeStore, fakeNotificationProvider)

        assertTrue(viewModel.uiState.value.isDarkTheme)
        assertTrue(viewModel.uiState.value.isSoundEnabled)
        assertTrue(viewModel.uiState.value.isVibrationEnabled)
        assertFalse(viewModel.uiState.value.hasNotificationPermission)

        viewModel.toggleDarkTheme(false)
        viewModel.toggleSound(false)
        viewModel.toggleVibration(false)

        assertFalse(viewModel.uiState.value.isDarkTheme)
        assertFalse(viewModel.uiState.value.isSoundEnabled)
        assertFalse(viewModel.uiState.value.isVibrationEnabled)
        assertFalse(fakeStore.isDarkTheme())
        assertFalse(fakeStore.isSoundEnabled())
        assertFalse(fakeStore.isVibrationEnabled())

        fakeNotificationProvider.granted = true
        fakeNotificationProvider.systemEnabled = true
        viewModel.refreshNotificationStatus()

        assertTrue(viewModel.uiState.value.hasNotificationPermission)
        assertTrue(viewModel.uiState.value.areSystemNotificationsEnabled)
    }

    private class FakeSettingsStore : AppSettingsStore {
        private val _darkThemeFlow = MutableStateFlow(true)
        override val darkThemeFlow: StateFlow<Boolean> = _darkThemeFlow.asStateFlow()

        private var sound = true
        private var vibration = true
        private var prompted = false

        override fun isDarkTheme(): Boolean = _darkThemeFlow.value
        override fun setDarkTheme(enabled: Boolean) {
            _darkThemeFlow.value = enabled
        }

        override fun isSoundEnabled(): Boolean = sound
        override fun setSoundEnabled(enabled: Boolean) {
            sound = enabled
        }

        override fun isVibrationEnabled(): Boolean = vibration
        override fun setVibrationEnabled(enabled: Boolean) {
            vibration = enabled
        }

        override fun hasPromptedNotificationPermission(): Boolean = prompted
        override fun markNotificationPermissionPrompted() {
            prompted = true
        }
    }

    private class FakeNotificationStatusProvider : NotificationStatusProvider {
        var granted: Boolean = false
        var systemEnabled: Boolean = false

        override fun hasNotificationPermission(): Boolean = granted
        override fun areSystemNotificationsEnabled(): Boolean = systemEnabled
    }
}
