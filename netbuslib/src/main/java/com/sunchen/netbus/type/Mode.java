package com.sunchen.netbus.type;

/**
 * Created by 「孙晨」 on 2019/5/21 0021   09:37.
 * <p>
 * God bless me only
 * <p>
 * Mode
 */

public enum Mode {

    /**
     * 默认的订阅模式，任何网络变化均会回调使用此模式的订阅方法
     */
    AUTO,

    /**
     * 针对 wifi 的订阅模式，当 wifi 连接或 wifi 本身断开时会回调使用此模式的订阅方法（其他网络断开不会回调）
     */
    WIFI,

    /**
     * 只有当 wifi 连接时才会回调使用此模式的订阅方法
     */
    WIFI_CONNECT,

    /**
     * 针对 移动网络 的订阅模式，当 移动网络 连接或移动网络本身断开时会回调使用此模式的订阅方法（其他网络断开不会回调）
     */
    MOBILE,

    /**
     * 只有当 移动网络 连接时才会回调使用此模式的订阅方法
     */
    MOBILE_CONNECT,

    /**
     * 当网络丢失时会调用使用该模式的订阅方法
     */
    NONE

}
