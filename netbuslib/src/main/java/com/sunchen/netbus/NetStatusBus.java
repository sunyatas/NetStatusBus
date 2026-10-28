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
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.sunchen.netbus.annotation.NetSubscribe;
import com.sunchen.netbus.type.NetType;

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
    private final MutableLiveData<NetType> netTypeLiveData = new MutableLiveData<>();

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
            throw new IllegalStateException("NetStatusBus is not initialized: call NetStatusBus.getInstance().init(application) first, "
                    + "or use the NetworkUtils methods that take a Context");
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
        // 监听在主线程回调，直接 setValue，保证 LiveData 的值与订阅方法收到的顺序一致
        receiver.setNetTypeListener(new NetStatusReceiver.NetTypeListener() {
            @Override
            public void onNetTypeChanged(NetType netType) {
                netTypeLiveData.setValue(netType);
            }
        });
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

    /**
     * 以 {@link LiveData} 形式观察网络类型，不需要注解和反射，Java/Kotlin 都可直接使用。
     * <p>
     * {@link #init(Application)} 之后会立即带上当前网络类型，之后每次变化都会更新（主线程）。
     * 观察者遵循 LiveData 的规则：只在 owner 处于 STARTED/RESUMED 时收到值，DESTROYED 时自动移除。
     * Kotlin 协程用户可以配合 {@code androidx.lifecycle:lifecycle-livedata-ktx} 的 {@code asFlow()} 转成 Flow。
     * <p>
     * 注意 LiveData 只保留最新值：观察者不活跃期间的中间变化会被合并，适合驱动 UI 状态；
     * 需要 {@link com.sunchen.netbus.type.Mode} 过滤或不想错过任何一次变化时，请用 {@link NetSubscribe} 订阅。
     */
    @NonNull
    public LiveData<NetType> getNetTypeLiveData() {
        return netTypeLiveData;
    }

    /**
     * Adds an annotation-free, reflection-free listener. It is called once with the current
     * {@link NetType} right after being added, then on every change, always on the main thread.
     * Unlike {@link #getNetTypeLiveData()}, every change is delivered even when no UI is visible,
     * so this also works from a {@code Service} or other code without a lifecycle.
     * <p>
     * The listener is held strongly (so lambdas work); call {@link #removeListener} when done,
     * or use {@link #addListener(OnNetTypeChangeListener, LifecycleOwner)}. Adding the same
     * instance twice has no effect.
     * <p>
     * 添加无注解、无反射的监听：添加后立即回调当前网络类型，之后每次变化都回调（主线程）。
     * 监听以强引用持有，用完请调用 {@link #removeListener}，或使用绑定生命周期的重载。
     */
    public void addListener(@NonNull OnNetTypeChangeListener listener) {
        if (application == null) {
            throw new IllegalArgumentException("you must NetStatusBus.getInstance().init(getApplication) first");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        receiver.addListener(listener);
    }

    /**
     * Like {@link #addListener(OnNetTypeChangeListener)}, but removes the listener automatically
     * when {@code owner} reaches {@code DESTROYED}. Not added if the owner is already destroyed.
     * Must be called on the main thread.
     * <p>
     * 添加监听，并在 owner 进入 DESTROYED 时自动移除；owner 已销毁时不添加。必须在主线程调用。
     */
    @MainThread
    public void addListener(@NonNull OnNetTypeChangeListener listener, @NonNull LifecycleOwner owner) {
        if (application == null) {
            throw new IllegalArgumentException("you must NetStatusBus.getInstance().init(getApplication) first");
        }
        if (listener == null || owner == null) {
            throw new IllegalArgumentException("listener and owner must not be null");
        }
        boolean bound = LifecycleBinding.bind(listener, owner.getLifecycle(),
                new LifecycleBinding.Unregistrar() {
                    @Override
                    public void unregister(Object l) {
                        receiver.removeListener((OnNetTypeChangeListener) l);
                    }
                });
        if (bound) {
            receiver.addListener(listener);
        }
    }

    /**
     * Removes a listener added with {@code addListener}. Safe to call more than once.
     * <p>
     * 移除通过 {@code addListener} 添加的监听，可重复调用。
     */
    public void removeListener(@NonNull OnNetTypeChangeListener listener) {
        receiver.removeListener(listener);
    }

    public void unregister(Object mContext) {
        receiver.unRegisterObserver(mContext);
    }

    /**
     * Unregisters every {@code @NetSubscribe} subscriber and removes every listener added with
     * {@code addListener}. {@link #getNetTypeLiveData()} observers are not affected.
     */
    public void unregisterAllObserver() {
        receiver.unRegisterAllObserver();
    }
}
