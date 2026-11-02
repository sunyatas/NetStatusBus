package com.sunchen.netbus;

import static org.junit.Assert.assertEquals;

import com.sunchen.netbus.annotation.NetSubscribe;
import com.sunchen.netbus.type.Mode;
import com.sunchen.netbus.type.NetType;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pins down which {@link Mode} fires for every network transition and for the initial
 * callback on register, so the documented semantics cannot drift silently.
 *
 * <p>覆盖所有模式 × 所有网络切换（含 ETHERNET），以及注册时的首次回调。
 */
public class ModeMatrixTest {

    /** One method per mode; each records which mode fired. */
    public static class AllModesSubscriber {
        final Set<Mode> fired = EnumSet.noneOf(Mode.class);
        final List<NetType> received = new ArrayList<>();

        @NetSubscribe(mode = Mode.AUTO)
        public void auto(NetType type) { record(Mode.AUTO, type); }

        @NetSubscribe(mode = Mode.WIFI)
        public void wifi(NetType type) { record(Mode.WIFI, type); }

        @NetSubscribe(mode = Mode.WIFI_CONNECT)
        public void wifiConnect(NetType type) { record(Mode.WIFI_CONNECT, type); }

        @NetSubscribe(mode = Mode.MOBILE)
        public void mobile(NetType type) { record(Mode.MOBILE, type); }

        @NetSubscribe(mode = Mode.MOBILE_CONNECT)
        public void mobileConnect(NetType type) { record(Mode.MOBILE_CONNECT, type); }

        @NetSubscribe(mode = Mode.NONE)
        public void none(NetType type) { record(Mode.NONE, type); }

        private void record(Mode mode, NetType type) {
            fired.add(mode);
            received.add(type);
        }

        void clear() {
            fired.clear();
            received.clear();
        }
    }

    private static final NetType W = NetType.WIFI;
    private static final NetType M = NetType.MOBILE;
    private static final NetType E = NetType.ETHERNET;
    private static final NetType N = NetType.NONE;

    private NetStatusReceiver receiver;

    @Before
    public void setUp() {
        receiver = new NetStatusReceiver(new NetStatusReceiver.Dispatcher() {
            @Override
            public void dispatch(Runnable runnable) {
                runnable.run();
            }
        });
        NetStatusReceiver.clearMethodCacheForTest();
    }

    @Test
    public void everyTransitionFiresExactlyTheExpectedModes() {
        List<String> failures = new ArrayList<>();
        for (NetType previous : NetType.values()) {
            for (NetType next : NetType.values()) {
                if (previous == next) {
                    continue;
                }
                setUp();
                AllModesSubscriber s = new AllModesSubscriber();
                receiver.post(previous);
                receiver.registerObserver(s);
                s.clear();

                receiver.post(next);

                Set<Mode> expected = expectedForTransition(previous, next);
                if (!expected.equals(s.fired)) {
                    failures.add(previous + "->" + next + ": expected " + expected + " but got " + s.fired);
                }
                // Every callback receives the new type, never the previous one.
                for (NetType t : s.received) {
                    if (t != next) {
                        failures.add(previous + "->" + next + ": a callback received " + t);
                    }
                }
            }
        }
        assertEquals("Mode mismatches:\n" + String.join("\n", failures), Collections.emptyList(), failures);
    }

    @Test
    public void initialCallbackOnRegisterFiresOnlyModesMatchingCurrentType() {
        for (NetType current : NetType.values()) {
            setUp();
            receiver.post(current);
            AllModesSubscriber s = new AllModesSubscriber();
            receiver.registerObserver(s);
            assertEquals("current=" + current, expectedForCurrent(current), s.fired);
        }
    }

    @Test
    public void wifiModeCoversBothConnectAndDisconnectOfWifiOnly() {
        // Spelled out explicitly as documentation of the WIFI vs WIFI_CONNECT difference.
        assertEquals(sorted("WIFI->MOBILE", "WIFI->ETHERNET", "WIFI->NONE",
                        "MOBILE->WIFI", "ETHERNET->WIFI", "NONE->WIFI"),
                transitionsFiring(Mode.WIFI));
        assertEquals(sorted("MOBILE->WIFI", "ETHERNET->WIFI", "NONE->WIFI"),
                transitionsFiring(Mode.WIFI_CONNECT));
    }

    @Test
    public void mobileModeCoversBothConnectAndDisconnectOfMobileOnly() {
        assertEquals(sorted("MOBILE->WIFI", "MOBILE->ETHERNET", "MOBILE->NONE",
                        "WIFI->MOBILE", "ETHERNET->MOBILE", "NONE->MOBILE"),
                transitionsFiring(Mode.MOBILE));
        assertEquals(sorted("WIFI->MOBILE", "ETHERNET->MOBILE", "NONE->MOBILE"),
                transitionsFiring(Mode.MOBILE_CONNECT));
    }

    @Test
    public void noneModeFiresOnEveryDisconnect() {
        assertEquals(sorted("WIFI->NONE", "MOBILE->NONE", "ETHERNET->NONE"),
                transitionsFiring(Mode.NONE));
    }

    // ---- expectations, written as plain rules ----

    private static Set<Mode> expectedForTransition(NetType previous, NetType next) {
        Set<Mode> modes = EnumSet.of(Mode.AUTO);
        if (previous == W || next == W) modes.add(Mode.WIFI);
        if (next == W) modes.add(Mode.WIFI_CONNECT);
        if (previous == M || next == M) modes.add(Mode.MOBILE);
        if (next == M) modes.add(Mode.MOBILE_CONNECT);
        if (next == N) modes.add(Mode.NONE);
        return modes;
    }

    private static Set<Mode> expectedForCurrent(NetType current) {
        Set<Mode> modes = EnumSet.of(Mode.AUTO);
        if (current == W) modes.addAll(Arrays.asList(Mode.WIFI, Mode.WIFI_CONNECT));
        if (current == M) modes.addAll(Arrays.asList(Mode.MOBILE, Mode.MOBILE_CONNECT));
        if (current == N) modes.add(Mode.NONE);
        return modes;
    }

    /** Runs every transition against the real receiver and collects those where {@code mode} fired. */
    private Set<String> transitionsFiring(Mode mode) {
        Set<String> result = new TreeSet<>();
        for (NetType previous : Arrays.asList(W, M, E, N)) {
            for (NetType next : Arrays.asList(W, M, E, N)) {
                if (previous == next) {
                    continue;
                }
                setUp();
                AllModesSubscriber s = new AllModesSubscriber();
                receiver.post(previous);
                receiver.registerObserver(s);
                s.clear();
                receiver.post(next);
                if (s.fired.contains(mode)) {
                    result.add(previous + "->" + next);
                }
            }
        }
        return result;
    }

    private static Set<String> sorted(String... items) {
        return new TreeSet<>(Arrays.asList(items));
    }
}
