package com.sunchen.netbus.type;

/**
 * 移动网络制式（2G/3G/4G/5G）。
 * 通过 NetworkUtils.getMobileGeneration() 获取；需要宿主 App 持有 READ_PHONE_STATE 权限，
 * 否则返回 UNKNOWN。
 */
public enum MobileGeneration {
    G2,
    G3,
    G4,
    G5,
    /** 无法判断：没有权限、不是移动网络或未知制式 */
    UNKNOWN
}
