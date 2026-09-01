package com.example.myapplication;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

/** 歷史頁：顯示 7 天服藥格子圖與本週遵從率（呼叫 ESP32 /api/history）。 */
public class HistoryFragment extends Fragment {

    public HistoryFragment() {
        super(R.layout.fragment_history);
    }

    /** 綁定「重新整理」按鈕並在頁面建立時自動載入一次歷史資料 */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.buttonHistoryRefresh).setOnClickListener(v -> loadHistory(view));
        loadHistory(view);
    }

    private boolean isOnWifi() {
        return NetworkUtils.isOnWifi(requireContext());
    }

    /** 向 ESP32 取得最近 7 天的服藥紀錄，依 bits 欄位逐格繪製綠色（已服）/ 紅色（未服）色塊，並顯示本週遵從率 */
    private void loadHistory(View view) {
        TextView     textCompliance = view.findViewById(R.id.textHistoryCompliance);
        ProgressBar  progressBar    = view.findViewById(R.id.progressHistoryCompliance);
        TextView     textMessage    = view.findViewById(R.id.textHistoryMessage);
        LinearLayout container      = view.findViewById(R.id.historyDaysContainer);

        String host = Prefs.getHost(requireContext());
        if (host.isEmpty()) {
            textCompliance.setText("");
            progressBar.setVisibility(View.INVISIBLE);
            container.removeAllViews();
            textMessage.setText(R.string.history_no_host);
            return;
        }

        if (!isOnWifi()) {
            textCompliance.setText("");
            progressBar.setVisibility(View.INVISIBLE);
            container.removeAllViews();
            textMessage.setText(R.string.error_no_wifi);
            return;
        }

        textCompliance.setText(R.string.history_loading);
        progressBar.setVisibility(View.INVISIBLE);
        textMessage.setText("");
        container.removeAllViews();

        PillBoxApi.getInstance().fetchHistory(host, result -> {
            if (!isAdded()) return;
            if (result == null) {
                textCompliance.setText("");
                progressBar.setVisibility(View.INVISIBLE);
                textMessage.setText(R.string.history_load_failed);
                return;
            }
            textMessage.setText("");
            textCompliance.setText(getString(R.string.history_compliance_format, result.getComplianceRate()));
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(result.getComplianceRate());
            container.removeAllViews();

            LayoutInflater inflater = LayoutInflater.from(requireContext());
            float dp  = getResources().getDisplayMetrics().density;
            int   pad = (int) (8 * dp + 0.5f);

            for (PillBoxApi.HistoryDay day : result.getDays()) {
                View row = inflater.inflate(R.layout.item_history_day, container, false);
                ((TextView) row.findViewById(R.id.textHistoryDate)).setText(day.getDate());
                LinearLayout cells = row.findViewById(R.id.historyCellsContainer);

                for (int slotIndex = 0; slotIndex < 4; slotIndex++) {
                    boolean taken = ((day.getBits() >> slotIndex) & 1) == 1;
                    TextView cell = new TextView(requireContext());
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    params.setMarginEnd((int) (4 * dp + 0.5f));
                    cell.setLayoutParams(params);
                    cell.setGravity(Gravity.CENTER);
                    cell.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
                    cell.setText(taken
                            ? getString(R.string.history_taken)
                            : getString(R.string.history_missed));
                    cell.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
                    cell.setBackgroundColor(ContextCompat.getColor(requireContext(),
                            taken ? R.color.color_taken : R.color.color_missed));
                    cell.setPadding(pad, pad, pad, pad);
                    cells.addView(cell);
                }
                container.addView(row);
            }
        });
    }
}
