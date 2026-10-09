package com.sunchen.netbus;

import android.os.Handler;
import android.os.Looper;

import com.sunchen.netbus.annotation.NetSubscribe;
import com.sunchen.netbus.type.NetType;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by 「孙晨」 on 2019/3/31 0031   18:31.
 * <p>
 * God bless me only
 * <p>
 * NetStatusReceiver
 * <p>
 * 订阅者保存在线程安全的 Map 中，所有订阅方法统一在主线程回调。
 */

public class NetStatusReceiver {

    /**
     * 订阅方法的执行线程，默认为主线程
     */
    interface Dispatcher {
        void dispatch(Runnable runnable);
    }

    private volatile NetType mNetType;//网络类型

    private final ConcurrentMap<Object, List<MethodManager>> networkList = new ConcurrentHashMap<>();

    private final Dispatcher dispatcher;

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
        this.mNetType = netType;
        dispatcher.dispatch(new Runnable() {
            @Override
            public void run() {
                for (Map.Entry<Object, List<MethodManager>> entry : networkList.entrySet()) {
                    executeInvoke(entry.getKey(), entry.getValue(), netType);
                }
            }
        });
    }

    private void executeInvoke(Object subscriber, List<MethodManager> methodManagerList, NetType netType) {
        if (methodManagerList == null) {
            return;
        }
        // 分发过程中订阅者可能已被注销
        if (!networkList.containsKey(subscriber)) {
            return;
        }
        for (MethodManager subscribeMethod : methodManagerList) {
            switch (subscribeMethod.getMode()) {
                case AUTO:
                    invoke(subscribeMethod, subscriber, netType);
                    break;

                case WIFI:
                    if (netType == NetType.WIFI || netType == NetType.NONE)
                        invoke(subscribeMethod, subscriber, netType);
                    break;

                case WIFI_CONNECT:
                    if (netType == NetType.WIFI)
                        invoke(subscribeMethod, subscriber, netType);
                    break;

                case MOBILE:
                    if (netType == NetType.MOBILE || netType == NetType.NONE)
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void registerObserver(final Object mContext) {
        List<MethodManager> methodList = networkList.get(mContext);
        if (methodList == null) {
            // 开始添加
            methodList = Collections.unmodifiableList(findAnnotationMethod(mContext));
            List<MethodManager> previous = networkList.putIfAbsent(mContext, methodList);
            if (previous != null) {
                methodList = previous;
            }
        }
        // 注册后立即回调一次当前网络状态
        final List<MethodManager> finalList = methodList;
        final NetType current = mNetType;
        dispatcher.dispatch(new Runnable() {
            @Override
            public void run() {
                executeInvoke(mContext, finalList, current);
            }
        });
    }

    private List<MethodManager> findAnnotationMethod(Object mContext) {
        List<MethodManager> methodManagerList = new ArrayList<>();
        // 获取到activity fragment
        Class<?> clazz = mContext.getClass();
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            NetSubscribe netSubscribe = method.getAnnotation(NetSubscribe.class);
            if (netSubscribe == null) {
                continue;
            }
            //注解方法校验返回值
            Type genericReturnType = method.getGenericReturnType();
            if (!"void".equalsIgnoreCase(genericReturnType.toString())) {
                throw new IllegalArgumentException("you " + method.getName() + "method return value must be void");
            }

            //判断参数
            Class<?>[] parameterTypes = method.getParameterTypes();
            MethodManager methodManager;
            if (parameterTypes.length == 0) {
                methodManager = new MethodManager(null, netSubscribe.mode(), method);
            } else if (parameterTypes.length == 1) {
                methodManager = new MethodManager(parameterTypes[0], netSubscribe.mode(), method);
            } else {
                throw new IllegalArgumentException("Your method " + method.getName() + " can have at most one parameter of type NetType ");
            }

            methodManagerList.add(methodManager);
        }

        return methodManagerList;
    }

    public void unRegisterObserver(Object mContext) {
        if (mContext != null) {
            networkList.remove(mContext);
        }
    }

    public void unRegisterAllObserver() {
        networkList.clear();
    }

    /**
     * 当前已知的网络类型
     */
    NetType getNetType() {
        return mNetType;
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
