package com.sunchen.netbus.utils;

import static org.junit.Assert.assertEquals;

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
}
