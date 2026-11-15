package com.limelight.preferences;

import android.os.Bundle;
import android.widget.SeekBar;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import com.limelight.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SeekBarPreferenceDialogTest {
    public static class Host extends AppCompatActivity {
        @Override public void onCreate(Bundle state) {
            setTheme(R.style.AppTheme);
            super.onCreate(state);
        }
    }

    private SeekBar slider(View view) {
        if (view instanceof SeekBar) return (SeekBar) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                SeekBar found = slider(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test public void cancelAndRejectedChangesDoNotPersist() {
        try (org.robolectric.android.controller.ActivityController<Host> controller =
                     Robolectric.buildActivity(Host.class).setup()) {
            Host host = controller.get();
            android.util.AttributeSet attrs = Robolectric.buildAttributeSet()
                    .addAttribute(android.R.attr.defaultValue, "20")
                    .addAttribute(android.R.attr.max, "100").build();
            SeekBarPreference preference = new SeekBarPreference(host, attrs);
            preference.setKey("dialog_test");
            androidx.preference.PreferenceScreen screen = new PreferenceManager(host).createPreferenceScreen(host);
            screen.addPreference(preference);
            android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(host);
            prefs.edit().putInt("dialog_test", 20).commit();
            preference.showDialog();
            AlertDialog dialog = preference.getDialog();
            SeekBar slider = slider(dialog.getWindow().getDecorView());
            assertNotNull(slider);
            slider.setProgress(49);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            assertEquals(20, prefs.getInt("dialog_test", -1));
            preference.setOnPreferenceChangeListener((p, value) -> false);
            preference.showDialog();
            slider.setProgress(69);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(20, prefs.getInt("dialog_test", -1));
            preference.setOnPreferenceChangeListener((p, value) -> true);
            preference.showDialog();
            slider.setProgress(39);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(40, prefs.getInt("dialog_test", -1));
            assertEquals("40", preference.getFormattedValue());
        }
    }
}
