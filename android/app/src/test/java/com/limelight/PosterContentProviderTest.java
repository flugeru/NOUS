package com.limelight;

import android.content.ContentResolver;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.File;
import java.io.FileNotFoundException;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PosterContentProviderTest {
    @Test public void acceptsOnlyReadOnlyArtworkUris() throws Exception {
        PosterContentProvider provider = Robolectric.buildContentProvider(PosterContentProvider.class).create().get();
        String uuid = "12345678-1234-1234-1234-123456789abc";
        android.content.Context context = ApplicationProvider.getApplicationContext();
        File directory = new File(context.getCacheDir(), "boxart/" + uuid);
        assertTrue(directory.isDirectory() || directory.mkdirs());
        File artwork = new File(directory, "1.png");
        assertTrue(artwork.exists() || artwork.createNewFile());
        Uri valid = PosterContentProvider.createBoxArtUri(uuid, "1");
        try (android.os.ParcelFileDescriptor descriptor = provider.openFile(valid, "r")) {
            assertNotNull(descriptor);
        }
        for (String path : new String[] {"boxart/%2e%2e%2fsecret/1", "other/" + uuid + "/1",
                "boxart/" + uuid + "/9999999999", "boxart/" + uuid + "/abc",
                "boxart/" + uuid + "/-1", "boxart/" + uuid + "/1/extra"}) {
            Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                    .authority(PosterContentProvider.AUTHORITY).encodedPath(path).build();
            try { provider.openFile(uri, "r"); fail("Accepted invalid URI: " + uri); }
            catch (FileNotFoundException expected) { }
        }
        try { provider.openFile(valid, "w"); fail("Accepted write access"); }
        catch (UnsupportedOperationException expected) { }
    }
}
