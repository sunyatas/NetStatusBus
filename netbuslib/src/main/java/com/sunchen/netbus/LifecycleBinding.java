package com.sunchen.netbus;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

/**
 * 把订阅者和一个 {@link LifecycleOwner} 绑定：生命周期进入 DESTROYED 时自动注销订阅者并移除自身。
 */
final class LifecycleBinding implements LifecycleEventObserver {

    interface Unregistrar {
        void unregister(Object subscriber);
    }

    private final Object subscriber;
    private final Unregistrar unregistrar;

    private LifecycleBinding(Object subscriber, Unregistrar unregistrar) {
        this.subscriber = subscriber;
        this.unregistrar = unregistrar;
    }

    /**
     * @return 已绑定返回 true；生命周期已经 DESTROYED 时不绑定并返回 false
     */
    static boolean bind(@NonNull Object subscriber, @NonNull Lifecycle lifecycle,
                        @NonNull Unregistrar unregistrar) {
        if (lifecycle.getCurrentState() == Lifecycle.State.DESTROYED) {
            return false;
        }
        lifecycle.addObserver(new LifecycleBinding(subscriber, unregistrar));
        return true;
    }

    @Override
    public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event) {
        if (event == Lifecycle.Event.ON_DESTROY) {
            source.getLifecycle().removeObserver(this);
            unregistrar.unregister(subscriber);
        }
    }
}
