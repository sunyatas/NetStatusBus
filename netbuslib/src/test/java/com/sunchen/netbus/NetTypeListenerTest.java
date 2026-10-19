package com.sunchen.netbus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.sunchen.netbus.annotation.NetSubscribe;
import com.sunchen.netbus.type.NetType;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 驱动 getNetTypeLiveData() 的内部监听：初始值、变化顺序、取消与替换。
 */
public class NetTypeListenerTest {

    private static class QueueDispatcher implements NetStatusReceiver.Dispatcher {
        final List<Runnable> pending = new ArrayList<>();
        boolean runImmediately = true;

        @Override
        public void dispatch(Runnable runnable) {
            if (runImmediately) {
                runnable.run();
            } else {
                pending.add(runnable);
            }
        }

        void runPending() {
            List<Runnable> copy = new ArrayList<>(pending);
            pending.clear();
            for (Runnable r : copy) {
                r.run();
            }
        }
    }

    private static class Recorder implements NetStatusReceiver.NetTypeListener {
        final List<NetType> values = new ArrayList<>();
        final List<String> log;

        Recorder(List<String> log) {
            this.log = log;
        }

        @Override
        public void onNetTypeChanged(NetType netType) {
            values.add(netType);
            log.add("listener:" + netType);
        }
    }

    public static class LoggingSubscriber {
        final List<String> log;

        public LoggingSubscriber(List<String> log) {
            this.log = log;
        }

        @NetSubscribe
        public void onNet(NetType type) {
            log.add("subscriber:" + type);
        }
    }

    private QueueDispatcher dispatcher;
    private NetStatusReceiver receiver;
    private List<String> log;

    @Before
    public void setUp() {
        dispatcher = new QueueDispatcher();
        receiver = new NetStatusReceiver(dispatcher);
        log = new ArrayList<>();
    }

    @Test
    public void listenerGetsCurrentTypeImmediately() {
        receiver.setInitialNetType(NetType.WIFI);
        Recorder recorder = new Recorder(log);
        receiver.setNetTypeListener(recorder);
        assertEquals(Collections.singletonList(NetType.WIFI), recorder.values);
    }

    @Test
    public void listenerGetsEveryChangeInOrderBeforeSubscribers() {
        receiver.setInitialNetType(NetType.WIFI);
        Recorder recorder = new Recorder(log);
        receiver.setNetTypeListener(recorder);
        receiver.registerObserver(new LoggingSubscriber(log));
        log.clear();

        receiver.postIfChanged(NetType.MOBILE);
        receiver.postIfChanged(NetType.MOBILE); // 没变化，不分发
        receiver.postIfChanged(NetType.NONE);

        assertEquals(Arrays.asList(NetType.WIFI, NetType.MOBILE, NetType.NONE), recorder.values);
        assertEquals(Arrays.asList(
                "listener:MOBILE", "subscriber:MOBILE",
                "listener:NONE", "subscriber:NONE"), log);
    }

    @Test
    public void listenerIsCalledOnDispatcherNotInline() {
        dispatcher.runImmediately = false;
        receiver.setInitialNetType(NetType.NONE);
        Recorder recorder = new Recorder(log);
        receiver.setNetTypeListener(recorder);
        receiver.post(NetType.ETHERNET);
        assertTrue(recorder.values.isEmpty());

        dispatcher.runPending();
        assertEquals(Arrays.asList(NetType.ETHERNET, NetType.ETHERNET), recorder.values);
    }

    @Test
    public void nullListenerStopsUpdates() {
        Recorder recorder = new Recorder(log);
        receiver.setNetTypeListener(recorder);
        receiver.setNetTypeListener(null);
        receiver.post(NetType.WIFI);
        assertEquals(Collections.singletonList(NetType.NONE), recorder.values);
    }

    @Test
    public void replacedListenerDoesNotGetPendingInitialValue() {
        dispatcher.runImmediately = false;
        Recorder first = new Recorder(log);
        Recorder second = new Recorder(log);
        receiver.setNetTypeListener(first);
        receiver.setNetTypeListener(second);
        dispatcher.runPending();
        assertTrue(first.values.isEmpty());
        assertEquals(Collections.singletonList(NetType.NONE), second.values);
    }
}
