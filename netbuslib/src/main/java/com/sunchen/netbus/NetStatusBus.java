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
 * Entry point of the library: tracks the device's default network and tells your code when
 * its {@link NetType} changes. Use the singleton from {@link #getInstance()} and call
 * {@link #init(Application)} once, usually in {@code Application.onCreate()}.
 * <p>
 * Three ways to receive changes, all called on the main thread:
 * <ul>
 * <li>{@link #register(Object)}: methods annotated with {@link NetSubscribe}, with
 *     {@link com.sunchen.netbus.type.Mode} filters;</li>
 * <li>{@link #addListener(OnNetTypeChangeListener)}: a plain callback, no annotations or
 *     reflection;</li>
 * <li>{@link #getNetTypeLiveData()}: a {@link LiveData} for UI state.</li>
 * </ul>
 * <p>
 * 库的入口：跟踪系统默认网络并在网络类型变化时通知。通过 {@link #getInstance()} 获取单例，
 * 在 {@code Application.onCreate()} 中调用一次 {@link #init(Application)}。
 * 可用注解订阅、无注解监听或 LiveData 三种方式接收变化，均在主线程回调。
 * <p>
 * Originally written by 孙晨 in 2019.
 */

public class NetStatusBus {

    private volatile Application application;
    private final NetStatusReceiver receiver;
    private ConnectivityManager.NetworkCallback networkCallback;
    private final MutableLiveData<NetType> netTypeLiveData = new MutableLiveData<>();

    /**
     * Creates a separate bus. Most apps should use {@link #getInstance()} instead.
     */
    public NetStatusBus() {
        this(new NetStatusReceiver());
    }

    /** For tests: lets them supply a receiver with a synchronous dispatcher. */
    NetStatusBus(NetStatusReceiver receiver) {
        this.receiver = receiver;
    }

    private static class HolderClass {
        private static final NetStatusBus instance = new NetStatusBus();
    }

    /**
     * Returns the shared bus.
     * <p>
     * 返回全局单例。
     */
    public static NetStatusBus getInstance() {
        return HolderClass.instance;
    }

    /**
     * Returns the application passed to {@link #init(Application)}.
     *
     * @throws IllegalStateException if {@link #init(Application)} has not been called
     */
    public Application getApplication() {
        requireInitialized();
        return application;
    }

    /**
     * Returns {@code true} once {@link #init(Application)} has been called.
     * <p>
     * 是否已调用过 {@link #init(Application)}。
     */
    public boolean isInitialized() {
        return application != null;
    }

    /**
     * Returns the network type the bus currently knows about: the type recorded by
     * {@link #init(Application)}, updated on every change the bus delivers. It reads a cached
     * value and does not query the system, so it is cheap to call from any thread. It is the
     * same value subscribers and listeners last received. To query the system directly, use
     * {@link com.sunchen.netbus.utils.NetworkUtils#getNetType()}.
     * <p>
     * 返回总线当前记录的网络类型（init 时记录，之后随每次分发更新），只读缓存、不查询系统，任意线程可调用。
     *
     * @throws IllegalStateException if {@link #init(Application)} has not been called
     */
    @NonNull
    public NetType getCurrentNetType() {
        requireInitialized();
        return receiver.getCurrentNetType();
    }

    private void requireInitialized() {
        if (application == null) {
            throw new IllegalStateException("NetStatusBus is not initialized: call NetStatusBus.getInstance().init(application) first, "
                    + "or use the NetworkUtils methods that take a Context");
        }
    }


    /**
     * Starts tracking the network. Call once, usually in {@code Application.onCreate()};
     * later calls only update the stored application and do not register the system
     * callback again. Records the current type, which {@link #getCurrentNetType()} and
     * {@link #getNetTypeLiveData()} report right away. The library's manifest already
     * declares {@code ACCESS_NETWORK_STATE}.
     * <p>
     * 开始跟踪网络，通常在 {@code Application.onCreate()} 中调用一次；重复调用不会重复注册系统回调。
     *
     * @throws IllegalArgumentException if {@code application} is {@code null}
     */
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

    /**
     * Registers an object whose {@link NetSubscribe} methods (including those declared in
     * superclasses) should receive network changes. Each method is called right away on the
     * main thread with the current type if its mode matches, then on every matching change.
     * The subscriber is held weakly, but call {@link #unregister(Object)} when done, or use
     * {@link #register(Object, LifecycleOwner)}. Registering the same object twice has no
     * effect; {@code null} is ignored. Can be called from any thread.
     * <p>
     * 注册订阅者：其（含父类的）{@link NetSubscribe} 方法会立即按 mode 回调一次当前类型，之后回调每次匹配的变化（主线程）。
     * 订阅者以弱引用持有，但用完仍请注销；重复注册无效果。
     *
     * @throws IllegalStateException if {@link #init(Application)} has not been called
     * @throws IllegalArgumentException if an annotated method has an invalid signature
     */
    public void register(Object mContext) {
        requireInitialized();
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
     * <p>
     * Registers {@code subscriber} like {@link #register(Object)} and unregisters it
     * automatically when {@code owner} reaches {@code DESTROYED}. The subscriber and the owner
     * may be the same object ({@code register(this, this)}) or different ones (for example a
     * presenter in a Fragment with {@code getViewLifecycleOwner()}). Nothing is registered if
     * the owner is already destroyed. Must be called on the main thread.
     *
     * @throws IllegalStateException if {@link #init(Application)} has not been called
     */
    @MainThread
    public void register(@NonNull Object subscriber, @NonNull LifecycleOwner owner) {
        requireInitialized();
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
     * <p>
     * Observes the network type as {@link LiveData}, with no annotations or reflection. After
     * {@link #init(Application)} it holds the current type and is updated on every change
     * (on the main thread). The usual LiveData rules apply: observers only receive values
     * while their owner is at least {@code STARTED}, and intermediate changes made while they
     * were inactive are merged into the latest value. That suits UI state; to filter by
     * {@link com.sunchen.netbus.type.Mode} or see every change, use {@link NetSubscribe} or
     * {@link #addListener(OnNetTypeChangeListener)}. Kotlin coroutine users can convert it with
     * {@code asFlow()} from {@code androidx.lifecycle:lifecycle-livedata-ktx}.
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
        requireInitialized();
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
        requireInitialized();
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

    /**
     * Stops delivering changes to a subscriber passed to {@code register}. Safe to call more
     * than once, before {@code init}, or with {@code null}.
     * <p>
     * 注销订阅者，可重复调用。
     */
    public void unregister(Object mContext) {
        receiver.unRegisterObserver(mContext);
    }

    /**
     * Unregisters every {@code @NetSubscribe} subscriber and removes every listener added with
     * {@code addListener}. {@link #getNetTypeLiveData()} observers are not affected.
     * <p>
     * 注销所有注解订阅者并移除所有 addListener 添加的监听；不影响 LiveData 观察者。
     */
    public void unregisterAllObserver() {
        receiver.unRegisterAllObserver();
    }
}
