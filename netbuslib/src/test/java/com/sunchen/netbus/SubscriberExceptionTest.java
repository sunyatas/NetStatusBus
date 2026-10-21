package com.sunchen.netbus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.sunchen.netbus.annotation.NetSubscribe;
import com.sunchen.netbus.type.NetType;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SubscriberExceptionTest {

    static final IllegalStateException BOOM = new IllegalStateException("boom");

    public static class Throwing {
        int calls;

        @NetSubscribe
        public void onNet(NetType type) {
            calls++;
            throw BOOM;
        }
    }

    public static class Healthy {
        final List<NetType> received = new ArrayList<>();

        @NetSubscribe
        public void onNet(NetType type) {
            received.add(type);
        }
    }

    public static class ThrowingChild extends Throwing {
    }

    private final List<String> messages = new ArrayList<>();
    private final List<Throwable> errors = new ArrayList<>();
    private NetStatusReceiver receiver;

    @Before
    public void setUp() {
        NetStatusReceiver.clearMethodCacheForTest();
        NetStatusReceiver.setErrorLoggerForTest(new NetStatusReceiver.ErrorLogger() {
            @Override
            public void log(String message, Throwable error) {
                messages.add(message);
                errors.add(error);
            }
        });
        receiver = new NetStatusReceiver(new NetStatusReceiver.Dispatcher() {
            @Override
            public void dispatch(Runnable runnable) {
                runnable.run();
            }
        });
    }

    @After
    public void tearDown() {
        NetStatusReceiver.setErrorLoggerForTest(null);
    }

    @Test
    public void exceptionIsLoggedWithRealCauseAndMethodName() {
        Throwing t = new Throwing();
        receiver.registerObserver(t);
        assertEquals(1, errors.size());
        assertSame(BOOM, errors.get(0));
        String msg = messages.get(0);
        assertTrue(msg, msg.contains(Throwing.class.getName() + "#onNet()"));
        assertTrue(msg, msg.contains(IllegalStateException.class.getName()));
        assertTrue(msg, msg.contains("NONE"));
    }

    @Test
    public void throwingSubscriberDoesNotBlockOthers() {
        Throwing t = new Throwing();
        Healthy h = new Healthy();
        receiver.registerObserver(t);
        receiver.registerObserver(h);
        h.received.clear();
        errors.clear();

        receiver.post(NetType.WIFI);
        assertEquals(1, errors.size());
        assertEquals(1, h.received.size());
        assertEquals(NetType.WIFI, h.received.get(0));

        // 抛异常的订阅者之后仍然会继续收到回调
        receiver.post(NetType.MOBILE);
        assertEquals(3, t.calls);
        assertEquals(2, h.received.size());
    }

    @Test
    public void inheritedMethodMentionsDeclaringClass() {
        receiver.registerObserver(new ThrowingChild());
        String msg = messages.get(0);
        assertTrue(msg, msg.contains(ThrowingChild.class.getName() + "#onNet()"));
        assertTrue(msg, msg.contains("declared in " + Throwing.class.getName()));
    }
}
