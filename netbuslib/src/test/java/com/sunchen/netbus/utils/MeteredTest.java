package com.sunchen.netbus.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.net.NetworkCapabilities;

import org.junit.Test;

public class MeteredTest {

    private static final int NOT_METERED = NetworkCapabilities.NET_CAPABILITY_NOT_METERED;
    private static final int TEMP_NOT_METERED =
            NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED;

    @Test
    public void flags() {
        assertTrue(NetworkUtils.isMeteredFromFlags(false, false));
        assertFalse(NetworkUtils.isMeteredFromFlags(true, false));
        assertFalse(NetworkUtils.isMeteredFromFlags(false, true));
        assertFalse(NetworkUtils.isMeteredFromFlags(true, true));
    }

    @Test
    public void nullCapabilitiesCountAsMetered() {
        assertTrue(NetworkUtils.isMetered(null, 35));
        assertTrue(NetworkUtils.isMetered(null, 23));
    }

    @Test
    public void notMeteredNetworkIsNotMetered() {
        NetworkCapabilities caps = caps(true, false);
        assertFalse(NetworkUtils.isMetered(caps, 23));
        assertFalse(NetworkUtils.isMetered(caps, 35));
    }

    @Test
    public void meteredNetworkIsMetered() {
        NetworkCapabilities caps = caps(false, false);
        assertTrue(NetworkUtils.isMetered(caps, 23));
        assertTrue(NetworkUtils.isMetered(caps, 35));
    }

    @Test
    public void temporarilyNotMeteredOnlyCountsFromAndroid11() {
        NetworkCapabilities caps = caps(false, true);
        assertFalse(NetworkUtils.isMetered(caps, 30));
        assertFalse(NetworkUtils.isMetered(caps, 35));
        // Below API 30 the capability does not exist, so it is not trusted.
        assertTrue(NetworkUtils.isMetered(caps, 29));
        assertTrue(NetworkUtils.isMetered(caps, 23));
    }

    @Test
    public void temporarilyNotMeteredConstantMatchesPlatform() {
        // NET_CAPABILITY_TEMPORARILY_NOT_METERED is 25 since API 30.
        org.junit.Assert.assertEquals(25, TEMP_NOT_METERED);
    }

    private static NetworkCapabilities caps(boolean notMetered, boolean temporarilyNotMetered) {
        NetworkCapabilities caps = mock(NetworkCapabilities.class);
        when(caps.hasCapability(NOT_METERED)).thenReturn(notMetered);
        when(caps.hasCapability(TEMP_NOT_METERED)).thenReturn(temporarilyNotMetered);
        return caps;
    }
}
