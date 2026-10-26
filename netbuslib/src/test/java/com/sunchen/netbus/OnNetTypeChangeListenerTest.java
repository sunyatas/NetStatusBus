package com.sunchen.netbus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.sunchen.netbus.type.NetType;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 无注解监听 OnNetTypeChangeListener：初始回调、每次变化、去重、移除、异常隔离、生命周期绑定。
 */
public class OnNetTypeChangeListenerTest {

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

    private static class Recorder implements OnNetTypeChangeListener {
        final List<NetType> values = new ArrayList<>();

        @Override
        public void onNetTypeChanged(@NonNull NetType netType) {
            values.add(netType);
        }
    }

    private static class FakeLifecycle extends Lifecycle implements LifecycleOwner {
        final List<LifecycleEventObserver> observers = new ArrayList<>();
        State state;

        FakeLifecycle(State state) {
            this.state = state;
        }

        @Override
        public void addObserver(@NonNull LifecycleObserver observer) {
            observers.add((LifecycleEventObserver) observer);
        }

        @Override
        public void removeObserver(@NonNull LifecycleObserver observer) {
            observers.remove(observer);
        }

        @NonNull
        @Override
        public State getCurrentState() {
            return state;
        }

        @NonNull
        @Override
        public Lifecycle getLifecycle() {
            return this;
        }

        void handle(Event event, State newState) {
            state = newState;
            for (LifecycleEventObserver o : new ArrayList<>(observers)) {
                o.onStateChanged(this, event);
            }
        }
    }

    private QueueDispatcher dispatcher;
    private NetStatusReceiver receiver;
    private final List<String> logged = new ArrayList<>();

    @Before
    public void setUp() {
        dispatcher = new QueueDispatcher();
        receiver = new NetStatusReceiver(dispatcher);
        NetStatusReceiver.setErrorLoggerForTest(new NetStatusReceiver.ErrorLogger() {
            @Override
            public void log(String message, Throwable error) {
                logged.add(message);
            }
        });
    }

    @After
    public void tearDown() {
        NetStatusReceiver.setErrorLoggerForTest(null);
    }

    @Test
    public void receivesCurrentTypeOnAddThenEveryChange() {
        receiver.setInitialNetType(NetType.WIFI);
        Recorder recorder = new Recorder();

        receiver.addListener(recorder);
        receiver.postIfChanged(NetType.MOBILE);
        receiver.postIfChanged(NetType.MOBILE); // 未变化，不回调
        receiver.postIfChanged(NetType.NONE);
        receiver.postIfChanged(NetType.ETHERNET);

        assertEquals(Arrays.asList(NetType.WIFI, NetType.MOBILE, NetType.NONE, NetType.ETHERNET),
                recorder.values);
    }

    @Test
    public void addingSameListenerTwiceDeliversOnce() {
        Recorder recorder = new Recorder();
        receiver.addListener(recorder);
        receiver.addListener(recorder);
        assertEquals(1, receiver.listenerCount());

        receiver.post(NetType.WIFI);
        assertEquals(Arrays.asList(NetType.NONE, NetType.WIFI), recorder.values);
    }

    @Test
    public void removedListenerStopsReceiving() {
        Recorder recorder = new Recorder();
        receiver.addListener(recorder);
        receiver.removeListener(recorder);
        receiver.removeListener(recorder); // 重复移除无副作用
        receiver.post(NetType.WIFI);

        assertEquals(Collections.singletonList(NetType.NONE), recorder.values);
        assertEquals(0, receiver.listenerCount());
    }

    @Test
    public void removedBeforeQueuedInitialCallbackRunsIsNotCalled() {
        dispatcher.runImmediately = false;
        Recorder recorder = new Recorder();
        receiver.addListener(recorder);
        receiver.removeListener(recorder);
        dispatcher.runPending();

        assertTrue(recorder.values.isEmpty());
    }

    @Test
    public void listenerRemovedByEarlierListenerInSameDispatchIsSkipped() {
        final Recorder second = new Recorder();
        OnNetTypeChangeListener first = new OnNetTypeChangeListener() {
            @Override
            public void onNetTypeChanged(@NonNull NetType netType) {
                if (netType == NetType.WIFI) {
                    receiver.removeListener(second);
                }
            }
        };
        receiver.addListener(first);
        receiver.addListener(second);
        receiver.post(NetType.WIFI);

        assertEquals(Collections.singletonList(NetType.NONE), second.values);
    }

    @Test
    public void throwingListenerIsLoggedAndOthersStillReceive() {
        OnNetTypeChangeListener bad = new OnNetTypeChangeListener() {
            @Override
            public void onNetTypeChanged(@NonNull NetType netType) {
                throw new IllegalStateException("boom");
            }
        };
        Recorder good = new Recorder();
        NetStatusReceiverTest.Subscriber subscriber = new NetStatusReceiverTest.Subscriber();
        receiver.addListener(bad);
        receiver.addListener(good);
        receiver.registerObserver(subscriber);
        logged.clear();

        receiver.post(NetType.WIFI);

        assertEquals(Arrays.asList(NetType.NONE, NetType.WIFI), good.values);
        assertTrue(subscriber.calls.contains("AUTO:WIFI"));
        assertEquals(1, logged.size());
        assertTrue(logged.get(0).contains("IllegalStateException"));
    }

    @Test
    public void unregisterAllRemovesListenersToo() {
        Recorder recorder = new Recorder();
        receiver.addListener(recorder);
        receiver.unRegisterAllObserver();
        receiver.post(NetType.WIFI);

        assertEquals(Collections.singletonList(NetType.NONE), recorder.values);
    }

    @Test
    public void lifecycleBoundListenerIsRemovedOnDestroy() {
        FakeLifecycle lifecycle = new FakeLifecycle(Lifecycle.State.RESUMED);
        Recorder recorder = new Recorder();
        assertTrue(LifecycleBinding.bind(recorder, lifecycle, new LifecycleBinding.Unregistrar() {
            @Override
            public void unregister(Object l) {
                receiver.removeListener((OnNetTypeChangeListener) l);
            }
        }));
        receiver.addListener(recorder);

        // 不在前台时仍会收到变化（与 LiveData 不同）
        lifecycle.handle(Lifecycle.Event.ON_STOP, Lifecycle.State.CREATED);
        receiver.post(NetType.MOBILE);
        lifecycle.handle(Lifecycle.Event.ON_DESTROY, Lifecycle.State.DESTROYED);
        receiver.post(NetType.WIFI);

        assertEquals(Arrays.asList(NetType.NONE, NetType.MOBILE), recorder.values);
        assertEquals(0, receiver.listenerCount());
        assertTrue(lifecycle.observers.isEmpty());
    }

    @Test
    public void lifecycleAlreadyDestroyedDoesNotBind() {
        FakeLifecycle lifecycle = new FakeLifecycle(Lifecycle.State.DESTROYED);
        assertFalse(LifecycleBinding.bind(new Recorder(), lifecycle,
                new LifecycleBinding.Unregistrar() {
                    @Override
                    public void unregister(Object l) {
                    }
                }));
    }
}
