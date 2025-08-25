package com.ities45.orion_navigation_bars

data class SettingItem(
    val id: Int,
    val name: String,
    val iconResId: Int,
    var isEnabled: Boolean = false,
    val onEnable: (() -> Unit)? = null,
    val onDisable: (() -> Unit)? = null
)