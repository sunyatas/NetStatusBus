package com.sunchen.netbus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.sunchen.netbus.type.NetType;

import org.junit.Test;

public class DefaultNetworkTrackerTest {

    private static final Object WIFI = "wifi";
    private static final Object WIFI_2 = "wifi-reconnected";
    private static final Object CELL = "cell";

    @Test
    public void switchToMobileIsReportedImmediatelyWithoutOnLost() {
        // #4：WIFI 断开后系统直接把默认网络切到移动网络，只回调新网络的 onAvailable
        DefaultNetworkTracker tracker = new DefaultNetworkTracker();
        assertEquals(NetType.WIFI, tracker.onAvailable(WIFI, NetType.WIFI));
        assertEquals(NetType.MOBILE, tracker.onAvailable(CELL, NetType.MOBILE));
    }

    @Test
    public void lateOnLostOfPreviousNetworkIsIgnored() {
        DefaultNetworkTracker tracker = new DefaultNetworkTracker();
        tracker.onAvailable(WIFI, NetType.WIFI);
        tracker.onAvailable(CELL, NetType.MOBILE);
        assertNull(tracker.onLost(WIFI));
        assertNull(tracker.onCapabilitiesChanged(WIFI, NetType.WIFI));
    }

    @Test
    public void wifiOffOnOffSequence() {
        // #11：断开 -> 重连 -> 再断开，每一步都应该正确上报
        DefaultNetworkTracker tracker = new DefaultNetworkTracker();
        assertEquals(NetType.WIFI, tracker.onAvailable(WIFI, NetType.WIFI));
        assertEquals(NetType.NONE, tracker.onLost(WIFI));
        assertEquals(NetType.WIFI, tracker.onAvailable(WIFI_2, NetType.WIFI));
        assertEquals(NetType.NONE, tracker.onLost(WIFI_2));
        assertNull(tracker.onLost(WIFI_2));
    }

    @Test
    public void capabilitiesOfCurrentNetworkAreReported() {
        DefaultNetworkTracker tracker = new DefaultNetworkTracker();
        // onCapabilitiesChanged 先于 onAvailable 到达时也能识别
        assertEquals(NetType.WIFI, tracker.onCapabilitiesChanged(WIFI, NetType.WIFI));
        assertEquals(NetType.WIFI, tracker.onAvailable(WIFI, NetType.WIFI));
        assertEquals(NetType.NONE, tracker.onCapabilitiesChanged(WIFI, NetType.NONE));
        assertEquals(NetType.NONE, tracker.onLost(WIFI));
    }
}
