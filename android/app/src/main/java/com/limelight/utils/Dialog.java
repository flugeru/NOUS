package com.limelight.utils;

import java.util.ArrayList;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import com.limelight.ui.ControllerDialogBuilder;
import android.content.DialogInterface;
import android.widget.Button;
import android.widget.TextView;
import android.view.View;

import com.limelight.R;

public class Dialog implements Runnable {
    private final String title;
    private final String message;
    private final Activity activity;
    private final Runnable runOnDismiss;

    private String pin;
    private AlertDialog alert;

    private static final ArrayList<Dialog> rundownDialogs = new ArrayList<>();

    private Dialog(Activity activity, String title, String message, Runnable runOnDismiss)
    {
        this.activity = activity;
        this.title = title;
        this.message = message;
        this.runOnDismiss = runOnDismiss;
    }

    public static void closeDialogs()
    {
        synchronized (rundownDialogs) {
            for (Dialog d : rundownDialogs) {
                if (d.alert.isShowing()) {
                    d.alert.dismiss();
                }
            }

            rundownDialogs.clear();
        }
    }

    public static void displayDialog(final Activity activity, String title, String message, final boolean endAfterDismiss)
    {
        activity.runOnUiThread(new Dialog(activity, title, message, new Runnable() {
            @Override
            public void run() {
                if (endAfterDismiss) {
                    activity.finish();
                }
            }
        }));
    }

    public static void displayDialog(Activity activity, String title, String message, Runnable runOnDismiss)
    {
        activity.runOnUiThread(new Dialog(activity, title, message, runOnDismiss));
    }

    public static void displayPairingPin(Activity activity, String hostName, String pin) {
        Dialog dialog = new Dialog(activity, activity.getString(R.string.pair_pairing_title), hostName, () -> {});
        dialog.pin = pin;
        activity.runOnUiThread(dialog);
    }

    @Override
    public void run() {
        // If we're dying, don't bother creating a dialog
        if (activity.isFinishing())
            return;

        AlertDialog.Builder builder = new ControllerDialogBuilder(activity).setTitle(title);
        if (pin != null && pin.length() == 4) {
            View content = activity.getLayoutInflater().inflate(R.layout.nous_pairing_pin, null);
            ((TextView) content.findViewById(R.id.nous_pair_host)).setText(message);
            int[] digitViews = {R.id.nous_pin_0, R.id.nous_pin_1, R.id.nous_pin_2, R.id.nous_pin_3};
            for (int i = 0; i < digitViews.length; i++) {
                ((TextView) content.findViewById(digitViews[i])).setText(pin.substring(i, i + 1));
            }
            builder.setView(content);
        } else {
            builder.setMessage(pin == null ? message : message + "\n\n" + pin);
        }
        alert = builder.create();
        alert.setCancelable(false);
        alert.setCanceledOnTouchOutside(false);
 
        alert.setButton(AlertDialog.BUTTON_POSITIVE, activity.getResources().getText(android.R.string.ok), new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                  synchronized (rundownDialogs) {
                      rundownDialogs.remove(Dialog.this);
                      alert.dismiss();
                  }

                  runOnDismiss.run();
              }
        });
        alert.setButton(AlertDialog.BUTTON_NEUTRAL, activity.getResources().getText(R.string.help), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                synchronized (rundownDialogs) {
                    rundownDialogs.remove(Dialog.this);
                    alert.dismiss();
                }

                runOnDismiss.run();

                HelpLauncher.launchTroubleshooting(activity);
            }
        });
        alert.setOnShowListener(new DialogInterface.OnShowListener(){

            @Override
            public void onShow(DialogInterface dialog) {
                // Set focus to the OK button by default
                Button button = alert.getButton(AlertDialog.BUTTON_POSITIVE);
                button.setFocusable(true);
                button.setFocusableInTouchMode(true);
                button.requestFocus();
            }
        });

        synchronized (rundownDialogs) {
            rundownDialogs.add(this);
            alert.show();
        }
    }

}
