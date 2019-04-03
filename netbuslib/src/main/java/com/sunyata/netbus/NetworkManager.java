package com.sunyata.netbus;

import android.app.Application;
import android.content.IntentFilter;

import com.sunyata.netbus.utils.Constrants;

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


    /**
     * 初始化方法
     *
     * @param application
     */
    public NetworkManager init(Application application) {
        if (application == null) {
            throw new IllegalArgumentException("application is empty");
        }
        this.application = application;
//        动态广播注册
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constrants.ANDROID_NET_CHANGE_ACTION);
        application.registerReceiver(receiver, intentFilter);

        return this;
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


    public Application getApplication() {
        if (application == null) {
            throw new RuntimeException("未传入application.context");
        }

        return application;
    }

    public void registerObserver(Object mContext) {
        receiver.registerObserver(mContext);
    }

    public void unRegisterObserver(Object mContext) {
        receiver.unRegisterObserver(mContext);
    }

    public void unRegisterAllObserver() {
        receiver.unRegisterAllObserver();
    }
}
