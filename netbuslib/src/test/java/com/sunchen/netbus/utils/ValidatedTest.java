package com.sunchen.netbus.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.net.NetworkCapabilities;

import org.junit.Test;

public class ValidatedTest {

    private static final int INTERNET = NetworkCapabilities.NET_CAPABILITY_INTERNET;
    private static final int VALIDATED = NetworkCapabilities.NET_CAPABILITY_VALIDATED;
    private static final int CAPTIVE = NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL;

    @Test
    public void flags() {
        assertTrue(NetworkUtils.isValidatedFromFlags(true, true, false));
        assertFalse(NetworkUtils.isValidatedFromFlags(true, false, false));
        assertFalse(NetworkUtils.isValidatedFromFlags(false, true, false));
        assertFalse(NetworkUtils.isValidatedFromFlags(true, true, true));
        assertFalse(NetworkUtils.isValidatedFromFlags(false, false, false));
    }

    @Test
    public void nullCapabilitiesAreNotValidated() {
        assertFalse(NetworkUtils.isValidated(null));
    }

    @Test
    public void validatedInternetNetwork() {
        assertTrue(NetworkUtils.isValidated(caps(true, true, false)));
    }

    @Test
    public void captivePortalIsAvailableButNotValidated() {
        NetworkCapabilities caps = caps(true, false, true);
        assertTrue(NetworkUtils.isAvailable(caps));
        assertFalse(NetworkUtils.isValidated(caps));
    }

    @Test
    public void lanOnlyIsAvailableButNotValidated() {
        NetworkCapabilities caps = caps(true, false, false);
        assertTrue(NetworkUtils.isAvailable(caps));
        assertFalse(NetworkUtils.isValidated(caps));
    }

    @Test
    public void noInternetCapabilityIsNotValidated() {
        assertFalse(NetworkUtils.isValidated(caps(false, true, false)));
    }

    private static NetworkCapabilities caps(boolean internet, boolean validated, boolean captive) {
        NetworkCapabilities caps = mock(NetworkCapabilities.class);
        when(caps.hasCapability(INTERNET)).thenReturn(internet);
        when(caps.hasCapability(VALIDATED)).thenReturn(validated);
        when(caps.hasCapability(CAPTIVE)).thenReturn(captive);
        return caps;
    }
}
