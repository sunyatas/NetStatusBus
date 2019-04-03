package com.sunyata.netbus.type;

/**
 * Created by 「孙晨」 on 2019/3/31 0031   18:53.
 * <p>
 * God bless me only
 * <p>
 * NetType
 */

public enum NetType {
    //只要有网络，不关心是wifi/gprs
    AUTO,
    //wifi网络
    WIFI,
    //pc、笔记本、pad上网
    CMNET,
    //手机上网
    CMWAP,
    //没有任何网络
    NONE,
}
