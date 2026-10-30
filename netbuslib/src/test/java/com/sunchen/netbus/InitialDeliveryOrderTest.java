package com.sunchen.netbus;

import static org.junit.Assert.assertEquals;

import androidx.annotation.NonNull;

import com.sunchen.netbus.annotation.NetSubscribe;
import com.sunchen.netbus.type.NetType;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 在后台线程 register/addListener 时，初始回调读取的类型可能已过期：网络回调线程在“读取当前类型”和
 * “投递初始回调”之间 post 了新类型，新类型的分发任务排在初始回调之前。初始回调不应再用旧值覆盖新值。
 */
public class InitialDeliveryOrderTest {

    /**
     * 模拟主线程消息队列。{@link #beforeNextEnqueue} 在下一次投递入队前执行一次，用来复现竞态：
     * 初始回调已读取当前类型、但还没入队时，另一个线程 post 了新类型。
     */
    private static class RacingDispatcher implements NetStatusReceiver.Dispatcher {
        final List<Runnable> queue = new ArrayList<>();
        Runnable beforeNextEnqueue;

        @Override
        public void dispatch(Runnable runnable) {
            Runnable hook = beforeNextEnqueue;
            beforeNextEnqueue = null;
            if (hook != null) {
                hook.run();
            }
            queue.add(runnable);
        }

        void drain() {
            while (!queue.isEmpty()) {
                queue.remove(0).run();
            }
        }
    }

    private static class Recorder implements OnNetTypeChangeListener {
        final List<NetType> values = new ArrayList<>();

        @Override
        public void onNetTypeChanged(@NonNull NetType netType) {
            values.add(netType);
        }
    }

    public static class Subscriber {
        final List<NetType> values = new ArrayList<>();

        @NetSubscribe
        public void onNet(NetType netType) {
            values.add(netType);
        }
    }

    private RacingDispatcher dispatcher;
    private NetStatusReceiver receiver;

    @Before
    public void setUp() {
        dispatcher = new RacingDispatcher();
        receiver = new NetStatusReceiver(dispatcher);
        receiver.setInitialNetType(NetType.WIFI);
    }

    private void postMobileBeforeNextEnqueue() {
        dispatcher.beforeNextEnqueue = new Runnable() {
            @Override
            public void run() {
                receiver.post(NetType.MOBILE);
            }
        };
    }

    @Test
    public void addListener_skipsStaleInitialValueWhenNewerChangeWasDeliveredFirst() {
        Recorder recorder = new Recorder();
        postMobileBeforeNextEnqueue();
        receiver.addListener(recorder);
        dispatcher.drain();

        assertEquals(Arrays.asList(NetType.MOBILE), recorder.values);
    }

    @Test
    public void register_skipsStaleInitialValueWhenNewerChangeWasDeliveredFirst() {
        Subscriber subscriber = new Subscriber();
        postMobileBeforeNextEnqueue();
        receiver.registerObserver(subscriber);
        dispatcher.drain();

        assertEquals(Arrays.asList(NetType.MOBILE), subscriber.values);
    }

    @Test
    public void netTypeListener_skipsStaleInitialValueWhenNewerChangeWasDeliveredFirst() {
        final List<NetType> values = new ArrayList<>();
        postMobileBeforeNextEnqueue();
        receiver.setNetTypeListener(new NetStatusReceiver.NetTypeListener() {
            @Override
            public void onNetTypeChanged(NetType netType) {
                values.add(netType);
            }
        });
        dispatcher.drain();

        assertEquals(Arrays.asList(NetType.MOBILE), values);
    }

    @Test
    public void initialValueStillDeliveredWhenChangeIsQueuedAfterIt() {
        Recorder recorder = new Recorder();
        receiver.addListener(recorder);
        receiver.post(NetType.MOBILE);
        dispatcher.drain();

        assertEquals(Arrays.asList(NetType.WIFI, NetType.MOBILE), recorder.values);
    }

    @Test
    public void laterAdditionsStillGetTheirInitialValueAfterEarlierChanges() {
        receiver.post(NetType.MOBILE);
        dispatcher.drain();

        Recorder recorder = new Recorder();
        receiver.addListener(recorder);
        Subscriber subscriber = new Subscriber();
        receiver.registerObserver(subscriber);
        dispatcher.drain();

        assertEquals(Arrays.asList(NetType.MOBILE), recorder.values);
        assertEquals(Arrays.asList(NetType.MOBILE), subscriber.values);
    }
}
