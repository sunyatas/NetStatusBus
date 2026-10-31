package com.sunchen.netbus;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import com.sunchen.netbus.type.NetType;

/**
 * Annotation-free, reflection-free callback for network type changes.
 * <p>
 * Register with {@link NetStatusBus#addListener(OnNetTypeChangeListener)} or
 * {@link NetStatusBus#addListener(OnNetTypeChangeListener, androidx.lifecycle.LifecycleOwner)}.
 * The listener is called once with the current type right after it is added, then on every change.
 * Calls are always made on the main thread.
 * <p>
 * 无需注解和反射的网络类型回调：添加后立即用当前网络类型回调一次，之后每次变化都会回调（主线程）。
 */
public interface OnNetTypeChangeListener {

    /**
     * Called on the main thread with the current network type.
     *
     * @param netType the type the bus just delivered; never {@code null}
     */
    @MainThread
    void onNetTypeChanged(@NonNull NetType netType);
}
