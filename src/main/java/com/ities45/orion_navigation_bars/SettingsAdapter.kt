package com.ities45.orion_navigation_bars

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView

class SettingsAdapter(
    private var settings: List<SettingItem>,
    private val iconSize: Float = -1f, // -1 means use default
    private val textSize: Float = -1f, // -1 means use default
    private val onItemChanged: (SettingItem) -> Unit
) : RecyclerView.Adapter<SettingsAdapter.SettingViewHolder>() {

    class SettingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.settingIcon)
        val name: TextView = view.findViewById(R.id.settingName)
        val toggle: SwitchCompat = view.findViewById(R.id.settingToggle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_setting, parent, false)
        return SettingViewHolder(view)
    }

    override fun onBindViewHolder(holder: SettingViewHolder, position: Int) {
        val setting = settings[position]

        // Apply icon size if specified
        if (iconSize > 0) {
            holder.icon.layoutParams.width = iconSize.toInt()
            holder.icon.layoutParams.height = iconSize.toInt()
            holder.icon.requestLayout()
        }

        // Apply text size if specified
        if (textSize > 0) {
            holder.name.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
        }

        holder.icon.setImageResource(setting.iconResId)
        holder.name.text = setting.name
        holder.toggle.isChecked = setting.isEnabled

        holder.toggle.setOnCheckedChangeListener { _, isChecked ->
            setting.isEnabled = isChecked
            onItemChanged(setting)
        }

        // Make the entire item clickable to toggle the switch
        holder.itemView.setOnClickListener {
            holder.toggle.isChecked = !holder.toggle.isChecked
        }
    }

    override fun getItemCount(): Int = settings.size

    fun updateSettings(newSettings: List<SettingItem>) {
        settings = newSettings
        notifyDataSetChanged()
    }
}