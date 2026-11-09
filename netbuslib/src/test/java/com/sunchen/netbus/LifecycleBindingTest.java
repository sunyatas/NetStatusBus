package com.sunchen.netbus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class LifecycleBindingTest {

    /** 纯 JVM 的 Lifecycle 实现，不依赖主线程检查 */
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

    private static class RecordingUnregistrar implements LifecycleBinding.Unregistrar {
        final List<Object> unregistered = new ArrayList<>();

        @Override
        public void unregister(Object subscriber) {
            unregistered.add(subscriber);
        }
    }

    @Test
    public void unregistersOnDestroyAndRemovesObserver() {
        FakeLifecycle lifecycle = new FakeLifecycle(Lifecycle.State.STARTED);
        RecordingUnregistrar unregistrar = new RecordingUnregistrar();
        Object subscriber = new Object();

        assertTrue(LifecycleBinding.bind(subscriber, lifecycle, unregistrar));
        assertEquals(1, lifecycle.observers.size());

        lifecycle.handle(Lifecycle.Event.ON_STOP, Lifecycle.State.CREATED);
        assertTrue(unregistrar.unregistered.isEmpty());

        lifecycle.handle(Lifecycle.Event.ON_DESTROY, Lifecycle.State.DESTROYED);
        assertEquals(1, unregistrar.unregistered.size());
        assertTrue(unregistrar.unregistered.get(0) == subscriber);
        assertTrue(lifecycle.observers.isEmpty());
    }

    @Test
    public void doesNotBindWhenAlreadyDestroyed() {
        FakeLifecycle lifecycle = new FakeLifecycle(Lifecycle.State.DESTROYED);
        RecordingUnregistrar unregistrar = new RecordingUnregistrar();

        assertFalse(LifecycleBinding.bind(new Object(), lifecycle, unregistrar));
        assertTrue(lifecycle.observers.isEmpty());
        assertTrue(unregistrar.unregistered.isEmpty());
    }

    @Test
    public void receiverDropsSubscriberWhenLifecycleDestroyed() {
        final NetStatusReceiver receiver = new NetStatusReceiver(new NetStatusReceiver.Dispatcher() {
            @Override
            public void dispatch(Runnable runnable) {
                runnable.run();
            }
        });
        NetStatusReceiverTest.Subscriber subscriber = new NetStatusReceiverTest.Subscriber();
        FakeLifecycle lifecycle = new FakeLifecycle(Lifecycle.State.RESUMED);

        LifecycleBinding.bind(subscriber, lifecycle, new LifecycleBinding.Unregistrar() {
            @Override
            public void unregister(Object s) {
                receiver.unRegisterObserver(s);
            }
        });
        receiver.registerObserver(subscriber);
        assertEquals(1, receiver.aliveObserverCount());

        lifecycle.handle(Lifecycle.Event.ON_DESTROY, Lifecycle.State.DESTROYED);
        assertEquals(0, receiver.aliveObserverCount());
    }

    private static NetStatusReceiver syncReceiver() {
        return new NetStatusReceiver(new NetStatusReceiver.Dispatcher() {
            @Override
            public void dispatch(Runnable runnable) {
                runnable.run();
            }
        });
    }

    private static LifecycleBinding.Unregistrar unregistrarFor(final NetStatusReceiver receiver) {
        return new LifecycleBinding.Unregistrar() {
            @Override
            public void unregister(Object s) {
                receiver.unRegisterObserver(s);
            }
        };
    }

    @Test
    public void manualCancelThenReRegisterSurvivesOldOwnerDestroy() {
        NetStatusReceiver receiver = syncReceiver();
        LifecycleBinding.Registry registry = new LifecycleBinding.Registry();
        NetStatusReceiverTest.Subscriber subscriber = new NetStatusReceiverTest.Subscriber();
        FakeLifecycle oldOwner = new FakeLifecycle(Lifecycle.State.RESUMED);

        assertTrue(registry.bind(subscriber, oldOwner, unregistrarFor(receiver)));
        receiver.registerObserver(subscriber);
        assertEquals(1, registry.size());

        // what NetStatusBus.unregister does
        registry.cancel(subscriber);
        receiver.unRegisterObserver(subscriber);
        assertEquals(0, registry.size());

        // registered again without an owner
        receiver.registerObserver(subscriber);
        oldOwner.handle(Lifecycle.Event.ON_DESTROY, Lifecycle.State.DESTROYED);

        assertEquals(1, receiver.aliveObserverCount());
        assertTrue(oldOwner.observers.isEmpty());
    }

    @Test
    public void manualCancelThenReBindToNewOwnerFollowsOnlyNewOwner() {
        NetStatusReceiver receiver = syncReceiver();
        LifecycleBinding.Registry registry = new LifecycleBinding.Registry();
        NetStatusReceiverTest.Subscriber subscriber = new NetStatusReceiverTest.Subscriber();
        FakeLifecycle oldOwner = new FakeLifecycle(Lifecycle.State.RESUMED);
        FakeLifecycle newOwner = new FakeLifecycle(Lifecycle.State.RESUMED);

        registry.bind(subscriber, oldOwner, unregistrarFor(receiver));
        receiver.registerObserver(subscriber);
        registry.cancel(subscriber);
        receiver.unRegisterObserver(subscriber);

        registry.bind(subscriber, newOwner, unregistrarFor(receiver));
        receiver.registerObserver(subscriber);

        oldOwner.handle(Lifecycle.Event.ON_DESTROY, Lifecycle.State.DESTROYED);
        assertEquals(1, receiver.aliveObserverCount());

        newOwner.handle(Lifecycle.Event.ON_DESTROY, Lifecycle.State.DESTROYED);
        assertEquals(0, receiver.aliveObserverCount());
        assertEquals(0, registry.size());
    }

    @Test
    public void destroyOfFirstOwnerAlsoDetachesOtherOwners() {
        NetStatusReceiver receiver = syncReceiver();
        LifecycleBinding.Registry registry = new LifecycleBinding.Registry();
        NetStatusReceiverTest.Subscriber subscriber = new NetStatusReceiverTest.Subscriber();
        FakeLifecycle first = new FakeLifecycle(Lifecycle.State.RESUMED);
        FakeLifecycle second = new FakeLifecycle(Lifecycle.State.RESUMED);
        RecordingUnregistrar recording = new RecordingUnregistrar();

        registry.bind(subscriber, first, unregistrarFor(receiver));
        registry.bind(subscriber, second, recording);
        receiver.registerObserver(subscriber);

        first.handle(Lifecycle.Event.ON_DESTROY, Lifecycle.State.DESTROYED);
        assertEquals(0, receiver.aliveObserverCount());
        assertEquals(0, registry.size());

        // re-registered later: the second owner's stale binding must not undo it
        receiver.registerObserver(subscriber);
        second.handle(Lifecycle.Event.ON_DESTROY, Lifecycle.State.DESTROYED);
        assertTrue(recording.unregistered.isEmpty());
        assertEquals(1, receiver.aliveObserverCount());
        assertTrue(second.observers.isEmpty());
    }

    @Test
    public void cancelAllDetachesEveryBindingAndCancelIgnoresUnknown() {
        LifecycleBinding.Registry registry = new LifecycleBinding.Registry();
        RecordingUnregistrar recording = new RecordingUnregistrar();
        FakeLifecycle owner = new FakeLifecycle(Lifecycle.State.STARTED);
        Object a = new Object();
        Object b = new Object();

        registry.bind(a, owner, recording);
        registry.bind(b, owner, recording);
        registry.cancel(null);
        registry.cancel(new Object());
        assertEquals(2, registry.size());

        registry.cancelAll();
        assertEquals(0, registry.size());
        owner.handle(Lifecycle.Event.ON_DESTROY, Lifecycle.State.DESTROYED);
        assertTrue(recording.unregistered.isEmpty());
        assertTrue(owner.observers.isEmpty());
    }

    @Test
    public void registryDoesNotBindToDestroyedLifecycle() {
        LifecycleBinding.Registry registry = new LifecycleBinding.Registry();
        FakeLifecycle owner = new FakeLifecycle(Lifecycle.State.DESTROYED);
        assertFalse(registry.bind(new Object(), owner, new RecordingUnregistrar()));
        assertEquals(0, registry.size());
        assertTrue(owner.observers.isEmpty());
    }
}
