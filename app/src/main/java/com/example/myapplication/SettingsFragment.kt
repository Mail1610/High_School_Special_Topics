package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

/** 設定頁：填寫藥盒 IP，並修改四格服藥時間（呼叫 ESP32 POST /api/settings）。 */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val slotLabelKeys = listOf(
        R.string.slot_morning,
        R.string.slot_noon,
        R.string.slot_evening,
        R.string.slot_night
    )
    private lateinit var slotTimeEdits: List<EditText>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val editHost = view.findViewById<EditText>(R.id.editHost)
        editHost.setText(Prefs.getHost(requireContext()))

        val container = view.findViewById<LinearLayout>(R.id.slotInputsContainer)
        val inflater = LayoutInflater.from(requireContext())
        slotTimeEdits = slotLabelKeys.map { labelRes ->
            val row = inflater.inflate(R.layout.item_slot_time_input, container, false)
            row.findViewById<TextView>(R.id.textSlotInputLabel).text =
                getString(R.string.settings_slot_label_format, getString(labelRes))
            container.addView(row)
            row.findViewById(R.id.editSlotTime)
        }

        val textMessage = view.findViewById<TextView>(R.id.textSettingsMessage)

        view.findViewById<View>(R.id.buttonSave).setOnClickListener {
            val host = editHost.text.toString().trim()
            Prefs.setHost(requireContext(), host)

            if (host.isBlank()) {
                textMessage.setText(R.string.settings_host_saved)
                return@setOnClickListener
            }

            val times = slotTimeEdits.map { it.text.toString().trim() }
            PillBoxApi.saveSettings(host, times) { ok ->
                if (!isAdded) return@saveSettings
                textMessage.setText(if (ok) R.string.settings_save_success else R.string.settings_save_failed)
            }
        }
    }
}
