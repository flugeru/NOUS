package com.limelight.preferences;

import android.content.Intent;
import android.os.Looper;
import android.widget.SeekBar;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import com.limelight.EditProfileActivity;
import com.limelight.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
public class ProfileBitrateTest {
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

    @Test public void profileEditorKeepsBitrateInKbpsWithoutChangingBase() {
        android.content.Context context = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putInt(PreferenceConfiguration.BITRATE_PREF_STRING, 200000).commit();
        try (org.robolectric.android.controller.ActivityController<EditProfileActivity> controller =
                     Robolectric.buildActivity(EditProfileActivity.class, new Intent(context, EditProfileActivity.class)).setup()) {
            EditProfileActivity activity = controller.get();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            EditProfileActivity.ProfilePreferenceFragment fragment =
                    (EditProfileActivity.ProfilePreferenceFragment) activity.getSupportFragmentManager()
                            .findFragmentById(R.id.preferences_container);
            SeekBarPreference preference = fragment.findPreference(PreferenceConfiguration.BITRATE_PREF_STRING);
            assertNotNull(preference);
            preference.showDialog();
            AlertDialog dialog = preference.getDialog();
            SeekBar seekBar = slider(dialog.getWindow().getDecorView());
            assertNotNull(seekBar);
            // Native slider stores kbps, not displayed Mbps. Min is 500 kbps.
            seekBar.setProgress(20000 - 500);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(20000, activity.getInMemoryPrefs().getInt(PreferenceConfiguration.BITRATE_PREF_STRING, -1));
            assertEquals(200000, PreferenceManager.getDefaultSharedPreferences(context)
                    .getInt(PreferenceConfiguration.BITRATE_PREF_STRING, -1));
            assertEquals(20000, preference.getProgress());
            androidx.preference.EditTextPreference custom = fragment.findPreference(PreferenceConfiguration.CUSTOM_BITRATE_PREF_STRING);
            assertNotNull(custom);
            assertTrue(custom.callChangeListener("20,5"));
            assertEquals(20500, activity.getInMemoryPrefs().getInt(PreferenceConfiguration.BITRATE_PREF_STRING, -1));
            assertFalse(custom.callChangeListener("NaN"));
            assertFalse(custom.callChangeListener("Infinity"));
            assertFalse(custom.callChangeListener("9999"));
            assertFalse(custom.callChangeListener("invalid"));
            assertEquals(20500, activity.getInMemoryPrefs().getInt(PreferenceConfiguration.BITRATE_PREF_STRING, -1));
            activity.getInMemoryPrefs().edit().putInt(PreferenceConfiguration.BITRATE_PREF_STRING, 20000).apply();
            preference.showDialog();
            assertEquals(19500, seekBar.getProgress());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(20000, activity.getInMemoryPrefs().getInt(PreferenceConfiguration.BITRATE_PREF_STRING, -1));
        }
    }
}
