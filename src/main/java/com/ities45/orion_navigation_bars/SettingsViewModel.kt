package com.ities45.orion_navigation_bars

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData

class SettingsViewModel(private val context: Context) : ViewModel() {
    private val sharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val _settings = MutableLiveData<List<SettingItem>>()
    val settings: LiveData<List<SettingItem>> = _settings

    fun updateSetting(settingId: Int, isEnabled: Boolean) {
        val currentSettings = _settings.value ?: return
        val updatedSettings = currentSettings.map { setting ->
            if (setting.id == settingId) {
                setting.copy(isEnabled = isEnabled).apply {
                    // Keep the original callbacks
                    this.onEnable = setting.onEnable
                    this.onDisable = setting.onDisable
                }
            } else {
                setting
            }
        }
        _settings.value = updatedSettings

        // Persist the setting
        sharedPreferences.edit().putBoolean("setting_$settingId", isEnabled).apply()
    }

    fun loadSettings(defaultSettings: List<SettingItem>): List<SettingItem> {
        val loadedSettings = defaultSettings.map { setting ->
            val isEnabled = sharedPreferences.getBoolean("setting_${setting.id}", setting.isEnabled)
            setting.copy(isEnabled = isEnabled).apply {
                // Keep the original callbacks
                this.onEnable = setting.onEnable
                this.onDisable = setting.onDisable
            }
        }

        // Update LiveData with loaded settings
        _settings.value = loadedSettings
        return loadedSettings
    }

    fun setSettings(settings: List<SettingItem>) {
        _settings.value = settings
    }

    fun getSetting(settingId: Int): SettingItem? {
        return _settings.value?.find { it.id == settingId }
    }
}