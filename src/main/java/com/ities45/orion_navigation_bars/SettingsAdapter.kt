package com.ities45.orion_navigation_bars

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class SettingsAdapter(
    private var settings: List<SettingItem>,
    private val switchCheckedColor: Int? = null,
    private val switchUncheckedColor: Int? = null,
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

        holder.icon.setImageResource(setting.iconResId)
        holder.name.text = setting.name
        holder.toggle.isChecked = setting.isEnabled

        // Apply custom switch colors if provided
        switchCheckedColor?.let { color ->
            holder.toggle.thumbTintList = ContextCompat.getColorStateList(holder.itemView.context, color)
            holder.toggle.trackTintList = ContextCompat.getColorStateList(holder.itemView.context, color)
        }

        switchUncheckedColor?.let { color ->
            // For unchecked state, we might need to create a custom ColorStateList
            // For simplicity, we'll just set the thumb and track tint lists
            // You might want to enhance this for better control
            if (switchCheckedColor == null) {
                holder.toggle.thumbTintList = ContextCompat.getColorStateList(holder.itemView.context, color)
                holder.toggle.trackTintList = ContextCompat.getColorStateList(holder.itemView.context, color)
            }
        }

        holder.toggle.setOnCheckedChangeListener { _, isChecked ->
            setting.isEnabled = isChecked
            if (isChecked) {
                setting.onEnable?.invoke()
            } else {
                setting.onDisable?.invoke()
            }
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