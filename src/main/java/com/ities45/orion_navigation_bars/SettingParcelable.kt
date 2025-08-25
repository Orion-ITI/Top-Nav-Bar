package com.ities45.orion_navigation_bars

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SettingParcelable(
    val id: Int,
    val name: String,
    val iconResId: Int,
    val isEnabled: Boolean
) : Parcelable {
    companion object {
        fun fromSettingItem(item: SettingItem): SettingParcelable {
            return SettingParcelable(item.id, item.name, item.iconResId, item.isEnabled)
        }
    }

    fun toSettingItem(): SettingItem {
        return SettingItem(id, name, iconResId, isEnabled)
    }
}