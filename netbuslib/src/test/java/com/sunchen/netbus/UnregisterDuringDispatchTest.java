package com.sunchen.netbus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.sunchen.netbus.annotation.NetSubscribe;
import com.sunchen.netbus.type.NetType;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Unregistering from inside a callback must not leave later work on a dead subscriber
 * (and must not stop other subscribers). Method discovery walks the subclass before the
 * superclass, so a child method that unregisters itself always runs before a parent method —
 * that ordering is used here so the test does not depend on {@code getDeclaredMethods} order.
 */
public class UnregisterDuringDispatchTest {

    private NetStatusReceiver receiver;

    @Before
    public void setUp() {
        NetStatusReceiver.clearMethodCacheForTest();
        receiver = new NetStatusReceiver(new NetStatusReceiver.Dispatcher() {
            @Override
            public void dispatch(Runnable runnable) {
                runnable.run();
            }
        });
    }

    /**
     * Parent method is discovered after the child's; if the child unregisters, the parent
     * method on the same instance must not run for that same event.
     */
    public static class ParentRecorder {
        final List<String> parentCalls = new ArrayList<>();

        @NetSubscribe
        public void onParent(NetType type) {
            parentCalls.add("PARENT:" + type);
        }
    }

    public static class SelfUnregisterChild extends ParentRecorder {
        final List<String> childCalls = new ArrayList<>();
        NetStatusReceiver bus;
        /** When false, onChild records but does not unregister (for setup). */
        boolean unregisterOnCallback = true;

        @NetSubscribe
        public void onChild(NetType type) {
            childCalls.add("CHILD:" + type);
            if (unregisterOnCallback) {
                bus.unRegisterObserver(this);
            }
        }
    }

    public static class SimpleRecorder {
        final List<NetType> calls = new ArrayList<>();

        @NetSubscribe
        public void onNet(NetType type) {
            calls.add(type);
        }
    }

    public static class UnregistersOther {
        final List<NetType> calls = new ArrayList<>();
        NetStatusReceiver bus;
        Object other;

        @NetSubscribe
        public void onNet(NetType type) {
            calls.add(type);
            bus.unRegisterObserver(other);
        }
    }

    @Test
    public void unregisteringSelfSkipsRemainingInheritedMethodsInSameDispatch() {
        SelfUnregisterChild child = new SelfUnregisterChild();
        child.bus = receiver;
        receiver.setInitialNetType(NetType.NONE);
        // Register delivers NONE: child runs and unregisters; parent must be skipped
        receiver.registerObserver(child);
        assertEquals(1, child.childCalls.size());
        assertEquals("CHILD:NONE", child.childCalls.get(0));
        assertTrue("parent method must not run after self-unregister", child.parentCalls.isEmpty());
        assertEquals(0, receiver.aliveObserverCount());

        // Further posts must not reach either method
        child.childCalls.clear();
        receiver.post(NetType.WIFI);
        assertTrue(child.childCalls.isEmpty());
        assertTrue(child.parentCalls.isEmpty());
    }

    @Test
    public void unregisteringOtherSubscriberInCallbackSkipsThemForSameEvent() {
        UnregistersOther first = new UnregistersOther();
        SimpleRecorder second = new SimpleRecorder();
        first.bus = receiver;
        first.other = second;

        receiver.setInitialNetType(NetType.NONE);
        receiver.registerObserver(first);
        receiver.registerObserver(second);
        first.calls.clear();
        second.calls.clear();

        receiver.post(NetType.WIFI);

        assertEquals(1, first.calls.size());
        assertEquals(NetType.WIFI, first.calls.get(0));
        assertTrue("second was unregistered by first during the same dispatch",
                second.calls.isEmpty());
        assertEquals(1, receiver.aliveObserverCount());
    }

    @Test
    public void otherSubscribersStillReceiveAfterSelfUnregister() {
        SelfUnregisterChild leaving = new SelfUnregisterChild();
        leaving.bus = receiver;
        SimpleRecorder staying = new SimpleRecorder();

        receiver.setInitialNetType(NetType.NONE);
        // Skip unregister on the initial register callback so both stay registered for the post.
        leaving.unregisterOnCallback = false;
        receiver.registerObserver(staying);
        receiver.registerObserver(leaving);
        staying.calls.clear();
        leaving.childCalls.clear();
        leaving.parentCalls.clear();
        leaving.unregisterOnCallback = true;

        receiver.post(NetType.WIFI);

        assertEquals(1, leaving.childCalls.size());
        assertEquals("CHILD:WIFI", leaving.childCalls.get(0));
        assertTrue("parent method must not run after self-unregister", leaving.parentCalls.isEmpty());
        assertEquals(1, staying.calls.size());
        assertEquals(NetType.WIFI, staying.calls.get(0));
        assertEquals(1, receiver.aliveObserverCount());
    }
}
