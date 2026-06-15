package com.example.myapplication;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

/** 今日頁：顯示四格即時服藥狀態與本週遵從率（呼叫 ESP32 /api/status）。 */
public class TodayFragment extends Fragment {

    public TodayFragment() {
        super(R.layout.fragment_today);
    }

    /** 綁定「重新整理」按鈕的點擊事件，並在頁面建立時自動呼叫一次 loadStatus */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.buttonRefresh).setOnClickListener(v -> loadStatus(view));
        loadStatus(view);
    }

    /** 檢查 Wi-Fi、讀取儲存的 IP，向 ESP32 查詢今日四格服藥狀態與遵從率，成功後動態填入卡片清單 */
    private void loadStatus(View view) {
        TextView     textCompliance    = view.findViewById(R.id.textCompliance);
        ProgressBar  progressCompliance = view.findViewById(R.id.progressCompliance);
        TextView     textMessage       = view.findViewById(R.id.textMessage);
        LinearLayout container         = view.findViewById(R.id.slotsContainer);

        String host = Prefs.getHost(requireContext());
        if (host.isEmpty()) {
            textCompliance.setText("");
            progressCompliance.setVisibility(View.INVISIBLE);
            container.removeAllViews();
            textMessage.setText(R.string.today_no_host);
            return;
        }

        if (!isOnWifi()) {
            textCompliance.setText("");
            progressCompliance.setVisibility(View.INVISIBLE);
            container.removeAllViews();
            textMessage.setText(R.string.error_no_wifi);
            return;
        }

        textCompliance.setText(R.string.today_loading);
        progressCompliance.setVisibility(View.INVISIBLE);
        textMessage.setText("");

        PillBoxApi.getInstance().fetchStatus(host, result -> {
            if (!isAdded()) return;
            if (result == null) {
                textCompliance.setText("");
                progressCompliance.setVisibility(View.INVISIBLE);
                container.removeAllViews();
                textMessage.setText(R.string.today_load_failed);
                return;
            }
            textMessage.setText("");
            textCompliance.setText(getString(R.string.today_compliance_format, result.getComplianceRate()));
            progressCompliance.setVisibility(View.VISIBLE);
            progressCompliance.setProgress(result.getComplianceRate());
            container.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(requireContext());
            for (PillBoxApi.SlotStatus slot : result.getSlots()) {
                View row = inflater.inflate(R.layout.item_slot_status, container, false);
                ((TextView) row.findViewById(R.id.textSlotLabel)).setText(slot.getLabel());
                ((TextView) row.findViewById(R.id.textSlotTime)).setText(slot.getTime());
                TextView stateView = row.findViewById(R.id.textSlotState);
                stateView.setText(getStateLabel(slot.getState()));
                stateView.setTextColor(getStateColor(slot.getState()));
                container.addView(row);
            }
        });
    }

    /** 透過 ConnectivityManager 判斷目前是否已連接 Wi-Fi */
    private boolean isOnWifi() {
        ConnectivityManager cm = (ConnectivityManager)
                requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    /** 將 API 回傳的英文狀態碼（ALERTING/TAKEN/MISSED/WAITING）轉為中文顯示文字 */
    private String getStateLabel(String state) {
        switch (state) {
            case "ALERTING": return getString(R.string.state_alerting);
            case "TAKEN":    return getString(R.string.state_taken);
            case "MISSED":   return getString(R.string.state_missed);
            default:         return getString(R.string.state_waiting);
        }
    }

    /** 依狀態碼回傳對應的顯示顏色：黃色=提醒中、綠色=已服、紅色=未服、灰色=等待中 */
    private int getStateColor(String state) {
        switch (state) {
            case "ALERTING": return ContextCompat.getColor(requireContext(), R.color.color_alerting);
            case "TAKEN":    return ContextCompat.getColor(requireContext(), R.color.color_taken);
            case "MISSED":   return ContextCompat.getColor(requireContext(), R.color.color_missed);
            default:         return ContextCompat.getColor(requireContext(), R.color.color_waiting);
        }
    }
}
