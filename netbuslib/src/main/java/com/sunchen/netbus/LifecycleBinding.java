package com.sunchen.netbus;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 把订阅者和一个 {@link LifecycleOwner} 绑定：生命周期进入 DESTROYED 时自动注销订阅者并移除自身。
 * <p>
 * Ties a subscriber (or listener) to a {@link Lifecycle}: when the lifecycle reaches
 * {@code DESTROYED} the subscriber is unregistered and the binding removes itself. A binding
 * can be {@linkplain #cancel() cancelled} (for example when the subscriber is unregistered by
 * hand), after which it does nothing on destroy and no longer references the subscriber.
 */
final class LifecycleBinding implements LifecycleEventObserver {

    interface Unregistrar {
        void unregister(Object subscriber);
    }

    private final AtomicReference<Object> subscriber;
    private final Unregistrar unregistrar;
    private final Registry registry;

    private LifecycleBinding(Object subscriber, Unregistrar unregistrar, Registry registry) {
        this.subscriber = new AtomicReference<>(subscriber);
        this.unregistrar = unregistrar;
        this.registry = registry;
    }

    /**
     * Binds without tracking; the binding can't be cancelled from outside.
     *
     * @return 已绑定返回 true；生命周期已经 DESTROYED 时不绑定并返回 false
     */
    static boolean bind(@NonNull Object subscriber, @NonNull Lifecycle lifecycle,
                        @NonNull Unregistrar unregistrar) {
        return bind(subscriber, lifecycle, unregistrar, null);
    }

    private static boolean bind(@NonNull Object subscriber, @NonNull Lifecycle lifecycle,
                                @NonNull Unregistrar unregistrar, Registry registry) {
        if (lifecycle.getCurrentState() == Lifecycle.State.DESTROYED) {
            return false;
        }
        LifecycleBinding binding = new LifecycleBinding(subscriber, unregistrar, registry);
        if (registry != null) {
            registry.add(subscriber, binding);
        }
        lifecycle.addObserver(binding);
        return true;
    }

    /** Detaches this binding: nothing happens on destroy and the subscriber is released. */
    void cancel() {
        subscriber.set(null);
    }

    @Override
    public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event) {
        if (event == Lifecycle.Event.ON_DESTROY) {
            source.getLifecycle().removeObserver(this);
            Object s = subscriber.getAndSet(null);
            if (s != null) {
                if (registry != null) {
                    // The subscriber is about to be unregistered: its other bindings are stale too.
                    registry.cancel(s);
                }
                unregistrar.unregister(s);
            }
        }
    }

    /**
     * Tracks the live bindings of each subscriber (by identity) so that a manual
     * {@code unregister} / {@code removeListener} also detaches them. Without this, a subscriber
     * that was unregistered by hand and registered again (with another owner or none) would be
     * unregistered a second time, silently, when the first owner was destroyed.
     * <p>
     * 记录每个订阅者的生命周期绑定：手动注销时一并取消，避免旧 owner 销毁时把重新注册的订阅者再次注销。
     * Thread-safe; {@link #cancel(Object)} may be called from any thread.
     */
    static final class Registry {
        private final Map<Object, List<LifecycleBinding>> bindings = new IdentityHashMap<>();

        /**
         * Like {@link LifecycleBinding#bind(Object, Lifecycle, Unregistrar)}, but tracked here.
         *
         * @return 已绑定返回 true；生命周期已经 DESTROYED 时返回 false
         */
        boolean bind(@NonNull Object subscriber, @NonNull Lifecycle lifecycle,
                     @NonNull Unregistrar unregistrar) {
            return LifecycleBinding.bind(subscriber, lifecycle, unregistrar, this);
        }

        /** Cancels every binding of {@code subscriber}; no-op for {@code null} or unknown. */
        void cancel(Object subscriber) {
            if (subscriber == null) {
                return;
            }
            List<LifecycleBinding> removed;
            synchronized (bindings) {
                removed = bindings.remove(subscriber);
            }
            if (removed != null) {
                for (LifecycleBinding b : removed) {
                    b.cancel();
                }
            }
        }

        /** Cancels every tracked binding. */
        void cancelAll() {
            List<LifecycleBinding> all = new ArrayList<>();
            synchronized (bindings) {
                for (List<LifecycleBinding> list : bindings.values()) {
                    all.addAll(list);
                }
                bindings.clear();
            }
            for (LifecycleBinding b : all) {
                b.cancel();
            }
        }

        /** Number of subscribers with at least one live binding (for tests). */
        int size() {
            synchronized (bindings) {
                return bindings.size();
            }
        }

        private void add(Object subscriber, LifecycleBinding binding) {
            synchronized (bindings) {
                List<LifecycleBinding> list = bindings.get(subscriber);
                if (list == null) {
                    list = new ArrayList<>(1);
                    bindings.put(subscriber, list);
                }
                list.add(binding);
            }
        }
    }
}
