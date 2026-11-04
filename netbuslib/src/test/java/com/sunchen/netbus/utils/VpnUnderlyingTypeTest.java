package com.sunchen.netbus.utils;

import static org.junit.Assert.assertEquals;

import com.sunchen.netbus.type.NetType;
import com.sunchen.netbus.utils.NetworkUtils.Transports;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

/**
 * VPN 没有声明底层传输类型时（主要是 Android 6.0–8.1），按其他网络推断底层类型。
 */
public class VpnUnderlyingTypeTest {

    private static final Transports VPN = new Transports(true, true, false, false, false);
    private static final Transports WIFI = new Transports(false, true, true, false, false);
    private static final Transports CELL = new Transports(false, true, false, true, false);
    private static final Transports ETH = new Transports(false, true, false, false, true);
    private static final Transports WIFI_NO_INTERNET = new Transports(false, false, true, false, false);

    @Test
    public void noOtherNetworkMeansNone() {
        assertEquals(NetType.NONE, NetworkUtils.underlyingTypeOf(Collections.<Transports>emptyList()));
        assertEquals(NetType.NONE, NetworkUtils.underlyingTypeOf(Collections.singletonList(VPN)));
    }

    @Test
    public void singleUnderlyingNetworkIsReported() {
        assertEquals(NetType.WIFI, NetworkUtils.underlyingTypeOf(Arrays.asList(VPN, WIFI)));
        assertEquals(NetType.MOBILE, NetworkUtils.underlyingTypeOf(Arrays.asList(VPN, CELL)));
        assertEquals(NetType.ETHERNET, NetworkUtils.underlyingTypeOf(Arrays.asList(VPN, ETH)));
    }

    @Test
    public void wifiWinsOverAlwaysOnMobileDataRegardlessOfOrder() {
        assertEquals(NetType.WIFI, NetworkUtils.underlyingTypeOf(Arrays.asList(CELL, VPN, WIFI)));
        assertEquals(NetType.WIFI, NetworkUtils.underlyingTypeOf(Arrays.asList(WIFI, VPN, CELL)));
        assertEquals(NetType.MOBILE, NetworkUtils.underlyingTypeOf(Arrays.asList(ETH, CELL, VPN)));
    }

    @Test
    public void networksWithoutInternetAreIgnored() {
        assertEquals(NetType.MOBILE, NetworkUtils.underlyingTypeOf(Arrays.asList(WIFI_NO_INTERNET, VPN, CELL)));
        assertEquals(NetType.NONE, NetworkUtils.underlyingTypeOf(Arrays.asList(WIFI_NO_INTERNET, VPN)));
    }

    @Test
    public void otherVpnsAreNotTreatedAsUnderlyingNetworks() {
        Transports vpnClaimingWifi = new Transports(true, true, true, false, false);
        assertEquals(NetType.MOBILE, NetworkUtils.underlyingTypeOf(Arrays.asList(vpnClaimingWifi, CELL)));
    }
}
