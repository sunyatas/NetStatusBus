package com.sunchen.netbus.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;

import com.sunchen.netbus.type.NetType;

import org.junit.Before;
import org.junit.Test;

/**
 * Drives {@link NetworkUtils#getNetType}, {@link NetworkUtils#isNetworkAvailable},
 * {@link NetworkUtils#isVpnActive}, {@link NetworkUtils#isInternetValidated} and
 * {@link NetworkUtils#isMetered} through a mocked {@link ConnectivityManager} at different
 * SDK levels. On the plain JVM {@code Build.VERSION.SDK_INT} is 0, so without the
 * {@code sdkInt} seam only the pre-6.0 branch would ever run in tests.
 */
@SuppressWarnings("deprecation") // NetworkInfo is the pre-6.0 API under test
public class ConnectivityQueryTest {

    private static final int L = 21;
    private static final int M = 23;
    private static final int LATEST = 35;

    private ConnectivityManager cm;

    @Before
    public void setUp() {
        cm = mock(ConnectivityManager.class);
        when(cm.getAllNetworks()).thenReturn(new Network[0]);
    }

    // ---- getNetType, Android 6.0+ ----

    @Test
    public void noConnectivityManagerIsNone() {
        assertEquals(NetType.NONE, NetworkUtils.getNetType(null, null, LATEST));
        assertFalse(NetworkUtils.isNetworkAvailable(null, LATEST));
        assertFalse(NetworkUtils.isVpnActive(null, LATEST));
    }

    @Test
    public void noActiveNetworkIsNone() {
        assertEquals(NetType.NONE, NetworkUtils.getNetType(cm, null, LATEST));
    }

    @Test
    public void activeNetworkTypeComesFromItsTransport() {
        Network wifi = network(caps(true, Transport.WIFI));
        active(wifi);
        assertEquals(NetType.WIFI, NetworkUtils.getNetType(cm, null, M));

        Network cell = network(caps(true, Transport.CELLULAR));
        active(cell);
        assertEquals(NetType.MOBILE, NetworkUtils.getNetType(cm, null, LATEST));

        Network eth = network(caps(true, Transport.ETHERNET));
        active(eth);
        assertEquals(NetType.ETHERNET, NetworkUtils.getNetType(cm, null, LATEST));
    }

    @Test
    public void activeNetworkWithoutInternetIsNone() {
        active(network(caps(false, Transport.WIFI)));
        assertEquals(NetType.NONE, NetworkUtils.getNetType(cm, null, LATEST));
    }

    @Test
    public void activeNetworkWhoseCapabilitiesAreGoneIsNone() {
        // getNetworkCapabilities returns null for a network that was just torn down
        active(network(null));
        assertEquals(NetType.NONE, NetworkUtils.getNetType(cm, null, LATEST));
    }

    @Test
    public void lostNetworkStillReportedAsActiveIsNone() {
        // #6 #9: in onLost the system may still return the lost Wi-Fi as the active network
        Network wifi = network(caps(true, Transport.WIFI));
        active(wifi);
        assertEquals(NetType.NONE, NetworkUtils.getNetType(cm, wifi, LATEST));
        // an unrelated lost network does not hide the active one
        assertEquals(NetType.WIFI,
                NetworkUtils.getNetType(cm, network(caps(true, Transport.CELLULAR)), LATEST));
    }

    @Test
    public void vpnReportsTheTransportItDeclares() {
        Network vpn = network(caps(true, Transport.VPN, Transport.CELLULAR));
        active(vpn);
        assertEquals(NetType.MOBILE, NetworkUtils.getNetType(cm, null, LATEST));
        verify(cm, never()).getAllNetworks();
    }

    @Test
    public void vpnWithoutTransportUsesTheUnderlyingNetwork() {
        Network vpn = network(caps(true, Transport.VPN));
        Network cell = network(caps(true, Transport.CELLULAR));
        Network wifi = network(caps(true, Transport.WIFI));
        active(vpn);
        when(cm.getAllNetworks()).thenReturn(new Network[]{vpn, cell, wifi});
        assertEquals(NetType.WIFI, NetworkUtils.getNetType(cm, null, LATEST));
    }

    @Test
    public void vpnUnderlyingLookupSkipsTheLostNetwork() {
        Network vpn = network(caps(true, Transport.VPN));
        Network cell = network(caps(true, Transport.CELLULAR));
        Network wifi = network(caps(true, Transport.WIFI));
        active(vpn);
        when(cm.getAllNetworks()).thenReturn(new Network[]{vpn, cell, wifi});
        assertEquals(NetType.MOBILE, NetworkUtils.getNetType(cm, wifi, LATEST));

        when(cm.getAllNetworks()).thenReturn(new Network[]{vpn, wifi});
        assertEquals(NetType.NONE, NetworkUtils.getNetType(cm, wifi, LATEST));
    }

    @Test
    public void vpnUnderlyingLookupIgnoresNetworksWithoutCapabilities() {
        Network vpn = network(caps(true, Transport.VPN));
        Network gone = network(null);
        Network eth = network(caps(true, Transport.ETHERNET));
        active(vpn);
        when(cm.getAllNetworks()).thenReturn(new Network[]{gone, eth});
        assertEquals(NetType.ETHERNET, NetworkUtils.getNetType(cm, null, LATEST));
    }

    // ---- getNetType, before Android 6.0 ----

    @Test
    public void preMarshmallowUsesActiveNetworkInfo() {
        assertEquals(NetType.NONE, NetworkUtils.getNetType(cm, null, L));

        activeInfo(info(ConnectivityManager.TYPE_WIFI, true));
        assertEquals(NetType.WIFI, NetworkUtils.getNetType(cm, null, L));

        activeInfo(info(ConnectivityManager.TYPE_MOBILE, true));
        assertEquals(NetType.MOBILE, NetworkUtils.getNetType(cm, null, L));

        activeInfo(info(ConnectivityManager.TYPE_ETHERNET, true));
        assertEquals(NetType.ETHERNET, NetworkUtils.getNetType(cm, null, L));

        activeInfo(info(ConnectivityManager.TYPE_BLUETOOTH, true));
        assertEquals(NetType.NONE, NetworkUtils.getNetType(cm, null, L));
    }

    @Test
    public void preMarshmallowDisconnectedInfoIsNone() {
        activeInfo(info(ConnectivityManager.TYPE_WIFI, false));
        assertEquals(NetType.NONE, NetworkUtils.getNetType(cm, null, L));
    }

    @Test
    public void preMarshmallowNeverTouchesMarshmallowApis() {
        activeInfo(info(ConnectivityManager.TYPE_WIFI, true));
        NetworkUtils.getNetType(cm, null, L);
        NetworkUtils.isNetworkAvailable(cm, L);
        assertFalse(NetworkUtils.isVpnActive(cm, L));
        verify(cm, never()).getActiveNetwork();
        verify(cm, never()).getAllNetworks();
    }

    // ---- isNetworkAvailable ----

    @Test
    public void availabilityNeedsInternetButNotValidation() {
        assertFalse(NetworkUtils.isNetworkAvailable(cm, LATEST));

        NetworkCapabilities captive = caps(true, Transport.WIFI);
        when(captive.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)).thenReturn(false);
        active(network(captive));
        assertTrue(NetworkUtils.isNetworkAvailable(cm, LATEST));

        active(network(caps(false, Transport.WIFI)));
        assertFalse(NetworkUtils.isNetworkAvailable(cm, LATEST));

        active(network(null));
        assertFalse(NetworkUtils.isNetworkAvailable(cm, LATEST));
    }

    @Test
    public void preMarshmallowAvailabilityLooksForAnyConnectedNetwork() {
        assertFalse(NetworkUtils.isNetworkAvailable(cm, L));

        NetworkInfo down = info(ConnectivityManager.TYPE_WIFI, false);
        when(cm.getAllNetworkInfo()).thenReturn(new NetworkInfo[]{down});
        assertFalse(NetworkUtils.isNetworkAvailable(cm, L));

        NetworkInfo up = info(ConnectivityManager.TYPE_MOBILE, true);
        when(cm.getAllNetworkInfo()).thenReturn(new NetworkInfo[]{down, up});
        assertTrue(NetworkUtils.isNetworkAvailable(cm, L));
    }

    // ---- isVpnActive ----

    @Test
    public void vpnActiveWhenAnyNetworkIsAVpn() {
        Network wifi = network(caps(true, Transport.WIFI));
        Network gone = network(null);
        when(cm.getAllNetworks()).thenReturn(new Network[]{wifi, gone});
        assertFalse(NetworkUtils.isVpnActive(cm, LATEST));

        Network vpn = network(caps(true, Transport.VPN));
        when(cm.getAllNetworks()).thenReturn(new Network[]{wifi, gone, vpn});
        assertTrue(NetworkUtils.isVpnActive(cm, M));
    }

    // ---- isInternetValidated ----

    @Test
    public void validationNeedsAnActiveNetworkWithCapabilities() {
        assertFalse(NetworkUtils.isActiveNetworkValidated(null, LATEST));
        assertFalse(NetworkUtils.isActiveNetworkValidated(cm, LATEST));

        active(network(null));
        assertFalse(NetworkUtils.isActiveNetworkValidated(cm, LATEST));
    }

    @Test
    public void validatedActiveNetworkIsValidated() {
        active(network(validatedCaps(true, false)));
        assertTrue(NetworkUtils.isActiveNetworkValidated(cm, M));
        assertTrue(NetworkUtils.isActiveNetworkValidated(cm, LATEST));
    }

    @Test
    public void captivePortalIsConnectedButNotValidated() {
        active(network(validatedCaps(false, true)));
        assertTrue(NetworkUtils.isNetworkAvailable(cm, LATEST));
        assertFalse(NetworkUtils.isActiveNetworkValidated(cm, LATEST));

        // a captive portal that reports VALIDATED is still not trusted
        active(network(validatedCaps(true, true)));
        assertFalse(NetworkUtils.isActiveNetworkValidated(cm, LATEST));
    }

    @Test
    public void lanOnlyNetworkIsConnectedButNotValidated() {
        active(network(validatedCaps(false, false)));
        assertTrue(NetworkUtils.isNetworkAvailable(cm, LATEST));
        assertFalse(NetworkUtils.isActiveNetworkValidated(cm, LATEST));
    }

    @Test
    public void preMarshmallowValidationFallsBackToAvailability() {
        assertFalse(NetworkUtils.isActiveNetworkValidated(null, L));
        assertFalse(NetworkUtils.isActiveNetworkValidated(cm, L));

        NetworkInfo up = info(ConnectivityManager.TYPE_WIFI, true);
        when(cm.getAllNetworkInfo()).thenReturn(new NetworkInfo[]{up});
        assertTrue(NetworkUtils.isActiveNetworkValidated(cm, L));
        verify(cm, never()).getActiveNetwork();
    }

    // ---- isMetered ----

    @Test
    public void noNetworkCountsAsMetered() {
        assertTrue(NetworkUtils.isActiveNetworkMetered(null, LATEST));
        assertTrue(NetworkUtils.isActiveNetworkMetered(null, L));
        assertTrue(NetworkUtils.isActiveNetworkMetered(cm, LATEST));

        active(network(null));
        assertTrue(NetworkUtils.isActiveNetworkMetered(cm, LATEST));
    }

    @Test
    public void meteredComesFromTheActiveNetworksCapabilities() {
        active(network(meteredCaps(true, false)));
        assertFalse(NetworkUtils.isActiveNetworkMetered(cm, M));

        active(network(meteredCaps(false, false)));
        assertTrue(NetworkUtils.isActiveNetworkMetered(cm, LATEST));
        verify(cm, never()).isActiveNetworkMetered();
    }

    @Test
    public void temporarilyNotMeteredIsPassedTheSdkLevel() {
        active(network(meteredCaps(false, true)));
        assertFalse(NetworkUtils.isActiveNetworkMetered(cm, 30));
        assertTrue(NetworkUtils.isActiveNetworkMetered(cm, 29));
    }

    @Test
    public void preMarshmallowMeteredAsksTheSystem() {
        when(cm.isActiveNetworkMetered()).thenReturn(false);
        assertFalse(NetworkUtils.isActiveNetworkMetered(cm, L));
        when(cm.isActiveNetworkMetered()).thenReturn(true);
        assertTrue(NetworkUtils.isActiveNetworkMetered(cm, L));
        verify(cm, never()).getActiveNetwork();
    }

    // ---- helpers ----

    // Arguments are built (and their own stubs finished) before stubbing cm.
    private void active(Network network) {
        when(cm.getActiveNetwork()).thenReturn(network);
    }

    private void activeInfo(NetworkInfo info) {
        when(cm.getActiveNetworkInfo()).thenReturn(info);
    }

    private enum Transport {
        WIFI(NetworkCapabilities.TRANSPORT_WIFI),
        CELLULAR(NetworkCapabilities.TRANSPORT_CELLULAR),
        ETHERNET(NetworkCapabilities.TRANSPORT_ETHERNET),
        VPN(NetworkCapabilities.TRANSPORT_VPN);

        final int value;

        Transport(int value) {
            this.value = value;
        }
    }

    private Network network(NetworkCapabilities caps) {
        Network network = mock(Network.class);
        when(cm.getNetworkCapabilities(network)).thenReturn(caps);
        return network;
    }

    private static NetworkCapabilities caps(boolean internet, Transport... transports) {
        NetworkCapabilities caps = mock(NetworkCapabilities.class);
        when(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(internet);
        for (Transport t : transports) {
            when(caps.hasTransport(t.value)).thenReturn(true);
        }
        return caps;
    }

    private static NetworkCapabilities validatedCaps(boolean validated, boolean captivePortal) {
        NetworkCapabilities caps = caps(true, Transport.WIFI);
        when(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)).thenReturn(validated);
        when(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL))
                .thenReturn(captivePortal);
        return caps;
    }

    private static NetworkCapabilities meteredCaps(boolean notMetered, boolean temporarilyNotMetered) {
        NetworkCapabilities caps = caps(true, Transport.WIFI);
        when(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)).thenReturn(notMetered);
        when(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED))
                .thenReturn(temporarilyNotMetered);
        return caps;
    }

    private static NetworkInfo info(int type, boolean connected) {
        NetworkInfo info = mock(NetworkInfo.class);
        when(info.getType()).thenReturn(type);
        when(info.isConnected()).thenReturn(connected);
        when(info.getState()).thenReturn(
                connected ? NetworkInfo.State.CONNECTED : NetworkInfo.State.DISCONNECTED);
        return info;
    }
}
