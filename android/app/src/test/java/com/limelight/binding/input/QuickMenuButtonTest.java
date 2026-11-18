package com.limelight.binding.input;

import android.app.Activity;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.GameGestures;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
public class QuickMenuButtonTest {
    public static class Host extends Activity {
        @Override public boolean bindService(android.content.Intent intent,
                android.content.ServiceConnection connection, int flags) {
            return false; // SHIELD extension service is absent on this test device.
        }
    }

    @Test public void disabledShortcutAndDisabledMenuPreserveHostInput() {
        try (var activity = Robolectric.buildActivity(Host.class).setup()) {
            for (boolean menuEnabled : new boolean[]{true, false}) {
                var config = PreferenceConfiguration.readPreferences(activity.get());
                config.enableBackMenu = menuEnabled;
                config.quickMenuButton = menuEnabled ? "disabled" : "start";
                NvConnection connection = mock(NvConnection.class);
                GameGestures gestures = mock(GameGestures.class);
                ControllerHandler handler = new ControllerHandler(activity.get(), connection, gestures, config);
                try {
                    handler.reportOscState(ControllerPacket.PLAY_FLAG, (short)0, (short)0, (short)0, (short)0, (byte)0, (byte)0);
                    Shadows.shadowOf(Looper.getMainLooper()).idle();
                    verify(connection).sendControllerInput(anyShort(), anyShort(), eq(ControllerPacket.PLAY_FLAG), anyByte(), anyByte(), anyShort(), anyShort(), anyShort(), anyShort());
                    verifyNoInteractions(gestures);
                } finally { handler.destroy(); }
            }
        }
    }

    @Test public void usbControllerReservesButtonAndKeepsOtherInput() {
        try (var activity = Robolectric.buildActivity(Host.class).setup()) {
            var config = PreferenceConfiguration.readPreferences(activity.get());
            config.enableBackMenu = true;
            config.quickMenuButton = "guide";
            NvConnection connection = mock(NvConnection.class);
            GameGestures gestures = mock(GameGestures.class);
            var device = mock(com.limelight.binding.input.driver.AbstractController.class);
            when(device.getControllerId()).thenReturn(42);
            ControllerHandler handler = new ControllerHandler(activity.get(), connection, gestures, config);
            try {
                handler.deviceAdded(device);
                for (int flags : new int[]{ControllerPacket.SPECIAL_BUTTON_FLAG | ControllerPacket.B_FLAG,
                        ControllerPacket.SPECIAL_BUTTON_FLAG | ControllerPacket.B_FLAG, 0,
                        ControllerPacket.SPECIAL_BUTTON_FLAG | ControllerPacket.B_FLAG}) {
                    handler.reportControllerState(42, flags, 0, 0, 0, 0, 0, 0);
                }
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                verify(gestures, times(2)).showGameMenu(any(GameInputDevice.class));
                verify(connection, times(3)).sendControllerInput(anyShort(), anyShort(), eq(ControllerPacket.B_FLAG), anyByte(), anyByte(), anyShort(), anyShort(), anyShort(), anyShort());
                verify(connection).sendControllerInput(anyShort(), anyShort(), eq(0), anyByte(), anyByte(), anyShort(), anyShort(), anyShort(), anyShort());
            } finally { handler.destroy(); }
        }
    }

    @Test public void reservedButtonOpensOncePerPressAndNeverReachesHost() {
        try (var activity = Robolectric.buildActivity(Host.class).setup()) {
            for (String choice : new String[]{"guide", "start", "select", "l3", "r3"}) {
                var prefs = PreferenceManager.getDefaultSharedPreferences(activity.get());
                prefs.edit().putString(PreferenceConfiguration.QUICK_MENU_BUTTON_PREF, choice).commit();
                var config = PreferenceConfiguration.readPreferences(activity.get());
                assertEquals(choice, config.quickMenuButton);
                NvConnection connection = mock(NvConnection.class);
                AtomicInteger opened = new AtomicInteger();
                GameGestures gestures = new GameGestures() {
                    @Override public void toggleKeyboard() {}
                    @Override public void showGameMenu(GameInputDevice device) { opened.incrementAndGet(); }
                };
                ControllerHandler handler = new ControllerHandler(activity.get(), connection, gestures, config);
                try {
                    int mask = ControllerHandler.quickMenuButtonMask(choice);
                    assertNotEquals(0, mask);
                    handler.reportOscState(mask | ControllerPacket.A_FLAG, (short)0, (short)0, (short)0, (short)0, (byte)0, (byte)0);
                    handler.reportOscState(mask | ControllerPacket.A_FLAG, (short)0, (short)0, (short)0, (short)0, (byte)0, (byte)0);
                    Shadows.shadowOf(Looper.getMainLooper()).idle();
                    assertEquals(1, opened.get());
                    verify(connection, times(2)).sendControllerInput(eq((short)0), anyShort(), eq(ControllerPacket.A_FLAG), anyByte(), anyByte(), anyShort(), anyShort(), anyShort(), anyShort());
                    handler.reportOscState(0, (short)0, (short)0, (short)0, (short)0, (byte)0, (byte)0);
                    handler.reportOscState(mask, (short)0, (short)0, (short)0, (short)0, (byte)0, (byte)0);
                    Shadows.shadowOf(Looper.getMainLooper()).idle();
                    assertEquals(2, opened.get());
                } finally { handler.destroy(); }
            }
            PreferenceManager.getDefaultSharedPreferences(activity.get()).edit()
                .putString(PreferenceConfiguration.QUICK_MENU_BUTTON_PREF, "invalid").commit();
            assertEquals("legacy", PreferenceConfiguration.readPreferences(activity.get()).quickMenuButton);
            assertEquals(0, ControllerHandler.quickMenuButtonMask("disabled"));
        }
    }
}
