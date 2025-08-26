package com.ities45.orion_navigation_bars

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SettingsFragment : DialogFragment() {

    companion object {
        private const val ARG_SETTINGS = "settings"
        private const val ARG_BUTTON_TEXT = "button_text"
        private const val ARG_TITLE = "title"
        private const val ARG_WIDTH = "width"
        private const val ARG_HEIGHT = "height"
        private const val ARG_ICON_SIZE = "icon_size"
        private const val ARG_TEXT_SIZE = "text_size"
        private const val ARG_BUTTON_TEXT_SIZE = "button_text_size"
        private const val ARG_BUTTON_BG_COLOR = "button_bg_color"
        private const val ARG_BUTTON_TEXT_COLOR = "button_text_color"
        private const val ARG_SWITCH_CHECKED_COLOR = "switch_checked_color"
        private const val ARG_SWITCH_UNCHECKED_COLOR = "switch_unchecked_color"

        fun newInstance(
            title: String,
            settings: List<SettingItem>,
            buttonText: String,
            width: Int = ViewGroup.LayoutParams.MATCH_PARENT,
            height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
            iconSize: Float = -1f, // -1 means use default
            textSize: Float = -1f, // -1 means use default
            buttonTextSize: Float = -1f, // -1 means use default
            onButtonClick: (() -> Unit)? = null,
            buttonBackgroundColor: Int? = null,
            buttonTextColor: Int? = null,
            switchCheckedColor: Int? = null,
            switchUncheckedColor: Int? = null
        ): SettingsFragment {
            val fragment = SettingsFragment()
            val args = Bundle()

            // Convert settings list to ArrayList of Parcelable
            val parcelableSettings = ArrayList(settings.map { SettingParcelable.fromSettingItem(it) })

            args.putParcelableArrayList(ARG_SETTINGS, parcelableSettings)
            args.putString(ARG_BUTTON_TEXT, buttonText)
            args.putString(ARG_TITLE, title)
            args.putInt(ARG_WIDTH, width)
            args.putInt(ARG_HEIGHT, height)
            args.putFloat(ARG_ICON_SIZE, iconSize)
            args.putFloat(ARG_TEXT_SIZE, textSize)
            args.putFloat(ARG_BUTTON_TEXT_SIZE, buttonTextSize)

            // Add color arguments if provided
            buttonBackgroundColor?.let { args.putInt(ARG_BUTTON_BG_COLOR, it) }
            buttonTextColor?.let { args.putInt(ARG_BUTTON_TEXT_COLOR, it) }
            switchCheckedColor?.let { args.putInt(ARG_SWITCH_CHECKED_COLOR, it) }
            switchUncheckedColor?.let { args.putInt(ARG_SWITCH_UNCHECKED_COLOR, it) }

            fragment.arguments = args
            fragment.buttonClickListener = onButtonClick

            return fragment
        }
    }

    private lateinit var settingsRecyclerView: RecyclerView
    private lateinit var actionButton: Button
    private var buttonClickListener: (() -> Unit)? = null
    private lateinit var adapter: SettingsAdapter
    private var settingsList = mutableListOf<SettingItem>()
    private var iconSize: Float = -1f
    private var textSize: Float = -1f
    private var buttonTextSize: Float = -1f

    // Color properties
    private var buttonBackgroundColor: Int? = null
    private var buttonTextColor: Int? = null
    private var switchCheckedColor: Int? = null
    private var switchUncheckedColor: Int? = null

    var viewModel: SettingsViewModel? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Parse arguments
        val title = arguments?.getString(ARG_TITLE) ?: "Settings"
        val buttonText = arguments?.getString(ARG_BUTTON_TEXT) ?: "Go to System Settings"
        val width = arguments?.getInt(ARG_WIDTH, ViewGroup.LayoutParams.MATCH_PARENT) ?: ViewGroup.LayoutParams.MATCH_PARENT
        val height = arguments?.getInt(ARG_HEIGHT, ViewGroup.LayoutParams.WRAP_CONTENT) ?: ViewGroup.LayoutParams.WRAP_CONTENT
        iconSize = arguments?.getFloat(ARG_ICON_SIZE, -1f) ?: -1f
        textSize = arguments?.getFloat(ARG_TEXT_SIZE, -1f) ?: -1f
        buttonTextSize = arguments?.getFloat(ARG_BUTTON_TEXT_SIZE, -1f) ?: -1f

        val parcelableSettings = arguments?.getParcelableArrayList<SettingParcelable>(ARG_SETTINGS)

        // Parse color arguments
        buttonBackgroundColor = arguments?.getInt(ARG_BUTTON_BG_COLOR, 0).takeIf { it != 0 }
        buttonTextColor = arguments?.getInt(ARG_BUTTON_TEXT_COLOR, 0).takeIf { it != 0 }
        switchCheckedColor = arguments?.getInt(ARG_SWITCH_CHECKED_COLOR, 0).takeIf { it != 0 }
        switchUncheckedColor = arguments?.getInt(ARG_SWITCH_UNCHECKED_COLOR, 0).takeIf { it != 0 }

        // Set up views
        view.findViewById<TextView>(R.id.settingsTitle).text = title
        actionButton = view.findViewById(R.id.settingsActionButton)
        actionButton.text = buttonText

        // Apply button text size if specified
        if (buttonTextSize > 0) {
            actionButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, buttonTextSize)
        }

        // Apply button colors if provided
        buttonBackgroundColor?.let { color ->
            actionButton.setBackgroundColor(ContextCompat.getColor(requireContext(), color))
        }
        buttonTextColor?.let { color ->
            actionButton.setTextColor(ContextCompat.getColor(requireContext(), color))
        }

        // Initialize settings list from arguments
        settingsList.clear()
        parcelableSettings?.forEach {
            val settingItem = it.toSettingItem()
            settingsList.add(settingItem)
        }

        // If we have a ViewModel, use its settings instead (which include persisted values)
        viewModel?.settings?.value?.let { persistedSettings ->
            settingsList.clear()
            settingsList.addAll(persistedSettings)
        }

        // Set up RecyclerView
        settingsRecyclerView = view.findViewById(R.id.settingsRecyclerView)
        settingsRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = SettingsAdapter(settingsList, iconSize, textSize) { updatedSetting ->
            // Update the setting in our list
            val index = settingsList.indexOfFirst { it.id == updatedSetting.id }
            if (index != -1) {
                settingsList[index] = updatedSetting
            }

            // Update the ViewModel if provided
            viewModel?.updateSetting(updatedSetting.id, updatedSetting.isEnabled)

            // Immediately trigger the appropriate callback
            if (updatedSetting.isEnabled) {
                updatedSetting.onEnable?.invoke()
            } else {
                updatedSetting.onDisable?.invoke()
            }
        }

        settingsRecyclerView.adapter = adapter

        // Observe ViewModel for settings changes
        viewModel?.settings?.observe(viewLifecycleOwner) { newSettings ->
            settingsList.clear()
            settingsList.addAll(newSettings)
            adapter.updateSettings(settingsList)
        }

        // Set up button click listener
        actionButton.setOnClickListener {
            buttonClickListener?.invoke()
            dismiss()
        }

        // Close button
        view.findViewById<ImageButton>(R.id.settingsCloseButton).setOnClickListener {
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        // Set dialog dimensions from arguments
        val width = arguments?.getInt(ARG_WIDTH, ViewGroup.LayoutParams.MATCH_PARENT) ?: ViewGroup.LayoutParams.MATCH_PARENT
        val height = arguments?.getInt(ARG_HEIGHT, ViewGroup.LayoutParams.WRAP_CONTENT) ?: ViewGroup.LayoutParams.WRAP_CONTENT

        dialog?.window?.setLayout(width, height)
    }
}