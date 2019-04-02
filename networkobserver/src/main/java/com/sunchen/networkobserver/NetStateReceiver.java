package com.sunchen.networkobserver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.sunchen.networkobserver.annotation.Network;
import com.sunchen.networkobserver.listenter.NetChangeObserver;
import com.sunchen.networkobserver.type.NetType;
import com.sunchen.networkobserver.utils.Constrants;
import com.sunchen.networkobserver.utils.NetworkUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by 「孙晨」 on 2019/3/31 0031   18:31.
 * <p>
 * God bless me only
 * <p>
 * NetStateReceiver
 */

public class NetStateReceiver extends BroadcastReceiver {

    private NetType netType;//网络类型

//    private NetChangeObserver listener;//网络监听

    private Map<Object, List<MethodManager>> networkList;


    public NetStateReceiver() {
        netType = NetType.NONE;
        networkList = new HashMap<>();
    }

//    public void setListener(NetChangeObserver listener) {
//        this.listener = listener;
//    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
//        处理广播事件
        if (intent.getAction().equalsIgnoreCase(Constrants.ANDROID_NET_CHANGE_ACTION)) {
            Log.e(Constrants.LOG_TAG, "网络发生了改变");
            netType = NetworkUtils.getNetType();
            if (NetworkUtils.isNetworkAvailable()) {
                Log.e(Constrants.LOG_TAG, "网络连接成功");
//                if (listener != null) {
//                    listener.onConnect(netType);
//                }
            } else {
                Log.e(Constrants.LOG_TAG, "没有网络连接");
//                if (listener != null) {
//                    listener.onDisConnect();
//                }
            }

            post(netType);
        }
    }


    /**
     * 分发
     *
     * @param netType
     */
    private void post(NetType netType) {
        //所有的注册类
        Set<Object> set = networkList.keySet();
        for (Object getter : set) {
            List<MethodManager> methodManagerList = networkList.get(getter);
            if (methodManagerList != null) {
                for (MethodManager method : methodManagerList) {
                    if (method.getClazz().isAssignableFrom(netType.getClass())) {
                        switch (method.getNetType()) {
                            case AUTO:
                                invoke(method, getter, netType);
                                break;

                            case WIFI:
                                if (netType == NetType.WIFI || netType == NetType.NONE)
                                    invoke(method, getter, netType);

                                break;

                            case CMNET:
                                if (netType == NetType.WIFI || netType == NetType.NONE)
                                    invoke(method, getter, netType);
                                break;

                            case CMWAP:
                                if (netType == NetType.WIFI || netType == NetType.NONE)
                                    invoke(method, getter, netType);
                                break;

                        }
                    }
                }
            }
        }
    }

    private void invoke(MethodManager method, Object getter, NetType netType) {
        Method excute = method.getMethod();
        try {
            excute.invoke(getter, netType);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void registerObserver(Object mContext) {
        List<MethodManager> methodList = networkList.get(mContext);
        if (methodList == null) {
//        开始添加
            methodList = findAnnotationMethod(mContext);
            networkList.put(mContext, methodList);
        }
    }

    private List<MethodManager> findAnnotationMethod(Object mContext) {
        List<MethodManager> methodManagerList = new ArrayList<>();
//        获取到activity fragment
        Class<?> clazz = mContext.getClass();
        Method[] methods = clazz.getMethods();

        for (Method method : methods) {
            Network networkAnnotation = method.getAnnotation(Network.class);
            if (networkAnnotation == null) {
                continue;
            }

//            注解方法校验
            Type genericReturnType = method.getGenericReturnType();
            if (!"void".equalsIgnoreCase(genericReturnType.toString())) {
                Log.e(Constrants.LOG_TAG, "方法不是void");
                throw new IllegalArgumentException("方法不是void");
            }


            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != 1) {
                throw new IllegalArgumentException(method.getName() + "方法只能有一个参数");
            }


            MethodManager methodManager = new MethodManager(parameterTypes[0], networkAnnotation.netType(), method);
            methodManagerList.add(methodManager);

        }
        return methodManagerList;
    }

    public void unRegisterObserver(Object mContext) {
        if (!networkList.isEmpty()) {
            networkList.remove(mContext);
        }
    }

    public void unRegisterAllObserver() {
        if (!networkList.isEmpty()) {
            networkList.clear();
        }
//注销广播
        NetworkManager.getInstance().unRegisterObserver(this);
    }
}
