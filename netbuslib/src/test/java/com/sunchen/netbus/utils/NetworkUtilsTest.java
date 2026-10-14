package com.sunchen.netbus.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.sunchen.netbus.type.MobileGeneration;
import com.sunchen.netbus.type.NetType;

import org.junit.Test;

public class NetworkUtilsTest {
    @Test
    public void mapsTransports() {
        assertEquals(NetType.WIFI, NetworkUtils.fromTransports(true, false, false));
        assertEquals(NetType.MOBILE, NetworkUtils.fromTransports(false, true, false));
        assertEquals(NetType.ETHERNET, NetworkUtils.fromTransports(false, false, true));
        assertEquals(NetType.NONE, NetworkUtils.fromTransports(false, false, false));
        assertEquals(NetType.WIFI, NetworkUtils.fromTransports(true, true, true));
        assertEquals(NetType.MOBILE, NetworkUtils.fromTransports(false, true, true));
    }

    @Test
    public void mapsTelephonyTypes() {
        assertEquals(MobileGeneration.G2, NetworkUtils.fromTelephonyType(2));
        assertEquals(MobileGeneration.G2, NetworkUtils.fromTelephonyType(16));
        assertEquals(MobileGeneration.G3, NetworkUtils.fromTelephonyType(3));
        assertEquals(MobileGeneration.G3, NetworkUtils.fromTelephonyType(17));
        assertEquals(MobileGeneration.G4, NetworkUtils.fromTelephonyType(13));
        assertEquals(MobileGeneration.G4, NetworkUtils.fromTelephonyType(19));
        assertEquals(MobileGeneration.G5, NetworkUtils.fromTelephonyType(20));
        assertEquals(MobileGeneration.UNKNOWN, NetworkUtils.fromTelephonyType(0));
        assertEquals(MobileGeneration.UNKNOWN, NetworkUtils.fromTelephonyType(99));
    }

    @Test
    public void availableRequiresInternetNotValidated() {
        // INTERNET alone is enough (captive / LAN-only Wi-Fi)
        assertTrue(NetworkUtils.isAvailableFromFlags(true, false));
        assertTrue(NetworkUtils.isAvailableFromFlags(true, true));
        // No INTERNET -> unavailable even if VALIDATED somehow set
        assertFalse(NetworkUtils.isAvailableFromFlags(false, false));
        assertFalse(NetworkUtils.isAvailableFromFlags(false, true));
    }
}
