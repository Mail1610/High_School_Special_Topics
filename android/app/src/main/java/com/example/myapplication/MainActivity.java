package com.example.myapplication;

import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private TodayFragment    todayFragment;
    private SettingsFragment settingsFragment;
    private HistoryFragment  historyFragment;

    /** 建立底部導覽列，以 show/hide 切換 Fragment 避免每次重建，設定 ActionBar 標題 */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            todayFragment    = new TodayFragment();
            settingsFragment = new SettingsFragment();
            historyFragment  = new HistoryFragment();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragmentContainer, historyFragment,  "history")
                    .add(R.id.fragmentContainer, settingsFragment, "settings")
                    .add(R.id.fragmentContainer, todayFragment,    "today")
                    .hide(historyFragment)
                    .hide(settingsFragment)
                    .commitNow();
        } else {
            todayFragment    = (TodayFragment)    getSupportFragmentManager().findFragmentByTag("today");
            settingsFragment = (SettingsFragment) getSupportFragmentManager().findFragmentByTag("settings");
            historyFragment  = (HistoryFragment)  getSupportFragmentManager().findFragmentByTag("history");
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            String title;
            if (id == R.id.nav_today) {
                title = getString(R.string.title_today);
                getSupportFragmentManager().beginTransaction()
                        .hide(settingsFragment).hide(historyFragment).show(todayFragment).commit();
            } else if (id == R.id.nav_settings) {
                title = getString(R.string.title_settings);
                getSupportFragmentManager().beginTransaction()
                        .hide(todayFragment).hide(historyFragment).show(settingsFragment).commit();
            } else if (id == R.id.nav_history) {
                title = getString(R.string.title_history);
                getSupportFragmentManager().beginTransaction()
                        .hide(todayFragment).hide(settingsFragment).show(historyFragment).commit();
            } else {
                return false;
            }
            ActionBar ab = getSupportActionBar();
            if (ab != null) ab.setTitle(title);
            return true;
        });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_today);
        } else {
            int id = bottomNav.getSelectedItemId();
            String title;
            if (id == R.id.nav_settings)      title = getString(R.string.title_settings);
            else if (id == R.id.nav_history)  title = getString(R.string.title_history);
            else                               title = getString(R.string.title_today);
            ActionBar ab = getSupportActionBar();
            if (ab != null) ab.setTitle(title);
        }
    }
}
