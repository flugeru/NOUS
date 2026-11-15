package com.limelight;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.limelight.preferences.StreamSettings;
import com.limelight.utils.HelpLauncher;
import com.limelight.utils.UiHelper;

public final class NousSettingsActivity extends AppCompatActivity {
    static final String OPEN_GAMES = "nous.open_games";
    static final String OPEN_FROM_GAMES = "nous.open_from_games";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiHelper.setLocale(this);
        setContentView(R.layout.activity_nous_settings);
        UiHelper.notifyNewRootView(this);

        findViewById(R.id.nous_settings_video).setOnClickListener(v ->
                startActivity(new Intent(this, StreamSettings.class)));
        findViewById(R.id.nous_settings_profiles).setOnClickListener(v ->
                startActivity(new Intent(this, ProfilesActivity.class)));
        findViewById(R.id.nous_settings_controls).setOnClickListener(v ->
                startActivity(new Intent(this, StreamSettings.class)
                        .putExtra(StreamSettings.INITIAL_CATEGORY, "category_onscreen_controls")));
        findViewById(R.id.nous_settings_diagnostics).setOnClickListener(v ->
                startActivity(new Intent(this, DebugInfoActivity.class)));
        findViewById(R.id.nous_settings_help).setOnClickListener(v -> HelpLauncher.launchSetupGuide(this));

        BottomNavigationView navigation = findViewById(R.id.nous_navigation);
        navigation.setSelectedItemId(R.id.nous_nav_settings);
        navigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nous_nav_games && getIntent().getBooleanExtra(OPEN_FROM_GAMES, false)) {
                finish();
            } else if (item.getItemId() != R.id.nous_nav_settings) {
                Intent intent = new Intent(this, PcView.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                if (item.getItemId() == R.id.nous_nav_games) {
                    intent.putExtra(OPEN_GAMES, true);
                }
                startActivity(intent);
                finish();
            }
            return false;
        });
    }
}
