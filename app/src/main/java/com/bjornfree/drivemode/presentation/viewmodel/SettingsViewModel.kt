package com.bjornfree.drivemode.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.bjornfree.drivemode.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel для SettingsTab (Настройки).
 *
 * Управляет настройками приложения:
 * - Demo mode (тестирование без реального автомобиля)
 * - Border overlay (включен/выключен)
 * - Panel overlay (включен/выключен)
 * - Статистика запусков
 *
 * @param context Application context для проверки permissions
 * @param prefsManager для сохранения настроек
 */
class SettingsViewModel(
    private val context: Context,
    private val prefsManager: PreferencesManager
) : ViewModel() {

    /**
     * Demo mode (для тестирования без автомобиля).
     */
    private val _demoMode = MutableStateFlow(prefsManager.demoMode)
    val demoMode: StateFlow<Boolean> = _demoMode.asStateFlow()

    /**
     * Border overlay включен.
     */
    private val _borderEnabled = MutableStateFlow(prefsManager.borderEnabled)
    val borderEnabled: StateFlow<Boolean> = _borderEnabled.asStateFlow()

    /**
     * Panel overlay включен.
     */
    private val _panelEnabled = MutableStateFlow(prefsManager.panelEnabled)
    val panelEnabled: StateFlow<Boolean> = _panelEnabled.asStateFlow()

    /**
     * Количество запусков приложения.
     */
    private val _launchCount = MutableStateFlow(prefsManager.launchCount)
    val launchCount: StateFlow<Int> = _launchCount.asStateFlow()

    /**
     * Переключает demo mode.
     */
    fun toggleDemoMode() {
        val newValue = !_demoMode.value
        prefsManager.demoMode = newValue
        _demoMode.value = newValue
    }

    /**
     * Устанавливает demo mode.
     *
     * @param enabled true для включения demo mode
     */
    fun setDemoMode(enabled: Boolean) {
        prefsManager.demoMode = enabled
        _demoMode.value = enabled
    }

    /**
     * Переключает border overlay.
     */
    fun toggleBorderOverlay() {
        val newValue = !_borderEnabled.value
        prefsManager.borderEnabled = newValue
        _borderEnabled.value = newValue
    }

    /**
     * Устанавливает border overlay.
     *
     * @param enabled true для включения border
     */
    fun setBorderEnabled(enabled: Boolean) {
        prefsManager.borderEnabled = enabled
        _borderEnabled.value = enabled
    }

    /**
     * Переключает panel overlay.
     */
    fun togglePanelOverlay() {
        val newValue = !_panelEnabled.value
        prefsManager.panelEnabled = newValue
        _panelEnabled.value = newValue
    }

    /**
     * Устанавливает panel overlay.
     *
     * @param enabled true для включения panel
     */
    fun setPanelEnabled(enabled: Boolean) {
        prefsManager.panelEnabled = enabled
        _panelEnabled.value = enabled
    }

    /**
     * Сбрасывает все настройки к значениям по умолчанию.
     */
    fun resetToDefaults() {
        setDemoMode(false)
        setBorderEnabled(true)
        setPanelEnabled(true)
    }

    /**
     * Очищает все данные (включая статистику).
     */
    fun clearAllData() {
        prefsManager.clearAll()
        _demoMode.value = false
        _borderEnabled.value = true
        _panelEnabled.value = true
        _launchCount.value = 0
    }

    /**
     * Проверяет наличие системных permissions.
     * Используется для отображения статуса в UI.
     */
    fun hasSystemAlertWindowPermission(): Boolean {
        return android.provider.Settings.canDrawOverlays(context)
    }

    /**
     * Проверяет игнорируются ли батарейные оптимизации.
     */
    fun isBatteryOptimizationIgnored(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        return pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }
}
