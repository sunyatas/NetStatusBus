package com.sunyata.sample;

import android.app.Application;

import com.sunyata.netbus.NetStatusBus;

/**
 * Created by 「孙晨」 on 2019/4/2   22:19.
 * <p>
 * God bless me only
 * <p>
 * BaseApplication
 */

public class BaseApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        NetStatusBus.getInstance().init(this);
    }
}
