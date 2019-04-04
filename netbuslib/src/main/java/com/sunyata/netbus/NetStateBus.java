package com.sunyata.netbus;

import android.app.Application;
import android.content.IntentFilter;

import com.sunyata.netbus.utils.Constrants;

/**
 * Created by 「孙晨」 on 2019/3/31 0031   19:07.
 * <p>
 * God bless me only
 * <p>
 * NetStateBus
 */

public class NetStateBus {

    private static volatile NetStateBus instance;
    private Application application;
    private NetStateReceiver receiver;


    public NetStateBus() {
        receiver = new NetStateReceiver();
    }


    /**
     * 初始化方法
     *
     * @param application
     */
    public NetStateBus init(Application application) {
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


    public static NetStateBus getDefault() {
        if (instance == null) {
            synchronized (NetStateBus.class) {
                if (instance == null) {
                    instance = new NetStateBus();
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

    public void register(Object mContext) {
        receiver.registerObserver(mContext);
    }

    public void unregister(Object mContext) {
        receiver.unRegisterObserver(mContext);
    }

    public void unregisterAllObserver() {
        receiver.unRegisterAllObserver();
    }
}
