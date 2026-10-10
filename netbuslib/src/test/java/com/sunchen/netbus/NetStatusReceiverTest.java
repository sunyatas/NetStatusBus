package com.sunchen.netbus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.sunchen.netbus.annotation.NetSubscribe;
import com.sunchen.netbus.type.Mode;
import com.sunchen.netbus.type.NetType;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class NetStatusReceiverTest {

    /** 记录所有交给 dispatcher 的任务，可选择立即执行 */
    private static class RecordingDispatcher implements NetStatusReceiver.Dispatcher {
        final List<Runnable> pending = new ArrayList<>();
        boolean runImmediately = true;

        @Override
        public synchronized void dispatch(Runnable runnable) {
            if (runImmediately) {
                runnable.run();
            } else {
                pending.add(runnable);
            }
        }

        synchronized void runPending() {
            List<Runnable> copy = new ArrayList<>(pending);
            pending.clear();
            for (Runnable r : copy) {
                r.run();
            }
        }
    }

    public static class Subscriber {
        final List<String> calls = new ArrayList<>();

        @NetSubscribe
        public void auto(NetType type) {
            calls.add("AUTO:" + type);
        }

        @NetSubscribe(mode = Mode.NONE)
        public void none() {
            calls.add("NONE");
        }

        @NetSubscribe(mode = Mode.WIFI_CONNECT)
        public void wifiConnect() {
            calls.add("WIFI_CONNECT");
        }

        @NetSubscribe(mode = Mode.MOBILE_CONNECT)
        public void mobileConnect() {
            calls.add("MOBILE_CONNECT");
        }
    }

    private RecordingDispatcher dispatcher;
    private NetStatusReceiver receiver;

    @Before
    public void setUp() {
        dispatcher = new RecordingDispatcher();
        receiver = new NetStatusReceiver(dispatcher);
    }

    @Test
    public void registerDeliversCurrentStateImmediately() {
        Subscriber s = new Subscriber();
        receiver.registerObserver(s);
        assertEquals(2, s.calls.size());
        assertTrue(s.calls.contains("AUTO:NONE"));
        assertTrue(s.calls.contains("NONE"));
    }

    @Test
    public void modeFilteringAndNoFallThrough() {
        Subscriber s = new Subscriber();
        receiver.registerObserver(s);
        s.calls.clear();

        receiver.post(NetType.WIFI);
        assertEquals(2, s.calls.size());
        assertTrue(s.calls.contains("AUTO:WIFI"));
        assertTrue(s.calls.contains("WIFI_CONNECT"));

        s.calls.clear();
        receiver.post(NetType.MOBILE);
        assertEquals(2, s.calls.size());
        assertTrue(s.calls.contains("MOBILE_CONNECT"));
    }

    @Test
    public void unregisteredSubscriberIsNotCalled() {
        Subscriber s = new Subscriber();
        receiver.registerObserver(s);
        s.calls.clear();
        receiver.unRegisterObserver(s);
        receiver.post(NetType.WIFI);
        assertTrue(s.calls.isEmpty());
    }

    @Test
    public void unregisterBeforePendingDispatchSkipsSubscriber() {
        Subscriber s = new Subscriber();
        receiver.registerObserver(s);
        s.calls.clear();
        dispatcher.runImmediately = false;
        receiver.post(NetType.WIFI);
        receiver.unRegisterObserver(s);
        dispatcher.runPending();
        assertTrue(s.calls.isEmpty());
    }

    @Test
    public void callbacksRunThroughDispatcher() {
        dispatcher.runImmediately = false;
        Subscriber s = new Subscriber();
        receiver.registerObserver(s);
        receiver.post(NetType.WIFI);
        assertTrue("callbacks must not run on the posting thread", s.calls.isEmpty());
        dispatcher.runPending();
        assertTrue(s.calls.contains("AUTO:WIFI"));
    }

    @Test
    public void unRegisterAllObserverCanBeCalledRepeatedlyAndReused() {
        receiver.unRegisterAllObserver();
        receiver.registerObserver(new Subscriber());
        receiver.unRegisterAllObserver();
        receiver.unRegisterAllObserver();
        receiver.unRegisterObserver(new Object());
        Subscriber s = new Subscriber();
        receiver.registerObserver(s);
        s.calls.clear();
        receiver.post(NetType.MOBILE);
        assertTrue(s.calls.contains("AUTO:MOBILE"));
    }

    @Test
    public void concurrentRegisterAndPostDoesNotThrow() throws Exception {
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicInteger counter = new AtomicInteger();
        final CountDownLatch done = new CountDownLatch(2);
        Thread poster = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (int i = 0; i < 2000; i++) {
                        receiver.post(i % 2 == 0 ? NetType.WIFI : NetType.NONE);
                    }
                } catch (Throwable t) {
                    error.set(t);
                } finally {
                    done.countDown();
                }
            }
        });
        Thread registrar = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (int i = 0; i < 2000; i++) {
                        Object s = new Object() {
                            @NetSubscribe
                            public void onChange() {
                                counter.incrementAndGet();
                            }
                        };
                        receiver.registerObserver(s);
                        receiver.unRegisterObserver(s);
                    }
                } catch (Throwable t) {
                    error.set(t);
                } finally {
                    done.countDown();
                }
            }
        });
        poster.start();
        registrar.start();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
    }

    public static class WifiSubscriber {
        final java.util.List<String> calls = new java.util.ArrayList<>();

        @NetSubscribe(mode = Mode.WIFI)
        public void onWifi(NetType type) {
            calls.add("WIFI:" + type);
        }
    }

    @Test
    public void wifiModeIgnoresMobileDrop() {
        WifiSubscriber s = new WifiSubscriber();
        receiver.post(NetType.MOBILE);
        receiver.registerObserver(s);
        s.calls.clear();
        receiver.post(NetType.NONE);
        assertTrue(s.calls.isEmpty());
        receiver.post(NetType.WIFI);
        receiver.post(NetType.NONE);
        assertEquals(java.util.Arrays.asList("WIFI:WIFI", "WIFI:NONE"), s.calls);
    }

    @Test
    public void postIfChangedSkipsDuplicates() {
        WifiSubscriber s = new WifiSubscriber();
        receiver.registerObserver(s);
        s.calls.clear();
        receiver.postIfChanged(NetType.WIFI);
        receiver.postIfChanged(NetType.WIFI);
        assertEquals(1, s.calls.size());
    }
}
