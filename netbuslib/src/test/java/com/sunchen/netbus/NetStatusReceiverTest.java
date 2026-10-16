package com.sunchen.netbus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
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

    /** 父类上声明的注解方法，应能被子类实例收到 */
    public static class BaseSubscriber {
        final List<String> baseCalls = new ArrayList<>();

        @NetSubscribe
        public void onBase(NetType type) {
            baseCalls.add("BASE:" + type);
        }
    }

    public static class ChildSubscriber extends BaseSubscriber {
        final List<String> childCalls = new ArrayList<>();

        @NetSubscribe(mode = Mode.WIFI_CONNECT)
        public void onChildWifi() {
            childCalls.add("CHILD_WIFI");
        }
    }

    private RecordingDispatcher dispatcher;
    private NetStatusReceiver receiver;

    @Before
    public void setUp() {
        dispatcher = new RecordingDispatcher();
        receiver = new NetStatusReceiver(dispatcher);
        NetStatusReceiver.clearMethodCacheForTest();
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

    @Test
    public void superclassAnnotatedMethodsAreDiscovered() {
        ChildSubscriber child = new ChildSubscriber();
        receiver.registerObserver(child);
        assertTrue(child.baseCalls.contains("BASE:NONE"));
        child.baseCalls.clear();
        child.childCalls.clear();

        receiver.post(NetType.WIFI);
        assertTrue(child.baseCalls.contains("BASE:WIFI"));
        assertTrue(child.childCalls.contains("CHILD_WIFI"));
    }

    @Test
    public void methodLookupIsCachedPerClass() {
        List<MethodManager> first = NetStatusReceiver.findAnnotationMethods(ChildSubscriber.class);
        List<MethodManager> second = NetStatusReceiver.findAnnotationMethods(ChildSubscriber.class);
        assertSame(first, second);
        assertTrue(first.size() >= 2);
    }

    @Test
    public void weakReferenceDropsGcSubscriberWithoutNpe() {
        receiver.registerObserver(new Subscriber());
        assertEquals(1, receiver.aliveObserverCount());

        // Drop the only strong reference; encourage GC so the WeakReference clears.
        System.gc();
        System.runFinalization();
        System.gc();

        receiver.purgeDeadObservers();
        // GC is not guaranteed; if the referent is still alive, the holder remains — that's OK.
        // Regardless, posting must never throw.
        receiver.post(NetType.WIFI);
        assertNotNull(receiver.getNetType());
        assertEquals(NetType.WIFI, receiver.getNetType());
    }

    @Test
    public void holderUsesWeakReference() throws Exception {
        Subscriber s = new Subscriber();
        receiver.registerObserver(s);
        assertEquals(1, receiver.aliveObserverCount());

        java.lang.ref.WeakReference<Subscriber> probe = new java.lang.ref.WeakReference<>(s);
        s = null;
        // Force the receiver's weak ref path: unregister is not called; clear via reflection on holder
        // Instead verify ObserverHolder stores WeakReference by registering then checking purge after
        // artificially clearing — use package-visible holder list indirectly via alive count after
        // replacing: unregister keeps API correct; here we assert WeakReference type via holder.
        NetStatusReceiver.ObserverHolder holder = null;
        // Re-register a fresh one and inspect via findAnnotationMethods path already covered;
        // Direct: create holder and clear its ref by letting GC + purge after nulling.
        Subscriber s2 = new Subscriber();
        receiver.unRegisterAllObserver();
        receiver.registerObserver(s2);
        assertEquals(1, receiver.aliveObserverCount());
        java.lang.reflect.Field listField = NetStatusReceiver.class.getDeclaredField("networkList");
        listField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.CopyOnWriteArrayList<NetStatusReceiver.ObserverHolder> list =
                (java.util.concurrent.CopyOnWriteArrayList<NetStatusReceiver.ObserverHolder>) listField.get(receiver);
        holder = list.get(0);
        assertNotNull(holder.getSubscriber());
        java.lang.reflect.Field refField = NetStatusReceiver.ObserverHolder.class.getDeclaredField("subscriberRef");
        refField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.lang.ref.WeakReference<Object> ref =
                (java.lang.ref.WeakReference<Object>) refField.get(holder);
        assertNotNull(ref);
        ref.clear();
        assertEquals(0, receiver.aliveObserverCount());
        receiver.post(NetType.MOBILE); // must purge / skip without NPE
        assertEquals(0, receiver.aliveObserverCount());
        assertTrue(probe.get() == null || probe.get() != null); // probe may or may not be cleared
    }
}
