package com.sunchen.netbus.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Process;
import android.telephony.TelephonyManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.RestrictTo;

import com.sunchen.netbus.NetStatusBus;
import com.sunchen.netbus.type.MobileGeneration;
import com.sunchen.netbus.type.NetType;

/**
 * Created by 「孙晨」 on 2019/3/31 0031   19:04.
 * <p>
 * God bless me only
 * <p>
 * NetworkUtils
 */
@SuppressLint("MissingPermission")
public class NetworkUtils {

    /**
     * 网络是否可用。
     * Android 6.0+ 使用当前活跃网络的 {@link NetworkCapabilities}：
     * 要求 {@link NetworkCapabilities#NET_CAPABILITY_INTERNET}，
     * 但不要求 {@link NetworkCapabilities#NET_CAPABILITY_VALIDATED}，
     * 以便仅能访问局域网 /被俗门的 Wi-Fi 仍被视为已连接。
     */
    public static boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) NetStatusBus.getInstance()
                .getApplication()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network active = connectivityManager.getActiveNetwork();
            if (active == null) {
                return false;
            }
            return isAvailable(connectivityManager.getNetworkCapabilities(active));
        }

        @SuppressWarnings("deprecation")
        NetworkInfo[] info = connectivityManager.getAllNetworkInfo();
        if (info != null) {
            for (NetworkInfo networkInfo : info) {
                if (networkInfo.getState() == NetworkInfo.State.CONNECTED) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Android 6.0+ availability from capabilities.
     * Requires INTERNET; does NOT require VALIDATED (captive / LAN-only still count).
     */
    @android.annotation.TargetApi(Build.VERSION_CODES.M)
    static boolean isAvailable(NetworkCapabilities caps) {
        if (caps == null) {
            return false;
        }
        return isAvailableFromFlags(
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
    }

    /**
     * Decide availability from capability flags.
     * INTERNET is required; VALIDATED is intentionally ignored so captive portal
     * and LAN-only Wi-Fi still count as connected.
     */
    static boolean isAvailableFromFlags(boolean hasInternet, boolean hasValidated) {
        return hasInternet;
    }

    /**
     * 获取当前的网络类型
     */
    public static NetType getNetType() {
        return getNetType(null);
    }

    /**
     * 获取当前的网络类型，忽略已经断开的 lostNetwork。
     * Android 6.0+ 使用 NetworkCapabilities 判断，旧系统回退到 NetworkInfo。
     */
    public static NetType getNetType(Network lostNetwork) {
        ConnectivityManager connectivityManager = (ConnectivityManager) NetStatusBus.getInstance()
                .getApplication()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return NetType.NONE;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network active = connectivityManager.getActiveNetwork();
            if (active == null || active.equals(lostNetwork)) {
                return NetType.NONE;
            }
            return typeOf(connectivityManager, connectivityManager.getNetworkCapabilities(active), lostNetwork);
        }
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        if (info == null || !info.isConnected()) {
            return NetType.NONE;
        }
        int type = info.getType();
        if (type == ConnectivityManager.TYPE_MOBILE) {
            return NetType.MOBILE;
        } else if (type == ConnectivityManager.TYPE_WIFI) {
            return NetType.WIFI;
        } else if (type == ConnectivityManager.TYPE_ETHERNET) {
            return NetType.ETHERNET;
        }
        return NetType.NONE;
    }


    /**
     * 根据某个网络的 NetworkCapabilities 计算网络类型（Android 6.0+）。
     * 仅供库内部使用。
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static NetType typeOf(NetworkCapabilities caps) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return NetType.NONE;
        }
        ConnectivityManager cm = (ConnectivityManager) NetStatusBus.getInstance()
                .getApplication()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        return typeOf(cm, caps, null);
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.M)
    private static NetType typeOf(ConnectivityManager cm, NetworkCapabilities caps, Network lostNetwork) {
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return NetType.NONE;
        }
        NetType type = fromTransports(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        if (type == NetType.NONE && cm != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            // VPN 没有声明底层传输时，按底层的非 VPN 网络判断
            type = underlyingType(cm, lostNetwork);
        }
        return type;
    }

    /**
     * 根据传输类型映射 NetType，优先级 WIFI > MOBILE > ETHERNET。
     */
    static NetType fromTransports(boolean wifi, boolean cellular, boolean ethernet) {
        if (wifi) {
            return NetType.WIFI;
        }
        if (cellular) {
            return NetType.MOBILE;
        }
        if (ethernet) {
            return NetType.ETHERNET;
        }
        return NetType.NONE;
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.M)
    private static NetType underlyingType(ConnectivityManager cm, Network lostNetwork) {
        for (Network network : cm.getAllNetworks()) {
            if (network.equals(lostNetwork)) {
                continue;
            }
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                continue;
            }
            NetType type = fromTransports(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
            if (type != NetType.NONE) {
                return type;
            }
        }
        return NetType.NONE;
    }

    /**
     * 当前是否有 VPN 处于连接状态（Android 6.0+，旧系统返回 false）。
     */
    public static boolean isVpnActive() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        ConnectivityManager cm = (ConnectivityManager) NetStatusBus.getInstance()
                .getApplication()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        for (Network network : cm.getAllNetworks()) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 当前移动网络制式（2G/3G/4G/5G），opt-in 功能。
     * 只有当前网络是 MOBILE 且宿主 App 已获得 READ_PHONE_STATE 权限时才能判断，
     * 否则返回 {@link MobileGeneration#UNKNOWN}，不会抛出异常。
     * 注意：5G NSA（非独立组网）在系统层面通常报告为 LTE，因此会返回 G4。
     */
    public static MobileGeneration getMobileGeneration() {
        if (getNetType() != NetType.MOBILE) {
            return MobileGeneration.UNKNOWN;
        }
        Context context = NetStatusBus.getInstance().getApplication();
        if (context.checkPermission(android.Manifest.permission.READ_PHONE_STATE,
                Process.myPid(), Process.myUid()) != PackageManager.PERMISSION_GRANTED) {
            return MobileGeneration.UNKNOWN;
        }
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm == null) {
            return MobileGeneration.UNKNOWN;
        }
        try {
            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    ? tm.getDataNetworkType() : tm.getNetworkType();
            return fromTelephonyType(type);
        } catch (SecurityException e) {
            return MobileGeneration.UNKNOWN;
        }
    }

    /**
     * TelephonyManager.NETWORK_TYPE_* 映射到制式（使用常量值以兼容低 compileSdk 语义）。
     */
    static MobileGeneration fromTelephonyType(int type) {
        switch (type) {
            case 1:  // GPRS
            case 2:  // EDGE
            case 4:  // CDMA
            case 7:  // 1xRTT
            case 11: // IDEN
            case 16: // GSM
                return MobileGeneration.G2;
            case 3:  // UMTS
            case 5:  // EVDO_0
            case 6:  // EVDO_A
            case 8:  // HSDPA
            case 9:  // HSUPA
            case 10: // HSPA
            case 12: // EVDO_B
            case 14: // EHRPD
            case 15: // HSPAP
            case 17: // TD_SCDMA
                return MobileGeneration.G3;
            case 13: // LTE
            case 18: // IWLAN
            case 19: // LTE_CA
                return MobileGeneration.G4;
            case 20: // NR
                return MobileGeneration.G5;
            default:
                return MobileGeneration.UNKNOWN;
        }
    }

    /**
     * 打开网络设置界面
     */
    public static void openSetting(Context context, int requestCode) {

        context.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
    }

}
