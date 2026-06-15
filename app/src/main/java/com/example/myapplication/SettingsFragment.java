package com.example.myapplication;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import java.util.ArrayList;
import java.util.List;

/** 設定頁：填寫藥盒 IP，並以時間選擇器修改四格服藥時間（呼叫 ESP32 POST /api/settings）。 */
public class SettingsFragment extends Fragment {

    private static final int[] SLOT_LABEL_RES = {
        R.string.slot_morning,
        R.string.slot_noon,
        R.string.slot_evening,
        R.string.slot_night
    };

    private List<EditText> slotTimeEdits;

    public SettingsFragment() {
        super(R.layout.fragment_settings);
    }

    /** 建立四個時段的時間輸入欄（點擊開啟時間選擇器），並預填上次儲存的時間 */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText editHost = view.findViewById(R.id.editHost);
        editHost.setText(Prefs.getHost(requireContext()));

        LinearLayout container = view.findViewById(R.id.slotInputsContainer);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        slotTimeEdits = new ArrayList<>();

        for (int i = 0; i < SLOT_LABEL_RES.length; i++) {
            final int labelRes = SLOT_LABEL_RES[i];
            final int index    = i;
            View row = inflater.inflate(R.layout.item_slot_time_input, container, false);
            ((TextView) row.findViewById(R.id.textSlotInputLabel))
                    .setText(getString(R.string.settings_slot_label_format, getString(labelRes)));
            EditText editTime = row.findViewById(R.id.editSlotTime);
            editTime.setText(Prefs.getSlotTime(requireContext(), index));
            editTime.setFocusable(false);
            editTime.setFocusableInTouchMode(false);
            editTime.setOnClickListener(v -> openTimePicker(editTime, labelRes));
            slotTimeEdits.add(editTime);
            container.addView(row);
        }

        TextView textMessage = view.findViewById(R.id.textSettingsMessage);

        view.postDelayed(() -> ((ScrollView) view).scrollTo(0, 0), 500);

        view.findViewById(R.id.buttonSave).setOnClickListener(v -> {
            String host = editHost.getText().toString().trim();

            if (!host.isEmpty() && !isValidIp(host)) {
                textMessage.setText(R.string.error_invalid_ip);
                return;
            }

            Prefs.setHost(requireContext(), host);

            if (host.isEmpty()) {
                textMessage.setText(R.string.settings_host_saved);
                return;
            }

            List<String> times = new ArrayList<>();
            for (EditText edit : slotTimeEdits) {
                times.add(edit.getText().toString().trim());
            }

            for (String t : times) {
                if (t.isEmpty()) {
                    textMessage.setText(R.string.error_times_required);
                    return;
                }
            }

            v.setEnabled(false);
            textMessage.setText("");

            PillBoxApi.getInstance().saveSettings(host, times, ok -> {
                if (!isAdded()) return;
                v.setEnabled(true);
                if (ok) {
                    Prefs.setSlotTimes(requireContext(), times);
                    textMessage.setText(R.string.settings_save_success);
                } else {
                    textMessage.setText(R.string.settings_save_failed);
                }
            });
        });
    }

    /** 開啟 24 小時制的時間選擇器，確認後將結果填入 editTime */
    private void openTimePicker(EditText editTime, int labelRes) {
        int hour = 8, minute = 0;
        String current = editTime.getText().toString().trim();
        if (current.matches("\\d{2}:\\d{2}")) {
            try {
                hour   = Integer.parseInt(current.substring(0, 2));
                minute = Integer.parseInt(current.substring(3, 5));
            } catch (NumberFormatException ignored) {}
        }
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(hour)
                .setMinute(minute)
                .setTitleText(getString(R.string.settings_slot_label_format, getString(labelRes)))
                .build();
        picker.addOnPositiveButtonClickListener(v ->
                editTime.setText(String.format(java.util.Locale.ROOT, "%02d:%02d",
                        picker.getHour(), picker.getMinute())));
        picker.show(getChildFragmentManager(), null);
    }

    /** 驗證字串是否符合 IPv4 格式（每段 0–255） */
    private static boolean isValidIp(String ip) {
        return ip.matches(
                "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");
    }
}
