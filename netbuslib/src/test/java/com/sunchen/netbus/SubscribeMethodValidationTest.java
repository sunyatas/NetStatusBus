package com.sunchen.netbus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.sunchen.netbus.annotation.NetSubscribe;
import com.sunchen.netbus.type.NetType;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link NetSubscribe} 方法签名校验：不合规的方法在 register 时就报错，而不是静默地从不回调。
 */
public class SubscribeMethodValidationTest {

    public static class WrongParamType {
        @NetSubscribe
        public void onNet(String type) {
        }
    }

    public static class TwoParams {
        @NetSubscribe
        public void onNet(NetType type, int extra) {
        }
    }

    public static class NonVoid {
        @NetSubscribe
        public boolean onNet(NetType type) {
            return true;
        }
    }

    /** 能接收 NetType 的父类型（Object、Enum）也算合法 */
    public static class WideParams {
        final List<Object> calls = new ArrayList<>();

        @NetSubscribe
        public void asObject(Object type) {
            calls.add(type);
        }

        @NetSubscribe
        public void asEnum(Enum<?> type) {
            calls.add(type);
        }
    }

    public static class NoArg {
        int count;

        @NetSubscribe
        public void onNet() {
            count++;
        }
    }

    /** 父类中的非法方法同样要在注册子类时报出来 */
    public static class BadBase {
        @NetSubscribe
        public void onNet(Integer type) {
        }
    }

    public static class ChildOfBadBase extends BadBase {
    }

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

    private static void assertRejected(Object subscriber, String expectedFragment, NetStatusReceiver r) {
        try {
            r.registerObserver(subscriber);
            fail("expected IllegalArgumentException for " + subscriber.getClass().getSimpleName());
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(expectedFragment));
            assertTrue(e.getMessage(), e.getMessage().contains("onNet"));
        }
        assertEquals(0, r.aliveObserverCount());
    }

    @Test
    public void wrongParameterTypeIsRejectedAtRegister() {
        assertRejected(new WrongParamType(), "java.lang.String", receiver);
    }

    @Test
    public void moreThanOneParameterIsRejected() {
        assertRejected(new TwoParams(), "at most one parameter", receiver);
    }

    @Test
    public void nonVoidReturnIsRejected() {
        assertRejected(new NonVoid(), "must return void", receiver);
    }

    @Test
    public void invalidMethodInSuperclassIsRejected() {
        assertRejected(new ChildOfBadBase(), "java.lang.Integer", receiver);
    }

    @Test
    public void rejectedClassIsNotCachedAndStaysRejected() {
        assertRejected(new WrongParamType(), "java.lang.String", receiver);
        assertRejected(new WrongParamType(), "java.lang.String", receiver);
    }

    @Test
    public void supertypesOfNetTypeAreAcceptedAndReceiveValue() {
        receiver.setInitialNetType(NetType.WIFI);
        WideParams subscriber = new WideParams();
        receiver.registerObserver(subscriber);
        assertEquals(2, subscriber.calls.size());
        for (Object call : subscriber.calls) {
            assertSame(NetType.WIFI, call);
        }
    }

    @Test
    public void noArgMethodIsAcceptedWithNullParameterClass() {
        List<MethodManager> methods = NetStatusReceiver.findAnnotationMethods(NoArg.class);
        assertEquals(1, methods.size());
        assertNull(methods.get(0).getParameterClazz());

        NoArg subscriber = new NoArg();
        receiver.registerObserver(subscriber);
        receiver.post(NetType.MOBILE);
        assertEquals(2, subscriber.count);
    }
}
