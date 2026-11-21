package com.sunchen.netbus.utils;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.telephony.TelephonyManager;

import com.sunchen.netbus.type.MobileGeneration;

import org.junit.Before;
import org.junit.Test;

/**
 * Drives {@link NetworkUtils#mobileGeneration} through a mocked {@link TelephonyManager}.
 * On the plain JVM {@code Build.VERSION.SDK_INT} is 0, so without the {@code sdkInt} seam only
 * the pre-7.0 {@code getNetworkType()} branch could run.
 */
@SuppressWarnings("deprecation") // getNetworkType() is the pre-7.0 API under test
public class MobileGenerationQueryTest {

    private static final int M = 23;
    private static final int N = 24;
    private static final int LATEST = 35;

    private TelephonyManager tm;

    @Before
    public void setUp() {
        tm = mock(TelephonyManager.class);
    }

    @Test
    public void noTelephonyManagerIsUnknown() {
        assertEquals(MobileGeneration.UNKNOWN, NetworkUtils.mobileGeneration(null, LATEST));
        assertEquals(MobileGeneration.UNKNOWN, NetworkUtils.mobileGeneration(null, M));
    }

    @Test
    public void android7AndUpReadsTheDataNetworkType() {
        when(tm.getDataNetworkType()).thenReturn(TelephonyManager.NETWORK_TYPE_NR);
        when(tm.getNetworkType()).thenReturn(TelephonyManager.NETWORK_TYPE_LTE);
        assertEquals(MobileGeneration.G5, NetworkUtils.mobileGeneration(tm, N));
        assertEquals(MobileGeneration.G5, NetworkUtils.mobileGeneration(tm, LATEST));
        verify(tm, never()).getNetworkType();
    }

    @Test
    public void beforeAndroid7ReadsTheNetworkType() {
        when(tm.getNetworkType()).thenReturn(TelephonyManager.NETWORK_TYPE_HSPA);
        assertEquals(MobileGeneration.G3, NetworkUtils.mobileGeneration(tm, M));
        verify(tm, never()).getDataNetworkType();
    }

    @Test
    public void unknownNetworkTypeIsUnknown() {
        when(tm.getDataNetworkType()).thenReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        assertEquals(MobileGeneration.UNKNOWN, NetworkUtils.mobileGeneration(tm, LATEST));
    }

    @Test
    public void securityExceptionIsUnknownNotThrown() {
        // Android 11+ throws when READ_PHONE_STATE was revoked between the check and the call
        when(tm.getDataNetworkType()).thenThrow(new SecurityException("READ_PHONE_STATE"));
        when(tm.getNetworkType()).thenThrow(new SecurityException("READ_PHONE_STATE"));
        assertEquals(MobileGeneration.UNKNOWN, NetworkUtils.mobileGeneration(tm, LATEST));
        assertEquals(MobileGeneration.UNKNOWN, NetworkUtils.mobileGeneration(tm, M));
    }
}
