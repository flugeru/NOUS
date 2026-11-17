package com.limelight.grid;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.limelight.AppView;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.grid.assets.CachedAppAssetLoader;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.grid.assets.MemoryAssetLoader;
import com.limelight.grid.assets.NetworkAssetLoader;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@SuppressWarnings("unchecked")
public class AppGridAdapter extends GenericGridAdapter<AppView.AppObject> {
    private static final int ART_WIDTH_PX = 300;
    private static final int SMALL_WIDTH_DP = 110;
    private static final int LARGE_WIDTH_DP = 170;

    private final ComputerDetails computer;
    private final String uniqueId;
    private final boolean showHiddenApps;

    private final SparseIntArray bundledArtwork = new SparseIntArray();
    private CachedAppAssetLoader loader;
    private Set<Integer> hiddenAppIds = new HashSet<>();
    private final List<AppView.AppObject> allApps = new ArrayList<>();
    private final List<AppView.AppObject> searchResults = new ArrayList<>();
    private String query = "";
    private boolean runningOnly;
    public enum LibraryCategory { DESKTOP, GAMES, LAUNCHERS }

    private final SparseArray<LibraryCategory> demoCategories = new SparseArray<>();
    private LibraryCategory category = LibraryCategory.GAMES;

    public void setCategory(LibraryCategory category) {
        if (this.category != category) {
            this.category = category;
            notifyDataSetChanged();
        }
    }

    public void setRunningOnly(boolean runningOnly) {
        this.runningOnly = runningOnly;
        notifyDataSetChanged();
    }

    public AppGridAdapter(Context context, PreferenceConfiguration prefs, ComputerDetails computer, String uniqueId, boolean showHiddenApps) {
        super(context, getLayoutIdForPreferences(prefs));

        this.computer = computer;
        this.uniqueId = uniqueId;
        this.showHiddenApps = showHiddenApps;

        updateLayoutWithPreferences(context, prefs);
    }

    public static AppGridAdapter createOfflineDemo(Context context, PreferenceConfiguration prefs) {
        AppGridAdapter adapter = new AppGridAdapter(context, prefs, null, null, false);
        adapter.addDemoItem(context, R.string.nous_demo_desktop, R.drawable.nous_demo_desktop,
                LibraryCategory.DESKTOP, false);
        adapter.addDemoItem(context, R.string.nous_demo_genshin, R.drawable.nous_demo_genshin,
                LibraryCategory.GAMES, false);
        adapter.addDemoItem(context, R.string.nous_demo_zenless, R.drawable.nous_demo_zenless,
                LibraryCategory.GAMES, false);
        adapter.addDemoItem(context, R.string.nous_demo_cyberpunk, R.drawable.nous_demo_cyberpunk,
                LibraryCategory.GAMES, true);
        adapter.addDemoItem(context, R.string.nous_demo_honkai, R.drawable.nous_demo_honkai,
                LibraryCategory.GAMES, false);
        adapter.addDemoItem(context, R.string.nous_demo_steam, R.drawable.nous_demo_steam,
                LibraryCategory.LAUNCHERS, false);
        adapter.notifyDataSetChanged();
        return adapter;
    }

    private void addDemoItem(Context context, int title, int artwork, LibraryCategory category,
                             boolean running) {
        int id = bundledArtwork.size() + 1;
        bundledArtwork.put(id, artwork);
        demoCategories.put(id, category);
        NvApp game = new NvApp(context.getString(title), "", id, false);
        game.setAppIndex(id);
        AppView.AppObject item = new AppView.AppObject(game);
        item.isRunning = running;
        addApp(item);
    }

    public void updateHiddenApps(Set<Integer> newHiddenAppIds, boolean hideImmediately) {
        this.hiddenAppIds.clear();
        this.hiddenAppIds.addAll(newHiddenAppIds);

        if (hideImmediately) {
            // Reconstruct the itemList with the new hidden app set
            itemList.clear();
            for (AppView.AppObject app : allApps) {
                app.isHidden = hiddenAppIds.contains(app.app.getAppId());

                if (!app.isHidden || showHiddenApps) {
                    itemList.add(app);
                }
            }
        }
        else {
            // Just update the isHidden state to show the correct UI indication
            for (AppView.AppObject app : allApps) {
                app.isHidden = hiddenAppIds.contains(app.app.getAppId());
            }
        }

        notifyDataSetChanged();
    }

    public void setQuery(CharSequence text) {
        String normalized = text.toString().trim().toLowerCase(Locale.ROOT);
        if (!query.equals(normalized)) {
            query = normalized;
            notifyDataSetChanged();
        }
    }

    public List<AppView.AppObject> getUnfilteredApps() {
        return Collections.unmodifiableList(itemList);
    }

    @Override
    public int getCount() {
        return searchResults.size();
    }

    @Override
    public AppView.AppObject getItem(int position) {
        return searchResults.get(position);
    }

    @Override
    public void notifyDataSetChanged() {
        searchResults.clear();
        for (AppView.AppObject app : itemList) {
            LibraryCategory appCategory = demoCategories.get(app.app.getAppId());
            if (appCategory == null) {
                appCategory = LibraryCategory.valueOf(app.app.getLibraryCategory().toUpperCase(Locale.ROOT));
            }
            boolean matchesCategory = appCategory == category;
            if (matchesCategory && (!runningOnly || app.isRunning)
                    && app.app.getAppName().toLowerCase(Locale.ROOT).contains(query)) {
                searchResults.add(app);
            }
        }
        super.notifyDataSetChanged();
    }

    private static int getLayoutIdForPreferences(PreferenceConfiguration prefs) {
        if (prefs.smallIconMode) {
            return R.layout.app_grid_item_small;
        }
        else {
            return R.layout.app_grid_item;
        }
    }

    public void updateLayoutWithPreferences(Context context, PreferenceConfiguration prefs) {
        if (computer == null) {
            setLayoutId(getLayoutIdForPreferences(prefs));
            return;
        }
        int dpi = context.getResources().getDisplayMetrics().densityDpi;
        int dp;

        if (prefs.smallIconMode) {
            dp = SMALL_WIDTH_DP;
        }
        else {
            dp = LARGE_WIDTH_DP;
        }

        double scalingDivisor = ART_WIDTH_PX / (dp * (dpi / 160.0));
        if (scalingDivisor < 1.0) {
            // We don't want to make them bigger before draw-time
            scalingDivisor = 1.0;
        }
        LimeLog.info("Art scaling divisor: " + scalingDivisor);

        if (loader != null) {
            // Cancel operations on the old loader
            cancelQueuedOperations();
        }

        this.loader = new CachedAppAssetLoader(computer, scalingDivisor,
                new NetworkAssetLoader(context, uniqueId),
                new MemoryAssetLoader(),
                new DiskAssetLoader(context),
                BitmapFactory.decodeResource(context.getResources(), R.drawable.no_app_image));

        // This will trigger the view to reload with the new layout
        setLayoutId(getLayoutIdForPreferences(prefs));
    }

    public void cancelQueuedOperations() {
        if (loader == null) {
            return;
        }
        loader.cancelForegroundLoads();
        loader.cancelBackgroundLoads();
        loader.freeCacheMemory();
    }

    private static void sortList(List<AppView.AppObject> list) {
        Collections.sort(list, new Comparator<AppView.AppObject>() {
            @Override
            public int compare(AppView.AppObject lhs, AppView.AppObject rhs) {
                int lIndex = lhs.app.getAppIndex();
                int rIndex = rhs.app.getAppIndex();
                if (lIndex == rIndex) {
                    return lhs.app.getAppName().toLowerCase().compareTo(rhs.app.getAppName().toLowerCase());
                } else {
                    return lIndex - rIndex;
                }
            }
        });
    }

    public void addApp(AppView.AppObject app) {
        // Update hidden state
        app.isHidden = hiddenAppIds.contains(app.app.getAppId());

        // Always add the app to the all apps list
        allApps.add(app);
        sortList(allApps);

        // Add the app to the adapter data if it's not hidden
        if (showHiddenApps || !app.isHidden) {
            // Queue a request to fetch this bitmap into cache
            if (loader != null) {
                loader.queueCacheLoad(app.app);
            }

            // Add the app to our sorted list
            itemList.add(app);
            sortList(itemList);
        }
    }

    public void removeApp(AppView.AppObject app) {
        itemList.remove(app);
        allApps.remove(app);
    }

    @Override
    public void clear() {
        super.clear();
        allApps.clear();
        searchResults.clear();
    }

    @Override
    public void populateView(View parentView, ImageView imgView, RelativeLayout gridMask, ProgressBar prgView, TextView txtView, ImageView overlayView, AppView.AppObject obj) {
        if (loader == null) {
            imgView.setImageResource(bundledArtwork.get(obj.app.getAppId()));
            imgView.setVisibility(View.VISIBLE);
            if (prgView != null) prgView.setVisibility(View.GONE);
            txtView.setText(obj.app.getAppName());
            txtView.setVisibility(View.VISIBLE);
        } else {
            loader.populateImageView(obj.app, imgView, txtView);
        }

        if (obj.isRunning) {
            // Show the play button overlay
            overlayView.setImageResource(R.drawable.ic_play);
            overlayView.setVisibility(View.VISIBLE);
            gridMask.setBackgroundColor(0x66000000);
        }
        else {
            overlayView.setVisibility(View.GONE);
            gridMask.setBackgroundColor(0x00000000);
        }

        if (obj.isHidden) {
            parentView.setAlpha(0.40f);
        }
        else {
            parentView.setAlpha(1.0f);
        }
    }
}
