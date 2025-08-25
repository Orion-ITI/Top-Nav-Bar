package com.ities45.orion_navigation_bars

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SettingsFragment : DialogFragment() {

    companion object {
        private const val ARG_SETTINGS = "settings"
        private const val ARG_BUTTON_TEXT = "button_text"
        private const val ARG_TITLE = "title"

        fun newInstance(
            title: String,
            settings: List<SettingItem>,
            buttonText: String,
            onButtonClick: (() -> Unit)? = null
        ): SettingsFragment {
            val fragment = SettingsFragment()
            val args = Bundle()

            // Convert settings list to ArrayList of Parcelable
            val parcelableSettings = ArrayList(settings.map { SettingParcelable.fromSettingItem(it) })

            args.putParcelableArrayList(ARG_SETTINGS, parcelableSettings)
            args.putString(ARG_BUTTON_TEXT, buttonText)
            args.putString(ARG_TITLE, title)

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
        val parcelableSettings = arguments?.getParcelableArrayList<SettingParcelable>(ARG_SETTINGS)

        // Set up views
        view.findViewById<TextView>(R.id.settingsTitle).text = title
        actionButton = view.findViewById(R.id.settingsActionButton)
        actionButton.text = buttonText

        // Initialize settings list
        settingsList.clear()
        parcelableSettings?.forEach {
            val settingItem = it.toSettingItem()
            settingsList.add(settingItem)
        }

        // Set up RecyclerView
        settingsRecyclerView = view.findViewById(R.id.settingsRecyclerView)
        settingsRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = SettingsAdapter(settingsList) { updatedSetting ->
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