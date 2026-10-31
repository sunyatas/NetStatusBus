package com.sunchen.netbus.type;

/**
 * Which network changes a {@link com.sunchen.netbus.annotation.NetSubscribe} method receives.
 * <p>
 * The filter also applies to the callback made right after {@code register}, which reports
 * the current type: for example a {@link #WIFI_CONNECT} method is only called then if the
 * device is on Wi-Fi.
 * <p>
 * 订阅方法接收哪些网络变化。注册后立即回调当前类型时同样按此过滤。
 */
public enum Mode {

    /**
     * Every change (the default).
     * <p>
     * 默认模式，任何网络变化都会回调。
     */
    AUTO,

    /**
     * When Wi-Fi connects, or when Wi-Fi itself goes away (switching from Wi-Fi to another
     * network or to none). Changes between other networks are ignored.
     * <p>
     * wifi 连接或 wifi 本身断开时回调（其他网络断开不会回调）。
     */
    WIFI,

    /**
     * Only when Wi-Fi connects.
     * <p>
     * 只有 wifi 连接时才回调。
     */
    WIFI_CONNECT,

    /**
     * When cellular data connects, or when cellular data itself goes away. Changes between
     * other networks are ignored.
     * <p>
     * 移动网络连接或移动网络本身断开时回调（其他网络断开不会回调）。
     */
    MOBILE,

    /**
     * Only when cellular data connects.
     * <p>
     * 只有移动网络连接时才回调。
     */
    MOBILE_CONNECT,

    /**
     * Only when the device loses all networks ({@link NetType#NONE}).
     * <p>
     * 网络全部丢失时回调。
     */
    NONE

}
