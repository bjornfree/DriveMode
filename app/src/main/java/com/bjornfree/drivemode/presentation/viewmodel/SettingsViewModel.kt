package com.bjornfree.drivemode.presentation.viewmodel

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import com.bjornfree.drivemode.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(
    context: Context,
    private val prefsManager: PreferencesManager
) : ViewModel() {

    private val appContext: Context = context.applicationContext

    private val _demoMode = MutableStateFlow(prefsManager.demoMode)
    val demoMode: StateFlow<Boolean> = _demoMode.asStateFlow()

    private val _borderEnabled = MutableStateFlow(prefsManager.borderEnabled)
    val borderEnabled: StateFlow<Boolean> = _borderEnabled.asStateFlow()

    private val _panelEnabled = MutableStateFlow(prefsManager.panelEnabled)
    val panelEnabled: StateFlow<Boolean> = _panelEnabled.asStateFlow()

    private val _metricsBarEnabled = MutableStateFlow(prefsManager.metricsBarEnabled)
    val metricsBarEnabled: StateFlow<Boolean> = _metricsBarEnabled.asStateFlow()

    private val _metricsBarPosition = MutableStateFlow(prefsManager.metricsBarPosition)
    val metricsBarPosition: StateFlow<String> = _metricsBarPosition.asStateFlow()

    private val _launchCount = MutableStateFlow(prefsManager.launchCount)
    val launchCount: StateFlow<Int> = _launchCount.asStateFlow()

    private val _themeMode = MutableStateFlow(prefsManager.themeMode)
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun toggleDemoMode() {
        val newValue = !_demoMode.value
        prefsManager.demoMode = newValue
        _demoMode.value = newValue
    }

    fun setDemoMode(enabled: Boolean) {
        prefsManager.demoMode = enabled
        _demoMode.value = enabled
    }

    fun toggleBorderOverlay() {
        val newValue = !_borderEnabled.value
        prefsManager.borderEnabled = newValue
        _borderEnabled.value = newValue
    }

    fun setBorderEnabled(enabled: Boolean) {
        prefsManager.borderEnabled = enabled
        _borderEnabled.value = enabled
    }

    fun togglePanelOverlay() {
        val newValue = !_panelEnabled.value
        prefsManager.panelEnabled = newValue
        _panelEnabled.value = newValue
    }

    fun setPanelEnabled(enabled: Boolean) {
        prefsManager.panelEnabled = enabled
        _panelEnabled.value = enabled
    }

    fun toggleMetricsBar() {
        val newValue = !_metricsBarEnabled.value
        prefsManager.metricsBarEnabled = newValue
        _metricsBarEnabled.value = newValue
    }

    fun setMetricsBarEnabled(enabled: Boolean) {
        prefsManager.metricsBarEnabled = enabled
        _metricsBarEnabled.value = enabled
    }

    fun setMetricsBarPosition(position: String) {
        prefsManager.metricsBarPosition = position
        _metricsBarPosition.value = position
    }

    fun setThemeMode(mode: String) {
        prefsManager.themeMode = mode
        _themeMode.value = mode
    }

    fun resetToDefaults() {
        setDemoMode(false)
        setBorderEnabled(true)
        setPanelEnabled(true)
        setMetricsBarEnabled(true)
        setThemeMode("auto")
    }

    fun clearAllData() {
        prefsManager.clearAll()
        _demoMode.value = false
        _borderEnabled.value = true
        _panelEnabled.value = true
        _metricsBarEnabled.value = true
        _launchCount.value = 0
    }

    fun hasSystemAlertWindowPermission(): Boolean {
        return Settings.canDrawOverlays(appContext)
    }

    fun isBatteryOptimizationIgnored(): Boolean {
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isIgnoringBatteryOptimizations(appContext.packageName) ?: false
    }
}