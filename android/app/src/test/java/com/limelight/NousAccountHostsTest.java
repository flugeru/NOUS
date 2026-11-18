package com.limelight;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class NousAccountHostsTest {
    @Test public void directoryUsesOnlyFixedGoogleOriginAndStableIdentity() {
        assertEquals("https://firestore.googleapis.com/v1/projects/nous-game-stream/databases/(default)/documents/accounts/user-ID/google/12345/hosts?pageSize=64",
                NousAccountHosts.directory("user-ID", "12345").toString());
        assertThrows(IllegalArgumentException.class, () -> NousAccountHosts.directory("../hosts", "1"));
        assertThrows(IllegalArgumentException.class, () -> NousAccountHosts.directory("valid", "name@example.org"));
        assertThrows(IllegalArgumentException.class, () -> NousAccountHosts.directory("valid", null));
    }
}
