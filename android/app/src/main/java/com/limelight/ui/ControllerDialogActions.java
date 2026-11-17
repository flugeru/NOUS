package com.limelight.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;

import com.limelight.R;

import java.util.Locale;

/** Adds controller hints to the existing button bar without changing the dialog content. */
final class ControllerDialogActions {
    private enum Layout {
        XBOX("A", "B"), PLAYSTATION("×", "○"), NUMBERED("1", "2"), GENERIC("✓", "↩");

        final String confirm;
        final String back;

        Layout(String confirm, String back) {
            this.confirm = confirm;
            this.back = back;
        }
    }

    private final AlertDialog dialog;
    private Layout displayedLayout;

    ControllerDialogActions(AlertDialog dialog) {
        this.dialog = dialog;
    }

    void show(InputDevice device, int keyCode) {
        Layout layout = layoutFor(device, keyCode);
        if (displayedLayout == layout) {
            return;
        }
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (positive == null || negative == null || neutral == null) {
            return;
        }

        displayedLayout = layout;
        style(positive, layout.confirm, true);
        style(negative, layout.back, false);
        style(neutral, null, false);
    }

    void back() {
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negative != null && negative.isShown() && negative.isEnabled()) {
            negative.performClick();
        } else {
            dialog.onBackPressed();
        }
    }

    private static Layout layoutFor(InputDevice device, int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_1 || keyCode == KeyEvent.KEYCODE_BUTTON_2
                || keyCode == KeyEvent.KEYCODE_BUTTON_3) {
            return Layout.NUMBERED;
        }
        if (device != null) {
            String name = device.getName().toLowerCase(Locale.ROOT);
            if (device.getVendorId() == 0x054c || name.contains("dualsense")
                    || name.contains("dualshock") || name.contains("playstation")) {
                return Layout.PLAYSTATION;
            }
            if (device.getVendorId() == 0x045e || name.contains("xbox")) {
                return Layout.XBOX;
            }
            boolean[] keys = device.hasKeys(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_1);
            if (!keys[0] && keys[1]) {
                return Layout.NUMBERED;
            }
        }
        return Layout.GENERIC;
    }

    private static void style(Button button, String glyph, boolean confirm) {
        if (button.getVisibility() != View.VISIBLE) {
            return;
        }
        Context context = button.getContext();
        int accent = context.getColor(R.color.nous_secondary);
        int base = context.getColor(confirm ? R.color.nous_secondary_container : R.color.nous_surface_high);
        int text = context.getColor(R.color.nous_text);
        int muted = context.getColor(R.color.nous_text_muted);
        int onAccent = context.getColor(R.color.nous_on_secondary);
        float density = context.getResources().getDisplayMetrics().density;

        StateListDrawable container = new StateListDrawable();
        container.addState(new int[]{android.R.attr.state_focused},
                shape(18 * density, context.getColor(R.color.nous_secondary_container), Math.round(2 * density), accent));
        container.addState(new int[0], shape(18 * density, base, 0, 0));
        RippleDrawable background = new RippleDrawable(ColorStateList.valueOf(0x3383DBE4), container, null);
        ViewCompat.setBackgroundTintList(button, null);
        button.setBackground(background);
        button.setMinHeight(Math.round(52 * density));
        button.setMinimumHeight(Math.round(52 * density));
        button.setMinimumWidth(Math.round((glyph == null ? 64 : 108) * density));
        int padding = Math.round(12 * density);
        button.setPaddingRelative(padding, 0, padding, 0);
        button.setTextColor(new ColorStateList(
                new int[][]{new int[]{-android.R.attr.state_enabled}, new int[0]},
                new int[]{muted, text}));
        if (glyph != null) {
            int size = Math.round(26 * density);
            Drawable badge = new Badge(glyph, confirm ? accent : muted, confirm ? onAccent : text, confirm);
            badge.setBounds(0, 0, size, size);
            button.setCompoundDrawablePadding(Math.round(8 * density));
            button.setCompoundDrawablesRelative(badge, null, null, null);
        }
    }

    private static GradientDrawable shape(float radius, int fill, int width, int stroke) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(radius);
        background.setColor(fill);
        background.setStroke(width, stroke);
        return background;
    }

    private static final class Badge extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String glyph;
        private final int ringColor;
        private final int glyphColor;
        private final boolean filled;

        Badge(String glyph, int ringColor, int glyphColor, boolean filled) {
            this.glyph = glyph;
            this.ringColor = ringColor;
            this.glyphColor = glyphColor;
            this.filled = filled;
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            paint.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float diameter = Math.min(bounds.width(), bounds.height());
            float x = bounds.exactCenterX();
            float y = bounds.exactCenterY();
            paint.setColor(ringColor);
            paint.setStyle(filled ? Paint.Style.FILL : Paint.Style.STROKE);
            paint.setStrokeWidth(diameter / 25f);
            canvas.drawCircle(x, y, diameter * 0.46f, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(glyphColor);
            paint.setTextSize(diameter * 0.55f);
            canvas.drawText(glyph, x, y - (paint.ascent() + paint.descent()) / 2f, paint);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter colorFilter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
