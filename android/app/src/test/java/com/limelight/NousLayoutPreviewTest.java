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
                    R.layout.activity_profiles, R.layout.activity_edit_profile, R.layout.nous_pairing_pin};
            String[] names = {"settings", "manual-connection", "profiles", "edit-profile", "pairing"};
            for (int i = 0; i < layouts.length; i++) {
                View view = host.getLayoutInflater().inflate(layouts[i], null, false);
                render(view, 393, 852, names[i]);
                render(view, 852, 393, names[i] + "-landscape");
            }
            View value = host.getLayoutInflater().inflate(R.layout.nous_preference_value, null, false);
            ((TextView)value.findViewById(android.R.id.title)).setText("Resolução");
            ((TextView)value.findViewById(android.R.id.summary)).setText("2712 × 1220");
            render(value, 345, 64, "active-value");
            assertNotNull(host.getLayoutInflater().inflate(R.layout.nous_preference_switch, null, false)
                    .findViewById(android.R.id.checkbox));
        }
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
