package com.sunyata.netbus.listenter;

import com.sunyata.netbus.type.NetType;

/**
 * Created by 「孙晨」 on 2019/3/31 0031   18:48.
 * <p>
 * God bless me only
 * <p>
 * NetChangeObserver
 */

public interface NetChangeObserver {

    void onConnect(NetType netType);

    void onDisConnect();
}