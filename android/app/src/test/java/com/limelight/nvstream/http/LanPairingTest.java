package com.limelight.nvstream.http;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LanPairingTest {
    @Test
    public void qrMatchesOnlySelectedHostAndInstallationRequest() {
        String host = "12345678-9abc-1234-5678-123456789abc";
        String id = hex('A');
        String pin = hex('B');
        String cert = hex('C');
        String payload = "NOUS-LAN-1:" + host + ":" + id + ":" + pin + ":" + cert + ":" + hex('D');

        assertEquals(pin, LanPairing.scannedPin(payload, host, id, cert));
        assertNull(LanPairing.scannedPin(payload, "02345678-9abc-1234-5678-123456789abc", id, cert));
        assertNull(LanPairing.scannedPin(payload, host, hex('E'), cert));
        assertNull(LanPairing.scannedPin(payload, host, id, hex('E')));
        assertNull(LanPairing.scannedPin(payload + ":", host, id, cert));
        assertNull(LanPairing.scannedPin(payload.replace('B', 'b'), host, id, cert));
    }

    private static String hex(char digit) {
        return new String(new char[64]).replace('\0', digit);
    }
}
