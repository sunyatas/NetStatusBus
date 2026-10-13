package com.sunchen.netbus;

import com.sunchen.netbus.type.NetType;

/**
 * 跟踪系统“默认网络”的纯逻辑部分（不依赖 Android 类，便于单元测试）。
 * <p>
 * 默认网络切换时（例如 WIFI 断开后切到移动网络），系统只会对新网络回调 onAvailable，
 * 不一定会对旧网络回调 onLost；而迟到的旧网络 onLost 不应把状态改成 NONE。
 * 因此只认当前默认网络的事件。
 * <p>
 * 所有方法返回需要分发的网络类型；返回 null 表示该事件与当前默认网络无关，应忽略。
 */
final class DefaultNetworkTracker {

    private Object current;

    synchronized NetType onAvailable(Object network, NetType type) {
        current = network;
        return type;
    }

    synchronized NetType onCapabilitiesChanged(Object network, NetType type) {
        if (current == null) {
            // 某些系统上 onCapabilitiesChanged 可能先于 onAvailable 到达
            current = network;
        }
        if (!current.equals(network)) {
            return null;
        }
        return type;
    }

    synchronized NetType onLost(Object network) {
        if (current == null || !current.equals(network)) {
            return null;
        }
        current = null;
        return NetType.NONE;
    }
}
