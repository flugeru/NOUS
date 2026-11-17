package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.ControllerDialogBuilder;
import com.limelight.ui.ControllerMenuActivity;
import com.limelight.utils.UiHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;

public final class VirtualControllerEditorActivity extends ControllerMenuActivity {
    private static final int MIN_SIZE_PERCENT = 25;
    private static final int MAX_SIZE_PERCENT = 250;

    private final Map<Integer, Rect> defaultSizes = new HashMap<>();
    private VirtualController controller;
    private VirtualControllerElement selected;
    private MaterialButton picker;
    private MaterialSwitch visibility;
    private SeekBar size;
    private TextView sizeValue;
    private TextView opacityValue;
    private int canvasWidth;
    private int canvasHeight;
    private int opacity;
    private boolean dirty;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiHelper.setLocale(this);
        setContentView(R.layout.activity_configure_virtual_controller);
        UiHelper.applyEdgeToEdgeInsets(this);

        picker = findViewById(R.id.nous_osc_pick);
        visibility = findViewById(R.id.nous_osc_visible);
        size = findViewById(R.id.nous_osc_size);
        sizeValue = findViewById(R.id.nous_osc_size_value);
        opacityValue = findViewById(R.id.nous_osc_opacity_value);
        opacity = state == null ? PreferenceConfiguration.readPreferences(this).oscOpacity
                : state.getInt("opacity");
        dirty = state != null && state.getBoolean("dirty");

        size.setMax(MAX_SIZE_PERCENT - MIN_SIZE_PERCENT);
        initPreview(state);
        picker.setOnClickListener(view -> pickControl());
        visibility.setOnClickListener(view -> {
            selected.enabled = visibility.isChecked();
            updatePreviewAppearance(selected);
            dirty = true;
        });

        listenToSlider(size, progress -> resizeSelected(MIN_SIZE_PERCENT + progress));

        SeekBar opacitySlider = findViewById(R.id.nous_osc_opacity);
        opacitySlider.setProgress(opacity);
        showOpacity();
        listenToSlider(opacitySlider, progress -> {
            opacity = progress;
            showOpacity();
            dirty = true;
        });

        findViewById(R.id.nous_osc_reset).setOnClickListener(view -> {
            populate(false, null, selected.elementId);
            dirty = true;
        });
        findViewById(R.id.nous_osc_save).setOnClickListener(view -> {
            if (dirty) {
                VirtualControllerConfigurationLoader.saveProfile(controller, this);
                PreferenceManager.getDefaultSharedPreferences(this).edit()
                        .putInt(PreferenceConfiguration.OSC_OPACITY_PREF_STRING, opacity).apply();
                Toast.makeText(this, R.string.nous_osc_saved, Toast.LENGTH_SHORT).show();
            }
            finish();
        });
        findViewById(R.id.nous_back).setOnClickListener(view -> leaveEditor());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                leaveEditor();
            }
        });
    }

    private void initPreview(Bundle state) {
        int width;
        int height;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect display = getWindowManager().getMaximumWindowMetrics().getBounds();
            width = display.width();
            height = display.height();
        } else {
            DisplayMetrics display = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getRealMetrics(display);
            width = display.widthPixels;
            height = display.heightPixels;
        }
        canvasWidth = Math.max(width, height);
        canvasHeight = Math.min(width, height);

        FrameLayout preview = findViewById(R.id.nous_osc_preview);
        Configuration config = getResources().getConfiguration();
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE && config.screenWidthDp >= 600) {
            LinearLayout body = findViewById(R.id.nous_osc_body);
            body.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(0, -1, 1);
            previewParams.setMarginEnd(dp(16));
            preview.setLayoutParams(previewParams);
            findViewById(R.id.nous_osc_inspector).setLayoutParams(new LinearLayout.LayoutParams(dp(256), -1));
        }

        ControllerPreview surface = new ControllerPreview(this);
        preview.addView(surface, new FrameLayout.LayoutParams(-1, -1));
        controller = new VirtualController(surface.canvas, this, canvasWidth, canvasHeight);
        Bundle draft = state == null ? null : state.getBundle("layout");
        populate(draft == null, draft, state == null ? 0 : state.getInt("selected"));
    }

    private void populate(boolean loadSaved, Bundle draft, int selectedId) {
        controller.removeElements();
        selected = null;
        defaultSizes.clear();
        VirtualControllerConfigurationLoader.createDefaultLayout(controller, this);
        for (VirtualControllerElement element : controller.getElements()) {
            FrameLayout.LayoutParams params = params(element);
            defaultSizes.put(element.elementId, new Rect(0, 0, params.width, params.height));
        }
        if (loadSaved) {
            VirtualControllerConfigurationLoader.loadFromPreferences(controller, this);
        }
        if (draft != null) {
            for (VirtualControllerElement element : controller.getElements()) {
                String config = draft.getString(Integer.toString(element.elementId));
                if (config != null) {
                    try {
                        element.loadConfiguration(new JSONObject(config));
                    } catch (JSONException ignored) {
                        // Keep the default for a malformed draft entry.
                    }
                }
            }
        }
        controller.setOpacity(opacity);
        for (VirtualControllerElement element : controller.getElements()) {
            FrameLayout.LayoutParams params = params(element);
            params.width = Math.max(1, Math.min(canvasWidth, params.width));
            params.height = Math.max(1, Math.min(canvasHeight, params.height));
            place(element, params.leftMargin, params.topMargin);
            element.setVisibility(View.VISIBLE);
            element.setContentDescription(label(element));
            element.setOnClickListener(view -> select(element));
            element.setOnTouchListener(new ControlDrag(element));
            updatePreviewAppearance(element);
        }
        VirtualControllerElement first = controller.getElements().get(0);
        for (VirtualControllerElement element : controller.getElements()) {
            if (element.elementId == selectedId) {
                first = element;
                break;
            }
        }
        select(first);
    }

    private static void listenToSlider(SeekBar slider, IntConsumer update) {
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    update.accept(progress);
                }
            }

            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
    }

    private String label(VirtualControllerElement element) {
        switch (element.elementId) {
            case VirtualControllerElement.EID_DPAD: return getString(R.string.nous_osc_dpad);
            case VirtualControllerElement.EID_LS: return getString(R.string.nous_osc_left_stick);
            case VirtualControllerElement.EID_RS: return getString(R.string.nous_osc_right_stick);
            case VirtualControllerElement.EID_BACK: return getString(R.string.nous_osc_back_button);
            case VirtualControllerElement.EID_START: return getString(R.string.nous_osc_start_button);
            case VirtualControllerElement.EID_GDB: return getString(R.string.nous_osc_guide_button);
            case VirtualControllerElement.EID_TOUCHPAD: return getString(R.string.nous_osc_touchpad);
            default: return ((DigitalButton) element).getLabel();
        }
    }

    private void pickControl() {
        CharSequence[] labels = controller.getElements().stream().map(this::label).toArray(CharSequence[]::new);
        new ControllerDialogBuilder(this)
                .setTitle(R.string.nous_osc_pick_title)
                .setItems(labels, (dialog, index) -> select(controller.getElements().get(index)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void select(VirtualControllerElement element) {
        VirtualControllerElement previous = selected;
        selected = element;
        if (previous != null && previous != element) {
            updatePreviewAppearance(previous);
        }
        updatePreviewAppearance(element);
        picker.setText(getString(R.string.nous_osc_pick, label(element)));
        visibility.setChecked(element.enabled);
        FrameLayout.LayoutParams params = params(element);
        Rect base = defaultSizes.get(element.elementId);
        int percent = Math.round(100f * Math.max(params.width, params.height) / Math.max(base.width(), base.height()));
        size.setProgress(percent - MIN_SIZE_PERCENT);
        sizeValue.setText(getString(R.string.nous_osc_size_format, percent));
    }

    private void resizeSelected(int percent) {
        FrameLayout.LayoutParams params = params(selected);
        Rect base = defaultSizes.get(selected.elementId);
        int longest = Math.max(params.width, params.height);
        int target = Math.max(1, Math.round(Math.max(base.width(), base.height()) * percent / 100f));
        float scale = Math.min((float) target / longest,
                Math.min((float) canvasWidth / params.width, (float) canvasHeight / params.height));
        int width = Math.max(1, Math.round(params.width * scale));
        int height = Math.max(1, Math.round(params.height * scale));
        int left = params.leftMargin + (params.width - width) / 2;
        int top = params.topMargin + (params.height - height) / 2;
        dirty |= params.width != width || params.height != height;
        params.width = width;
        params.height = height;
        place(selected, left, top);
        sizeValue.setText(getString(R.string.nous_osc_size_format,
                Math.round(100f * Math.max(width, height) / Math.max(base.width(), base.height()))));
    }

    private void showOpacity() {
        opacityValue.setText(getString(R.string.nous_osc_opacity_format, opacity));
        controller.setOpacity(opacity);
        for (VirtualControllerElement element : controller.getElements()) {
            updatePreviewAppearance(element);
        }
    }

    private void updatePreviewAppearance(VirtualControllerElement element) {
        element.setAlpha(element.enabled ? 1f : 0.35f);
        if (selected == element || !element.enabled || opacity < 20) {
            GradientDrawable outline = new GradientDrawable();
            outline.setCornerRadius(canvasHeight * 0.015f);
            outline.setColor(0x00000000);
            outline.setStroke(Math.max(2, canvasHeight / 160),
                    getColor(selected == element ? R.color.nous_secondary : R.color.nous_text_muted));
            element.setForeground(outline);
        } else {
            element.setForeground(null);
        }
        element.setSelected(selected == element);
    }

    private void place(VirtualControllerElement element, int left, int top) {
        FrameLayout.LayoutParams params = params(element);
        params.leftMargin = Math.max(0, Math.min(left, canvasWidth - params.width));
        params.topMargin = Math.max(0, Math.min(top, canvasHeight - params.height));
        element.setLayoutParams(params);
    }

    private static FrameLayout.LayoutParams params(VirtualControllerElement element) {
        return (FrameLayout.LayoutParams) element.getLayoutParams();
    }

    private void leaveEditor() {
        if (dirty) {
            new ControllerDialogBuilder(this)
                    .setTitle(R.string.nous_osc_discard)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.nous_osc_discard_action, (dialog, which) -> finish())
                    .show();
        } else {
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bundle draft = new Bundle();
        for (VirtualControllerElement element : controller.getElements()) {
            try {
                draft.putString(Integer.toString(element.elementId), element.getConfiguration().toString());
            } catch (JSONException e) {
                throw new IllegalStateException("Invalid controller layout", e);
            }
        }
        outState.putBundle("layout", draft);
        outState.putInt("opacity", opacity);
        outState.putBoolean("dirty", dirty);
        outState.putInt("selected", selected.elementId);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class ControlDrag implements View.OnTouchListener {
        private final VirtualControllerElement element;
        private float offsetX;
        private float offsetY;
        private int pointerId;

        ControlDrag(VirtualControllerElement element) {
            this.element = element;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pointerId = event.getPointerId(0);
                    offsetX = event.getX();
                    offsetY = event.getY();
                    view.performClick();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int pointer = event.findPointerIndex(pointerId);
                    if (pointer >= 0) {
                        FrameLayout.LayoutParams params = params(element);
                        int left = params.leftMargin;
                        int top = params.topMargin;
                        place(element, Math.round(element.getLeft() + event.getX(pointer) - offsetX),
                                Math.round(element.getTop() + event.getY(pointer) - offsetY));
                        dirty |= left != params.leftMargin || top != params.topMargin;
                    }
                    return true;
                default:
                    return true;
            }
        }
    }

    private final class ControllerPreview extends ViewGroup {
        private final FrameLayout canvas;
        private float scale;

        ControllerPreview(Context context) {
            super(context);
            canvas = new FrameLayout(context);
            GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{getColor(R.color.nous_secondary_container), getColor(R.color.nous_surface)});
            background.setCornerRadius(canvasHeight * 0.035f);
            background.setStroke(Math.max(2, canvasHeight / 180), getColor(R.color.nous_outline));
            canvas.setBackground(background);
            canvas.setClipToOutline(true);
            canvas.setPivotX(0);
            canvas.setPivotY(0);
            addView(canvas, new LayoutParams(canvasWidth, canvasHeight));
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int preferredHeight = Math.round((float) width * canvasHeight / canvasWidth);
            int height = resolveSize(preferredHeight, heightSpec);
            scale = Math.min((float) width / canvasWidth, (float) height / canvasHeight);
            canvas.measure(MeasureSpec.makeMeasureSpec(canvasWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(canvasHeight, MeasureSpec.EXACTLY));
            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int x = Math.round((getWidth() - canvasWidth * scale) / 2);
            int y = Math.round((getHeight() - canvasHeight * scale) / 2);
            canvas.setScaleX(scale);
            canvas.setScaleY(scale);
            canvas.layout(x, y, x + canvasWidth, y + canvasHeight);
        }
    }
}
