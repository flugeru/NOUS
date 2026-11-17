package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;

public final class VirtualControllerEditorActivity extends ControllerMenuActivity {
    private static final int MIN_SIZE_PERCENT = 25;
    private static final int MAX_SIZE_PERCENT = 250;

    private enum Tool {
        MOVE(R.id.nous_osc_tool_move),
        SIZE(R.id.nous_osc_tool_size),
        OPACITY(R.id.nous_osc_tool_opacity),
        VISIBLE(R.id.nous_osc_tool_visible),
        LAYOUT(R.id.nous_osc_tool_layout),
        TEST(R.id.nous_osc_tool_test);

        final int id;

        Tool(int id) {
            this.id = id;
        }
    }

    private final ArrayList<Bundle> undo = new ArrayList<>();
    private final ArrayList<Bundle> redo = new ArrayList<>();
    private Tool tool = Tool.MOVE;
    private ControllerPreview surface;
    private SeekBar opacitySlider;
    private TextView panelTitle;
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
        opacitySlider = findViewById(R.id.nous_osc_opacity);
        panelTitle = findViewById(R.id.nous_osc_panel_title);
        opacity = state == null ? PreferenceConfiguration.readPreferences(this).oscOpacity
                : state.getInt("opacity");
        dirty = state != null && state.getBoolean("dirty");
        if (state != null) {
            tool = Tool.valueOf(state.getString("tool", Tool.MOVE.name()));
            restoreHistory(state, "undo", undo);
            restoreHistory(state, "redo", redo);
        }

        size.setMax(MAX_SIZE_PERCENT - MIN_SIZE_PERCENT);
        initPreview(state);
        picker.setOnClickListener(view -> pickControl());
        visibility.setOnClickListener(view -> {
            recordEdit();
            selected.enabled = visibility.isChecked();
            updatePreviewAppearance(selected);
        });
        listenToSlider(size, progress -> resizeSelected(MIN_SIZE_PERCENT + progress));
        listenToSlider(opacitySlider, progress -> {
            if (tool == Tool.LAYOUT) {
                opacity = progress;
            } else {
                selected.opacityPercent = progress;
            }
            showOpacity();
        });
        findViewById(R.id.nous_osc_reset).setOnClickListener(view -> {
            recordEdit();
            populate(false, null, selected.elementId);
        });
        findViewById(R.id.nous_osc_undo).setOnClickListener(view -> restoreEdit(undo, redo));
        findViewById(R.id.nous_osc_redo).setOnClickListener(view -> restoreEdit(redo, undo));
        for (Tool item : Tool.values()) {
            findViewById(item.id).setOnClickListener(view -> setTool(tool == item ? Tool.MOVE : item));
        }
        setTool(tool);
        updateHistoryActions();

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

        surface = new ControllerPreview(this);
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

    private void listenToSlider(SeekBar slider, IntConsumer update) {
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private boolean tracked;
            private boolean recorded;
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (!tracked || !recorded) {
                        recordEdit();
                        recorded = true;
                    }
                    update.accept(progress);
                }
            }

            @Override public void onStartTrackingTouch(SeekBar bar) {
                tracked = true;
                recorded = false;
            }

            @Override public void onStopTrackingTouch(SeekBar bar) {
                tracked = false;
            }
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
        if (tool != Tool.LAYOUT) panelTitle.setText(label(element));
        updateOpacityControls();
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
        updateOpacityControls();
        controller.setOpacity(opacity);
        for (VirtualControllerElement element : controller.getElements()) {
            updatePreviewAppearance(element);
        }
    }

    private void updateOpacityControls() {
        boolean global = tool == Tool.LAYOUT;
        int value = global ? opacity : selected.opacityPercent;
        opacitySlider.setProgress(value);
        opacityValue.setText(getString(global ? R.string.nous_osc_opacity_format
                : R.string.nous_osc_button_opacity_format, value));
    }

    private void setTool(Tool next) {
        tool = next;
        surface.canvas.setMotionEventSplittingEnabled(tool == Tool.TEST);
        for (Tool item : Tool.values()) {
            MaterialButton button = findViewById(item.id);
            boolean active = tool == item;
            button.setSelected(active);
            button.setTextColor(getColor(active ? R.color.nous_secondary : R.color.nous_text_muted));
            button.setBackgroundTintList(ColorStateList.valueOf(
                    active ? getColor(R.color.nous_secondary_container) : 0xFF121418));
        }
        boolean layout = tool == Tool.LAYOUT;
        boolean showSize = tool == Tool.SIZE;
        boolean showOpacity = layout || tool == Tool.OPACITY;
        findViewById(R.id.nous_osc_inspector).setVisibility(
                tool == Tool.MOVE || tool == Tool.TEST ? View.GONE : View.VISIBLE);
        panelTitle.setText(layout ? getString(R.string.nous_osc_tool_layout) : label(selected));
        picker.setVisibility(layout ? View.VISIBLE : View.GONE);
        findViewById(R.id.nous_osc_reset).setVisibility(layout ? View.VISIBLE : View.GONE);
        size.setVisibility(showSize ? View.VISIBLE : View.GONE);
        sizeValue.setVisibility(showSize ? View.VISIBLE : View.GONE);
        opacitySlider.setVisibility(showOpacity ? View.VISIBLE : View.GONE);
        opacityValue.setVisibility(showOpacity ? View.VISIBLE : View.GONE);
        visibility.setVisibility(tool == Tool.VISIBLE ? View.VISIBLE : View.GONE);
        showOpacity();
    }

    private void recordEdit() {
        if (undo.size() == 20) undo.remove(0);
        undo.add(snapshot());
        redo.clear();
        dirty = true;
        updateHistoryActions();
    }

    private Bundle snapshot() {
        Bundle state = new Bundle();
        Bundle layout = new Bundle();
        for (VirtualControllerElement element : controller.getElements()) {
            try {
                layout.putString(Integer.toString(element.elementId), element.getConfiguration().toString());
            } catch (JSONException e) {
                throw new IllegalStateException("Invalid controller layout", e);
            }
        }
        state.putBundle("layout", layout);
        state.putInt("opacity", opacity);
        state.putInt("selected", selected.elementId);
        return state;
    }

    private void updateHistoryActions() {
        View undoButton = findViewById(R.id.nous_osc_undo);
        View redoButton = findViewById(R.id.nous_osc_redo);
        undoButton.setEnabled(!undo.isEmpty());
        redoButton.setEnabled(!redo.isEmpty());
        undoButton.setAlpha(undo.isEmpty() ? .35f : 1f);
        redoButton.setAlpha(redo.isEmpty() ? .35f : 1f);
    }

    private void restoreEdit(ArrayList<Bundle> source, ArrayList<Bundle> destination) {
        if (source.isEmpty()) return;
        destination.add(snapshot());
        Bundle state = source.remove(source.size() - 1);
        opacity = state.getInt("opacity");
        populate(false, state.getBundle("layout"), state.getInt("selected"));
        setTool(tool);
        updateHistoryActions();
    }

    @SuppressWarnings("deprecation")
    private static void restoreHistory(Bundle state, String key, ArrayList<Bundle> history) {
        ArrayList<Bundle> saved = state.getParcelableArrayList(key);
        if (saved != null) history.addAll(saved);
    }

    private void updatePreviewAppearance(VirtualControllerElement element) {
        boolean testing = tool == Tool.TEST;
        element.setVisibility(testing && !element.enabled ? View.GONE : View.VISIBLE);
        element.setAlpha(element.enabled ? 1f : 0.35f);
        if (!testing && (selected == element || !element.enabled || opacity * element.opacityPercent < 2000)) {
            GradientDrawable outline = new GradientDrawable();
            outline.setCornerRadius(canvasHeight * 0.015f);
            outline.setColor(0x00000000);
            outline.setStroke(Math.max(2, canvasHeight / 160),
                    getColor(selected == element ? R.color.nous_secondary : R.color.nous_text_muted));
            element.setForeground(outline);
        } else {
            element.setForeground(null);
        }
        element.setSelected(!testing && selected == element);
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
        if (tool != Tool.MOVE) {
            setTool(Tool.MOVE);
            return;
        }
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
        outState.putAll(snapshot());
        outState.putBoolean("dirty", dirty);
        outState.putString("tool", tool.name());
        outState.putParcelableArrayList("undo", undo);
        outState.putParcelableArrayList("redo", redo);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class ControlDrag implements View.OnTouchListener {
        private final VirtualControllerElement element;
        private float offsetX;
        private float offsetY;
        private int pointerId;
        private boolean changed;
        private float pinchSpan;
        private int pinchSize;

        ControlDrag(VirtualControllerElement element) {
            this.element = element;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (tool == Tool.TEST) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    changed = false;
                    pinchSpan = 0;
                    pointerId = event.getPointerId(0);
                    offsetX = event.getX();
                    offsetY = event.getY();
                    view.performClick();
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() == 2) {
                        pinchSpan = span(event);
                        FrameLayout.LayoutParams params = params(element);
                        Rect base = defaultSizes.get(element.elementId);
                        pinchSize = Math.round(100f * Math.max(params.width, params.height)
                                / Math.max(base.width(), base.height()));
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (pinchSpan > 0) {
                        if (event.getPointerCount() >= 2) {
                            markChanged();
                            int percent = Math.round(pinchSize * span(event) / pinchSpan);
                            resizeSelected(Math.max(MIN_SIZE_PERCENT, Math.min(MAX_SIZE_PERCENT, percent)));
                            size.setProgress(percent - MIN_SIZE_PERCENT);
                        }
                        return true;
                    }
                    int pointer = event.findPointerIndex(pointerId);
                    if (pointer >= 0) {
                        FrameLayout.LayoutParams params = params(element);
                        int left = Math.round(element.getLeft() + event.getX(pointer) - offsetX);
                        int top = Math.round(element.getTop() + event.getY(pointer) - offsetY);
                        int guidedX = snap(left + params.width / 2, canvasWidth);
                        int guidedY = snap(top + params.height / 2, canvasHeight);
                        surface.showGuides(guidedX, guidedY);
                        if (guidedX >= 0) left = guidedX - params.width / 2;
                        if (guidedY >= 0) top = guidedY - params.height / 2;
                        if (left != params.leftMargin || top != params.topMargin) {
                            markChanged();
                            place(element, left, top);
                        }
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    surface.showGuides(-1, -1);
                    return true;
                default:
                    return true;
            }
        }

        private void markChanged() {
            if (!changed) {
                recordEdit();
                changed = true;
            }
        }

        private float span(MotionEvent event) {
            return (float) Math.hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0));
        }

        private int snap(int center, int limit) {
            int nearest = Math.round(center * 10f / limit) * limit / 10;
            return Math.abs(center - nearest) <= canvasHeight * .01f ? nearest : -1;
        }
    }

    private final class ControllerPreview extends ViewGroup {
        private final FrameLayout canvas;
        private float scale;
        private final Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int guideX = -1;
        private int guideY = -1;

        ControllerPreview(Context context) {
            super(context);
            canvas = new FrameLayout(context) {
                @Override
                protected void onDraw(Canvas localCanvas) {
                    super.onDraw(localCanvas);
                    if (guideX >= 0) localCanvas.drawLine(guideX, 0, guideX, canvasHeight, guide);
                    if (guideY >= 0) localCanvas.drawLine(0, guideY, canvasWidth, guideY, guide);
                }
            };
            canvas.setWillNotDraw(false);
            guide.setColor(0x6683DBE4);
            guide.setStrokeWidth(Math.max(1, canvasHeight / 450f));
            GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF171B21, 0xFF0B0C0F});
            background.setCornerRadius(canvasHeight * 0.035f);
            background.setStroke(Math.max(2, canvasHeight / 180), getColor(R.color.nous_outline));
            canvas.setBackground(background);
            canvas.setClipToOutline(true);
            canvas.setPivotX(0);
            canvas.setPivotY(0);
            addView(canvas, new LayoutParams(canvasWidth, canvasHeight));
        }

        void showGuides(int x, int y) {
            guideX = x;
            guideY = y;
            canvas.invalidate();
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
