package com.limelight.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

/** Base for local screens that use Android focus navigation. */
public abstract class ControllerMenuActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentStarted(FragmentManager manager, Fragment fragment) {
                        if (fragment instanceof DialogFragment) {
                            android.app.Dialog dialog = ((DialogFragment) fragment).getDialog();
                            if (dialog != null) {
                                ControllerNavigation.attach(dialog);
                            }
                        }
                    }
                }, true);
    }

    @Override
    protected void onPostCreate(Bundle state) {
        super.onPostCreate(state);
        ControllerNavigation.attach(getWindow(), this::onBackPressed);
    }
}
