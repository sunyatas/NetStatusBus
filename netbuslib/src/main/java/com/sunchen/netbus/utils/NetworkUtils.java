package com.sunchen.netbus.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.provider.Settings;

import com.sunchen.netbus.NetStatusBus;
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
     * 网络是否可用
     */
    public static boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) NetStatusBus.getInstance()
                .getApplication()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }

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
            NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(active);
            if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return NetType.NONE;
            }
            NetType type = fromTransports(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
            if (type == NetType.NONE && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                // VPN 没有声明底层传输时，按底层的非 VPN 网络判断
                type = underlyingType(connectivityManager, lostNetwork);
            }
            return type;
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
     * 打开网络设置界面
     */
    public static void openSetting(Context context, int requestCode) {

        context.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
    }

}
