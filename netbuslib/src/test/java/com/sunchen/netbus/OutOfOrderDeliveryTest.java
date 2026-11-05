package com.sunchen.netbus;

import static org.junit.Assert.assertEquals;

import androidx.annotation.NonNull;

import com.sunchen.netbus.annotation.NetSubscribe;
import com.sunchen.netbus.type.Mode;
import com.sunchen.netbus.type.NetType;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * 两个线程几乎同时 post 时，较新的变化可能先入队（它的入队在锁外）。
 * 较旧的值随后分发时不应覆盖较新的值，postIfChanged 也不应因此重复回调同一个类型。
 */
public class OutOfOrderDeliveryTest {

    /** 模拟主线程消息队列；{@link #beforeNextEnqueue} 在下一次入队前执行一次，用来让另一个 post 插队 */
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
        final List<String> calls = new ArrayList<>();

        @NetSubscribe
        public void onAuto(NetType netType) {
            calls.add("AUTO:" + netType);
        }

        @NetSubscribe(mode = Mode.WIFI)
        public void onWifi(NetType netType) {
            calls.add("WIFI:" + netType);
        }
    }

    private RacingDispatcher dispatcher;
    private NetStatusReceiver receiver;
    private Recorder recorder;
    private Subscriber subscriber;
    private final List<NetType> liveDataValues = new ArrayList<>();

    @Before
    public void setUp() {
        dispatcher = new RacingDispatcher();
        receiver = new NetStatusReceiver(dispatcher);
        receiver.setInitialNetType(NetType.WIFI);
        recorder = new Recorder();
        subscriber = new Subscriber();
        receiver.addListener(recorder);
        receiver.registerObserver(subscriber);
        receiver.setNetTypeListener(new NetStatusReceiver.NetTypeListener() {
            @Override
            public void onNetTypeChanged(NetType netType) {
                liveDataValues.add(netType);
            }
        });
        dispatcher.drain();
        recorder.values.clear();
        subscriber.calls.clear();
        liveDataValues.clear();
    }

    private void cutInBeforeNextEnqueue(final NetType newer, final boolean ifChanged) {
        dispatcher.beforeNextEnqueue = new Runnable() {
            @Override
            public void run() {
                if (ifChanged) {
                    receiver.postIfChanged(newer);
                } else {
                    receiver.post(newer);
                }
            }
        };
    }

    @Test
    public void olderChangeDispatchedLateDoesNotOverwriteNewerOne() {
        cutInBeforeNextEnqueue(NetType.NONE, false);
        receiver.post(NetType.MOBILE); // MOBILE 先拿到版本号，但 NONE 先入队
        dispatcher.drain();

        assertEquals(Arrays.asList(NetType.NONE), recorder.values);
        assertEquals(Arrays.asList(NetType.NONE), liveDataValues);
        // 同一订阅者内多个方法的回调顺序取决于反射返回的顺序，这里只比较集合
        assertEquals(new HashSet<>(Arrays.asList("AUTO:NONE", "WIFI:NONE")), new HashSet<>(subscriber.calls));
        assertEquals(2, subscriber.calls.size());
        assertEquals(NetType.NONE, receiver.getCurrentNetType());
    }

    @Test
    public void postIfChangedDoesNotRepeatTypeSubscribersAlreadyHave() {
        // WIFI -> MOBILE -> WIFI，后一个先分发：订阅者一直在 WIFI，不应再收到一次 WIFI
        cutInBeforeNextEnqueue(NetType.WIFI, true);
        receiver.postIfChanged(NetType.MOBILE);
        dispatcher.drain();

        assertEquals(Arrays.<NetType>asList(), recorder.values);
        assertEquals(Arrays.<NetType>asList(), liveDataValues);
        assertEquals(Arrays.<String>asList(), subscriber.calls);
        assertEquals(NetType.WIFI, receiver.getCurrentNetType());
    }

    @Test
    public void modeFilterUsesTheTypeSubscribersLastReceived() {
        receiver.post(NetType.MOBILE);
        dispatcher.drain();
        subscriber.calls.clear();

        // MOBILE -> WIFI -> NONE，NONE 先分发；对订阅者来说是 MOBILE -> NONE，WIFI 模式不应回调
        cutInBeforeNextEnqueue(NetType.NONE, true);
        receiver.postIfChanged(NetType.WIFI);
        dispatcher.drain();

        assertEquals(Arrays.asList("AUTO:NONE"), subscriber.calls);
    }

    @Test
    public void inOrderChangesAreAllDelivered() {
        receiver.postIfChanged(NetType.MOBILE);
        receiver.postIfChanged(NetType.NONE);
        receiver.postIfChanged(NetType.WIFI);
        dispatcher.drain();

        assertEquals(Arrays.asList(NetType.MOBILE, NetType.NONE, NetType.WIFI), recorder.values);
    }

    @Test
    public void plainPostStillDeliversRepeatedType() {
        receiver.post(NetType.WIFI);
        dispatcher.drain();

        assertEquals(Arrays.asList(NetType.WIFI), recorder.values);
    }
}
