package com.limelight;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputLayout;
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
    private void renderFocusedField(Host host, int layout, int inputId, String text, String name)
            throws Exception {
        host.setContentView(layout);
        TextView input = host.findViewById(inputId);
        TextInputLayout field = (TextInputLayout) input.getParent().getParent();
        assertEquals(TextInputLayout.BOX_BACKGROUND_OUTLINE, field.getBoxBackgroundMode());
        input.setText(text);
        assertTrue(input.requestFocus());
        render(host.findViewById(android.R.id.content), 393, 852, name);
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
            renderFocusedField(host, R.layout.activity_add_computer_manually, R.id.hostTextView,
                    "192.0.2.1", "manual-connection-focused");
            renderFocusedField(host, R.layout.activity_app_view, R.id.nous_game_search,
                    "Genshin", "library-search-focused");
            View value = host.getLayoutInflater().inflate(R.layout.nous_preference_value, null, false);
            ((TextView)value.findViewById(android.R.id.title)).setText("Resolução");
            ((TextView)value.findViewById(android.R.id.summary)).setText("2712 × 1220");
            render(value, 345, 96, "active-value");
            View title = value.findViewById(android.R.id.title);
            View summary = value.findViewById(android.R.id.summary);
            assertTrue("Active value must not overlap its label", summary.getLeft() >= title.getRight());
            assertTrue("Active value must stay within the row", summary.getRight() <= value.getWidth());
            assertTrue("Active value must remain visible", summary.getWidth() > 0 && summary.getHeight() > 0);
            MaterialSwitch toggle = (MaterialSwitch) host.getLayoutInflater()
                    .inflate(R.layout.nous_preference_switch, null, false);
            assertEquals(android.R.id.checkbox, toggle.getId());
            assertNotNull(toggle.getTrackDrawable());
            assertNotNull(toggle.getThumbDrawable());
            int[] checked = {android.R.attr.state_enabled, android.R.attr.state_checked};
            assertEquals(host.getColor(R.color.nous_secondary),
                    toggle.getTrackTintList().getColorForState(checked, 0));
            assertEquals(host.getColor(R.color.nous_on_secondary),
                    toggle.getThumbTintList().getColorForState(checked, 0));
            Bitmap logo = BitmapFactory.decodeResource(host.getResources(), R.drawable.nous_brand);
            assertNotNull(logo);
            assertEquals(0, android.graphics.Color.alpha(logo.getPixel(0, 0)));
            logo.recycle();
        }
    }
    @Test public void portraitLibraryUsesTwoColumnsAndWidescreenCovers() {
        try (org.robolectric.android.controller.ActivityController<Host> controller =
                     Robolectric.buildActivity(Host.class).setup()) {
            Host host = controller.get();
            for (int layout : new int[]{R.layout.app_grid_view, R.layout.app_grid_view_small}) {
                android.widget.GridView grid = (android.widget.GridView) host.getLayoutInflater()
                        .inflate(layout, null, false);
                grid.measure(View.MeasureSpec.makeMeasureSpec(393, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
                assertEquals(2, grid.getNumColumns());
                assertEquals(24, grid.getPaddingStart());
                assertEquals(24, grid.getPaddingEnd());
            }
            for (int layout : new int[]{R.layout.app_grid_item, R.layout.app_grid_item_small}) {
                View row = host.getLayoutInflater().inflate(layout, null, false);
                row.measure(View.MeasureSpec.makeMeasureSpec(168, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.UNSPECIFIED);
                android.widget.ImageView cover = row.findViewById(R.id.grid_image);
                assertTrue(row.getBackground() instanceof android.graphics.drawable.RippleDrawable);
                assertEquals(164, cover.getMeasuredWidth());
                assertEquals(92, cover.getMeasuredHeight());
                assertEquals(android.widget.ImageView.ScaleType.CENTER_CROP, cover.getScaleType());
            }
        }
    }

    @Test
    @Config(qualifiers = "+land")
    public void landscapeLibraryAdaptsColumnsToAvailableWidth() {
        try (org.robolectric.android.controller.ActivityController<Host> controller =
                     Robolectric.buildActivity(Host.class).setup()) {
            android.widget.GridView grid = (android.widget.GridView) controller.get().getLayoutInflater()
                    .inflate(R.layout.app_grid_view, null, false);
            int height = View.MeasureSpec.makeMeasureSpec(340, View.MeasureSpec.EXACTLY);
            View shell = controller.get().getLayoutInflater().inflate(R.layout.activity_pc_view, null, false);
            com.google.android.material.navigationrail.NavigationRailView rail = shell.findViewById(R.id.nous_navigation);
            assertEquals(android.view.Gravity.CENTER, rail.getMenuGravity());
            View tabs = shell.findViewById(R.id.nous_tab_content);
            View searchAction = shell.findViewById(R.id.nous_library_search_toggle);
            assertEquals(View.GONE, searchAction.getVisibility());
            searchAction.setVisibility(View.VISIBLE);
            for (int screenWidth : new int[]{640, 852}) {
                shell.measure(View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.EXACTLY), height);
                shell.layout(0, 0, screenWidth, 340);
                assertTrue(rail.getRight() <= tabs.getLeft());
                View first = rail.findViewById(R.id.nous_nav_computers);
                View last = rail.findViewById(R.id.nous_nav_settings);
                android.graphics.Rect firstBounds = new android.graphics.Rect();
                android.graphics.Rect lastBounds = new android.graphics.Rect();
                first.getDrawingRect(firstBounds);
                last.getDrawingRect(lastBounds);
                rail.offsetDescendantRectToMyCoords(first, firstBounds);
                rail.offsetDescendantRectToMyCoords(last, lastBounds);
                assertTrue(Math.abs(firstBounds.top + lastBounds.bottom - rail.getHeight()) <= 2);
                assertTrue(searchAction.getTop() >= rail.getBottom());
                assertEquals(rail.getWidth(), searchAction.getLeft() + searchAction.getRight());
                grid.measure(View.MeasureSpec.makeMeasureSpec(tabs.getWidth(), View.MeasureSpec.EXACTLY), height);
                assertEquals(screenWidth == 640 ? 3 : 4, grid.getNumColumns());
            }
        }
    }

    @Test
    @Config(qualifiers = "+land")
    public void libraryCategoriesKeepOneBaselineWhenSelectionChanges() {
        try (org.robolectric.android.controller.ActivityController<Host> controller =
                     Robolectric.buildActivity(Host.class).setup()) {
            android.widget.RadioGroup categories = (android.widget.RadioGroup) controller.get().getLayoutInflater()
                    .inflate(R.layout.nous_offline_categories, null, false);
            categories.setVisibility(View.VISIBLE);
            for (int category : new int[]{R.id.nous_offline_desktop, R.id.nous_offline_games, R.id.nous_offline_launchers}) {
                categories.check(category);
                categories.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.UNSPECIFIED);
                categories.layout(0, 0, 360, categories.getMeasuredHeight());

                TextView desktop = categories.findViewById(R.id.nous_offline_desktop);
                int baseline = desktop.getTop() + desktop.getBaseline();
                for (int i = 1; i < categories.getChildCount(); i++) {
                    TextView label = (TextView) categories.getChildAt(i);
                    assertEquals(baseline, label.getTop() + label.getBaseline());
                }
            }
        }
    }

    @Test
    public void computerAndProfileActionsRestoreViewStateAcrossRotation() {
        try (org.robolectric.android.controller.ActivityController<Host> controller =
                     Robolectric.buildActivity(Host.class).setup()) {
            Host host = controller.get();
            android.content.res.Configuration landscape = new android.content.res.Configuration(
                    host.getResources().getConfiguration());
            landscape.orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE;
            landscape.screenWidthDp = 852;
            landscape.screenHeightDp = 393;
            android.view.ContextThemeWrapper landscapeContext = new android.view.ContextThemeWrapper(
                    host.createConfigurationContext(landscape), R.style.AppTheme);
            android.view.LayoutInflater landscapeInflater = host.getLayoutInflater().cloneInContext(landscapeContext);

            for (int[] layoutAndAction : new int[][]{
                    {R.layout.activity_pc_view, R.id.manuallyAddPc},
                    {R.layout.activity_profiles, R.id.addProfileFab}
            }) {
                View portrait = host.getLayoutInflater().inflate(layoutAndAction[0], null, false);
                View horizontal = landscapeInflater.inflate(layoutAndAction[0], null, false);
                if (layoutAndAction[0] == R.layout.activity_pc_view) {
                    assertTrue(horizontal.findViewById(R.id.nous_navigation)
                            instanceof com.google.android.material.navigationrail.NavigationRailView);
                } else {
                    assertTrue(horizontal instanceof android.widget.LinearLayout);
                }
                assertEquals(portrait.findViewById(layoutAndAction[1]).getClass(),
                        horizontal.findViewById(layoutAndAction[1]).getClass());
                android.util.SparseArray<android.os.Parcelable> saved = new android.util.SparseArray<>();
                portrait.saveHierarchyState(saved);
                horizontal.restoreHierarchyState(saved);
                saved.clear();
                horizontal.saveHierarchyState(saved);
                portrait.restoreHierarchyState(saved);
            }
        }
    }

    @Test public void computerProfileButtonAlignsWithTheCardAndManualAction() {
        try (org.robolectric.android.controller.ActivityController<Host> controller =
                     Robolectric.buildActivity(Host.class).setup()) {
            android.view.ViewGroup root = (android.view.ViewGroup) controller.get()
                    .getLayoutInflater().inflate(R.layout.activity_pc_view, null, false);
            root.measure(View.MeasureSpec.makeMeasureSpec(393, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(852, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 393, 852);
            android.widget.ScrollView scan = root.findViewById(R.id.no_pc_found_layout);
            android.graphics.Rect profileBounds = new android.graphics.Rect();
            android.graphics.Rect cardBounds = new android.graphics.Rect();
            android.graphics.Rect manualBounds = new android.graphics.Rect();
            View profile = root.findViewById(R.id.profilesButton);
            View card = scan.getChildAt(0);
            View manual = root.findViewById(R.id.manuallyAddPc);
            profile.getDrawingRect(profileBounds);
            card.getDrawingRect(cardBounds);
            manual.getDrawingRect(manualBounds);
            root.offsetDescendantRectToMyCoords(profile, profileBounds);
            root.offsetDescendantRectToMyCoords(card, cardBounds);
            root.offsetDescendantRectToMyCoords(manual, manualBounds);
            assertEquals(manualBounds.right, profileBounds.right);
            assertEquals(manualBounds.right, cardBounds.right);
        }
    }

    @Test public void switchUpdatesOnRowTapWithoutWaitingForRecyclerView() {
        try (org.robolectric.android.controller.ActivityController<Host> controller =
                     Robolectric.buildActivity(Host.class).setup()) {
            Host host = controller.get();
            View row = host.getLayoutInflater().inflate(R.layout.nous_preference_row, null, false);
            android.view.ViewGroup widgetFrame = row.findViewById(android.R.id.widget_frame);
            MaterialSwitch toggle = (MaterialSwitch) host.getLayoutInflater()
                    .inflate(R.layout.nous_preference_switch, widgetFrame, false);
            widgetFrame.addView(toggle);
            com.limelight.preferences.NousSwitchPreference preference =
                    new com.limelight.preferences.NousSwitchPreference(host);
            java.util.List<Object> changes = new java.util.ArrayList<>();
            preference.setOnPreferenceChangeListener((ignored, newValue) -> {
                changes.add(newValue);
                return changes.size() < 3;
            });
            preference.onBindViewHolder(
                    androidx.preference.PreferenceViewHolder.createInstanceForTests(row));

            row.performClick();
            assertTrue(preference.isChecked());
            assertTrue(toggle.isChecked());
            row.performClick();
            assertFalse(preference.isChecked());
            assertFalse(toggle.isChecked());
            row.performClick();
            assertFalse(preference.isChecked());
            assertFalse(toggle.isChecked());
            assertEquals(java.util.Arrays.asList(true, false, true), changes);
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

    @Test public void legacyLibraryShortcutPreservesExtras() {
        android.content.Intent shortcut = new android.content.Intent()
                .putExtra(AppView.UUID_EXTRA, "fixture-pc")
                .putExtra(AppView.NAME_EXTRA, "Fixture PC")
                .putExtra(AppView.SHOW_HIDDEN_APPS_EXTRA, true);
        try (org.robolectric.android.controller.ActivityController<AppView> controller =
                     Robolectric.buildActivity(AppView.class, shortcut).setup()) {
            AppView activity = controller.get();
            android.content.Intent target = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
            assertTrue(activity.isFinishing());
            assertNotNull(target);
            assertEquals(PcView.class.getName(), target.getComponent().getClassName());
            assertEquals("fixture-pc", target.getStringExtra(AppView.UUID_EXTRA));
            assertEquals("Fixture PC", target.getStringExtra(AppView.NAME_EXTRA));
            assertTrue(target.getBooleanExtra(AppView.SHOW_HIDDEN_APPS_EXTRA, false));
            assertEquals(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    target.getFlags());
        }
    }

    @Test public void detachedLibraryDoesNotHandleHostContextMenu() {
        assertFalse(new GamesFragment().handleGameAction(null, null, 0));
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
