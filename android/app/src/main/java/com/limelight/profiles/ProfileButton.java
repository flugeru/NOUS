package com.limelight.profiles;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;

public class ProfileButton extends LinearLayout {
    public ProfileButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void refresh() {
        String name = ProfilesManager.getInstance().getActiveName();
        TextView label = findViewById(R.id.nous_profile_button_label);
        label.setText(name);
        label.setVisibility(name.isEmpty() ? GONE : VISIBLE);
        setContentDescription(name.isEmpty()
                ? getContext().getText(R.string.profile_manager_choose_profile) : name);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return Button.class.getName();
    }
}
