package com.limelight.binding.input.virtual_controller;

import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import static com.limelight.binding.input.virtual_controller.VirtualControllerElement.*;

final class VirtualControllerSkin {
    private static final int SURFACE = 0x14181E;
    private static final int TEXT = 0xF2F2F4;
    private static final int ACCENT = 0x83DBE4;
    private static final int[] DPAD_DIRECTIONS = {DigitalPad.DIGITAL_PAD_DIRECTION_UP,
            DigitalPad.DIGITAL_PAD_DIRECTION_RIGHT, DigitalPad.DIGITAL_PAD_DIRECTION_DOWN,
            DigitalPad.DIGITAL_PAD_DIRECTION_LEFT};
    private static final float[] CROSS = {
            .34f, .04f, .66f, .04f, .66f, .34f, .96f, .34f, .96f, .66f, .66f, .66f,
            .66f, .96f, .34f, .96f, .34f, .66f, .04f, .66f, .04f, .34f, .34f, .34f
    };
    private static final CornerPathEffect CROSS_CORNERS = new CornerPathEffect(.082f);
    private static final Typeface LABEL_FONT = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Path path = new Path();

    void drawButton(Canvas canvas, int width, int height, int id, String label, int opacity,
                    boolean pressed, boolean outlineStyle, boolean squared) {
        float diameter = Math.min(width, height);
        boolean circular = id >= EID_A && id <= EID_Y || id == EID_LSB || id == EID_RSB;
        float left = (circular ? (width - diameter) * .5f : 0) + diameter * .06f;
        float top = (circular ? (height - diameter) * .5f : 0) + diameter * .06f;
        bounds.set(left, top, width - left, height - top);
        float corner = diameter * (squared ? .16f : .5f);
        fill(pressed ? 0x29404A : SURFACE, opacity, pressed ? .54f : outlineStyle ? .08f : .34f);
        canvas.drawRoundRect(bounds, corner, corner, paint);
        outline(pressed, opacity, Math.max(.75f, diameter * .017f));
        canvas.drawRoundRect(bounds, corner, corner, paint);

        String symbol = label;
        if (id == EID_START) symbol = "+";
        if (id == EID_BACK) symbol = "−";
        if (id == EID_GDB) symbol = "NOUS";
        if (id == EID_TOUCHPAD) {
            fill(TEXT, opacity, .86f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1, diameter * .022f));
            float x = width / 2f;
            float y = height / 2f;
            canvas.drawRoundRect(x - diameter * .21f, y - diameter * .13f,
                    x + diameter * .21f, y + diameter * .13f, diameter * .045f, diameter * .045f, paint);
            canvas.drawLine(x - diameter * .06f, y + diameter * .07f,
                    x + diameter * .06f, y + diameter * .07f, paint);
            return;
        }
        fill(TEXT, opacity, .87f);
        paint.setTypeface(LABEL_FONT);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(diameter * (symbol.length() > 2 ? .27f : symbol.length() == 2 ? .36f : .47f));
        canvas.drawText(symbol, width / 2f, height / 2f - (paint.ascent() + paint.descent()) / 2f, paint);
    }

    void drawDpad(Canvas canvas, int width, int height, int direction, int opacity) {
        float side = Math.min(width, height);
        if (side <= 0) return;
        int saved = canvas.save();
        canvas.translate((width - side) / 2f, (height - side) / 2f);
        canvas.scale(side, side);
        path.rewind();
        path.moveTo(CROSS[0], CROSS[1]);
        for (int i = 2; i < CROSS.length; i += 2) path.lineTo(CROSS[i], CROSS[i + 1]);
        path.close();

        paint.setPathEffect(CROSS_CORNERS);
        fill(SURFACE, opacity, .34f);
        canvas.drawPath(path, paint);
        outline(false, opacity, .012f);
        canvas.drawPath(path, paint);
        paint.setPathEffect(null);

        canvas.translate(.5f, .5f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        path.rewind();
        path.moveTo(-.047f, -.237f);
        path.lineTo(0, -.29f);
        path.lineTo(.047f, -.237f);
        for (int mask : DPAD_DIRECTIONS) {
            boolean pressed = (direction & mask) != 0;
            if (pressed) {
                fill(ACCENT, opacity, .20f);
                canvas.drawCircle(0, -.26f, .108f, paint);
            }
            fill(pressed ? ACCENT : TEXT, opacity, .87f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(.023f);
            canvas.drawPath(path, paint);
            canvas.rotate(90);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStrokeJoin(Paint.Join.MITER);
        canvas.restoreToCount(saved);
    }

    void drawStick(Canvas canvas, float x, float y, float knobX, float knobY,
                   float radius, int opacity, boolean active) {
        if (radius <= 0) return;
        float line = Math.max(.75f, radius * .025f);
        fill(SURFACE, opacity, .31f);
        canvas.drawCircle(x, y, radius, paint);
        outline(active, opacity, line);
        canvas.drawCircle(x, y, radius, paint);

        float knob = radius * .43f;
        float offsetX = knobX - x;
        float offsetY = knobY - y;
        float distance = (float) Math.hypot(offsetX, offsetY);
        float travel = Math.max(0, radius - knob - line * 2);
        if (distance > travel && distance > 0) {
            knobX = x + offsetX * travel / distance;
            knobY = y + offsetY * travel / distance;
        }
        fill(active ? 0x354E58 : 0x272D36, opacity, active ? .65f : .52f);
        canvas.drawCircle(knobX, knobY, knob, paint);
        outline(active, opacity, line);
        canvas.drawCircle(knobX, knobY, knob, paint);
        fill(TEXT, opacity, .25f);
        canvas.drawCircle(knobX, knobY, radius * .045f, paint);
    }

    private void fill(int rgb, int opacity, float alpha) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(rgb | (Math.round(opacity * alpha) << 24));
    }

    private void outline(boolean active, int opacity, float width) {
        fill(active ? ACCENT : TEXT, opacity, active ? .60f : .37f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
    }
}
