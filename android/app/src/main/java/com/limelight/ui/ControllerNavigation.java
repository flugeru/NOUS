package com.limelight.ui;

import android.app.Dialog;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.FocusFinder;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;

import androidx.appcompat.view.WindowCallbackWrapper;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/** Adapts controllers to Android focus navigation in local menus. */
final class ControllerNavigation extends WindowCallbackWrapper {
    private static final float ENGAGE = 0.55f;
    private static final float RELEASE = 0.35f;
    private static final int NO_DIRECTION = KeyEvent.KEYCODE_UNKNOWN;

    private final Window window;
    private final Runnable goBack;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable repeat = this::repeatDirection;
    private int heldDirection = NO_DIRECTION;
    private int heldDevice;

    static void attach(Window window, Runnable goBack) {
        if (!(window.getCallback() instanceof ControllerNavigation)) {
            window.setCallback(new ControllerNavigation(window, goBack));
        }
    }

    static void attach(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            attach(window, dialog::onBackPressed);
        }
    }

    private ControllerNavigation(Window window, Runnable goBack) {
        super(window.getCallback());
        this.window = window;
        this.goBack = goBack;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!event.isFromSource(InputDevice.SOURCE_GAMEPAD)
                && !event.isFromSource(InputDevice.SOURCE_DPAD)
                && !event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            return super.dispatchKeyEvent(event);
        }

        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_BUTTON_1:
            case KeyEvent.KEYCODE_BUTTON_A:
                View focus = window.getCurrentFocus();
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (focus == null) {
                        focusFirst(View.FOCUS_DOWN);
                        return true;
                    }
                    if (focus instanceof EditText) {
                        WindowCompat.getInsetsController(window, focus).show(WindowInsetsCompat.Type.ime());
                        return true;
                    }
                } else if (focus instanceof EditText) {
                    return true;
                }
                KeyEvent confirm = new KeyEvent(event.getDownTime(), event.getEventTime(),
                        event.getAction(), KeyEvent.KEYCODE_DPAD_CENTER, event.getRepeatCount(),
                        event.getMetaState(), event.getDeviceId(), event.getScanCode(),
                        event.getFlags(), event.getSource());
                return super.dispatchKeyEvent(confirm);

            case KeyEvent.KEYCODE_BUTTON_2:
            case KeyEvent.KEYCODE_BUTTON_B:
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                    View decor = window.getDecorView();
                    WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(decor);
                    if (insets != null && insets.isVisible(WindowInsetsCompat.Type.ime())) {
                        WindowCompat.getInsetsController(window, decor).hide(WindowInsetsCompat.Type.ime());
                    } else {
                        goBack.run();
                    }
                }
                return true;

            case KeyEvent.KEYCODE_BUTTON_3:
            case KeyEvent.KEYCODE_BUTTON_X:
                View selected = window.getCurrentFocus();
                if (selected instanceof AbsListView) {
                    AbsListView list = (AbsListView) selected;
                    AdapterView.OnItemLongClickListener actions = list.getOnItemLongClickListener();
                    View row = list.getSelectedView();
                    if (actions != null && row != null) {
                        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                            actions.onItemLongClick(list, row, list.getSelectedItemPosition(), list.getSelectedItemId());
                        }
                        return true;
                    }
                }
                break;

            default:
                if (event.getAction() == KeyEvent.ACTION_DOWN && window.getCurrentFocus() == null) {
                    int direction = focusDirection(event.getKeyCode());
                    if (direction != 0 && focusFirst(direction)) {
                        return true;
                    }
                }
                break;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_MOVE
                || !event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            return super.dispatchGenericMotionEvent(event);
        }
        float x = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float y = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if (x == 0 && y == 0) {
            x = event.getAxisValue(MotionEvent.AXIS_X);
            y = event.getAxisValue(MotionEvent.AXIS_Y);
        }

        int device = event.getDeviceId();
        int direction = joystickDirection(x, y, device == heldDevice ? heldDirection : NO_DIRECTION);
        if (direction != heldDirection || device != heldDevice) {
            stopRepeating();
            if (direction != NO_DIRECTION) {
                heldDirection = direction;
                heldDevice = device;
                navigate(direction);
                handler.postDelayed(repeat, 340);
            }
        }
        return true;
    }

    static int joystickDirection(float x, float y, int previous) {
        if (Math.max(Math.abs(x), Math.abs(y)) < (previous == NO_DIRECTION ? ENGAGE : RELEASE)) {
            return NO_DIRECTION;
        }
        if (Math.abs(x) > Math.abs(y)) {
            return x < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
        }
        return y < 0 ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (!hasFocus) {
            stopRepeating();
        }
        super.onWindowFocusChanged(hasFocus);
    }

    private void stopRepeating() {
        heldDirection = NO_DIRECTION;
        handler.removeCallbacks(repeat);
    }

    private void repeatDirection() {
        if (heldDirection != NO_DIRECTION && window.getDecorView().hasWindowFocus()) {
            navigate(heldDirection);
            if (heldDirection != NO_DIRECTION) {
                handler.postDelayed(repeat, 115);
            }
        } else {
            stopRepeating();
        }
    }

    private void navigate(int code) {
        int direction = focusDirection(code);
        View current = window.getCurrentFocus();
        if (current == null) {
            focusFirst(direction);
            return;
        }
        long time = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(time, time, KeyEvent.ACTION_DOWN, code,
                0, 0, heldDevice, 0, 0, InputDevice.SOURCE_DPAD);
        boolean handled = super.dispatchKeyEvent(down);
        super.dispatchKeyEvent(KeyEvent.changeAction(down, KeyEvent.ACTION_UP));
        if (!handled) {
            View next = current.focusSearch(direction);
            if (next != null) {
                next.requestFocus(direction);
            }
        }
    }

    private boolean focusFirst(int direction) {
        ViewGroup root = (ViewGroup) window.getDecorView();
        View next = FocusFinder.getInstance().findNextFocus(root, null, direction);
        return next != null && next.requestFocusFromTouch();
    }

    private static int focusDirection(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: return View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return View.FOCUS_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return View.FOCUS_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return View.FOCUS_RIGHT;
            default: return 0;
        }
    }
}
