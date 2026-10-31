package com.sunchen.netbus.type;

/**
 * Cellular generation (2G/3G/4G/5G), returned by
 * {@code NetworkUtils.getMobileGeneration()}. The host app must hold
 * {@code READ_PHONE_STATE}; without it the result is {@link #UNKNOWN}.
 * <p>
 * 移动网络制式（2G/3G/4G/5G），通过 {@code NetworkUtils.getMobileGeneration()} 获取；
 * 需要宿主 App 持有 READ_PHONE_STATE 权限，否则返回 UNKNOWN。
 */
public enum MobileGeneration {
    /** 2G (GPRS, EDGE, CDMA, ...). */
    G2,
    /** 3G (UMTS, HSPA, EVDO, ...). */
    G3,
    /** 4G (LTE). */
    G4,
    /** 5G (NR). */
    G5,
    /**
     * Unknown: no permission, not on cellular data, or an unrecognized network type.
     * <p>
     * 无法判断：没有权限、不是移动网络或未知制式。
     */
    UNKNOWN
}
