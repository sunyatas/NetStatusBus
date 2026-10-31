package com.sunchen.netbus.type;

/**
 * The kind of network the device is currently using, as reported by
 * {@link com.sunchen.netbus.NetStatusBus} and {@link com.sunchen.netbus.utils.NetworkUtils}.
 * <p>
 * Only the system's default network counts. A VPN is reported as the type of the network
 * underneath it; use {@code NetworkUtils.isVpnActive()} to tell whether a VPN is up.
 * <p>
 * 当前使用的网络类型。只看系统默认网络；VPN 按其底层网络的类型上报。
 */
public enum NetType {
    /** Wi-Fi. 无线局域网。 */
    WIFI,
    /** Cellular data (2G/3G/4G/5G). 移动网络。 */
    MOBILE,
    /** Wired Ethernet. 有线网络（以太网）。 */
    ETHERNET,
    /** No network at all. 没有任何网络。 */
    NONE
}
