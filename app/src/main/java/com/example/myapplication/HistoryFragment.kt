package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

/** 歷史頁：顯示 7 天服藥格子圖與本週遵從率（呼叫 ESP32 /api/history）。 */
class HistoryFragment : Fragment(R.layout.fragment_history) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val textCompliance = view.findViewById<TextView>(R.id.textHistoryCompliance)
        val textMessage = view.findViewById<TextView>(R.id.textHistoryMessage)
        val container = view.findViewById<LinearLayout>(R.id.historyDaysContainer)

        val host = Prefs.getHost(requireContext())
        if (host.isBlank()) {
            textCompliance.text = ""
            textMessage.setText(R.string.history_load_failed)
            return
        }

        PillBoxApi.fetchHistory(host) { result ->
            if (!isAdded) return@fetchHistory
            if (result == null) {
                textCompliance.text = ""
                textMessage.setText(R.string.history_load_failed)
                return@fetchHistory
            }
            textMessage.text = ""
            textCompliance.text = getString(R.string.history_compliance_format, result.complianceRate)
            container.removeAllViews()
            val inflater = LayoutInflater.from(requireContext())
            for (day in result.days) {
                val row = inflater.inflate(R.layout.item_history_day, container, false)
                row.findViewById<TextView>(R.id.textHistoryDate).text = day.date
                val cells = row.findViewById<LinearLayout>(R.id.historyCellsContainer)
                cells.removeAllViews()
                for (slotIndex in 0 until 4) {
                    val taken = (day.bits shr slotIndex) and 1 == 1
                    val cell = TextView(requireContext()).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        gravity = android.view.Gravity.CENTER
                        text = if (taken) getString(R.string.history_taken) else getString(R.string.history_missed)
                        setTextColor(Color.WHITE)
                        setBackgroundColor(if (taken) Color.parseColor("#0F9D58") else Color.parseColor("#DB4437"))
                        setPadding(8, 8, 8, 8)
                    }
                    val params = cell.layoutParams as LinearLayout.LayoutParams
                    params.marginEnd = 8
                    cell.layoutParams = params
                    cells.addView(cell)
                }
                container.addView(row)
            }
        }
    }
}
