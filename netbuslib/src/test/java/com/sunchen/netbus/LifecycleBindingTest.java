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
}
