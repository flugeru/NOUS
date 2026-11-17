package com.limelight.profiles;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.content.SharedPreferences;
import android.os.Build;
import android.content.Context;
import android.app.GameManager;

import com.limelight.PcView;
import com.limelight.preferences.StreamSettings;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.limelight.ProfilesActivity;
import com.limelight.R;
import com.limelight.AppView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.junit.BeforeClass;
import com.limelight.TestLogSuppressor;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@RunWith(RobolectricTestRunner.class)
public class ProfilesNavigationTest {

    @BeforeClass
    public static void suppressInvalidIdLogs() {
        TestLogSuppressor.install();
    }

    private void prepareEnvironment() {
        // Ensure GL renderer prefs exist so PcView skips GLSurface initialization
        SharedPreferences glPrefs = ApplicationProvider.getApplicationContext()
                .getSharedPreferences("GlPreferences", 0);
        glPrefs.edit()
                .putString("Renderer", "TestRenderer")
                .putString("Fingerprint", Build.FINGERPRINT)
                .commit();

        // Provide fake GameManager service to avoid ServiceNotFoundException
        org.robolectric.shadows.ShadowApplication shadowApp = org.robolectric.Shadows.shadowOf((android.app.Application) ApplicationProvider.getApplicationContext());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            shadowApp.setSystemService(Context.GAME_SERVICE, mock(GameManager.class));
        } else {
            // On older APIs, the service doesn't exist, but our code might still try to access it.
            // The cast will fail if it's just `new Object()`, but it shouldn't be called anyway.
            shadowApp.setSystemService(Context.GAME_SERVICE, null);
        }

        // Provide stub ComputerManagerService binder for bindService()
        Context ctx = ApplicationProvider.getApplicationContext();
        android.content.ComponentName cn = new android.content.ComponentName(ctx, com.limelight.computers.ComputerManagerService.class);

        // We only need a mock binder, not the real service
        com.limelight.computers.ComputerManagerService.ComputerManagerBinder binder = mock(com.limelight.computers.ComputerManagerService.ComputerManagerBinder.class);
        org.robolectric.Shadows.shadowOf((android.app.Application) ctx)
                .setComponentNameAndServiceForBindService(cn, binder);

        // Apply an AppCompat theme required by ProfilesActivity
        // ApplicationProvider.getApplicationContext().setTheme(R.style.SettingsTheme);
    }

    @Test
    public void settingsTab_switchesContentAndOpensOnlyTheSelectedSection() {
        prepareEnvironment();
        PcView activity = Robolectric.buildActivity(PcView.class).setup().get();
        BottomNavigationView navigation = activity.findViewById(R.id.nous_navigation);

        navigation.setSelectedItemId(R.id.nous_nav_settings);
        assertEquals(R.id.nous_nav_settings, navigation.getSelectedItemId());
        assertEquals(View.GONE, activity.findViewById(R.id.nous_main_tab).getVisibility());
        assertNull("Switching tabs should not start an activity",
                Shadows.shadowOf(activity).getNextStartedActivity());

        activity.findViewById(R.id.nous_settings_controls).performClick();
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(next);
        assertEquals(StreamSettings.class.getName(), next.getComponent().getClassName());
        assertEquals(StreamSettings.Section.CONTROLS.name(),
                next.getStringExtra(StreamSettings.SECTION));

        navigation.setSelectedItemId(R.id.nous_nav_computers);
        assertEquals(View.VISIBLE, activity.findViewById(R.id.nous_main_tab).getVisibility());
    }

    @Test
    public void addComputerButtonOpensManualConnection() {
        prepareEnvironment();
        try (ActivityController<PcView> controller = Robolectric.buildActivity(PcView.class).setup()) {
            PcView activity = controller.get();
            View button = activity.findViewById(R.id.manuallyAddPc);
            assertNotNull(button);
            assertTrue(button.performClick());
            Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(next);
            assertEquals(com.limelight.preferences.AddComputerManually.class.getName(),
                    next.getComponent().getClassName());
        }
    }

    @Test
    public void profileButtonUsesACircleAndCentersTheSelectedProfile() {
        prepareEnvironment();
        try (ActivityController<PcView> controller = Robolectric.buildActivity(PcView.class).setup()) {
            ProfileButton button = controller.get().findViewById(R.id.profilesButton);
            View icon = button.findViewById(R.id.nous_profile_button_icon);
            android.widget.TextView label = button.findViewById(R.id.nous_profile_button_label);
            ProfilesManager manager = ProfilesManager.getInstance();
            manager.setActive(null);
            button.refresh();
            measureProfileButton(button);
            assertEquals(button.getHeight(), button.getWidth());
            assertEquals(View.GONE, label.getVisibility());
            assertEquals(button.getWidth(), icon.getLeft() + icon.getRight(), 1);

            java.util.UUID id = java.util.UUID.randomUUID();
            manager.add(new SettingsProfile(id, "Teste", 0, 0, java.util.Collections.emptyMap()));
            manager.setActive(id);
            button.refresh();
            measureProfileButton(button);
            assertEquals("Teste", label.getText().toString());
            assertTrue(button.getWidth() > button.getHeight());
            assertEquals(button.getWidth(), icon.getLeft() + label.getRight(), 1);
            assertEquals(button.getHeight(), icon.getTop() + icon.getBottom(), 1);
            assertEquals(button.getHeight(), label.getTop() + label.getBottom(), 1);
        }
    }

    private void measureProfileButton(ProfileButton button) {
        ViewGroup.LayoutParams size = button.getLayoutParams();
        int height = size.height;
        int widthSpec = size.width > 0 ? View.MeasureSpec.makeMeasureSpec(size.width, View.MeasureSpec.EXACTLY)
                : View.MeasureSpec.UNSPECIFIED;
        button.measure(widthSpec,
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        button.layout(0, 0, button.getMeasuredWidth(), height);
    }

    @Test
    public void clickingProfileButton_launchesProfilesActivity() {
        prepareEnvironment();
        ActivityController<PcView> controller = Robolectric.buildActivity(PcView.class).setup();
        PcView pcView = controller.get();

        View btn = pcView.findViewById(R.id.profilesButton);
        assertNotNull("profilesButton not found", btn);

        btn.performClick();

        Intent next = Shadows.shadowOf(pcView).getNextStartedActivity();
        assertNotNull("ProfilesActivity should be launched", next);
        assertEquals(ProfilesActivity.class.getName(), next.getComponent().getClassName());
    }

    @Test
    public void clickingProfileButton_launchesProfilesActivityFromAppView() {
        prepareEnvironment();
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), AppView.class);
        intent.putExtra("UUID", "test-uuid");
        intent.putExtra("Name", "Test PC");

        ActivityController<AppView> controller = Robolectric.buildActivity(AppView.class, intent).setup();
        AppView appView = controller.get();

        Intent redirect = Shadows.shadowOf(appView).getNextStartedActivity();
        assertNotNull("Library shortcut should open unified navigation", redirect);
        assertEquals(PcView.class.getName(), redirect.getComponent().getClassName());
        assertEquals("test-uuid", redirect.getStringExtra(AppView.UUID_EXTRA));
        assertTrue(appView.isFinishing());

        PcView pcView = Robolectric.buildActivity(PcView.class, redirect).setup().get();
        pcView.findViewById(R.id.profilesButton).performClick();
        Intent next = Shadows.shadowOf(pcView).getNextStartedActivity();
        assertNotNull(next);
        assertEquals(ProfilesActivity.class.getName(), next.getComponent().getClassName());
    }

    @Test
    public void profilesActivity_startsWithoutCrash() {
        prepareEnvironment();
        ProfilesActivity activity = Robolectric.buildActivity(ProfilesActivity.class).setup().get();
        assertNotNull(activity);
    }
}
