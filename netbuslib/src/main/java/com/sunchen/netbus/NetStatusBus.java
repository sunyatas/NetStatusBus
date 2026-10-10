package com.sunchen.netbus;

import android.annotation.SuppressLint;
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
                ConnectivityManager.NetworkCallback callback = new NetworkCallbackImpl(receiver);
                NetworkRequest request = new NetworkRequest.Builder().build();
                manager.registerNetworkCallback(request, callback);
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

    public void unregister(Object mContext) {
        receiver.unRegisterObserver(mContext);
    }

    public void unregisterAllObserver() {
        receiver.unRegisterAllObserver();
    }
}
