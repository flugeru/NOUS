package com.limelight.ui;

import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Window;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;

import com.limelight.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ControllerNavigationTest {
    public static class Host extends ControllerMenuActivity {
        @Override
        protected void onCreate(android.os.Bundle state) {
            setTheme(R.style.AppTheme);
            super.onCreate(state);
        }
    }

    @Test
    public void gamepadConfirmsFocusedButtonAndCancelsDialog() {
        try (ActivityController<Host> controller = Robolectric.buildActivity(Host.class).setup()) {
            Host host = controller.get();
            Button button = new Button(host);
            button.setFocusableInTouchMode(true);
            AtomicInteger clicks = new AtomicInteger();
            button.setOnClickListener(view -> clicks.incrementAndGet());
            host.setContentView(button);
            assertTrue(button.requestFocus());
            assertTrue(host.getWindow().getCallback() instanceof ControllerNavigation);

            press(host.getWindow(), KeyEvent.KEYCODE_BUTTON_A);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, clicks.get());
            press(host.getWindow(), KeyEvent.KEYCODE_BUTTON_1);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(2, clicks.get());

            AlertDialog dialog = new ControllerDialogBuilder(host)
                    .setMessage("Test")
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            press(dialog.getWindow(), KeyEvent.KEYCODE_BUTTON_B);
            assertFalse(dialog.isShowing());

            AtomicInteger cancellations = new AtomicInteger();
            AlertDialog confirmation = new ControllerDialogBuilder(host)
                    .setMessage("Confirm?")
                    .setNegativeButton(android.R.string.cancel, (ignored, which) -> cancellations.incrementAndGet())
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            press(confirmation.getWindow(), KeyEvent.KEYCODE_DPAD_DOWN);
            Button confirm = confirmation.getButton(AlertDialog.BUTTON_POSITIVE);
            Button cancel = confirmation.getButton(AlertDialog.BUTTON_NEGATIVE);
            assertTrue(confirm.getCompoundDrawablesRelative()[0] != null);
            assertTrue(cancel.getCompoundDrawablesRelative()[0] != null);
            press(confirmation.getWindow(), KeyEvent.KEYCODE_BUTTON_B);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, cancellations.get());
            assertFalse(confirmation.isShowing());
        }
    }

    @Test
    public void joystickUsesDominantAxisAndIgnoresSmallMovements() {
        int none = KeyEvent.KEYCODE_UNKNOWN;
        assertEquals(none, ControllerNavigation.joystickDirection(0.25f, -0.32f, none));
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, ControllerNavigation.joystickDirection(-0.9f, 0.6f, none));
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, ControllerNavigation.joystickDirection(0.62f, 0.85f, none));
        assertEquals(KeyEvent.KEYCODE_DPAD_UP,
                ControllerNavigation.joystickDirection(0, -0.4f, KeyEvent.KEYCODE_DPAD_UP));
        assertEquals(none, ControllerNavigation.joystickDirection(0, -0.3f, KeyEvent.KEYCODE_DPAD_UP));
    }

    private void press(Window window, int code) {
        long time = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(time, time, KeyEvent.ACTION_DOWN, code,
                0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD);
        window.getCallback().dispatchKeyEvent(down);
        window.getCallback().dispatchKeyEvent(KeyEvent.changeAction(down, KeyEvent.ACTION_UP));
    }
}
