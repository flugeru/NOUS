package com.limelight;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;

import com.limelight.binding.PlatformBinding;
import com.limelight.binding.crypto.AndroidCryptoProvider;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.grid.PcGridAdapter;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.LanPairing;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.http.PairingManager.PairState;
import com.limelight.nvstream.wol.WakeOnLanSender;
import com.limelight.preferences.AddComputerManually;
import com.limelight.profiles.ProfileButton;
import com.limelight.preferences.GlPreferences;
import com.limelight.preferences.PreferenceConfiguration;
import com.google.android.material.navigation.NavigationBarView;
import java.util.ArrayList;
import java.util.List;
import com.limelight.ui.AdapterFragment;
import com.limelight.ui.AdapterFragmentCallbacks;
import com.limelight.utils.Dialog;
import com.limelight.utils.HelpLauncher;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.ShortcutHelper;
import com.limelight.utils.UiHelper;

import android.app.ActivityManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.menu.MenuBuilder;
import com.limelight.ui.ControllerDialogBuilder;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.limelight.ui.ControllerMenuActivity;
import androidx.preference.PreferenceManager;

import org.xmlpull.v1.XmlPullParserException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PcView extends ControllerMenuActivity implements AdapterFragmentCallbacks {
    private final android.os.Handler discoveryUi = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean discoveryWindowElapsed;
    private final Runnable finishDiscoveryWindow = () -> {
        discoveryWindowElapsed = true;
        renderDiscoveryState();
    };

    private void renderDiscoveryState() {
        if (noPcFoundLayout == null) return;
        noPcFoundLayout.findViewById(R.id.pcs_loading).setVisibility(discoveryWindowElapsed ? View.GONE : View.VISIBLE);
        ((android.widget.TextView) noPcFoundLayout.findViewById(R.id.nous_scan_status))
                .setText(discoveryWindowElapsed ? R.string.nous_scan_empty : R.string.nous_scan_title);
        noPcFoundLayout.findViewById(R.id.nous_scan_retry).setVisibility(discoveryWindowElapsed ? View.VISIBLE : View.GONE);
    }

    private void beginDiscoveryWindow() {
        if (!inForeground || isFinishing()) return;
        discoveryUi.removeCallbacks(finishDiscoveryWindow);
        discoveryWindowElapsed = false;
        renderDiscoveryState();
        discoveryUi.postDelayed(finishDiscoveryWindow, 12000);
    }

    private View noPcFoundLayout;
    private PcGridAdapter pcGridAdapter;
    private ShortcutHelper shortcutHelper;
    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private boolean freezeUpdates, runningPolling, inForeground, completeOnCreateCalled;
    private ComputerDetails.AddressTuple pendingPairingAddress;
    private String pendingPairingPin, pendingPairingPassphrase;
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

                    // Now make the binder visible
                    managerBinder = localBinder;

                    // Start updates
                    startComputerUpdates();
                    runOnUiThread(() -> openLibraryShortcut());

                    // Force a keypair to be generated early to avoid discovery delays
                    new AndroidCryptoProvider(PcView.this).getClientCertificate();
                }
            }.start();
        }

        public void onServiceDisconnected(ComponentName className) {
            managerBinder = null;
        }
    };

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Only reinitialize views if completeOnCreate() was called
        // before this callback. If it was not, completeOnCreate() will
        // handle initializing views with the config change accounted for.
        // This is not prone to races because both callbacks are invoked
        // in the main thread.
        if (completeOnCreateCalled) {
            recreate();
        }

        refreshProfileButton();
    }

    private final static int PAIR_ID = 2;
    private final static int UNPAIR_ID = 3;
    private final static int WOL_ID = 4;
    private final static int DELETE_ID = 5;
    private final static int RESUME_ID = 6;
    private final static int QUIT_ID = 7;
    private final static int VIEW_DETAILS_ID = 8;
    private final static int FULL_APP_LIST_ID = 9;
    private final static int TEST_NETWORK_ID = 10;
    private final static int GAMESTREAM_EOL_ID = 11;
    private final static int OPEN_MANAGEMENT_PAGE_ID = 20;
    private final static int PAIR_ID_OTP = 21;

    private NousSettingsTab settingsTab;
    private volatile ComputerManagerListener gamesListener;
    private int restoredTab = R.id.nous_nav_computers;

    @Override
    protected void onSaveInstanceState(Bundle state) {
        NavigationBarView navigation = findViewById(R.id.nous_navigation);
        state.putInt("nous.tab", navigation == null ? restoredTab : navigation.getSelectedItemId());
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openLibraryShortcut();
    }

    private void openLibraryShortcut() {
        String uuid = getIntent().getStringExtra(AppView.UUID_EXTRA);
        if (uuid == null || managerBinder == null || settingsTab == null) return;
        ComputerDetails host = managerBinder.getComputer(uuid);
        getIntent().removeExtra(AppView.UUID_EXTRA);
        if (host != null) doAppList(host,
                getIntent().getBooleanExtra(AppView.NEW_PAIR_EXTRA, false),
                getIntent().getBooleanExtra(AppView.SHOW_HIDDEN_APPS_EXTRA, false));
        else Toast.makeText(this, R.string.nous_games_need_pc, Toast.LENGTH_SHORT).show();
    }

    void openPairedAccountHost(NousAccountHosts.Host entry, com.google.firebase.auth.FirebaseAuth auth, com.google.firebase.auth.FirebaseUser user) {
        ComputerDetails host = managerBinder == null ? null : managerBinder.getAccountComputer(entry.uuid);
        int message = R.string.nous_account_host_not_paired;
        if (host != null && entry.uuid.equalsIgnoreCase(host.uuid) && host.serverCert != null) {
            try {
                if (!entry.matchesSavedCertificate(host.serverCert.getEncoded())) message = R.string.nous_account_host_pin_conflict;
                else if (host.state != ComputerDetails.State.ONLINE) message = R.string.error_pc_offline;
                else if (host.pairState == PairState.PAIRED) {
                    doAppList(host, false, false);
                    return;
                }
            } catch (java.security.GeneralSecurityException invalidCertificate) { message = R.string.nous_account_host_pin_conflict; }
        }
        if (message == R.string.nous_account_host_not_paired && host != null && host.state == ComputerDetails.State.ONLINE && host.activeAddress != null) {
            new ControllerDialogBuilder(this).setTitle(entry.name).setMessage(R.string.nous_account_connect_explain)
                    .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.nous_account_connect, (dialog, which) -> {
                        if (lanPairingBusy || auth.getCurrentUser() != user) return;
                        lanPairingBusy = true;
                        user.getIdToken(false).addOnCompleteListener(task -> {
                            var binder = managerBinder;
                            if (!task.isSuccessful() || isDestroyed() || isFinishing() || binder == null || auth.getCurrentUser() != user) {
                                lanPairingBusy = false;
                                if (!isDestroyed() && !isFinishing()) Toast.makeText(this,R.string.nous_account_connect_failed,Toast.LENGTH_LONG).show();
                                return;
                            }
                            new Thread(() -> {
                                boolean stored = false;
                                try {
                                    var cert = LanPairing.connectAccount(host.activeAddress, entry.uuid, entry.pin, task.getResult().getToken(), PlatformBinding.getCryptoProvider(this));
                                    stored = auth.getCurrentUser() == user && binder.persistPairingPin(host.uuid, cert);
                                } catch (Exception ignored) { /* Never log identity credentials. */ }
                                final boolean ok = stored;
                                runOnUiThread(() -> {
                                    lanPairingBusy = false;
                                    if (isDestroyed() || isFinishing()) return;
                                    if (ok && auth.getCurrentUser() == user) doAppList(host,true,false);
                                    else Toast.makeText(this,R.string.nous_account_connect_failed,Toast.LENGTH_LONG).show();
                                });
                            },"nous-account-connect").start();
                        });
                    }).show();
            return;
        }
        new ControllerDialogBuilder(this).setTitle(entry.name).setMessage(message).setPositiveButton(android.R.string.ok, null).show();
    }

    void setGamesListener(ComputerManagerListener listener) {
        gamesListener = listener;
    }

    private void initializeViews() {
        boolean showSettings = settingsTab != null && settingsTab.isVisible();
        if (settingsTab != null) {
            settingsTab.detach();
        }
        setContentView(R.layout.activity_pc_view);

        UiHelper.notifyNewRootView(this);

        // Allow floating expanded PiP overlays while browsing PCs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setShouldDockBigOverlays(false);
        }

        // Set default preferences if we've never been run
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        // Set the correct layout for the PC grid
        pcGridAdapter.updateLayoutWithPreferences(this, PreferenceConfiguration.readPreferences(this));

        // Setup the list view
        NavigationBarView navigation = findViewById(R.id.nous_navigation);
        settingsTab = new NousSettingsTab(this, navigation, R.id.nous_nav_computers);
        View searchToggle = findViewById(R.id.nous_library_search_toggle);
        if (searchToggle != null) {
            searchToggle.setOnClickListener(view -> {
                GamesFragment games = (GamesFragment) getFragmentManager().findFragmentByTag("games");
                if (games != null) {
                    games.toggleLibrarySearch();
                }
            });
        }
        navigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nous_nav_games) {
                return openGamesTab();
            }
            if (searchToggle != null) {
                GamesFragment games = (GamesFragment) getFragmentManager().findFragmentByTag("games");
                if (games != null) {
                    games.closeLibrarySearch();
                }
                searchToggle.setVisibility(View.GONE);
            }
            findViewById(R.id.nous_games_container).setVisibility(View.GONE);
            settingsTab.show(id == R.id.nous_nav_settings);
            return true;
        });
        navigation.setSelectedItemId(showSettings ? R.id.nous_nav_settings : R.id.nous_nav_computers);
        if (restoredTab == R.id.nous_nav_settings) navigation.setSelectedItemId(R.id.nous_nav_settings);
        else if (restoredTab == R.id.nous_nav_games && getFragmentManager().findFragmentByTag("games") != null) {
            navigation.setSelectedItemId(R.id.nous_nav_games);
        }
        restoredTab = R.id.nous_nav_computers;
        openLibraryShortcut();

        findViewById(R.id.manuallyAddPc).setOnClickListener(v ->
                startActivity(new Intent(this, AddComputerManually.class)));
        findViewById(R.id.profilesButton).setOnClickListener(v ->
                startActivity(new Intent(this, ProfilesActivity.class)));
        refreshProfileButton();

        getFragmentManager().beginTransaction()
            .replace(R.id.pcFragmentContainer, new AdapterFragment())
            .commitAllowingStateLoss();

        noPcFoundLayout = findViewById(R.id.no_pc_found_layout);
        noPcFoundLayout.findViewById(R.id.nous_scan_retry).setOnClickListener(v -> {
            stopComputerUpdates(false);
            startComputerUpdates();
        });
        renderDiscoveryState();
        if (pcGridAdapter.getCount() == 0) {
            noPcFoundLayout.setVisibility(View.VISIBLE);
        }
        else {
            noPcFoundLayout.setVisibility(View.INVISIBLE);
        }
        pcGridAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) restoredTab = savedInstanceState.getInt("nous.tab", R.id.nous_nav_computers);

        // Assume we're in the foreground when created to avoid a race
        // between binding to CMS and onResume()
        inForeground = true;

        // Create a GLSurfaceView to fetch GLRenderer unless we have
        // a cached result already.
        final GlPreferences glPrefs = GlPreferences.readPreferences(this);
        if (!glPrefs.savedFingerprint.equals(Build.FINGERPRINT) || glPrefs.glRenderer.isEmpty()) {
            GLSurfaceView surfaceView = new GLSurfaceView(this);
            surfaceView.setRenderer(new GLSurfaceView.Renderer() {
                @Override
                public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
                    // Save the GLRenderer string so we don't need to do this next time
                    glPrefs.glRenderer = gl10.glGetString(GL10.GL_RENDERER);
                    glPrefs.savedFingerprint = Build.FINGERPRINT;
                    glPrefs.writePreferences();

                    LimeLog.info("Fetched GL Renderer: " + glPrefs.glRenderer);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            completeOnCreate();
                        }
                    });
                }

                @Override
                public void onSurfaceChanged(GL10 gl10, int i, int i1) {
                }

                @Override
                public void onDrawFrame(GL10 gl10) {
                }
            });
            setContentView(surfaceView);
        }
        else {
            LimeLog.info("Cached GL Renderer: " + glPrefs.glRenderer);
            completeOnCreate();
        }

        Intent intent = getIntent();

        String hostname = intent.getStringExtra("hostname");
        int port = intent.getIntExtra("port", NvHTTP.DEFAULT_HTTP_PORT);
        pendingPairingPin = intent.getStringExtra("pin");
        pendingPairingPassphrase = intent.getStringExtra("passphrase");

        if (hostname != null && pendingPairingPin != null && pendingPairingPassphrase != null) {
            pendingPairingAddress = new ComputerDetails.AddressTuple(hostname, port);
        } else {
            pendingPairingPin = null;
            pendingPairingPassphrase = null;
        }
    }

    private void completeOnCreate() {
        completeOnCreateCalled = true;

        shortcutHelper = new ShortcutHelper(this);

        UiHelper.setLocale(this);

        // Bind to the computer manager service
        bindService(new Intent(PcView.this, ComputerManagerService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);

        pcGridAdapter = new PcGridAdapter(this, PreferenceConfiguration.readPreferences(this));

        initializeViews();
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                NavigationBarView navigation = findViewById(R.id.nous_navigation);
                if (navigation.getSelectedItemId() == R.id.nous_nav_games) {
                    GamesFragment games = (GamesFragment) getFragmentManager().findFragmentByTag("games");
                    if (games != null && games.closeLibrarySearch()) {
                        return;
                    }
                }
                if (navigation.getSelectedItemId() != R.id.nous_nav_computers) showComputers();
                else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
    }

    private void startComputerUpdates() {
        // Only allow polling to start if we're bound to CMS, polling is not already running,
        // and our activity is in the foreground.
        if (managerBinder != null && !runningPolling && inForeground) {
            freezeUpdates = false;
            managerBinder.startPolling(new ComputerManagerListener() {
                @Override
                public void notifyComputerUpdated(final ComputerDetails details) {
                    ComputerManagerListener listener = gamesListener;
                    if (listener != null) listener.notifyComputerUpdated(details);
                    if (!freezeUpdates) {
                        PcView.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateComputer(details);
                            }
                        });

                        // Add a launcher shortcut for this PC (off the main thread to prevent ANRs)
                        if (details.pairState == PairState.PAIRED) {
                            shortcutHelper.createAppViewShortcutForOnlineHost(details);
//                        } else
                        }
                            if (pendingPairingAddress != null) {
                            if (
                                details.state == ComputerDetails.State.ONLINE &&
                                details.activeAddress.equals(pendingPairingAddress)
                            ) {
                                PcView.this.runOnUiThread(() -> {
                                    doPair(details, pendingPairingPin, pendingPairingPassphrase);
                                    pendingPairingAddress = null;
                                    pendingPairingPin = null;
                                    pendingPairingPassphrase = null;
                                });
                            }
                        }
                    }
                }
            });
            runningPolling = true;
            runOnUiThread(this::beginDiscoveryWindow);
        }
    }

    private void stopComputerUpdates(boolean wait) {
        if (managerBinder != null) {
            if (!runningPolling) {
                return;
            }

            freezeUpdates = true;

            managerBinder.stopPolling();

            if (wait) {
                managerBinder.waitForPollingStopped();
            }

            runningPolling = false;
        }
    }

    private void refreshProfileButton() {
        ProfileButton profileButton = findViewById(R.id.profilesButton);
        if (profileButton != null) {
            profileButton.refresh();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        discoveryUi.removeCallbacksAndMessages(null);

        if (managerBinder != null) {
            unbindService(serviceConnection);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Display a decoder crash notification if we've returned after a crash
        UiHelper.showDecoderCrashDialog(this);

        refreshProfileButton();

        inForeground = true;
        startComputerUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();

        inForeground = false;
        discoveryUi.removeCallbacks(finishDiscoveryWindow);
        stopComputerUpdates(false);
    }

    @Override
    protected void onStop() {
        super.onStop();

        Dialog.closeDialogs();
    }

    private void showComputerActions(ComputerObject computer) {
        stopComputerUpdates(false);
        MenuBuilder menu = new MenuBuilder(this);
        int icon = 0;
        String headerTitle = computer.details.name + " - ";
        switch (computer.details.state)
        {
            case ONLINE:
                headerTitle += getResources().getString(R.string.pcview_menu_header_online);
                break;
            case OFFLINE:
                icon = R.drawable.ic_pc_offline;
                headerTitle += getResources().getString(R.string.pcview_menu_header_offline);
                break;
            case UNKNOWN:
                headerTitle += getResources().getString(R.string.pcview_menu_header_unknown);
                break;
        }

        // Inflate the context menu
        if (computer.details.state == ComputerDetails.State.OFFLINE ||
            computer.details.state == ComputerDetails.State.UNKNOWN) {
            menu.add(Menu.NONE, WOL_ID, 1, getResources().getString(R.string.pcview_menu_send_wol));
            menu.add(Menu.NONE, GAMESTREAM_EOL_ID, 2, getResources().getString(R.string.pcview_menu_eol));
        }
        else if (computer.details.pairState != PairState.PAIRED) {
            menu.add(Menu.NONE, PAIR_ID_OTP, 1, getResources().getString(R.string.pcview_menu_pair_pc_otp));
            menu.add(Menu.NONE, PAIR_ID, 2, getResources().getString(R.string.pcview_menu_pair_pc));
            if (computer.details.nvidiaServer) {
                menu.add(Menu.NONE, GAMESTREAM_EOL_ID, 3, getResources().getString(R.string.pcview_menu_eol));
            } else {
                menu.add(Menu.NONE, OPEN_MANAGEMENT_PAGE_ID, 3, getResources().getString(R.string.pcview_menu_open_management_page));
            }
        }
        else {
            if (computer.details.runningGameId != 0) {
                menu.add(Menu.NONE, RESUME_ID, 1, getResources().getString(R.string.applist_menu_resume));
                menu.add(Menu.NONE, QUIT_ID, 2, getResources().getString(R.string.applist_menu_quit));
            }

            if (computer.details.nvidiaServer) {
                menu.add(Menu.NONE, GAMESTREAM_EOL_ID, 3, getResources().getString(R.string.pcview_menu_eol));
            } else {
                menu.add(Menu.NONE, OPEN_MANAGEMENT_PAGE_ID, 3, getResources().getString(R.string.pcview_menu_open_management_page));
            }

            menu.add(Menu.NONE, FULL_APP_LIST_ID, 4, getResources().getString(R.string.pcview_menu_app_list));
        }

        menu.add(Menu.NONE, TEST_NETWORK_ID, 5, getResources().getString(R.string.pcview_menu_test_network));
        menu.add(Menu.NONE, DELETE_ID, 6, getResources().getString(R.string.pcview_menu_delete_pc));
        menu.add(Menu.NONE, VIEW_DETAILS_ID, 7, getString(R.string.pcview_menu_details));
        ControllerDialogBuilder actionDialog = new ControllerDialogBuilder(this);
        if (icon != 0) {
            actionDialog.setIcon(icon);
        }
        actionDialog.showActions(headerTitle, menu, id -> handleComputerAction(computer, id),
                this::startComputerUpdates);
    }

    private LanPairing lanPairing;
    private ComputerDetails lanPairingComputer;
    private boolean lanPairingBusy;
    private final androidx.activity.result.ActivityResultLauncher<ScanOptions> lanScanner =
            registerForActivityResult(new ScanContract(), result -> {
                LanPairing request = lanPairing;
                ComputerDetails computer = lanPairingComputer;
                lanPairing = null;
                lanPairingComputer = null;
                String qr = result.getContents();
                if (request == null || computer == null || qr == null) return;
                lanPairingBusy = true;
                var binder = managerBinder;
                new Thread(() -> {
                    boolean paired = false;
                    try {
                        var cert = request.redeem(qr, computer.uuid);
                        paired = binder != null && binder.persistPairingPin(computer.uuid, cert);
                    } catch (java.io.IOException | org.json.JSONException | java.security.GeneralSecurityException ignored) {
                        // Do not display or log scanned payload, proof, or raw response.
                    }
                    final boolean success = paired;
                    runOnUiThread(() -> {
                        lanPairingBusy = false;
                        if (isDestroyed() || isFinishing()) return;
                        if (success) doAppList(computer, true, false);
                        else Toast.makeText(this, R.string.nous_lan_pair_error, Toast.LENGTH_LONG).show();
                    });
                }, "nous-lan-pair-redeem").start();
            });

    private void choosePairing(ComputerDetails computer) {
        if (!computer.lanPairingSupported || !getPackageManager().hasSystemFeature("android.hardware.camera.any")) {
            doPair(computer, null, null);
            return;
        }
        if (lanPairingBusy || lanPairing != null) return;
        new ControllerDialogBuilder(this)
                .setTitle(R.string.pair_pairing_title)
                .setMessage(R.string.nous_lan_pair_explain)
                .setPositiveButton(R.string.nous_lan_pair_request, (dialog, which) -> beginLanPairing(computer))
                .setNeutralButton(R.string.nous_lan_pair_pin, (dialog, which) -> doPair(computer, null, null))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void beginLanPairing(ComputerDetails computer) {
        if (lanPairingBusy || lanPairing != null) return;
        lanPairingBusy = true;
        var address = computer.activeAddress;
        var pin = computer.serverCert;
        new Thread(() -> {
            LanPairing pending = null;
            try {
                if (address != null) pending = LanPairing.begin(address, pin, PlatformBinding.getCryptoProvider(this));
            } catch (java.io.IOException | org.json.JSONException | java.security.GeneralSecurityException | IllegalArgumentException ignored) {
                // Preserve manual pairing on hosts that do not support local QR.
            }
            final LanPairing request = pending;
            runOnUiThread(() -> {
                lanPairingBusy = false;
                if (isFinishing() || isDestroyed()) return;
                if (request == null) {
                    new ControllerDialogBuilder(this).setTitle(R.string.pair_pairing_title)
                            .setMessage(R.string.nous_lan_pair_error)
                            .setPositiveButton(R.string.nous_lan_pair_pin, (dialog, which) -> doPair(computer, null, null))
                            .setNegativeButton(R.string.cancel, null).show();
                    return;
                }
                lanPairing = request;
                lanPairingComputer = computer;
                lanScanner.launch(new ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt(getString(R.string.nous_lan_pair_scan)).setBeepEnabled(false)
                        .setBarcodeImageEnabled(false).setOrientationLocked(false));
            });
        }, "nous-lan-pair-request").start();
    }

    private void doPair(final ComputerDetails computer, String otp, String passphrase) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.pair_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(PcView.this, getResources().getString(R.string.pairing), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                NvHTTP httpConn;
                String message;
                boolean success = false;
                try {
                    // Stop updates and wait while pairing
                    stopComputerUpdates(true);

                    httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer),
                            computer.httpsPort, managerBinder.getUniqueId(), computer.serverCert,
                            PlatformBinding.getCryptoProvider(PcView.this));
                    if (httpConn.getPairState() == PairState.PAIRED) {
                        // Don't display any toast, but open the app list
                        message = null;
                        success = true;
                    }
                    else {
                        String pinStr = otp;
                        if (pinStr == null) {
                            pinStr = PairingManager.generatePinString();
                        }

                        // Spin the dialog off in a thread because it blocks
                        if (passphrase == null) {
                            Dialog.displayPairingPin(PcView.this, computer.name, pinStr);
                        } else {
                            Dialog.displayDialog(PcView.this, getResources().getString(R.string.pair_pairing_title),
                                    getResources().getString(R.string.pair_otp_pairing_msg)+"\n\n"+
                                            getResources().getString(R.string.pair_otp_pairing_help), false);
                        }

                        PairingManager pm = httpConn.getPairingManager();

                        PairState pairState = pm.pair(httpConn.getServerInfo(true), pinStr, passphrase);
                        if (pairState == PairState.PIN_WRONG) {
                            message = getResources().getString(R.string.pair_incorrect_pin);
                        }
                        else if (pairState == PairState.FAILED) {
                            if (computer.runningGameId != 0) {
                                message = getResources().getString(R.string.pair_pc_ingame);
                            }
                            else {
                                message = getResources().getString(R.string.pair_fail);
                            }
                        }
                        else if (pairState == PairState.ALREADY_IN_PROGRESS) {
                            message = getResources().getString(R.string.pair_already_in_progress);
                        }
                        else if (pairState == PairState.PAIRED) {
                            // Just navigate to the app view without displaying a toast
                            message = null;
                            success = true;

                            // Pin this certificate for later HTTPS use
                            managerBinder.getComputer(computer.uuid).serverCert = pm.getPairedCert();

                            // Invalidate reachability information after pairing to force
                            // a refresh before reading pair state again
                            managerBinder.invalidateStateForComputer(computer.uuid);
                        }
                        else {
                            // Should be no other values
                            message = null;
                        }
                    }
                } catch (UnknownHostException e) {
                    message = getResources().getString(R.string.error_unknown_host);
                } catch (FileNotFoundException e) {
                    message = getResources().getString(R.string.error_404);
                } catch (XmlPullParserException | IOException e) {
                    e.printStackTrace();
                    message = e.getMessage();
                }

                Dialog.closeDialogs();

                final String toastMessage = message;
                final boolean toastSuccess = success;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (toastMessage != null) {
                            Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                        }

                        if (toastSuccess) {
                            // The library shares this Activity, so onResume() will not restart polling.
                            startComputerUpdates();
                            // Open the app list after a successful pairing attempt
                            doAppList(computer, true, false);
                        }
                        else {
                            // Start polling again if we're still in the foreground
                            startComputerUpdates();
                        }
                    }
                });
            }
        }).start();
    }

    private void doOTPPair(final ComputerDetails computer) {
        Context context = PcView.this;

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);

        final EditText otpInput = new EditText(context);
        otpInput.setHint("PIN");
        otpInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        otpInput.setFilters(new InputFilter[] { new InputFilter.LengthFilter(4) });

        final EditText passphraseInput = new EditText(context);
        passphraseInput.setHint(getString(R.string.pair_passphrase_hint));
        passphraseInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        layout.addView(otpInput);
        layout.addView(passphraseInput);

        AlertDialog.Builder dialogBuilder = new ControllerDialogBuilder(context);
        dialogBuilder.setTitle(R.string.pcview_menu_pair_pc_otp);
        dialogBuilder.setView(layout);

        dialogBuilder.setPositiveButton(getString(R.string.proceed), null);

        dialogBuilder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = dialogBuilder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String pin = otpInput.getText().toString();
            String passphrase = passphraseInput.getText().toString();
            if (pin.length() != 4) {
                Toast.makeText(context, getString(R.string.pair_pin_length_msg), Toast.LENGTH_SHORT).show();
                return;
            }
            if (passphrase.length() < 4 ) {
                Toast.makeText(context, getString(R.string.pair_passphrase_length_msg), Toast.LENGTH_SHORT).show();
                return;
            }
            doPair(computer, pin, passphrase);
            dialog.dismiss(); // Manually dismiss the dialog if the input is valid
        });
    }

    private void doWakeOnLan(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.ONLINE) {
            Toast.makeText(PcView.this, getResources().getString(R.string.wol_pc_online), Toast.LENGTH_SHORT).show();
            return;
        }

        if (computer.macAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.wol_no_mac), Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                String message;
                try {
                    WakeOnLanSender.sendWolPacket(computer);
                    message = getResources().getString(R.string.wol_waking_msg);
                } catch (IOException e) {
                    message = getResources().getString(R.string.wol_fail);
                }

                final String toastMessage = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void doUnpair(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(PcView.this, getResources().getString(R.string.unpairing), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                NvHTTP httpConn;
                String message;
                try {
                    httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer),
                            computer.httpsPort, managerBinder.getUniqueId(), computer.serverCert,
                            PlatformBinding.getCryptoProvider(PcView.this));
                    if (httpConn.getPairState() == PairState.PAIRED) {
                        httpConn.unpair();
                        if (httpConn.getPairState() == PairState.NOT_PAIRED) {
                            message = getResources().getString(R.string.unpair_success);
                        }
                        else {
                            message = getResources().getString(R.string.unpair_fail);
                        }
                    }
                    else {
                        message = getResources().getString(R.string.unpair_error);
                    }
                } catch (UnknownHostException e) {
                    message = getResources().getString(R.string.error_unknown_host);
                } catch (FileNotFoundException e) {
                    message = getResources().getString(R.string.error_404);
                } catch (XmlPullParserException | IOException e) {
                    message = e.getMessage();
                    e.printStackTrace();
                }

                final String toastMessage = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    void showComputers() {
        ((NavigationBarView) findViewById(R.id.nous_navigation)).setSelectedItemId(R.id.nous_nav_computers);
    }

    private boolean openGamesTab() {
        GamesFragment existing = (GamesFragment) getFragmentManager().findFragmentByTag("games");
        boolean hasLibrary = existing != null && !existing.isRemoving();
        if (hasLibrary && !existing.isOfflineDemo()) {
            showGamesContent();
            return true;
        }
        List<ComputerDetails> hosts = new ArrayList<>();
        for (int i = 0; i < pcGridAdapter.getCount(); i++) {
            ComputerDetails host = pcGridAdapter.getItem(i).details;
            if (host.state == ComputerDetails.State.ONLINE && host.pairState == PairState.PAIRED) {
                hosts.add(host);
            }
        }
        if (hosts.isEmpty()) {
            if (!PreferenceManager.getDefaultSharedPreferences(this)
                    .getBoolean(PreferenceConfiguration.OFFLINE_LIBRARY_PREF_STRING, false)) {
                Toast.makeText(this, R.string.nous_games_need_pc, Toast.LENGTH_SHORT).show();
                return false;
            }
            if (hasLibrary) {
                showGamesContent();
            } else {
                showGamesLibrary(GamesFragment.offlineDemoArguments());
            }
            return true;
        } else if (hosts.size() == 1) {
            doAppList(hosts.get(0), false, false);
        } else {
            String[] names = new String[hosts.size()];
            for (int i = 0; i < hosts.size(); i++) {
                names[i] = hosts.get(i).name;
            }
            new ControllerDialogBuilder(this)
                    .setTitle(R.string.nous_choose_pc)
                    .setItems(names, (dialog, which) -> doAppList(hosts.get(which), false, false))
                    .show();
        }
        return hosts.size() == 1;
    }

    private void doAppList(ComputerDetails computer, boolean newlyPaired, boolean showHiddenGames) {
        if (computer.state == ComputerDetails.State.OFFLINE) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Bundle arguments = new Bundle();
        arguments.putString(AppView.NAME_EXTRA, computer.name);
        arguments.putString(AppView.UUID_EXTRA, computer.uuid);
        arguments.putBoolean(AppView.NEW_PAIR_EXTRA, newlyPaired);
        arguments.putBoolean(AppView.SHOW_HIDDEN_APPS_EXTRA, showHiddenGames);
        showGamesLibrary(arguments);
    }

    private void showGamesLibrary(Bundle arguments) {
        GamesFragment games = new GamesFragment();
        games.setArguments(arguments);
        getFragmentManager().beginTransaction().replace(R.id.nous_games_container, games, "games").commitAllowingStateLoss();
        showGamesContent();
    }

    private void showGamesContent() {
        settingsTab.show(false);
        View header = findViewById(R.id.nous_games_container).findViewById(R.id.nous_library_header);
        if (header != null) header.setVisibility(View.GONE);
        findViewById(R.id.nous_main_tab).setVisibility(View.GONE);
        findViewById(R.id.nous_games_container).setVisibility(View.VISIBLE);
        View searchToggle = findViewById(R.id.nous_library_search_toggle);
        if (searchToggle != null) {
            searchToggle.setVisibility(View.VISIBLE);
        }
        ((NavigationBarView) findViewById(R.id.nous_navigation)).getMenu().findItem(R.id.nous_nav_games).setChecked(true);
    }

    private void handleComputerAction(ComputerObject computer, int action) {
        switch (action) {
            case PAIR_ID:
                doPair(computer.details, null, null);
                return;

            case PAIR_ID_OTP:
                doOTPPair(computer.details);
                return;

            case UNPAIR_ID:
                doUnpair(computer.details);
                return;

            case WOL_ID:
                doWakeOnLan(computer.details);
                return;

            case DELETE_ID:
                if (ActivityManager.isUserAMonkey()) {
                    LimeLog.info("Ignoring delete PC request from monkey");
                    return;
                }
                UiHelper.displayDeletePcConfirmationDialog(this, computer.details, new Runnable() {
                    @Override
                    public void run() {
                        if (managerBinder == null) {
                            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
                            return;
                        }
                        removeComputer(computer.details);
                    }
                }, null);
                return;

            case FULL_APP_LIST_ID:
                doAppList(computer.details, false, true);
                return;

            case RESUME_ID:
                if (managerBinder == null) {
                    Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
                    return;
                }

                ServerHelper.doStart(this, new NvApp("app", null, computer.details.runningGameId, false), computer.details, managerBinder, false);
                return;

            case QUIT_ID:
                if (managerBinder == null) {
                    Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
                    return;
                }

                // Display a confirmation dialog first
                UiHelper.displayQuitConfirmationDialog(this, new Runnable() {
                    @Override
                    public void run() {
                        ServerHelper.doQuit(PcView.this, computer.details,
                                new NvApp("app", null, 0, false), managerBinder, null);
                    }
                }, null);
                return;

            case VIEW_DETAILS_ID:
                Dialog.displayDialog(PcView.this, getResources().getString(R.string.title_details), computer.details.toString(), false);
                return;

            case TEST_NETWORK_ID:
                ServerHelper.doNetworkTest(PcView.this);
                return;

            case GAMESTREAM_EOL_ID:
                HelpLauncher.launchGameStreamEolFaq(PcView.this);
                return;

            case OPEN_MANAGEMENT_PAGE_ID:
                String managementUrl = computer.guessManagementUrl();
                if (managementUrl == null) {
                    Toast.makeText(PcView.this, getResources().getString(R.string.pcview_error_no_management_url), Toast.LENGTH_LONG).show();
                } else {
                    HelpLauncher.launchUrl(PcView.this, managementUrl);
                }

                break;
        }
    }

    private void removeComputer(ComputerDetails details) {
        managerBinder.removeComputer(details);

        new DiskAssetLoader(this).deleteAssetsForComputer(details.uuid);

        // Delete hidden games preference value
        getSharedPreferences(AppView.HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE)
                .edit()
                .remove(details.uuid)
                .apply();

        for (int i = 0; i < pcGridAdapter.getCount(); i++) {
            ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(i);

            if (details.equals(computer.details)) {
                // Disable or delete shortcuts referencing this PC
                shortcutHelper.disableComputerShortcut(details,
                        getResources().getString(R.string.scut_deleted_pc));

                pcGridAdapter.removeComputer(computer);
                pcGridAdapter.notifyDataSetChanged();

                if (pcGridAdapter.getCount() == 0) {
                    // Show the "Discovery in progress" view
                    noPcFoundLayout.setVisibility(View.VISIBLE);
                }

                break;
            }
        }
    }

    private void updateComputer(ComputerDetails details) {
        ComputerObject existingEntry = null;

        for (int i = 0; i < pcGridAdapter.getCount(); i++) {
            ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(i);

            // Check if this is the same computer
            if (details.uuid.equals(computer.details.uuid)) {
                existingEntry = computer;
                break;
            }
        }

        if (existingEntry != null) {
            // Replace the information in the existing entry
            existingEntry.details = details;
        }
        else {
            // Add a new entry
            pcGridAdapter.addComputer(new ComputerObject(details));

            // Remove the "Discovery in progress" view
            noPcFoundLayout.setVisibility(View.INVISIBLE);
        }

        // Notify the view that the data has changed
        pcGridAdapter.notifyDataSetChanged();
    }

    @Override
    public int getAdapterFragmentLayoutId() {
        return R.layout.pc_grid_view;
    }

    @Override
    public void receiveAbsListView(AbsListView listView) {
        listView.setAdapter(pcGridAdapter);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int pos,
                                    long id) {
                ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(pos);
                if (computer.details.state == ComputerDetails.State.UNKNOWN ||
                    computer.details.state == ComputerDetails.State.OFFLINE) {
                    // Open the context menu if a PC is offline or refreshing
                    showComputerActions(computer);
                } else if (computer.details.pairState != PairState.PAIRED) {
                    choosePairing(computer.details);
                } else {
                    doAppList(computer.details, false, false);
                }
            }
        });
        UiHelper.applyStatusBarPadding(listView);
        listView.setOnItemLongClickListener((parent, row, position, id) -> {
            showComputerActions(pcGridAdapter.getItem(position));
            return true;
        });
    }

    public static class ComputerObject {
        public ComputerDetails details;

        public ComputerObject(ComputerDetails details) {
            if (details == null) {
                throw new IllegalArgumentException("details must not be null");
            }
            this.details = details;
        }

        @Override
        public String toString() {
            return details.name;
        }
        public String guessManagementUrl() {
            if (details.activeAddress == null) return null;
            return "https://" + details.activeAddress.address + ":" + (details.guessExternalPort() + 1);
        }
    }
}
