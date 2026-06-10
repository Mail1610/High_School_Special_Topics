package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

/** 今日頁：顯示四格即時服藥狀態與本週遵從率（呼叫 ESP32 /api/status）。 */
class TodayFragment : Fragment(R.layout.fragment_today) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.buttonRefresh).setOnClickListener { loadStatus(view) }
        loadStatus(view)
    }

    private fun loadStatus(view: View) {
        val textCompliance = view.findViewById<TextView>(R.id.textCompliance)
        val textMessage = view.findViewById<TextView>(R.id.textMessage)
        val container = view.findViewById<LinearLayout>(R.id.slotsContainer)

        val host = Prefs.getHost(requireContext())
        if (host.isBlank()) {
            textCompliance.text = ""
            container.removeAllViews()
            textMessage.setText(R.string.today_load_failed)
            return
        }

        textCompliance.setText(R.string.today_loading)
        textMessage.text = ""

        PillBoxApi.fetchStatus(host) { result ->
            if (!isAdded) return@fetchStatus
            if (result == null) {
                textCompliance.text = ""
                container.removeAllViews()
                textMessage.setText(R.string.today_load_failed)
                return@fetchStatus
            }
            textMessage.text = ""
            textCompliance.text = getString(R.string.today_compliance_format, result.complianceRate)
            container.removeAllViews()
            val inflater = LayoutInflater.from(requireContext())
            for (slot in result.slots) {
                val row = inflater.inflate(R.layout.item_slot_status, container, false)
                row.findViewById<TextView>(R.id.textSlotLabel).text = slot.label
                row.findViewById<TextView>(R.id.textSlotTime).text = slot.time
                val stateView = row.findViewById<TextView>(R.id.textSlotState)
                stateView.text = stateLabel(slot.state)
                stateView.setTextColor(stateColor(slot.state))
                container.addView(row)
            }
        }
    }

    private fun stateLabel(state: String): String = when (state) {
        "ALERTING" -> getString(R.string.state_alerting)
        "TAKEN" -> getString(R.string.state_taken)
        "MISSED" -> getString(R.string.state_missed)
        else -> getString(R.string.state_waiting)
    }

    private fun stateColor(state: String): Int = when (state) {
        "ALERTING" -> Color.parseColor("#F4B400")
        "TAKEN" -> Color.parseColor("#0F9D58")
        "MISSED" -> Color.parseColor("#DB4437")
        else -> Color.GRAY
    }
}
