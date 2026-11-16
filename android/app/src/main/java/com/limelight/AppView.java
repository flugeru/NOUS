package com.limelight;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/** Compatibility entry point for existing pinned library shortcuts. */
public class AppView extends AppCompatActivity {
    public static final String HIDDEN_APPS_PREF_FILENAME = GamesFragment.HIDDEN_APPS_PREF_FILENAME;
    public static final String NAME_EXTRA = GamesFragment.NAME_EXTRA;
    public static final String UUID_EXTRA = GamesFragment.UUID_EXTRA;
    public static final String NEW_PAIR_EXTRA = GamesFragment.NEW_PAIR_EXTRA;
    public static final String SHOW_HIDDEN_APPS_EXTRA = GamesFragment.SHOW_HIDDEN_APPS_EXTRA;
    public static class AppObject {
        public final com.limelight.nvstream.http.NvApp app;
        public boolean isRunning;
        public boolean isHidden;
        public AppObject(com.limelight.nvstream.http.NvApp app) {
            if (app == null) throw new IllegalArgumentException("app must not be null");
            this.app = app;
        }
        @Override public String toString() { return app.getAppName(); }
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        startActivity(new Intent(this, PcView.class).putExtras(getIntent())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
}
