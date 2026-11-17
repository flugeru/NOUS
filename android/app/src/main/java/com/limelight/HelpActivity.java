package com.limelight;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.appcompat.app.AppCompatActivity;

import com.limelight.utils.SpinnerDialog;
import com.limelight.utils.UiHelper;

public class HelpActivity extends AppCompatActivity {

    private SpinnerDialog loadingDialog;
    private WebView webView;

    private boolean backCallbackRegistered;
    private OnBackInvokedCallback onBackInvokedCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getIntent().getData() == null) {
            showHelpHome();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedCallback = new OnBackInvokedCallback() {
                @Override
                public void onBackInvoked() {
                    // We should always be able to go back because we unregister our callback
                    // when we can't go back. Nonetheless, we will still check anyway.
                    if (webView.canGoBack()) {
                        webView.goBack();
                    }
                }
            };
        }

        setContentView(R.layout.activity_nous_help);
        FrameLayout content = findViewById(R.id.nous_help_content);
        webView = new WebView(this);
        findViewById(R.id.nous_back).setOnClickListener(v -> {
            if (webView.canGoBack()) {
                webView.goBack();
            } else {
                finish();
            }
        });
        content.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        UiHelper.notifyNewRootView(this);

        // These allow the user to zoom the page
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);

        // This sets the view to display the whole page by default
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);

        // This allows the links to places on the same page to work
        webView.getSettings().setJavaScriptEnabled(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (loadingDialog == null) {
                    loadingDialog = SpinnerDialog.displayDialog(HelpActivity.this,
                            getResources().getString(R.string.help_loading_title),
                            getResources().getString(R.string.help_loading_msg), false);
                }

                refreshBackDispatchState();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (loadingDialog != null) {
                    loadingDialog.dismiss();
                    loadingDialog = null;
                }

                refreshBackDispatchState();
            }
        });

        webView.loadUrl(getIntent().getData().toString());
    }

    private void showHelpHome() {
        setContentView(R.layout.activity_nous_help);
        findViewById(R.id.nous_back).setOnClickListener(v -> finish());
        FrameLayout container = findViewById(R.id.nous_help_content);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        android.widget.LinearLayout body = new android.widget.LinearLayout(this);
        body.setOrientation(android.widget.LinearLayout.VERTICAL);
        int space = getResources().getDimensionPixelSize(R.dimen.nous_settings_inset);
        int logoSize = Math.round(56 * getResources().getDisplayMetrics().density);
        body.setPadding(space, space, space, space);
        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(R.drawable.nous_brand);
        body.addView(logo, new android.widget.LinearLayout.LayoutParams(logoSize, logoSize));
        android.widget.TextView brand = new android.widget.TextView(this);
        brand.setText("NOUS\nAndroid · " + BuildConfig.VERSION_NAME);
        brand.setTextAppearance(R.style.Nous_Title);
        body.addView(brand);
        addHelpCard(body, R.string.nous_help_pair, R.drawable.ic_pairing, () -> com.limelight.utils.HelpLauncher.launchUrl(this,
                "https://github.com/moonlight-stream/moonlight-docs/wiki/Setup-Guide"));
        addHelpCard(body, R.string.nous_help_connection, R.drawable.ic_connection_help, () -> com.limelight.utils.HelpLauncher.launchTroubleshooting(this));
        addHelpCard(body, R.string.nous_diagnostics, R.drawable.ic_device_diagnostics, () -> startActivity(new android.content.Intent(this, DebugInfoActivity.class)));
        addHelpCard(body, R.string.nous_credits, R.drawable.ic_credits, () -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.nous_credits).setMessage(R.string.nous_credits_text)
                .setPositiveButton(android.R.string.ok, null).show());
        addHelpCard(body, R.string.title_software_update, R.drawable.ic_versions, () -> com.limelight.utils.HelpLauncher.launchUrl(this,
                "https://github.com/flugeru/NOUS/releases"));
        scroll.addView(body);
        container.addView(scroll);
        UiHelper.notifyNewRootView(this);
    }

    private void addHelpCard(android.widget.LinearLayout body, int label, int icon, Runnable action) {
        com.google.android.material.button.MaterialButton card = new com.google.android.material.button.MaterialButton(this);
        card.setText(label);
        card.setIconResource(icon);
        card.setIconTint(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this, R.color.nous_secondary)));
        card.setMaxLines(3);
        card.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        card.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(this, R.color.nous_surface)));
        card.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.nous_text));
        card.setCornerRadius(Math.round(14 * getResources().getDisplayMetrics().density));
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(-1, -2);
        params.topMargin = getResources().getDimensionPixelSize(R.dimen.nous_settings_row_gap);
        card.setMinHeight(Math.round(56 * getResources().getDisplayMetrics().density));
        body.addView(card, params);
        card.setOnClickListener(v -> action.run());
    }

    private void refreshBackDispatchState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (webView.canGoBack() && !backCallbackRegistered) {
                getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_DEFAULT, onBackInvokedCallback);
                backCallbackRegistered = true;
            }
            else if (!webView.canGoBack() && backCallbackRegistered) {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(onBackInvokedCallback);
                backCallbackRegistered = false;
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (backCallbackRegistered) {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(onBackInvokedCallback);
            }
        }

        super.onDestroy();
    }

    @Override
    // NOTE: This will NOT be called on Android 13+ with android:enableOnBackInvokedCallback="true"
    public void onBackPressed() {
        // Back goes back through the WebView history
        // until no more history remains
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        }
        else {
            super.onBackPressed();
        }
    }
}
