package com.sunchen.netbus;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkRequest;
import android.os.Build;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;

/**
 * Created by 「孙晨」 on 2019/3/31 0031   19:07.
 * <p>
 * God bless me only
 * <p>
 * NetStatusBus
 */

public class NetStatusBus {

    private volatile Application application;
    private final NetStatusReceiver receiver;
    private ConnectivityManager.NetworkCallback networkCallback;

    public NetStatusBus() {
        receiver = new NetStatusReceiver();
    }

    private static class HolderClass {
        private static final NetStatusBus instance = new NetStatusBus();
    }

    public static NetStatusBus getInstance() {
        return HolderClass.instance;
    }

    public Application getApplication() {
        if (application == null) {
            throw new RuntimeException("application is empty");
        }
        return application;
    }


    @SuppressLint("MissingPermission")
    public synchronized void init(Application application) {
        if (application == null) {
            throw new IllegalArgumentException("application is empty");
        }
        this.application = application;
        // 重复调用 init 时不再重复注册网络回调
        if (networkCallback != null) {
            return;
        }
        receiver.setInitialNetType(com.sunchen.netbus.utils.NetworkUtils.getNetType());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ConnectivityManager manager = (ConnectivityManager) application
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager != null) {
                ConnectivityManager.NetworkCallback callback;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // 只跟踪系统默认网络，网络切换时立即回调（#4 #11）
                    callback = new DefaultNetworkCallbackImpl(receiver, manager);
                    manager.registerDefaultNetworkCallback(callback);
                } else {
                    callback = new NetworkCallbackImpl(receiver);
                    NetworkRequest request = new NetworkRequest.Builder().build();
                    manager.registerNetworkCallback(request, callback);
                }
                networkCallback = callback;
            }
        }
    }

    public void register(Object mContext) {
        if (application == null) {
            throw new IllegalArgumentException("you must NetStatusBus.getInstance().init(getApplication) first");
        }
        receiver.registerObserver(mContext);
    }

    /**
     * 注册订阅者，并在 {@code owner} 的生命周期进入 DESTROYED（例如 Activity/Fragment 的 onDestroy）时自动注销，
     * 不必再手动调用 {@link #unregister(Object)}。
     * <p>
     * 订阅者和 owner 可以是同一个对象（如 {@code register(this, this)}），也可以不同（如 Fragment 里的某个 presenter，
     * 传 {@code getViewLifecycleOwner()}）。如果 owner 已经 DESTROYED，则不会注册。提前手动 {@code unregister} 也没问题。
     * <p>
     * 必须在主线程调用（Lifecycle 的要求）。
     */
    @MainThread
    public void register(@NonNull Object subscriber, @NonNull LifecycleOwner owner) {
        if (application == null) {
            throw new IllegalArgumentException("you must NetStatusBus.getInstance().init(getApplication) first");
        }
        if (subscriber == null || owner == null) {
            throw new IllegalArgumentException("subscriber and owner must not be null");
        }
        boolean bound = LifecycleBinding.bind(subscriber, owner.getLifecycle(),
                new LifecycleBinding.Unregistrar() {
                    @Override
                    public void unregister(Object s) {
                        NetStatusBus.this.unregister(s);
                    }
                });
        if (bound) {
            receiver.registerObserver(subscriber);
        }
    }

    public void unregister(Object mContext) {
        receiver.unRegisterObserver(mContext);
    }

    public void unregisterAllObserver() {
        receiver.unRegisterAllObserver();
    }
}
