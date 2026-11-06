package com.sunchen.netbus.utils;

/**
 * Legacy sample helpers that leaked into the published API. Not part of the supported API;
 * apps should use their own log tag and request codes. Kept only for binary compatibility.
 * <p>
 * 历史遗留的示例常量（类名也拼错了），不属于受支持的公开 API；应用应使用自己的 log tag / request code。
 * 仅保留字段以维持二进制兼容。
 *
 * @deprecated Not part of the supported API. Use your own log tag / request codes instead.
 */
@Deprecated
public class Constrants {

    /**
     * Log 日志前缀名
     */
    public static final String LOG_TAG = "network>>>>>";


    // 跳转设置回调请求标志码
    public static final int SETTING_REQUEST_CODE = 666;

}
