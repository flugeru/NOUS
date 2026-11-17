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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.WindowCallbackWrapper;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/** Adapts controllers to Android focus navigation in local menus. */
final class ControllerNavigation extends WindowCallbackWrapper {
    private static final float ENGAGE = 0.55f;
    private static final float RELEASE = 0.35f;
    private static final int NO_DIRECTION = KeyEvent.KEYCODE_UNKNOWN;

    private static int lastControllerId = Integer.MIN_VALUE;

    private final Window window;
    private final Runnable goBack;
    private final ControllerDialogActions dialogActions;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable repeat = this::repeatDirection;
    private int heldDirection = NO_DIRECTION;
    private int heldDevice;
    private View confirmFocus;
    private boolean dispatchNativeConfirm;

    static void attach(Window window, Runnable goBack) {
        if (!(window.getCallback() instanceof ControllerNavigation)) {
            window.setCallback(new ControllerNavigation(window, goBack, null));
        }
    }

    static void attach(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null || window.getCallback() instanceof ControllerNavigation) {
            return;
        }
        if (dialog instanceof AlertDialog) {
            ControllerDialogActions actions = new ControllerDialogActions((AlertDialog) dialog);
            window.setCallback(new ControllerNavigation(window, actions::back, actions));
        } else {
            attach(window, dialog::onBackPressed);
        }
    }

    private ControllerNavigation(Window window, Runnable goBack, ControllerDialogActions dialogActions) {
        super(window.getCallback());
        this.window = window;
        this.goBack = goBack;
        this.dialogActions = dialogActions;
    }

    private static InputDevice connectedController() {
        InputDevice last = InputDevice.getDevice(lastControllerId);
        if (isController(last)) {
            return last;
        }
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (isController(device)) {
                return device;
            }
        }
        return null;
    }

    private static boolean isController(InputDevice device) {
        return device != null && !device.isVirtual() && (device.supportsSource(InputDevice.SOURCE_GAMEPAD)
                || device.supportsSource(InputDevice.SOURCE_JOYSTICK));
    }

    private void controllerUsed(InputDevice device, int keyCode) {
        if (isController(device)) {
            lastControllerId = device.getId();
        }
        if (dialogActions != null) {
            dialogActions.show(device, keyCode);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!event.isFromSource(InputDevice.SOURCE_GAMEPAD)
                && !event.isFromSource(InputDevice.SOURCE_DPAD)
                && !event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            return super.dispatchKeyEvent(event);
        }

        if (event.isFromSource(InputDevice.SOURCE_GAMEPAD)
                || event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            controllerUsed(event.getDevice(), event.getKeyCode());
        }
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_BUTTON_1:
            case KeyEvent.KEYCODE_BUTTON_A:
                return confirm(event);

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

    private boolean confirm(KeyEvent event) {
        View focus = window.getCurrentFocus();
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            clearConfirmFocus();
            dispatchNativeConfirm = false;
            if (focus == null) {
                focusFirst(View.FOCUS_DOWN);
                return true;
            }
            if (focus.isInTouchMode()) {
                focus.requestFocusFromTouch();
            }
            if (focus instanceof EditText) {
                WindowCompat.getInsetsController(window, focus).show(WindowInsetsCompat.Type.ime());
                return true;
            }
            if (focus instanceof AbsListView || focus.isClickable()) {
                confirmFocus = focus;
                if (!(focus instanceof AbsListView)) {
                    focus.setPressed(true);
                }
            } else {
                dispatchNativeConfirm = true;
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP && confirmFocus != null) {
            View target = confirmFocus;
            clearConfirmFocus();
            if (target == focus && target.isEnabled() && target.isShown() && !event.isCanceled()) {
                if (target instanceof AbsListView) {
                    AbsListView list = (AbsListView) target;
                    View row = list.getSelectedView();
                    if (row != null) {
                        list.performItemClick(row, list.getSelectedItemPosition(), list.getSelectedItemId());
                    }
                } else {
                    target.performClick();
                }
            }
        }

        if (!dispatchNativeConfirm) {
            return true;
        }
        KeyEvent nativeConfirm = new KeyEvent(event.getDownTime(), event.getEventTime(),
                event.getAction(), KeyEvent.KEYCODE_DPAD_CENTER, event.getRepeatCount(),
                event.getMetaState(), event.getDeviceId(), event.getScanCode(),
                event.getFlags(), event.getSource());
        return super.dispatchKeyEvent(nativeConfirm);
    }

    private void clearConfirmFocus() {
        if (confirmFocus != null) {
            confirmFocus.setPressed(false);
            confirmFocus = null;
        }
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
                controllerUsed(event.getDevice(), NO_DIRECTION);
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
            clearConfirmFocus();
            dispatchNativeConfirm = false;
        } else if (dialogActions != null) {
            InputDevice device = connectedController();
            if (device != null) {
                dialogActions.show(device, NO_DIRECTION);
            }
        }
        super.onWindowFocusChanged(hasFocus);
    }

    private void stopRepeating() {
        heldDirection = NO_DIRECTION;
        handler.removeCallbacks(repeat);
    }

    private void repeatDirection() {
        if (heldDirection != NO_DIRECTION && InputDevice.getDevice(heldDevice) != null
                && window.getDecorView().hasWindowFocus()) {
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
        if (current.isInTouchMode()) {
            current.requestFocusFromTouch();
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
        if (root.isInTouchMode()) {
            root.requestFocusFromTouch();
            View focused = root.findFocus();
            if (focused != null && focused != root) {
                return true;
            }
        }
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
