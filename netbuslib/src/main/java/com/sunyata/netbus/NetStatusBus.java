package com.sunyata.netbus;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkRequest;
import android.os.Build;

/**
 * Created by 「孙晨」 on 2019/3/31 0031   19:07.
 * <p>
 * God bless me only
 * <p>
 * NetStatusBus
 */

public class NetStatusBus {

    private static volatile NetStatusBus instance;
    private Application application;
    private NetStatusReceiver receiver;

    public NetStatusBus() {
        receiver = new NetStatusReceiver();
    }

    public static NetStatusBus getDefault() {
        if (instance == null) {
            synchronized (NetStatusBus.class) {
                if (instance == null) {
                    instance = new NetStatusBus();
                }
            }
        }
        return instance;
    }

    public Application getApplication() {
        if (application == null) {
            throw new RuntimeException("未传入application.context");
        }
        return application;
    }

    /**
     * 初始化方法
     * @param application
     */
    @SuppressLint("MissingPermission")
    public void init(Application application) {
        if (application == null) {
            throw new IllegalArgumentException("application is empty");
        }
        this.application = application;
        //不通过广播注册
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            ConnectivityManager.NetworkCallback networkCallback = new NetworkCallbackImpl(receiver);
            NetworkRequest.Builder builder = new NetworkRequest.Builder();
            NetworkRequest request = builder.build();
            ConnectivityManager manager = (ConnectivityManager) NetStatusBus
                    .getDefault().getApplication()
                    .getSystemService(Context.CONNECTIVITY_SERVICE);

            if (manager != null) {
                manager.registerNetworkCallback(request, networkCallback);
            }
        }

    }

    private ConnectivityManager.NetworkCallback networkCallback;

    @SuppressLint("MissingPermission")
    public void register(Object mContext) {
//        if (application == null) {
//            throw new IllegalArgumentException("application is empty");
//        }
        if (mContext instanceof Activity) {
            this.application = ((Activity) mContext).getApplication();
            //不通过广播注册
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
                if (networkCallback == null){
                    networkCallback = new NetworkCallbackImpl(receiver);
                    NetworkRequest.Builder builder = new NetworkRequest.Builder();
                    NetworkRequest request = builder.build();
                    ConnectivityManager manager = (ConnectivityManager) NetStatusBus
                            .getDefault().getApplication()
                            .getSystemService(Context.CONNECTIVITY_SERVICE);

                    if (manager != null) {
                        manager.registerNetworkCallback(request, networkCallback);
                    }
                }

            }

            receiver.registerObserver(mContext);
        }
    }

    public void unregister(Object mContext) {
        receiver.unRegisterObserver(mContext);
    }

    public void unregisterAllObserver() {
        receiver.unRegisterAllObserver();
    }
}
