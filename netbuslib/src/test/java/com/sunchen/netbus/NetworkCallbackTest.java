package com.sunchen.netbus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Observer;

import com.sunchen.netbus.type.NetType;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The system network callbacks ({@link DefaultNetworkCallbackImpl} on Android 7.0+,
 * {@link NetworkCallbackImpl} below) driven with a mocked {@link ConnectivityManager}, and a
 * bus created with {@code new NetStatusBus()} initialized without the singleton.
 */
@SuppressWarnings("deprecation") // NetworkInfo is the pre-6.0 API under test
public class NetworkCallbackTest {

    private static final int L = 21;
    private static final int M = 23;
    private static final int N = 24;
    private static final int LATEST = 35;

    private static final NetStatusReceiver.Dispatcher SYNC = new NetStatusReceiver.Dispatcher() {
        @Override
        public void dispatch(Runnable runnable) {
            runnable.run();
        }
    };

    /** LiveData.setValue checks for the main thread, which does not exist on the JVM. */
    @Rule
    public final InstantTaskExecutorRule instantTaskExecutor = new InstantTaskExecutorRule();

    private ConnectivityManager cm;
    private NetStatusReceiver receiver;
    private final List<NetType> delivered = new ArrayList<>();

    @Before
    public void setUp() {
        cm = mock(ConnectivityManager.class);
        when(cm.getAllNetworks()).thenReturn(new Network[0]);
        receiver = new NetStatusReceiver(SYNC);
    }

    private void record(NetType initial) {
        receiver.setInitialNetType(initial);
        receiver.setNetTypeListener(new NetStatusReceiver.NetTypeListener() {
            @Override
            public void onNetTypeChanged(NetType netType) {
                delivered.add(netType);
            }
        });
        delivered.clear(); // drop the immediate delivery of the initial type
    }

    // ---- DefaultNetworkCallbackImpl (Android 7.0+) ----

    @Test
    public void defaultCallbackTypesOnAvailableFromThatNetworksCapabilities() {
        record(NetType.NONE);
        Network wifi = network(caps(true, Transport.WIFI));
        DefaultNetworkCallbackImpl callback = new DefaultNetworkCallbackImpl(receiver, cm, N);

        callback.onAvailable(wifi);

        verify(cm).getNetworkCapabilities(wifi);
        assertEquals(Arrays.asList(NetType.WIFI), delivered);
        assertEquals(NetType.WIFI, receiver.getCurrentNetType());
    }

    @Test
    public void defaultCallbackSwitchWithoutOnLostAndLateOnLostIsIgnored() {
        // #4: Wi-Fi off, the system moves the default network to cellular with only onAvailable
        record(NetType.NONE);
        Network wifi = network(caps(true, Transport.WIFI));
        Network cell = network(caps(true, Transport.CELLULAR));
        DefaultNetworkCallbackImpl callback = new DefaultNetworkCallbackImpl(receiver, cm, LATEST);

        callback.onAvailable(wifi);
        callback.onAvailable(cell);
        callback.onLost(wifi); // late, for the previous default network
        callback.onCapabilitiesChanged(wifi, caps(true, Transport.WIFI));
        callback.onLost(cell);

        assertEquals(Arrays.asList(NetType.WIFI, NetType.MOBILE, NetType.NONE), delivered);
    }

    @Test
    public void defaultCallbackDeliversRepeatedCapabilityChangesOnce() {
        record(NetType.NONE);
        Network wifi = network(caps(true, Transport.WIFI));
        DefaultNetworkCallbackImpl callback = new DefaultNetworkCallbackImpl(receiver, cm, LATEST);

        callback.onAvailable(wifi);
        callback.onCapabilitiesChanged(wifi, caps(true, Transport.WIFI));
        callback.onCapabilitiesChanged(wifi, caps(true, Transport.WIFI));

        assertEquals(Arrays.asList(NetType.WIFI), delivered);
    }

    @Test
    public void defaultCallbackTreatsLostInternetOrVanishedCapabilitiesAsNone() {
        record(NetType.WIFI);
        Network gone = network(null); // torn down before getNetworkCapabilities answered
        DefaultNetworkCallbackImpl callback = new DefaultNetworkCallbackImpl(receiver, cm, LATEST);
        callback.onAvailable(gone);
        assertEquals(Arrays.asList(NetType.NONE), delivered);

        Network wifi = network(caps(true, Transport.WIFI));
        callback.onAvailable(wifi);
        callback.onCapabilitiesChanged(wifi, caps(false, Transport.WIFI));
        assertEquals(Arrays.asList(NetType.NONE, NetType.WIFI, NetType.NONE), delivered);
    }

    @Test
    public void defaultCallbackResolvesVpnThroughTheBusManager() {
        record(NetType.NONE);
        Network cell = network(caps(true, Transport.CELLULAR));
        Network vpn = network(caps(true, Transport.VPN));
        when(cm.getAllNetworks()).thenReturn(new Network[]{vpn, cell});
        DefaultNetworkCallbackImpl callback = new DefaultNetworkCallbackImpl(receiver, cm, LATEST);

        callback.onAvailable(vpn);

        assertEquals(Arrays.asList(NetType.MOBILE), delivered);
    }

    // ---- NetworkCallbackImpl (Android 5.0 - 6.0) ----

    @Test
    public void legacyCallbackOnLostIgnoresTheStillActiveLostNetwork() {
        // #6 #9: in onLost the system may still report the lost Wi-Fi as the active network
        record(NetType.NONE);
        Network wifi = network(caps(true, Transport.WIFI));
        when(cm.getActiveNetwork()).thenReturn(wifi);
        NetworkCallbackImpl callback = new NetworkCallbackImpl(receiver, cm, M);

        callback.onAvailable(wifi);
        callback.onLost(wifi);

        assertEquals(Arrays.asList(NetType.WIFI, NetType.NONE), delivered);
    }

    @Test
    public void legacyCallbackReadsTheActiveNetworkOnEveryEvent() {
        record(NetType.NONE);
        Network wifi = network(caps(true, Transport.WIFI));
        Network cell = network(caps(true, Transport.CELLULAR));
        NetworkCallbackImpl callback = new NetworkCallbackImpl(receiver, cm, M);

        when(cm.getActiveNetwork()).thenReturn(wifi);
        callback.onAvailable(cell); // the event's network does not matter, the active one does
        when(cm.getActiveNetwork()).thenReturn(cell);
        callback.onCapabilitiesChanged(cell, caps(true, Transport.CELLULAR));
        callback.onCapabilitiesChanged(cell, caps(true, Transport.CELLULAR));

        assertEquals(Arrays.asList(NetType.WIFI, NetType.MOBILE), delivered);
    }

    @Test
    public void legacyCallbackUsesNetworkInfoBeforeAndroid6() {
        record(NetType.NONE);
        NetworkInfo info = info(ConnectivityManager.TYPE_WIFI, true);
        when(cm.getActiveNetworkInfo()).thenReturn(info);
        NetworkCallbackImpl callback = new NetworkCallbackImpl(receiver, cm, L);

        callback.onAvailable(mock(Network.class));

        assertEquals(Arrays.asList(NetType.WIFI), delivered);
    }

    // ---- a separate bus, without the singleton ----

    @Test
    public void separateBusInitsAndTracksWithoutTheSingleton() {
        // Before, init and the callbacks read the singleton's application, so a bus made with
        // new NetStatusBus() threw IllegalStateException unless getInstance() was initialized too.
        // (The singleton cannot even be created on the JVM, so touching it here would fail.)
        Network wifi = network(caps(true, Transport.WIFI));
        Network cell = network(caps(true, Transport.CELLULAR));
        when(cm.getActiveNetwork()).thenReturn(wifi);
        Application app = mock(Application.class);
        when(app.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(cm);
        NetStatusBus bus = new NetStatusBus(receiver);

        bus.init(app, LATEST);

        assertTrue(bus.isInitialized());
        assertEquals(NetType.WIFI, bus.getCurrentNetType());
        assertEquals(NetType.WIFI, bus.getNetTypeLiveData().getValue());
        ArgumentCaptor<ConnectivityManager.NetworkCallback> registered =
                ArgumentCaptor.forClass(ConnectivityManager.NetworkCallback.class);
        verify(cm).registerDefaultNetworkCallback(registered.capture());

        final List<NetType> seen = new ArrayList<>();
        bus.getNetTypeLiveData().observeForever(new Observer<NetType>() {
            @Override
            public void onChanged(NetType netType) {
                seen.add(netType);
            }
        });
        seen.clear();
        registered.getValue().onAvailable(cell);

        assertEquals(NetType.MOBILE, bus.getCurrentNetType());
        assertEquals(Arrays.asList(NetType.MOBILE), seen);
    }

    @Test
    public void secondInitDoesNotRegisterAgain() {
        Application app = mock(Application.class);
        when(app.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(cm);
        NetStatusBus bus = new NetStatusBus(receiver);

        bus.init(app, LATEST);
        bus.init(app, LATEST);

        verify(cm, times(1))
                .registerDefaultNetworkCallback(any(ConnectivityManager.NetworkCallback.class));
    }

    // ---- helpers ----

    private enum Transport {
        WIFI(NetworkCapabilities.TRANSPORT_WIFI),
        CELLULAR(NetworkCapabilities.TRANSPORT_CELLULAR),
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

    private static NetworkInfo info(int type, boolean connected) {
        NetworkInfo info = mock(NetworkInfo.class);
        when(info.getType()).thenReturn(type);
        when(info.isConnected()).thenReturn(connected);
        return info;
    }
}
