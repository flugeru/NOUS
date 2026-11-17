package com.limelight.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Material dialogs with the same controller navigation as local screens. */
public final class ControllerDialogBuilder extends MaterialAlertDialogBuilder {
    public ControllerDialogBuilder(Context context) {
        super(context);
    }

    public void showActions(CharSequence title, Menu menu, IntConsumer select, Runnable dismissed) {
        List<MenuItem> items = new ArrayList<>();
        for (int i = 0; i < menu.size(); i++) {
            items.add(menu.getItem(i));
        }

        setTitle(title);
        setAdapter(new ArrayAdapter<MenuItem>(getContext(), 0, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                MenuItem item = items.get(position);
                int layout = item.isCheckable()
                        ? android.R.layout.simple_list_item_multiple_choice
                        : android.R.layout.simple_list_item_1;
                TextView row = (TextView) LayoutInflater.from(getContext()).inflate(layout, parent, false);
                row.setText(item.getTitle());
                if (row instanceof CheckedTextView) {
                    ((CheckedTextView) row).setChecked(item.isChecked());
                }
                return row;
            }
        }, (dialog, position) -> select.accept(items.get(position).getItemId()));
        AlertDialog dialog = create();
        if (dismissed != null) {
            dialog.setOnDismissListener(ignored -> dismissed.run());
        }
        dialog.show();
    }

    @Override
    public AlertDialog create() {
        AlertDialog dialog = super.create();
        ControllerNavigation.attach(dialog);
        return dialog;
    }
}
