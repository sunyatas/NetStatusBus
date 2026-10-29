package com.sunchen.netbus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.sunchen.netbus.type.NetType;

import org.junit.Test;

public class NetStatusBusStateTest {

    private static final NetStatusReceiver.Dispatcher SYNC = new NetStatusReceiver.Dispatcher() {
        @Override
        public void dispatch(Runnable runnable) {
            runnable.run();
        }
    };

    @Test
    public void uninitializedBusReportsNotInitialized() {
        NetStatusBus bus = new NetStatusBus(new NetStatusReceiver(SYNC));
        assertFalse(bus.isInitialized());
    }

    @Test
    public void usingTheBusBeforeInitThrowsIllegalStateException() {
        final NetStatusBus bus = new NetStatusBus(new NetStatusReceiver(SYNC));
        final OnNetTypeChangeListener listener = new OnNetTypeChangeListener() {
            @Override
            public void onNetTypeChanged(NetType netType) {
            }
        };
        assertNotInitialized(new Runnable() {
            @Override
            public void run() {
                bus.getCurrentNetType();
            }
        });
        assertNotInitialized(new Runnable() {
            @Override
            public void run() {
                bus.getApplication();
            }
        });
        assertNotInitialized(new Runnable() {
            @Override
            public void run() {
                bus.register(new Object());
            }
        });
        assertNotInitialized(new Runnable() {
            @Override
            public void run() {
                bus.addListener(listener);
            }
        });
    }

    @Test
    public void receiverTracksTheLatestDeliveredType() {
        NetStatusReceiver receiver = new NetStatusReceiver(SYNC);
        assertEquals(NetType.NONE, receiver.getCurrentNetType());
        receiver.setInitialNetType(NetType.WIFI);
        assertEquals(NetType.WIFI, receiver.getCurrentNetType());
        receiver.post(NetType.MOBILE);
        assertEquals(NetType.MOBILE, receiver.getCurrentNetType());
        receiver.postIfChanged(NetType.ETHERNET);
        assertEquals(NetType.ETHERNET, receiver.getCurrentNetType());
    }

    private static void assertNotInitialized(Runnable call) {
        try {
            call.run();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("init"));
        }
    }
}
