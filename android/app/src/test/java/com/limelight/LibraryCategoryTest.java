package com.limelight;

import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import java.io.StringReader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class LibraryCategoryTest {
    @Test
    public void realAppsFilterByCategorySearchAndRunningState() {
        var context = org.robolectric.RuntimeEnvironment.getApplication();
        var adapter = new com.limelight.grid.AppGridAdapter(context,
                new com.limelight.preferences.PreferenceConfiguration(), null, null, false);
        var desktop = new NvApp("Workspace", "", 1, false);
        desktop.setLibraryCategory("desktop");
        var launcher = new NvApp("Steam", "", 2, false);
        launcher.setLibraryCategory("launchers");
        var game = new NvApp("Portal", "", 3, false);
        adapter.addApp(new AppView.AppObject(desktop));
        var runningLauncher = new AppView.AppObject(launcher);
        runningLauncher.isRunning = true;
        adapter.addApp(runningLauncher);
        adapter.addApp(new AppView.AppObject(game));
        adapter.notifyDataSetChanged();
        assertEquals(3, adapter.getItem(0).app.getAppId());
        assertEquals(1, adapter.getCount());
        adapter.setCategory(com.limelight.grid.AppGridAdapter.LibraryCategory.DESKTOP);
        assertEquals(1, adapter.getItem(0).app.getAppId());
        adapter.setRunningOnly(true);
        assertEquals(0, adapter.getCount());
        adapter.setCategory(com.limelight.grid.AppGridAdapter.LibraryCategory.LAUNCHERS);
        assertEquals(2, adapter.getItem(0).app.getAppId());
        adapter.setQuery(" PORT ");
        assertEquals(0, adapter.getCount());
        adapter.setRunningOnly(false);
        adapter.setCategory(com.limelight.grid.AppGridAdapter.LibraryCategory.GAMES);
        assertEquals(1, adapter.getCount());
    }

    @Test
    public void optionalCategoriesKeepLegacyHostsCompatible() throws Exception {
        var apps = NvHTTP.getAppListByReader(new StringReader(
                "<root status_code=\"200\"><App><AppTitle>Discord</AppTitle><ID>1</ID>"
                + "<LibraryCategory>desktop</LibraryCategory></App>"
                + "<App><AppTitle>Game</AppTitle><ID>2</ID></App></root>"));
        assertEquals("desktop", apps.get(0).getLibraryCategory());
        assertEquals("games", apps.get(1).getLibraryCategory());
        apps.get(0).setLibraryCategory("invalid");
        assertEquals("desktop", apps.get(0).getLibraryCategory());
    }
}
