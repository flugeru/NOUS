package com.limelight;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

/** Layout renders are not a substitute for a device streaming test. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NousLayoutPreviewTest {
    public static class Host extends AppCompatActivity {
        @Override public void onCreate(Bundle state) {
            setTheme(R.style.AppTheme);
            super.onCreate(state);
        }
    }
    private void render(View view, int width, int height, String name) throws Exception {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        File folder = new File("build/outputs/nous-layout-previews");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(folder, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
        bitmap.recycle();
    }
    @Test public void keyLayoutsInflateInPortraitAndLandscape() throws Exception {
        try (org.robolectric.android.controller.ActivityController<Host> controller =
                     Robolectric.buildActivity(Host.class).setup()) {
            Host host = controller.get();
            int[] layouts = {R.layout.activity_nous_settings, R.layout.activity_add_computer_manually,
                    R.layout.activity_profiles, R.layout.activity_edit_profile, R.layout.nous_pairing_pin,
                    R.layout.activity_pc_view, R.layout.activity_app_view};
            String[] names = {"settings", "manual-connection", "profiles", "edit-profile", "pairing",
                    "computers", "library"};
            for (int i = 0; i < layouts.length; i++) {
                View view = host.getLayoutInflater().inflate(layouts[i], null, false);
                render(view, 393, 852, names[i]);
                render(view, 852, 393, names[i] + "-landscape");
            }
            View value = host.getLayoutInflater().inflate(R.layout.nous_preference_value, null, false);
            ((TextView)value.findViewById(android.R.id.title)).setText("Resolução");
            ((TextView)value.findViewById(android.R.id.summary)).setText("2712 × 1220");
            render(value, 345, 96, "active-value");
            assertTrue(value.findViewById(android.R.id.summary).getTop()
                    >= value.findViewById(android.R.id.title).getBottom());
            assertNotNull(host.getLayoutInflater().inflate(R.layout.nous_preference_switch, null, false)
                    .findViewById(android.R.id.checkbox));
        }
    }
    @Test public void settingsHideLibraryHeaderAndRestoreItOnReturn() {
        try (org.robolectric.android.controller.ActivityController<Host> controller =
                     Robolectric.buildActivity(Host.class).setup()) {
            Host host = controller.get();
            host.setContentView(R.layout.activity_app_view);
            com.google.android.material.bottomnavigation.BottomNavigationView navigation =
                    host.findViewById(R.id.nous_navigation);
            NousSettingsTab tab = new NousSettingsTab(host, navigation, R.id.nous_nav_games);
            tab.show(true);
            assertEquals(View.GONE, host.findViewById(R.id.nous_library_header).getVisibility());
            assertEquals(View.GONE, host.findViewById(R.id.nous_main_tab).getVisibility());
            tab.show(false);
            assertEquals(View.VISIBLE, host.findViewById(R.id.nous_library_header).getVisibility());
            assertEquals(View.VISIBLE, host.findViewById(R.id.nous_main_tab).getVisibility());
        }
    }
    @Test public void embeddedLibraryDoesNotShadowHostTabIds() {
        try (org.robolectric.android.controller.ActivityController<Host> controller =
                     Robolectric.buildActivity(Host.class).setup()) {
            Host host = controller.get();
            host.setContentView(R.layout.activity_pc_view);
            android.widget.FrameLayout container = host.findViewById(R.id.nous_games_container);
            View library = new GamesFragment().onCreateView(host.getLayoutInflater(), container, null);
            container.addView(library);
            assertNull(library.findViewById(R.id.nous_navigation));
            assertNull(library.findViewById(R.id.nous_main_tab));
            assertNull(library.findViewById(R.id.nous_tab_content));
            assertNotNull(library.findViewById(R.id.nous_game_search));
            assertNotSame(library, host.findViewById(R.id.nous_main_tab));
        }
    }

    @Test
    @Config(shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
    public void systemBackAppliesChangedSettingsLanguage() {
        try (org.robolectric.android.controller.ActivityController<com.limelight.preferences.StreamSettings> controller =
                     Robolectric.buildActivity(com.limelight.preferences.StreamSettings.class).setup()) {
            com.limelight.preferences.StreamSettings activity = controller.get();
            android.preference.PreferenceManager.getDefaultSharedPreferences(activity).edit()
                    .putString("list_languages", "en").commit();
            activity.getOnBackPressedDispatcher().onBackPressed();
            assertTrue(activity.isFinishing());
            android.content.Intent restart = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(restart);
            assertEquals(PcView.class.getName(), restart.getComponent().getClassName());
        }
    }

    @Test public void detachedLibraryDoesNotHandleHostContextMenu() {
        assertFalse(new GamesFragment().onContextItemSelected(null));
    }

    @Test public void helpHomeWorksWithoutAnExternalUrl() {
        try (org.robolectric.android.controller.ActivityController<HelpActivity> controller =
                     Robolectric.buildActivity(HelpActivity.class).setup()) {
            HelpActivity activity = controller.get();
            assertNotNull(activity.findViewById(R.id.nous_help_content));
            activity.findViewById(R.id.nous_back).performClick();
            assertTrue(activity.isFinishing());
        }
    }
}
