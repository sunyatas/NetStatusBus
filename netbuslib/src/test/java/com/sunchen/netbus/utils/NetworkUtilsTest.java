package com.sunchen.netbus.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.ContextThemeWrapper;

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
    public void mapsEveryTelephonyNetworkType() {
        // TelephonyManager.NETWORK_TYPE_* 常量值 0..20 的完整对照表
        MobileGeneration[] expected = {
                MobileGeneration.UNKNOWN, // 0 UNKNOWN
                MobileGeneration.G2,      // 1 GPRS
                MobileGeneration.G2,      // 2 EDGE
                MobileGeneration.G3,      // 3 UMTS
                MobileGeneration.G2,      // 4 CDMA
                MobileGeneration.G3,      // 5 EVDO_0
                MobileGeneration.G3,      // 6 EVDO_A
                MobileGeneration.G2,      // 7 1xRTT
                MobileGeneration.G3,      // 8 HSDPA
                MobileGeneration.G3,      // 9 HSUPA
                MobileGeneration.G3,      // 10 HSPA
                MobileGeneration.G2,      // 11 IDEN
                MobileGeneration.G3,      // 12 EVDO_B
                MobileGeneration.G4,      // 13 LTE
                MobileGeneration.G3,      // 14 EHRPD
                MobileGeneration.G3,      // 15 HSPAP
                MobileGeneration.G2,      // 16 GSM
                MobileGeneration.G3,      // 17 TD_SCDMA
                MobileGeneration.G4,      // 18 IWLAN
                MobileGeneration.G4,      // 19 LTE_CA
                MobileGeneration.G5,      // 20 NR
        };
        for (int type = 0; type < expected.length; type++) {
            assertEquals("NETWORK_TYPE " + type, expected[type], NetworkUtils.fromTelephonyType(type));
        }
        assertEquals(MobileGeneration.UNKNOWN, NetworkUtils.fromTelephonyType(-1));
        assertEquals(MobileGeneration.UNKNOWN, NetworkUtils.fromTelephonyType(21));
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

    @Test
    public void openSettingUsesForResultOnlyForActivityWithNonNegativeCode() {
        assertTrue(NetworkUtils.shouldStartForResult(true, 0));
        assertTrue(NetworkUtils.shouldStartForResult(true, 42));
        // 负数 requestCode 表示不需要结果
        assertFalse(NetworkUtils.shouldStartForResult(true, -1));
        // 没有 Activity（例如 Application）时不能 startActivityForResult
        assertFalse(NetworkUtils.shouldStartForResult(false, 0));
        assertFalse(NetworkUtils.shouldStartForResult(false, -1));
    }

    @Test
    public void findActivityReturnsPlainActivity() {
        Activity activity = mock(Activity.class);
        assertSame(activity, NetworkUtils.findActivity(activity));
    }

    @Test
    public void findActivityUnwrapsContextWrapper() {
        Activity activity = mock(Activity.class);
        ContextWrapper wrapper = mock(ContextWrapper.class);
        when(wrapper.getBaseContext()).thenReturn(activity);
        assertSame(activity, NetworkUtils.findActivity(wrapper));
    }

    @Test
    public void findActivityUnwrapsContextThemeWrapper() {
        Activity activity = mock(Activity.class);
        ContextThemeWrapper wrapper = mock(ContextThemeWrapper.class);
        when(wrapper.getBaseContext()).thenReturn(activity);
        assertSame(activity, NetworkUtils.findActivity(wrapper));
    }

    @Test
    public void findActivityUnwrapsNestedWrappers() {
        Activity activity = mock(Activity.class);
        ContextWrapper inner = mock(ContextWrapper.class);
        ContextThemeWrapper outer = mock(ContextThemeWrapper.class);
        when(inner.getBaseContext()).thenReturn(activity);
        when(outer.getBaseContext()).thenReturn(inner);
        assertSame(activity, NetworkUtils.findActivity(outer));
    }

    @Test
    public void findActivityReturnsNullForApplication() {
        Application application = mock(Application.class);
        Context base = mock(Context.class);
        when(application.getBaseContext()).thenReturn(base);
        assertNull(NetworkUtils.findActivity(application));
    }

    @Test
    public void findActivityReturnsNullForWrapperAroundNonActivity() {
        Context notActivity = mock(Context.class);
        ContextWrapper wrapper = mock(ContextWrapper.class);
        when(wrapper.getBaseContext()).thenReturn(notActivity);
        assertNull(NetworkUtils.findActivity(wrapper));
    }

    @Test
    public void findActivityReturnsNullForSelfReferentialWrapper() {
        ContextWrapper wrapper = mock(ContextWrapper.class);
        when(wrapper.getBaseContext()).thenReturn(wrapper);
        assertNull(NetworkUtils.findActivity(wrapper));
    }

    @Test(timeout = 1000)
    public void findActivityReturnsNullForWrapperCycleWithoutHanging() {
        ContextWrapper first = mock(ContextWrapper.class);
        ContextWrapper second = mock(ContextWrapper.class);
        when(first.getBaseContext()).thenReturn(second);
        when(second.getBaseContext()).thenReturn(first);
        assertNull(NetworkUtils.findActivity(first));
    }
}
