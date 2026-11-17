/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.limelight.Game;

import org.json.JSONException;
import org.json.JSONObject;

public abstract class VirtualControllerElement extends View {
    protected static boolean _PRINT_DEBUG_INFORMATION = false;

    public static final int EID_DPAD = 1;
    public static final int EID_LT = 2;
    public static final int EID_RT = 3;
    public static final int EID_LB = 4;
    public static final int EID_RB = 5;
    public static final int EID_A = 6;
    public static final int EID_B = 7;
    public static final int EID_X = 8;
    public static final int EID_Y = 9;
    public static final int EID_BACK = 10;
    public static final int EID_START = 11;
    public static final int EID_LS = 12;
    public static final int EID_RS = 13;
    public static final int EID_LSB = 14;
    public static final int EID_RSB = 15;
    public static final int EID_GDB = 16;
    public static final int EID_TOUCHPAD = 65;

    protected VirtualController virtualController;
    protected final int elementId;

    protected final VirtualControllerSkin skin = new VirtualControllerSkin();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    protected int normalColor = 0xF0888888;
    protected int pressedColor = 0xF07272ED;
    private static final int EDIT_OUTLINE = 0xB883DBE4;
    protected int startSize_x;
    protected int startSize_y;

    float position_pressed_x = 0;
    float position_pressed_y = 0;

    public boolean enabled = true;
    int opacityPercent = 100;

    private enum Mode {
        Normal,
        Resize,
        Move
    }

    private Mode currentMode = Mode.Normal;

    protected VirtualControllerElement(VirtualController controller, Context context, int elementId) {
        super(context);

        this.virtualController = controller;
        this.elementId = elementId;
    }

    protected void moveElement(int pressed_x, int pressed_y, int x, int y) {
        int newPos_x = (int) getX() + x - pressed_x;
        int newPos_y = (int) getY() + y - pressed_y;

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        layoutParams.leftMargin = newPos_x > 0 ? newPos_x : 0;
        layoutParams.topMargin = newPos_y > 0 ? newPos_y : 0;
        layoutParams.rightMargin = 0;
        layoutParams.bottomMargin = 0;

        requestLayout();
    }

    protected void resizeElement(int pressed_x, int pressed_y, int width, int height) {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        int newHeight = height + (startSize_y - pressed_y);
        int newWidth = width + (startSize_x - pressed_x);

        layoutParams.height = newHeight > 20 ? newHeight : 20;
        layoutParams.width = newWidth > 20 ? newWidth : 20;

        requestLayout();
    }

    protected  void actionDisableEnableButton(){
        enabled = !enabled;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        onElementDraw(canvas);

        VirtualController.ControllerMode editorMode = virtualController.getControllerMode();
        if (currentMode != Mode.Normal || editorMode != VirtualController.ControllerMode.Active) {
            paint.setColor(enabled ? EDIT_OUTLINE : 0x60F2F2F4);
            paint.setStrokeWidth(Math.max(1, getCorrectWidth() * 0.012f));
            paint.setStyle(Paint.Style.STROKE);

            float inset = paint.getStrokeWidth();
            canvas.drawRoundRect(inset, inset, getWidth() - inset, getHeight() - inset,
                    inset * 4, inset * 4, paint);
        }

        super.onDraw(canvas);
    }

    /*
    protected void actionShowNormalColorChooser() {
        AmbilWarnaDialog colorDialog = new AmbilWarnaDialog(getContext(), normalColor, true, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onCancel(AmbilWarnaDialog dialog)
            {}

            @Override
            public void onOk(AmbilWarnaDialog dialog, int color) {
                normalColor = color;
                invalidate();
            }
        });
        colorDialog.show();
    }

    protected void actionShowPressedColorChooser() {
        AmbilWarnaDialog colorDialog = new AmbilWarnaDialog(getContext(), normalColor, true, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onCancel(AmbilWarnaDialog dialog) {
            }

            @Override
            public void onOk(AmbilWarnaDialog dialog, int color) {
                pressedColor = color;
                invalidate();
            }
        });
        colorDialog.show();
    }
    */

    protected void actionEnableMove() {
        currentMode = Mode.Move;
    }

    protected void actionEnableResize() {
        currentMode = Mode.Resize;
    }

    protected void actionCancel() {
        currentMode = Mode.Normal;
        invalidate();
    }

    protected int getDefaultStrokeWidth() {
        return (int) (virtualController.layoutHeight() * 0.004f);
    }

    protected void showConfigurationDialog() {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getContext());

        alertBuilder.setTitle(com.limelight.R.string.nous_configuration);

        CharSequence functions[] = new CharSequence[]{
                getContext().getString(com.limelight.R.string.nous_move),
                getContext().getString(com.limelight.R.string.nous_resize),
            /*election
            "Set n
            Disable color sormal color",
            "Set pressed color",
            */
                getContext().getString(com.limelight.R.string.cancel)
        };

        alertBuilder.setItems(functions, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0: { // move
                        actionEnableMove();
                        break;
                    }
                    case 1: { // resize
                        actionEnableResize();
                        break;
                    }
                /*
                case 2: { // set default color
                    actionShowNormalColorChooser();
                    break;
                }
                case 3: { // set pressed color
                    actionShowPressedColorChooser();
                    break;
                }
                */
                    default: { // cancel
                        actionCancel();
                        break;
                    }
                }
            }
        });
        AlertDialog alert = alertBuilder.create();
        // show menu
        alert.show();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Ignore secondary touches on controls
        //
        // NB: We can get an additional pointer down if the user touches a non-StreamView area
        // while also touching an OSC control, even if that pointer down doesn't correspond to
        // an area of the OSC control.
        if (event.getActionIndex() != 0) {
            return true;
        }

        if (virtualController.getControllerMode() == VirtualController.ControllerMode.Active) {
            return onElementTouchEvent(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                position_pressed_x = event.getX();
                position_pressed_y = event.getY();
                startSize_x = getWidth();
                startSize_y = getHeight();

                if (virtualController.getControllerMode() == VirtualController.ControllerMode.MoveButtons)
                    actionEnableMove();
                else if (virtualController.getControllerMode() == VirtualController.ControllerMode.ResizeButtons)
                    actionEnableResize();
                else if (virtualController.getControllerMode() == VirtualController.ControllerMode.DisableEnableButtons)
                    actionDisableEnableButton();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                switch (currentMode) {
                    case Move: {
                        moveElement(
                                (int) position_pressed_x,
                                (int) position_pressed_y,
                                (int) event.getX(),
                                (int) event.getY());
                        break;
                    }
                    case Resize: {
                        resizeElement(
                                (int) position_pressed_x,
                                (int) position_pressed_y,
                                (int) event.getX(),
                                (int) event.getY());
                        break;
                    }
                    case Normal: {
                        break;
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                actionCancel();
                return true;
            }
            default: {
            }
        }
        return true;
    }

    abstract protected void onElementDraw(Canvas canvas);

    abstract public boolean onElementTouchEvent(MotionEvent event);

    protected static final void _DBG(String text) {
        if (_PRINT_DEBUG_INFORMATION) {
//            System.out.println(text);
        }
    }

    public void setColors(int normalColor, int pressedColor) {
        this.normalColor = normalColor;
        this.pressedColor = pressedColor;

        invalidate();
    }


    public void setOpacity(int opacity) {
        int hexOpacity = opacity * opacityPercent * 255 / 10000;
        this.normalColor = (hexOpacity << 24) | (normalColor & 0x00FFFFFF);
        this.pressedColor = (hexOpacity << 24) | (pressedColor & 0x00FFFFFF);

        invalidate();
    }

    protected final float getPercent(float value, float percent) {
        return value / 100 * percent;
    }

    protected final int getCorrectWidth() {
        return getWidth() > getHeight() ? getHeight() : getWidth();
    }


    public JSONObject getConfiguration() throws JSONException {
        JSONObject configuration = new JSONObject();

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        configuration.put("LEFT", layoutParams.leftMargin);
        configuration.put("TOP", layoutParams.topMargin);
        configuration.put("WIDTH", layoutParams.width);
        configuration.put("HEIGHT", layoutParams.height);
        configuration.put("ENABLED", enabled);
        configuration.put("OPACITY", opacityPercent);
        configuration.put("CANVAS_WIDTH", virtualController.layoutWidth());
        configuration.put("CANVAS_HEIGHT", virtualController.layoutHeight());
        return configuration;
    }

    public void loadConfiguration(JSONObject configuration) throws JSONException {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        int left = configuration.getInt("LEFT");
        int top = configuration.getInt("TOP");
        int width = configuration.getInt("WIDTH");
        int height = configuration.getInt("HEIGHT");
        enabled = configuration.getBoolean("ENABLED");
        opacityPercent = Math.max(0, Math.min(100, configuration.optInt("OPACITY", 100)));

        int savedWidth = configuration.optInt("CANVAS_WIDTH");
        int savedHeight = configuration.optInt("CANVAS_HEIGHT");
        int canvasWidth = virtualController.layoutWidth();
        int canvasHeight = virtualController.layoutHeight();
        if (savedWidth > 0 && savedHeight > 0 && canvasWidth > 0 && canvasHeight > 0
                && (savedWidth != canvasWidth || savedHeight != canvasHeight)) {
            float scale = (float) Math.min(canvasWidth, canvasHeight) / Math.min(savedWidth, savedHeight);
            int scaledWidth = Math.max(1, Math.min(canvasWidth, Math.round(width * scale)));
            int scaledHeight = Math.max(1, Math.min(canvasHeight, Math.round(height * scale)));
            left = Math.round((left + width / 2f) / savedWidth * canvasWidth - scaledWidth / 2f);
            top = Math.round((top + height / 2f) / savedHeight * canvasHeight - scaledHeight / 2f);
            width = scaledWidth;
            height = scaledHeight;
            left = Math.max(0, Math.min(left, canvasWidth - width));
            top = Math.max(0, Math.min(top, canvasHeight - height));
        }
        layoutParams.leftMargin = left;
        layoutParams.topMargin = top;
        layoutParams.width = width;
        layoutParams.height = height;
        setVisibility(enabled ? VISIBLE: GONE);
        requestLayout();
    }
}
