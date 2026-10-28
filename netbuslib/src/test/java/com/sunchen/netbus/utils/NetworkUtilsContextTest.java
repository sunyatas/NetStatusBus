package com.sunchen.netbus.utils;

import static org.junit.Assert.fail;

import android.content.Context;

import org.junit.Test;

public class NetworkUtilsContextTest {

    @Test
    public void contextOverloadsRejectNull() {
        Context nul = null;
        assertRejectsNull(() -> NetworkUtils.isNetworkAvailable(nul));
        assertRejectsNull(() -> NetworkUtils.getNetType(nul));
        assertRejectsNull(() -> NetworkUtils.isVpnActive(nul));
        assertRejectsNull(() -> NetworkUtils.getMobileGeneration(nul));
    }

    private static void assertRejectsNull(Runnable call) {
        try {
            call.run();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
