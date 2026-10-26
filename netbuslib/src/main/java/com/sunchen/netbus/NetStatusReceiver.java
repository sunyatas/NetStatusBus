package com.sunchen.netbus;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.sunchen.netbus.annotation.NetSubscribe;
import com.sunchen.netbus.type.NetType;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by 「孙晨」 on 2019/3/31 0031   18:31.
 * <p>
 * God bless me only
 * <p>
 * NetStatusReceiver
 * <p>
 * 订阅者以弱引用保存，避免 Activity/Fragment 忘记 unregister 时泄漏；
 * 注解方法按 Class 缓存，并向上查找父类中的 {@link NetSubscribe} 方法。
 * 所有订阅方法统一在主线程回调。
 */

public class NetStatusReceiver {

    /**
     * 订阅方法的执行线程，默认为主线程
     */
    interface Dispatcher {
        void dispatch(Runnable runnable);
    }

    /**
     * 网络类型变化的监听（库内部使用，例如驱动 LiveData），在 dispatcher 线程（默认主线程）回调
     */
    interface NetTypeListener {
        void onNetTypeChanged(NetType netType);
    }

    /**
     * 订阅方法抛出异常时的记录方式（库内部使用，测试中可替换）
     */
    interface ErrorLogger {
        void log(String message, Throwable error);
    }

    static final String TAG = "NetStatusBus";

    private static final ErrorLogger LOGCAT_LOGGER = new ErrorLogger() {
        @Override
        public void log(String message, Throwable error) {
            Log.e(TAG, message, error);
        }
    };

    private static volatile ErrorLogger errorLogger = LOGCAT_LOGGER;

    /** 仅供单元测试替换日志输出；传 null 恢复为 Logcat */
    static void setErrorLoggerForTest(ErrorLogger logger) {
        errorLogger = logger != null ? logger : LOGCAT_LOGGER;
    }

    /**
     * 单个订阅者及其已解析的注解方法；订阅者本身以弱引用持有。
     */
    static final class ObserverHolder {
        final WeakReference<Object> subscriberRef;
        final int identityHash;
        final List<MethodManager> methods;

        ObserverHolder(Object subscriber, List<MethodManager> methods) {
            this.subscriberRef = new WeakReference<>(subscriber);
            this.identityHash = System.identityHashCode(subscriber);
            this.methods = methods;
        }

        Object getSubscriber() {
            return subscriberRef.get();
        }

        boolean isFor(Object subscriber) {
            return subscriber != null
                    && identityHash == System.identityHashCode(subscriber)
                    && subscriber == subscriberRef.get();
        }
    }

    /** 按订阅者 Class 缓存已解析的注解方法（含父类），进程内只解析一次 */
    private static final ConcurrentMap<Class<?>, List<MethodManager>> METHOD_CACHE =
            new ConcurrentHashMap<>();

    private volatile NetType mNetType;//网络类型

    private final CopyOnWriteArrayList<ObserverHolder> networkList = new CopyOnWriteArrayList<>();

    /**
     * 保护“查找已有条目 + 新增条目”这一组合操作，避免多个线程同时 register 同一个订阅者时
     * 产生重复条目（导致每次变化回调两次）。分发路径只读 CopyOnWriteArrayList，不需要加锁。
     */
    private final Object observersLock = new Object();

    private final Dispatcher dispatcher;

    private volatile NetTypeListener netTypeListener;

    /** 公开的无注解监听（强引用，需调用方移除或绑定生命周期） */
    private final CopyOnWriteArrayList<OnNetTypeChangeListener> listeners = new CopyOnWriteArrayList<>();

    protected NetStatusReceiver() {
        this(new MainThreadDispatcher());
    }

    NetStatusReceiver(Dispatcher dispatcher) {
        this.mNetType = NetType.NONE;
        this.dispatcher = dispatcher;
    }

    /**
     * 分发（可在任意线程调用，订阅方法会切换到主线程执行）
     */
    protected void post(final NetType netType) {
        final NetType previous;
        synchronized (this) {
            previous = this.mNetType;
            this.mNetType = netType;
        }
        dispatcher.dispatch(new Runnable() {
            @Override
            public void run() {
                NetTypeListener listener = netTypeListener;
                if (listener != null) {
                    listener.onNetTypeChanged(netType);
                }
                for (OnNetTypeChangeListener l : listeners) {
                    // 本轮分发中已被前面的回调移除的监听不再回调
                    if (listeners.contains(l)) {
                        invokeListener(l, netType);
                    }
                }
                for (ObserverHolder holder : networkList) {
                    Object subscriber = holder.getSubscriber();
                    if (subscriber == null) {
                        networkList.remove(holder);
                        continue;
                    }
                    executeInvoke(subscriber, holder, netType, previous);
                }
            }
        });
    }

    /**
     * 仅当网络类型发生变化时才分发，避免系统多次回调造成重复通知
     */
    protected void postIfChanged(NetType netType) {
        synchronized (this) {
            if (netType == mNetType) {
                return;
            }
        }
        post(netType);
    }

    /**
     * 设置网络类型监听，并立即（经 dispatcher）用当前网络类型回调一次；传 null 取消监听
     */
    void setNetTypeListener(final NetTypeListener listener) {
        this.netTypeListener = listener;
        if (listener == null) {
            return;
        }
        dispatcher.dispatch(new Runnable() {
            @Override
            public void run() {
                // 期间若已被替换，则不再回调旧监听
                if (netTypeListener == listener) {
                    listener.onNetTypeChanged(mNetType);
                }
            }
        });
    }

    /**
     * 添加无注解监听，并立即（经 dispatcher）用当前网络类型回调一次。同一个监听重复添加只保留一份。
     */
    void addListener(final OnNetTypeChangeListener listener) {
        if (listener == null) {
            return;
        }
        if (!listeners.addIfAbsent(listener)) {
            return;
        }
        final NetType current = mNetType;
        dispatcher.dispatch(new Runnable() {
            @Override
            public void run() {
                // 期间若已被移除，则不再回调
                if (listeners.contains(listener)) {
                    invokeListener(listener, current);
                }
            }
        });
    }

    void removeListener(OnNetTypeChangeListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /** 已添加的无注解监听数量（测试与诊断用） */
    int listenerCount() {
        return listeners.size();
    }

    private static void invokeListener(OnNetTypeChangeListener listener, NetType netType) {
        try {
            listener.onNetTypeChanged(netType);
        } catch (RuntimeException e) {
            // 与 @NetSubscribe 一致：记录异常，不影响其他监听和订阅者
            errorLogger.log("OnNetTypeChangeListener " + listener.getClass().getName()
                    + " threw " + e.getClass().getName() + " while handling " + netType, e);
        }
    }

    /**
     * 设置初始网络类型（不分发）
     */
    void setInitialNetType(NetType netType) {
        this.mNetType = netType;
    }

    private void executeInvoke(Object subscriber, ObserverHolder holder, NetType netType) {
        executeInvoke(subscriber, holder, netType, netType);
    }

    /**
     * @param previous 变化前的网络类型。WIFI/MOBILE 模式下，只有对应网络本身连上或断开时才回调（#7）
     */
    private void executeInvoke(Object subscriber, ObserverHolder holder, NetType netType, NetType previous) {
        if (holder == null || holder.methods == null) {
            return;
        }
        // 分发过程中订阅者可能已被注销或已被回收
        if (!networkList.contains(holder) || holder.getSubscriber() != subscriber) {
            return;
        }
        for (MethodManager subscribeMethod : holder.methods) {
            switch (subscribeMethod.getMode()) {
                case AUTO:
                    invoke(subscribeMethod, subscriber, netType);
                    break;

                case WIFI:
                    if (netType == NetType.WIFI || previous == NetType.WIFI)
                        invoke(subscribeMethod, subscriber, netType);
                    break;

                case WIFI_CONNECT:
                    if (netType == NetType.WIFI)
                        invoke(subscribeMethod, subscriber, netType);
                    break;

                case MOBILE:
                    if (netType == NetType.MOBILE || previous == NetType.MOBILE)
                        invoke(subscribeMethod, subscriber, netType);
                    break;

                case MOBILE_CONNECT:
                    if (netType == NetType.MOBILE)
                        invoke(subscribeMethod, subscriber, netType);
                    break;

                case NONE:
                    if (netType == NetType.NONE)
                        invoke(subscribeMethod, subscriber, netType);
                    break;

                default:
                    break;
            }
        }
    }

    private void invoke(MethodManager subscribeMethod, Object subscriber, NetType netType) {
        Method execute = subscribeMethod.getMethod();
        try {
            //有参数时
            if (subscribeMethod.getParameterClazz() != null) {
                if (subscribeMethod.getParameterClazz().isAssignableFrom(netType.getClass())) {
                    execute.invoke(subscriber, netType);
                }
            } else {
                execute.invoke(subscriber);
            }
        } catch (InvocationTargetException e) {
            // 订阅方法自身抛出的异常：记录真正的原因，不影响其他订阅者继续收到回调
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            errorLogger.log("@NetSubscribe method " + describe(subscriber, execute)
                    + " threw " + cause.getClass().getName() + " while handling " + netType, cause);
        } catch (Exception e) {
            errorLogger.log("Failed to invoke @NetSubscribe method "
                    + describe(subscriber, execute) + " for " + netType, e);
        }
    }

    private static String describe(Object subscriber, Method method) {
        return subscriber.getClass().getName() + "#" + method.getName() + "()"
                + (method.getDeclaringClass() != subscriber.getClass()
                ? " (declared in " + method.getDeclaringClass().getName() + ")" : "");
    }

    protected void registerObserver(final Object mContext) {
        if (mContext == null) {
            return;
        }
        // 在锁外解析注解（可能抛出签名错误），锁内只做查重和新增
        List<MethodManager> methodList = findAnnotationMethods(mContext.getClass());
        final ObserverHolder holder;
        synchronized (observersLock) {
            ObserverHolder existing = findHolder(mContext);
            if (existing != null) {
                holder = existing;
            } else {
                holder = new ObserverHolder(mContext, methodList);
                networkList.add(holder);
            }
        }
        // 注册后立即回调一次当前网络状态
        final NetType current = mNetType;
        dispatcher.dispatch(new Runnable() {
            @Override
            public void run() {
                executeInvoke(mContext, holder, current);
            }
        });
    }

    private ObserverHolder findHolder(Object subscriber) {
        for (ObserverHolder holder : networkList) {
            if (holder.isFor(subscriber)) {
                return holder;
            }
            if (holder.getSubscriber() == null) {
                networkList.remove(holder);
            }
        }
        return null;
    }

    /**
     * 解析 clazz 及其父类上的 {@link NetSubscribe} 方法，结果按 Class 缓存。
     * 子类若覆盖同签名方法，只保留子类声明的那一份，避免重复回调。
     */
    static List<MethodManager> findAnnotationMethods(Class<?> clazz) {
        List<MethodManager> cached = METHOD_CACHE.get(clazz);
        if (cached != null) {
            return cached;
        }
        List<MethodManager> methodManagerList = new ArrayList<>();
        Set<String> seenSignatures = new HashSet<>();
        Class<?> type = clazz;
        while (type != null && type != Object.class) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.isBridge() || method.isSynthetic()) {
                    continue;
                }
                NetSubscribe netSubscribe = method.getAnnotation(NetSubscribe.class);
                if (netSubscribe == null) {
                    continue;
                }
                String signature = methodSignature(method);
                if (!seenSignatures.add(signature)) {
                    // 子类已声明同签名方法，跳过父类
                    continue;
                }
                MethodManager methodManager = new MethodManager(
                        validateSignature(method), netSubscribe.mode(), method);

                if (!method.isAccessible()) {
                    method.setAccessible(true);
                }
                methodManagerList.add(methodManager);
            }
            type = type.getSuperclass();
        }

        List<MethodManager> immutable = Collections.unmodifiableList(methodManagerList);
        List<MethodManager> raced = METHOD_CACHE.putIfAbsent(clazz, immutable);
        return raced != null ? raced : immutable;
    }

    /**
     * 校验注解方法签名：返回值必须是 void，最多一个参数，且参数类型必须能接收 {@link NetType}。
     * 不合规时在 register 阶段直接抛出异常，而不是注册成功后静默地从不回调。
     *
     * @return 参数类型；无参方法返回 null
     */
    private static Class<?> validateSignature(Method method) {
        String where = method.getDeclaringClass().getName() + "#" + method.getName();
        if (method.getReturnType() != void.class) {
            throw new IllegalArgumentException("@NetSubscribe method " + where
                    + " must return void, but returns " + method.getReturnType().getName());
        }
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length == 0) {
            return null;
        }
        if (parameterTypes.length > 1) {
            throw new IllegalArgumentException("@NetSubscribe method " + where
                    + " can have at most one parameter of type NetType, but has "
                    + parameterTypes.length);
        }
        Class<?> parameter = parameterTypes[0];
        if (!parameter.isAssignableFrom(NetType.class)) {
            throw new IllegalArgumentException("@NetSubscribe method " + where
                    + " parameter must be NetType, but is " + parameter.getName());
        }
        return parameter;
    }

    private static String methodSignature(Method method) {
        StringBuilder sb = new StringBuilder(method.getName());
        sb.append('(');
        Class<?>[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(params[i].getName());
        }
        sb.append(')');
        return sb.toString();
    }

    public void unRegisterObserver(Object mContext) {
        if (mContext == null) {
            return;
        }
        synchronized (observersLock) {
            for (ObserverHolder holder : networkList) {
                if (holder.isFor(mContext) || holder.getSubscriber() == null) {
                    networkList.remove(holder);
                }
            }
        }
    }

    public void unRegisterAllObserver() {
        synchronized (observersLock) {
            networkList.clear();
        }
        listeners.clear();
    }

    /**
     * 当前已知的网络类型
     */
    NetType getNetType() {
        return mNetType;
    }

    /** 仍存活的订阅者数量（测试与诊断用） */
    int aliveObserverCount() {
        int count = 0;
        for (ObserverHolder holder : networkList) {
            if (holder.getSubscriber() != null) {
                count++;
            }
        }
        return count;
    }

    /** 清理已被 GC 的弱引用条目（测试与诊断用） */
    void purgeDeadObservers() {
        for (ObserverHolder holder : networkList) {
            if (holder.getSubscriber() == null) {
                networkList.remove(holder);
            }
        }
    }

    /** 清空注解方法缓存（仅测试用） */
    static void clearMethodCacheForTest() {
        METHOD_CACHE.clear();
    }

    /**
     * 将订阅方法切换到主线程执行；若当前已在主线程则直接执行
     */
    private static final class MainThreadDispatcher implements Dispatcher {
        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void dispatch(Runnable runnable) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                runnable.run();
            } else {
                handler.post(runnable);
            }
        }
    }
}
