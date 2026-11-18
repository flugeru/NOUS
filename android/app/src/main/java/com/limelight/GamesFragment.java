package com.limelight;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.List;

import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.grid.AppGridAdapter;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.AdapterFragment;
import com.limelight.ui.AdapterFragmentCallbacks;
import com.limelight.ui.ControllerDialogBuilder;
import com.limelight.utils.CacheHelper;
import com.limelight.utils.Dialog;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.ShortcutHelper;
import com.limelight.utils.SpinnerDialog;
import com.limelight.utils.UiHelper;
import com.limelight.profiles.ProfileButton;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;

import androidx.core.view.OneShotPreDrawListener;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import org.xmlpull.v1.XmlPullParserException;

public class GamesFragment extends android.app.Fragment implements AdapterFragmentCallbacks {
    private static final String SEARCH_EXPANDED_STATE = "nous.search_expanded";
    private static final String OFFLINE_DEMO_ARG = "nous.offline_demo";

    static Bundle offlineDemoArguments() {
        Bundle arguments = new Bundle();
        arguments.putBoolean(OFFLINE_DEMO_ARG, true);
        return arguments;
    }

    boolean isOfflineDemo() {
        Bundle arguments = getArguments();
        return arguments != null && arguments.getBoolean(OFFLINE_DEMO_ARG);
    }

    @Override
    public View onCreateView(android.view.LayoutInflater inflater, android.view.ViewGroup container, Bundle state) {
        View view = inflater.inflate(R.layout.activity_app_view, container, false);
        View navigation = view.findViewById(R.id.nous_navigation);
        ((android.view.ViewGroup) navigation.getParent()).removeView(navigation);
        view.findViewById(R.id.nous_main_tab).setId(View.NO_ID);
        view.findViewById(R.id.nous_tab_content).setId(View.NO_ID);
        view.findViewById(R.id.nous_library_header).setVisibility(View.GONE);
        return view;
    }

    private <T extends View> T findViewById(int id) { return getView().findViewById(id); }
    private void finish() { if (getActivity() != null) ((PcView) getActivity()).showComputers(); }
    private boolean isFinishing() { return getActivity() == null || getActivity().isFinishing() || isRemoving(); }
    private boolean isChangingConfigurations() { return getActivity() == null || getActivity().isChangingConfigurations(); }

    private AppGridAdapter appGridAdapter;
    private EditText gameSearch;
    private ValueAnimator librarySearchAnimation;
    private boolean librarySearchExpanded;
    private String uuidString;
    private ShortcutHelper shortcutHelper;

    private ComputerDetails computer;
    private ComputerManagerService.ApplistPoller poller;
    private SpinnerDialog blockingLoadSpinner;
    private String lastRawApplist;
    private int lastRunningAppId;
    private boolean suspendGridUpdates;
    private boolean inForeground;
    private boolean serviceBound;

    private void runOnUiThread(Runnable action) {
        Activity activity = getActivity();
        if (activity != null) activity.runOnUiThread(() -> {
            if (!isFinishing() && getView() != null) action.run();
        });
    }
    private boolean showHiddenApps;
    private HashSet<Integer> hiddenAppIds = new HashSet<>();

    private PreferenceConfiguration prefConfig;

    private final static int START_OR_RESUME_ID = 1;
    private final static int QUIT_ID = 2;
    private final static int START_WITH_QUIT = 4;
    private final static int VIEW_DETAILS_ID = 5;
    private final static int CREATE_SHORTCUT_ID = 6;
    private final static int EXPORT_LAUNCHER_FILE_ID = 7;
    private final static int HIDE_APP_ID = 8;
    private final static int START_WITH_VDISPLAY = 20;
    private final static int START_WITH_QUIT_VDISPLAY = 21;

    public final static String HIDDEN_APPS_PREF_FILENAME = "HiddenApps";

    public final static String NAME_EXTRA = "Name";
    public final static String UUID_EXTRA = "UUID";
    public final static String NEW_PAIR_EXTRA = "NewPair";
    public final static String SHOW_HIDDEN_APPS_EXTRA = "ShowHiddenApps";

    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binder) {
            final ComputerManagerService.ComputerManagerBinder localBinder =
                    ((ComputerManagerService.ComputerManagerBinder)binder);

            // Wait in a separate thread to avoid stalling the UI
            new Thread() {
                @Override
                public void run() {
                    // Wait for the binder to be ready
                    localBinder.waitForReady();

                    final Activity activity = getActivity();
                    if (activity == null) return;
                    activity.runOnUiThread(() -> {
                    if (isFinishing() || getView() == null) return;
                    // Get the computer object
                    computer = localBinder.getComputer(uuidString);
                    if (computer == null) {
                        finish();
                        return;
                    }

                    // Add a launcher shortcut for this PC (forced, since this is user interaction)
                    shortcutHelper.createAppViewShortcut(computer, true, getArguments().getBoolean(NEW_PAIR_EXTRA, false));
                    shortcutHelper.reportComputerShortcutUsed(computer);

                    try {
                        appGridAdapter = new AppGridAdapter(getActivity(),
                                PreferenceConfiguration.readPreferences(getActivity()),
                                computer, localBinder.getUniqueId(),
                                showHiddenApps);
                    } catch (Exception e) {
                        e.printStackTrace();
                        finish();
                        return;
                    }

                    RadioGroup categories = findViewById(R.id.nous_offline_categories);
                    int categoryId = categories.getCheckedRadioButtonId();
                    appGridAdapter.setCategory(categoryId == R.id.nous_offline_desktop
                            ? AppGridAdapter.LibraryCategory.DESKTOP
                            : categoryId == R.id.nous_offline_launchers
                            ? AppGridAdapter.LibraryCategory.LAUNCHERS : AppGridAdapter.LibraryCategory.GAMES);
                    appGridAdapter.updateHiddenApps(hiddenAppIds, true);
                    appGridAdapter.setQuery(gameSearch.getText());
                    com.google.android.material.chip.ChipGroup filters = findViewById(R.id.nous_game_filters);
                    appGridAdapter.setRunningOnly(filters.getCheckedChipId() == R.id.nous_filter_running);

                    // Now make the binder visible. We must do this after appGridAdapter
                    // is set to prevent us from reaching updateUiWithServerinfo() and
                    // touching the appGridAdapter prior to initialization.
                    managerBinder = localBinder;

                    // Load the app grid with cached data (if possible).
                    // This must be done _before_ startComputerUpdates()
                    // so the initial serverinfo response can update the running
                    // icon.
                    populateAppGridWithCache();

                    // Start updates
                    startComputerUpdates();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing() || isChangingConfigurations()) {
                                return;
                            }

                            // Despite my best efforts to catch all conditions that could
                            // cause the activity to be destroyed when we try to commit
                            // I haven't been able to, so we have this try-catch block.
                            try {
                                getChildFragmentManager().beginTransaction()
                                        .replace(R.id.appFragmentContainer, new AdapterFragment())
                                        .commitAllowingStateLoss();
                            } catch (IllegalStateException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    });
                }
            }.start();
        }

        public void onServiceDisconnected(ComponentName className) {
            managerBinder = null;
        }
    };



    private void startComputerUpdates() {
        // Don't start polling if we're not bound or in the foreground
        if (managerBinder == null || !inForeground) {
            return;
        }

        ((PcView) getActivity()).setGamesListener(new ComputerManagerListener() {
            @Override
            public void notifyComputerUpdated(final ComputerDetails details) {
                // Do nothing if updates are suspended
                if (suspendGridUpdates) {
                    return;
                }

                // Don't care about other computers
                if (!details.uuid.equalsIgnoreCase(uuidString)) {
                    return;
                }

                if (details.state == ComputerDetails.State.OFFLINE) {
                    // The PC is unreachable now
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Display a toast to the user and quit the activity
                            Toast.makeText(getActivity(), R.string.lost_connection, Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });

                    return;
                }

                // Close immediately if the PC is no longer paired
                if (details.state == ComputerDetails.State.ONLINE && details.pairState != PairingManager.PairState.PAIRED) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Disable shortcuts referencing this PC for now
                            shortcutHelper.disableComputerShortcut(details,
                                    getResources().getString(R.string.scut_not_paired));

                            // Display a toast to the user and quit the activity
                            Toast.makeText(getActivity(), R.string.scut_not_paired, Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });

                    return;
                }

                // App list is the same or empty
                if (details.rawAppList == null || details.rawAppList.equals(lastRawApplist)) {

                    // Let's check if the running app ID changed
                    if (details.runningGameId != lastRunningAppId) {
                        // Update the currently running game using the app ID
                        lastRunningAppId = details.runningGameId;
                        updateUiWithServerinfo(details);
                    }

                    return;
                }

                lastRunningAppId = details.runningGameId;
                lastRawApplist = details.rawAppList;

                try {
                    updateUiWithAppList(NvHTTP.getAppListByReader(new StringReader(details.rawAppList)));
                    updateUiWithServerinfo(details);

                    if (blockingLoadSpinner != null) {
                        blockingLoadSpinner.dismiss();
                        blockingLoadSpinner = null;
                    }
                } catch (XmlPullParserException | IOException e) {
                    e.printStackTrace();
                }
            }
        });

        if (poller == null) {
            poller = managerBinder.createAppListPoller(computer);
        }
        poller.start();
    }

    private void stopComputerUpdates() {
        if (isOfflineDemo()) {
            return;
        }
        if (poller != null) {
            poller.stop();
        }

        if (getActivity() instanceof PcView) {
            ((PcView) getActivity()).setGamesListener(null);
        }

        if (appGridAdapter != null) {
            appGridAdapter.cancelQueuedOperations();
        }
    }

    private ImageButton getLandscapeSearchToggle() {
        return getActivity().findViewById(R.id.nous_library_search_toggle);
    }

    private void setupLandscapeLibrary(Bundle state) {
        View heading = findViewById(R.id.nous_landscape_library_heading);
        if (heading == null) {
            return;
        }
        ImageButton toggle = getLandscapeSearchToggle();
        toggle.setImageResource(R.drawable.ic_nous_search);
        toggle.setContentDescription(getText(R.string.nous_search_library));
        toggle.setSelected(false);
        if (state != null && state.getBoolean(SEARCH_EXPANDED_STATE)) {
            OneShotPreDrawListener.add(heading, () -> {
                if (getView() != null && !librarySearchExpanded) {
                    setLibrarySearchExpanded(true, false);
                }
            });
        }

        Configuration config = getResources().getConfiguration();
        if (config.screenWidthDp < 160 + 360 * config.fontScale) {
            LinearLayout controls = findViewById(R.id.nous_landscape_library_controls);
            controls.setOrientation(LinearLayout.VERTICAL);
            View categories = findViewById(R.id.nous_offline_categories);
            categories.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            View filters = findViewById(R.id.nous_game_filters);
            LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            filterParams.gravity = Gravity.END;
            filters.setLayoutParams(filterParams);
        }
    }

    private void setLibrarySearchExpanded(boolean expanded, boolean animate) {
        View heading = findViewById(R.id.nous_landscape_library_heading);
        View field = findViewById(R.id.nous_landscape_search_field);
        ImageButton toggle = getLandscapeSearchToggle();
        librarySearchExpanded = expanded;
        toggle.setImageResource(expanded ? R.drawable.ic_close : R.drawable.ic_nous_search);
        toggle.setContentDescription(getText(expanded ? R.string.nous_collapse_search : R.string.nous_search_library));
        toggle.setSelected(!expanded && gameSearch.length() > 0);

        if (librarySearchAnimation != null) {
            librarySearchAnimation.cancel();
        }
        if (!expanded) {
            hideSearchKeyboard();
            toggle.requestFocus();
        }
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) field.getLayoutParams();
        int availableWidth = Math.max(0, heading.getWidth() - heading.getPaddingStart() - heading.getPaddingEnd());
        int expandedWidth = Math.min(Math.round(320 * getResources().getDisplayMetrics().density),
                Math.round(availableWidth * 0.55f));
        int targetWidth = expanded ? expandedWidth : 0;
        if (!animate) {
            params.width = targetWidth;
            field.setLayoutParams(params);
            field.setAlpha(1f);
            field.setVisibility(expanded ? View.VISIBLE : View.GONE);
            return;
        }

        field.setVisibility(View.VISIBLE);
        ValueAnimator animator = ValueAnimator.ofInt(params.width, targetWidth);
        librarySearchAnimation = animator;
        animator.setDuration(180);
        animator.addUpdateListener(animation -> {
            params.width = (int) animation.getAnimatedValue();
            field.setLayoutParams(params);
            field.setAlpha(Math.min(1f, (float) params.width / Math.max(expandedWidth, 1)));
            if (animation.getAnimatedFraction() >= 1f) {
                librarySearchAnimation = null;
                if (expanded) {
                    gameSearch.requestFocus();
                    WindowCompat.getInsetsController(getActivity().getWindow(), gameSearch)
                            .show(WindowInsetsCompat.Type.ime());
                } else {
                    field.setVisibility(View.GONE);
                }
            }
        });
        animator.start();
    }

    private void hideSearchKeyboard() {
        WindowCompat.getInsetsController(getActivity().getWindow(), gameSearch)
                .hide(WindowInsetsCompat.Type.ime());
    }

    void toggleLibrarySearch() {
        if (getView() != null) {
            setLibrarySearchExpanded(!librarySearchExpanded, true);
        }
    }

    boolean closeLibrarySearch() {
        if (!librarySearchExpanded || getView() == null) {
            return false;
        }
        setLibrarySearchExpanded(false, true);
        return true;
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean(SEARCH_EXPANDED_STATE, librarySearchExpanded);
    }

    @Override
    public void onDestroyView() {
        if (librarySearchAnimation != null) {
            librarySearchAnimation.cancel();
            librarySearchAnimation = null;
        }
        librarySearchExpanded = false;
        super.onDestroyView();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Assume we're in the foreground when created to avoid a race
        // between binding to CMS and onResume()
        inForeground = true;

        shortcutHelper = new ShortcutHelper(getActivity());



        com.google.android.material.chip.ChipGroup filters = findViewById(R.id.nous_game_filters);
        filters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (appGridAdapter != null) appGridAdapter.setRunningOnly(checkedIds.contains(R.id.nous_filter_running));
        });
        gameSearch = findViewById(R.id.nous_game_search);
        gameSearch.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) {
                return false;
            }
            hideSearchKeyboard();
            return true;
        });
        setupLandscapeLibrary(savedInstanceState);
        gameSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                ImageButton toggle = getLandscapeSearchToggle();
                if (toggle != null) {
                    toggle.setSelected(!librarySearchExpanded && text.length() > 0);
                }
                if (appGridAdapter != null) {
                    appGridAdapter.setQuery(text);
                }
            }

            @Override
            public void afterTextChanged(Editable text) { }
        });

        // Allow floating expanded PiP overlays while browsing apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getActivity().setShouldDockBigOverlays(false);
        }



        // Setup the profiles button
        findViewById(R.id.profilesButton)
            .setOnClickListener(v -> startActivity(new Intent(getActivity(), ProfilesActivity.class)));

        this.prefConfig = PreferenceConfiguration.readPreferences(getActivity());
        RadioGroup libraryCategories = findViewById(R.id.nous_offline_categories);
        libraryCategories.setVisibility(View.VISIBLE);
        libraryCategories.setOnCheckedChangeListener((group, checkedId) -> {
            if (appGridAdapter == null) return;
            appGridAdapter.setCategory(checkedId == R.id.nous_offline_desktop
                    ? AppGridAdapter.LibraryCategory.DESKTOP
                    : checkedId == R.id.nous_offline_launchers
                    ? AppGridAdapter.LibraryCategory.LAUNCHERS : AppGridAdapter.LibraryCategory.GAMES);
        });
        if (isOfflineDemo()) {
            TextView label = findViewById(R.id.appListText);
            label.setText(R.string.nous_demo_library);
            appGridAdapter = AppGridAdapter.createOfflineDemo(getActivity(), prefConfig);
            appGridAdapter.setQuery(gameSearch.getText());
            appGridAdapter.setRunningOnly(filters.getCheckedChipId() == R.id.nous_filter_running);
            if (getChildFragmentManager().findFragmentById(R.id.appFragmentContainer) == null) {
                getChildFragmentManager().beginTransaction()
                        .replace(R.id.appFragmentContainer, new AdapterFragment())
                        .commit();
            }
            return;
        }

        showHiddenApps = getArguments().getBoolean(SHOW_HIDDEN_APPS_EXTRA, false);
        uuidString = getArguments().getString(UUID_EXTRA);

        SharedPreferences hiddenAppsPrefs = getActivity().getSharedPreferences(HIDDEN_APPS_PREF_FILENAME, android.content.Context.MODE_PRIVATE);
        for (String hiddenAppIdStr : hiddenAppsPrefs.getStringSet(uuidString, new HashSet<String>())) {
            hiddenAppIds.add(Integer.parseInt(hiddenAppIdStr));
        }

        String computerName = getArguments().getString(NAME_EXTRA);

        TextView label = findViewById(R.id.appListText);

        label.setText(computerName);

        // Bind to the computer manager service
        serviceBound = getActivity().bindService(new Intent(getActivity(), ComputerManagerService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);
    }

    private void updateHiddenApps(boolean hideImmediately) {
        HashSet<String> hiddenAppIdStringSet = new HashSet<>();

        for (Integer hiddenAppId : hiddenAppIds) {
            hiddenAppIdStringSet.add(hiddenAppId.toString());
        }

        getActivity().getSharedPreferences(HIDDEN_APPS_PREF_FILENAME, android.content.Context.MODE_PRIVATE)
                .edit()
                .putStringSet(uuidString, hiddenAppIdStringSet)
                .apply();

        appGridAdapter.updateHiddenApps(hiddenAppIds, hideImmediately);
    }

    private void populateAppGridWithCache() {
        try {
            // Try to load from cache
            lastRawApplist = CacheHelper.readInputStreamToString(CacheHelper.openCacheFileForInput(getActivity().getCacheDir(), "applist", uuidString));
            List<NvApp> applist = NvHTTP.getAppListByReader(new StringReader(lastRawApplist));
            updateUiWithAppList(applist);
            LimeLog.info("Loaded applist from cache");
        } catch (IOException | XmlPullParserException e) {
            if (lastRawApplist != null) {
                LimeLog.warning("Saved applist corrupted: "+lastRawApplist);
                e.printStackTrace();
            }
            LimeLog.info("Loading applist from the network");
            // We'll need to load from the network
            loadAppsBlocking();
        }
    }

    private void loadAppsBlocking() {
        blockingLoadSpinner = SpinnerDialog.displayDialog(getActivity(), getResources().getString(R.string.applist_refresh_title),
                getResources().getString(R.string.applist_refresh_msg), true);
    }

    @Override
    public void onDestroy() {
        stopComputerUpdates();
        super.onDestroy();

        SpinnerDialog.closeDialogs(getActivity());
        Dialog.closeDialogs();

        if (serviceBound) {
            getActivity().unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Display a decoder crash notification if we've returned after a crash
        UiHelper.showDecoderCrashDialog(getActivity());

        inForeground = true;
        startComputerUpdates();

        ProfileButton profileButton = findViewById(R.id.profilesButton);
        if (profileButton != null) {
            profileButton.refresh();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        inForeground = false;
        stopComputerUpdates();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ShortcutHelper.REQUEST_CODE_EXPORT_ART_FILE) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                ShortcutHelper.writeArtFileToUri(getActivity(), uri);
            } else {
                // Clear the content if the user cancelled or if there was an error before this point
                ShortcutHelper.artFileContentToExport = null;
                // Show "File export cancelled." toast only if the user explicitly cancelled.
                if (resultCode == Activity.RESULT_CANCELED) {
                    Toast.makeText(getActivity(), R.string.file_export_cancelled, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void showGameActions(View row, AppView.AppObject selectedApp) {
        MenuBuilder menu = new MenuBuilder(getActivity());

        if (lastRunningAppId == 0) {
            if (prefConfig.useVirtualDisplay) {
                menu.add(Menu.NONE, START_OR_RESUME_ID, 1, getResources().getString(R.string.applist_menu_start_primarydisplay));
            } else {
                menu.add(Menu.NONE, START_WITH_VDISPLAY, 1, getResources().getString(R.string.applist_menu_start_vdisplay));
            }
        } else {
            if (lastRunningAppId == selectedApp.app.getAppId()) {
                menu.add(Menu.NONE, START_OR_RESUME_ID, 1, getResources().getString(R.string.applist_menu_resume));
                menu.add(Menu.NONE, QUIT_ID, 2, getResources().getString(R.string.applist_menu_quit));
            }
            else {
                if (prefConfig.useVirtualDisplay) {
                    menu.add(Menu.NONE, START_WITH_QUIT_VDISPLAY, 1, getResources().getString(R.string.applist_menu_quit_and_start));
                    menu.add(Menu.NONE, START_WITH_QUIT, 2, getResources().getString(R.string.applist_menu_quit_and_start_primarydisplay));
                } else{
                    menu.add(Menu.NONE, START_WITH_QUIT, 1, getResources().getString(R.string.applist_menu_quit_and_start));
                    menu.add(Menu.NONE, START_WITH_QUIT_VDISPLAY, 2, getResources().getString(R.string.applist_menu_quit_and_start_vdisplay));
                }
            }
        }

        // Only show the hide checkbox if this is not the currently running app or it's already hidden
        if (lastRunningAppId != selectedApp.app.getAppId() || selectedApp.isHidden) {
            MenuItem hideAppItem = menu.add(Menu.NONE, HIDE_APP_ID, 3, getResources().getString(R.string.applist_menu_hide_app));
            hideAppItem.setCheckable(true);
            hideAppItem.setChecked(selectedApp.isHidden);
        }

        menu.add(Menu.NONE, VIEW_DETAILS_ID, 4, getResources().getString(R.string.applist_menu_details));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Only add an option to create shortcut if box art is loaded
            // and when we're in grid-mode (not list-mode).
            ImageView appImageView = row.findViewById(R.id.grid_image);
            if (appImageView != null) {
                // We have a grid ImageView, so we must be in grid-mode
                BitmapDrawable drawable = (BitmapDrawable)appImageView.getDrawable();
                if (drawable != null && drawable.getBitmap() != null) {
                    // We have a bitmap loaded too
                    menu.add(Menu.NONE, CREATE_SHORTCUT_ID, 5, getResources().getString(R.string.applist_menu_scut));
                }
            }
        }

        menu.add(Menu.NONE, EXPORT_LAUNCHER_FILE_ID, 6, getResources().getString(R.string.applist_menu_export_launcher));
        new ControllerDialogBuilder(getActivity()).showActions(selectedApp.app.getAppName(), menu,
                id -> handleGameAction(row, selectedApp, id), null);
    }

    boolean handleGameAction(View row, AppView.AppObject app, int itemId) {
        if (getView() == null || !getView().isShown()) return false;
        switch (itemId) {
            case START_WITH_QUIT:
            case START_WITH_QUIT_VDISPLAY: {
                boolean withVDiaplay = itemId == START_WITH_QUIT_VDISPLAY;
                if (withVDiaplay && !(computer.vDisplaySupported && computer.vDisplayDriverReady)) {
                    UiHelper.displayVdisplayConfirmationDialog(
                        getActivity(),
                        computer,
                        () -> UiHelper.displayQuitConfirmationDialog(getActivity(), new Runnable() {
                            @Override
                            public void run() {
                                ServerHelper.doStart(getActivity(), app.app, computer, managerBinder, true);
                            }
                        }, null),
                        null
                    );
                } else {
                    // Display a confirmation dialog first
                    UiHelper.displayQuitConfirmationDialog(getActivity(), new Runnable() {
                        @Override
                        public void run() {
                            ServerHelper.doStart(getActivity(), app.app, computer, managerBinder, withVDiaplay);
                        }
                    }, null);
                }
                return true;
            }

            case START_OR_RESUME_ID:
            case START_WITH_VDISPLAY: {
                boolean withVDiaplay = itemId == START_WITH_VDISPLAY;
                if (withVDiaplay && !(computer.vDisplaySupported && computer.vDisplayDriverReady)) {
                    UiHelper.displayVdisplayConfirmationDialog(
                            getActivity(),
                            computer,
                            () -> ServerHelper.doStart(getActivity(), app.app, computer, managerBinder, true),
                            null
                    );
                } else {
                    // Resume is the same as start for us
                    ServerHelper.doStart(getActivity(), app.app, computer, managerBinder, withVDiaplay);
                }
                return true;
            }

            case QUIT_ID: {
                // Display a confirmation dialog first
                UiHelper.displayQuitConfirmationDialog(getActivity(), new Runnable() {
                    @Override
                    public void run() {
                        suspendGridUpdates = true;
                        ServerHelper.doQuit(getActivity(), computer,
                                app.app, managerBinder, new Runnable() {
                                    @Override
                                    public void run() {
                                        // Trigger a poll immediately
                                        suspendGridUpdates = false;
                                        if (poller != null) {
                                            poller.pollNow();
                                        }
                                    }
                                });
                    }
                }, null);
                return true;
            }

            case VIEW_DETAILS_ID: {
                Dialog.displayDialog(getActivity(), getResources().getString(R.string.title_details), app.app.toString(), false);
                return true;
            }

            case HIDE_APP_ID: {
                if (app.isHidden) {
                    // Transitioning hidden to shown
                    hiddenAppIds.remove(app.app.getAppId());
                } else {
                    // Transitioning shown to hidden
                    hiddenAppIds.add(app.app.getAppId());
                }
                updateHiddenApps(false);
                return true;
            }

            case CREATE_SHORTCUT_ID: {
                ImageView appImageView = row.findViewById(R.id.grid_image);
                Bitmap appBits = ((BitmapDrawable) appImageView.getDrawable()).getBitmap();
                if (!shortcutHelper.createPinnedGameShortcut(computer, app.app, appBits)) {
                    Toast.makeText(getActivity(), getResources().getString(R.string.unable_to_pin_shortcut), Toast.LENGTH_LONG).show();
                }
                return true;
            }

            case EXPORT_LAUNCHER_FILE_ID: {
                if (app.app.getAppUUID() == null || (app.app.getAppUUID() != null && app.app.getAppUUID().isEmpty())) {
                    UiHelper.displayConfirmationDialog(
                            getActivity(),
                            getResources().getString(R.string.title_export_sunshine_launcher_file),
                            getResources().getString(R.string.message_export_sunshine_launcher_file),
                            getResources().getString(R.string.proceed),
                            getResources().getString(R.string.cancel),
                            () -> shortcutHelper.exportLauncherFile(computer, app.app),
                            null
                    );
                } else {
                    shortcutHelper.exportLauncherFile(computer, app.app);
                }
                return true;
            }

            default: {
                return false;
            }
        }
    }

    private void updateUiWithServerinfo(final ComputerDetails details) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean updated = false;

                    // Look through our current app list to tag the running app
                for (AppView.AppObject existingApp : appGridAdapter.getUnfilteredApps()) {

                    // There can only be one or zero apps running.
                    if (existingApp.isRunning &&
                            existingApp.app.getAppId() == details.runningGameId) {
                        // This app was running and still is, so we're done now
                        return;
                    }
                    else if (existingApp.app.getAppId() == details.runningGameId) {
                        // This app wasn't running but now is
                        existingApp.isRunning = true;
                        updated = true;
                    }
                    else if (existingApp.isRunning) {
                        // This app was running but now isn't
                        existingApp.isRunning = false;
                        updated = true;
                    }
                    else {
                        // This app wasn't running and still isn't
                    }
                }

                if (updated) {
                    appGridAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    private void updateUiWithAppList(final List<NvApp> appList) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean updated = false;

                List<AppView.AppObject> unfilteredApps = appGridAdapter.getUnfilteredApps();

                // First handle app updates and additions
                for (NvApp app : appList) {
                    boolean foundExistingApp = false;

                    // Try to update an existing app in the list first
                    for (AppView.AppObject existingApp : unfilteredApps) {
                        if (existingApp.app.getAppId() == app.getAppId()) {
                            // Found the app; update its properties
                            if (!existingApp.app.getArtVersion().equals(app.getArtVersion())) {
                                existingApp.app.setArtVersion(app.getArtVersion());
                                updated = true;
                            }
                            if (!existingApp.app.getLibraryCategory().equals(app.getLibraryCategory())) {
                                existingApp.app.setLibraryCategory(app.getLibraryCategory());
                                updated = true;
                            }
                            if (!existingApp.app.getAppName().equals(app.getAppName())) {
                                existingApp.app.setAppName(app.getAppName());
                                updated = true;
                            }

                            foundExistingApp = true;
                            break;
                        }
                    }

                    if (!foundExistingApp) {
                        // This app must be new
                        appGridAdapter.addApp(new AppView.AppObject(app));

                        // We could have a leftover shortcut from last time this PC was paired
                        // or if this app was removed then added again. Enable those shortcuts
                        // again if present.
                        shortcutHelper.enableAppShortcut(computer, app);

                        updated = true;
                    }
                }

                // Next handle app removals
                int i = 0;
                while (i < unfilteredApps.size()) {
                    boolean foundExistingApp = false;
                    AppView.AppObject existingApp = unfilteredApps.get(i);

                    // Check if this app is in the latest list
                    for (NvApp app : appList) {
                        if (existingApp.app.getAppId() == app.getAppId()) {
                            foundExistingApp = true;
                            break;
                        }
                    }

                    // This app was removed in the latest app list
                    if (!foundExistingApp) {
                        shortcutHelper.disableAppShortcut(computer, existingApp.app, getString(R.string.app_removed_from_pc));
                        appGridAdapter.removeApp(existingApp);
                        updated = true;

                        // Check this same index again because the item at i+1 is now at i after
                        // the removal
                        continue;
                    }

                    // Move on to the next item
                    i++;
                }

                if (updated) {
                    appGridAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    @Override
    public int getAdapterFragmentLayoutId() {
        return PreferenceConfiguration.readPreferences(getActivity()).smallIconMode ?
                    R.layout.app_grid_view_small : R.layout.app_grid_view;
    }

    @Override
    public void receiveAbsListView(AbsListView listView) {
        listView.setAdapter(appGridAdapter);
        if (isOfflineDemo()) {
            listView.setEmptyView(findViewById(R.id.nous_library_empty));
            listView.setOnItemClickListener((parent, view, position, id) ->
                    Toast.makeText(parent.getContext(), R.string.nous_demo_item, Toast.LENGTH_SHORT).show());
            return;
        }
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int pos,
                                    long id) {
                AppView.AppObject app = (AppView.AppObject) appGridAdapter.getItem(pos);

                // Only open the context menu if something is running, otherwise start it
                if (lastRunningAppId != 0) {
                    if (prefConfig.resumeWithoutConfirm && lastRunningAppId == app.app.getAppId()) {
                        ServerHelper.doStart(getActivity(), app.app, computer, managerBinder, prefConfig.useVirtualDisplay);
                    } else {
                        showGameActions(arg1, app);
                    }
                } else {
                    if (prefConfig.useVirtualDisplay && !(computer.vDisplaySupported && computer.vDisplayDriverReady)) {
                        UiHelper.displayVdisplayConfirmationDialog(
                                getActivity(),
                                computer,
                                () -> ServerHelper.doStart(getActivity(), app.app, computer, managerBinder, true),
                                null
                        );
                    } else {
                        ServerHelper.doStart(getActivity(), app.app, computer, managerBinder, prefConfig.useVirtualDisplay);
                    }
                }
            }
        });

        listView.setOnItemLongClickListener((parent, row, position, id) -> {
            showGameActions(row, appGridAdapter.getItem(position));
            return true;
        });
        listView.requestFocus();
    }

}
