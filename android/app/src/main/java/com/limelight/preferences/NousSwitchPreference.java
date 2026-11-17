package com.limelight.preferences;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.CheckBoxPreference;

public class NousSwitchPreference extends CheckBoxPreference {
    public NousSwitchPreference(@NonNull Context context, @Nullable AttributeSet attrs,
                                int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public NousSwitchPreference(@NonNull Context context, @Nullable AttributeSet attrs,
                                int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public NousSwitchPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public NousSwitchPreference(@NonNull Context context) {
        super(context);
    }

    @Override
    @SuppressLint("RestrictedApi")
    protected void performClick(@NonNull View view) {
        super.performClick(view);
        // Start the thumb animation on the attached widget before RecyclerView rebinds the row.
        View widget = view.findViewById(android.R.id.checkbox);
        if (widget instanceof CompoundButton) {
            CompoundButton toggle = (CompoundButton) widget;
            if (toggle.isChecked() != isChecked()) {
                // The row already persisted the change and ran the preference listener.
                toggle.setOnCheckedChangeListener(null);
                toggle.setChecked(isChecked());
            }
        }
    }
}
