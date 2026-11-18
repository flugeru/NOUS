package com.limelight.preferences;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;
import androidx.preference.EditTextPreferenceDialogFragmentCompat;
import androidx.preference.PreferenceDialogFragmentCompat;

import com.limelight.R;
import com.limelight.ui.ControllerDialogBuilder;

/** Uses the NOUS dialog shell while retaining AndroidX preference binding and persistence. */
public final class MaterialPreferenceDialogs {
    private MaterialPreferenceDialogs() {}

    private static ControllerDialogBuilder builder(PreferenceDialogFragmentCompat fragment) {
        DialogPreference preference = fragment.getPreference();
        ControllerDialogBuilder builder = new ControllerDialogBuilder(fragment.requireContext());
        builder.setTitle(preference.getDialogTitle())
                .setIcon(preference.getDialogIcon())
                .setPositiveButton(preference.getPositiveButtonText(), fragment)
                .setNegativeButton(preference.getNegativeButtonText(), fragment);
        return builder;
    }

    static Dialog confirmation(PreferenceDialogFragmentCompat fragment) {
        return builder(fragment).setMessage(fragment.getPreference().getDialogMessage()).create();
    }

    public static final class Text extends EditTextPreferenceDialogFragmentCompat {
        static Text forKey(String key) {
            Text fragment = new Text();
            Bundle args = new Bundle(1);
            args.putString(ARG_KEY, key);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        @NonNull
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            ControllerDialogBuilder builder = builder(this);
            View content = LayoutInflater.from(builder.getContext()).inflate(R.layout.nous_preference_edit_dialog, null);
            onBindDialogView(content);
            Dialog dialog = builder.setView(content).create();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                        | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            return dialog;
        }
    }
}
