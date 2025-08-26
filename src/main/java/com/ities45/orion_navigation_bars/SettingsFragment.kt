package com.ities45.orion_navigation_bars

import android.os.Bundle
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
        private const val ARG_BUTTON_BG_COLOR = "button_bg_color"
        private const val ARG_BUTTON_TEXT_COLOR = "button_text_color"
        private const val ARG_SWITCH_CHECKED_COLOR = "switch_checked_color"
        private const val ARG_SWITCH_UNCHECKED_COLOR = "switch_unchecked_color"

        fun newInstance(
            title: String,
            settings: List<SettingItem>,
            buttonText: String,
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

    // Color properties
    private var buttonBackgroundColor: Int? = null
    private var buttonTextColor: Int? = null
    private var switchCheckedColor: Int? = null
    private var switchUncheckedColor: Int? = null

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
        val buttonText = arguments?.getString(ARG_BUTTON_TEXT) ?: "Apply"
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

        // Apply button colors if provided
        buttonBackgroundColor?.let { color ->
            actionButton.setBackgroundColor(ContextCompat.getColor(requireContext(), color))
        }
        buttonTextColor?.let { color ->
            actionButton.setTextColor(ContextCompat.getColor(requireContext(), color))
        }

        // Initialize settings list
        settingsList.clear()
        parcelableSettings?.forEach {
            val settingItem = it.toSettingItem()
            settingsList.add(settingItem)
        }

        // Set up RecyclerView with custom colors
        settingsRecyclerView = view.findViewById(R.id.settingsRecyclerView)
        settingsRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = SettingsAdapter(
            settingsList,
            switchCheckedColor,
            switchUncheckedColor
        ) { updatedSetting ->
            // Update the setting in our list
            val index = settingsList.indexOfFirst { it.id == updatedSetting.id }
            if (index != -1) {
                settingsList[index] = updatedSetting
            }
        }

        settingsRecyclerView.adapter = adapter

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
        // Set dialog dimensions
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}