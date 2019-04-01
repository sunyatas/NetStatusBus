package com.sunchen.networkobserver;

import android.app.Application;
import android.content.IntentFilter;

import com.sunchen.networkobserver.listenter.NetChangeObserver;
import com.sunchen.networkobserver.utils.Constrants;

/**
 * Created by 「孙晨」 on 2019/3/31 0031   19:07.
 * <p>
 * God bless me only
 * <p>
 * NetworkManager
 */

public class NetworkManager {

    private static volatile NetworkManager instance;
    private Application application;
    private NetStateReceiver receiver;


    public NetworkManager() {
        receiver = new NetStateReceiver();
    }


    public static NetworkManager getInstance() {
        if (instance == null) {
            synchronized (NetworkManager.class) {
                if (instance == null) {
                    instance = new NetworkManager();
                }
            }
        }
        return instance;
    }


    /**
     * 初始化方法
     * @param application
     */
    public NetworkManager init(Application application) {
        if (application == null) {
            throw new IllegalArgumentException("application must be an instance of Application");
        }
        this.application = application;
//        动态广播注册
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constrants.ANDROID_NET_CHANGE_ACTION);
        application.registerReceiver(receiver, intentFilter);

        return this;
    }

    public void setListener(NetChangeObserver listener) {
        receiver.setListener(listener);
    }

    public Application getApplication() {
        if (application == null) {
            throw new RuntimeException("未传入application.context");
        }
        return application;
    }

}
