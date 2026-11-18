package com.limelight;

import android.content.Intent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.navigation.NavigationBarView;
import com.limelight.preferences.StreamSettings;
import com.limelight.utils.HelpLauncher;

/** Keeps the existing host/library view and its adapter attached while Settings is visible. */
final class NousSettingsTab {
    private final AppCompatActivity activity;
    private final NavigationBarView navigation;
    private final View mainTab;
    private final int mainItem;
    private final OnBackPressedCallback back;
    private View settings;

    NousSettingsTab(AppCompatActivity activity, NavigationBarView navigation, int mainItem) {
        this.activity = activity;
        this.navigation = navigation;
        this.mainItem = mainItem;
        mainTab = activity.findViewById(R.id.nous_main_tab);
        back = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                navigation.setSelectedItemId(mainItem);
            }
        };
        activity.getOnBackPressedDispatcher().addCallback(activity, back);
    }

    void show(boolean visible) {
        if (visible && settings == null) {
            FrameLayout container = activity.findViewById(R.id.nous_tab_content);
            settings = activity.getLayoutInflater().inflate(R.layout.activity_nous_settings, container, false);
            container.addView(settings);
            bindSection(R.id.nous_settings_video, StreamSettings.Section.VIDEO_AUDIO);
            bindSection(R.id.nous_settings_controls, StreamSettings.Section.CONTROLS);
            bindSection(R.id.nous_settings_advanced, StreamSettings.Section.ADVANCED);
            bindSection(R.id.nous_settings_development, StreamSettings.Section.DEVELOPMENT);
            settings.findViewById(R.id.nous_settings_profiles).setOnClickListener(v ->
                    activity.startActivity(new Intent(activity, ProfilesActivity.class)));
            settings.findViewById(R.id.nous_settings_diagnostics).setOnClickListener(v ->
                    activity.startActivity(new Intent(activity, DebugInfoActivity.class)));
            settings.findViewById(R.id.nous_settings_help).setOnClickListener(v ->
                    HelpLauncher.launchSetupGuide(activity));
            View account = settings.findViewById(R.id.nous_settings_account);
            account.setVisibility(NousAccountDialog.isConfigured() ? View.VISIBLE : View.GONE);
            account.setOnClickListener(v -> NousAccountDialog.show(activity));
        }
        if (settings != null) {
            settings.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (visible) {
            WindowCompat.getInsetsController(activity.getWindow(), navigation).hide(WindowInsetsCompat.Type.ime());
        }
        View libraryHeader = activity.findViewById(R.id.nous_library_header);
        if (libraryHeader != null) libraryHeader.setVisibility(visible ? View.GONE : View.VISIBLE);
        mainTab.setVisibility(visible ? View.GONE : View.VISIBLE);
        back.setEnabled(visible);
    }

    boolean isVisible() {
        return back.isEnabled();
    }

    void detach() {
        back.remove();
    }

    private void bindSection(int viewId, StreamSettings.Section section) {
        settings.findViewById(viewId).setOnClickListener(v ->
                activity.startActivity(new Intent(activity, StreamSettings.class)
                        .putExtra(StreamSettings.SECTION, section.name())));
    }
}
