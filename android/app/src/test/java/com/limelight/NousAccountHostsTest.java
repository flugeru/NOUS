package com.limelight;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class NousAccountHostsTest {
    @Test public void hostIdentityHasExactParentIdAndFingerprint() throws Exception {
        String prefix = "projects/demo/databases/(default)/documents/accounts/owner/google/12345/hosts/";
        String doc = prefix + "01234567-abcd-abcd-abcd-0123456789ab";
        String pin = "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855";
        NousAccountHosts.Host host = new NousAccountHosts.Host(doc, prefix, "PC", pin);
        org.junit.Assert.assertTrue(host.matchesSavedCertificate(new byte[0]));
        org.junit.Assert.assertFalse(host.matchesSavedCertificate(new byte[]{1}));
        assertThrows(java.io.IOException.class, () -> new NousAccountHosts.Host(doc.replace("owner", "other"), prefix, "PC", pin));
        assertThrows(java.io.IOException.class, () -> new NousAccountHosts.Host(doc + "/nested", prefix, "PC", pin));
        assertThrows(java.io.IOException.class, () -> new NousAccountHosts.Host(doc, prefix, "PC", "bad"));
        assertThrows(java.io.IOException.class, () -> new NousAccountHosts.Host(doc, prefix, "PC\u202e", pin));
    }

    @Test public void directoryUsesOnlyFixedGoogleOriginAndStableIdentity() {
        assertEquals("https://firestore.googleapis.com/v1/projects/nous-game-stream/databases/(default)/documents/accounts/user-ID/google/12345/hosts?pageSize=64",
                NousAccountHosts.directory("user-ID", "12345").toString());
        assertThrows(IllegalArgumentException.class, () -> NousAccountHosts.directory("../hosts", "1"));
        assertThrows(IllegalArgumentException.class, () -> NousAccountHosts.directory("valid", "name@example.org"));
        assertThrows(IllegalArgumentException.class, () -> NousAccountHosts.directory("valid", null));
    }
}
