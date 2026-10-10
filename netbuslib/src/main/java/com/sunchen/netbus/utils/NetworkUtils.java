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
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return NetType.WIFI;
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return NetType.MOBILE;
            }
            return NetType.NONE;
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
        }
        return NetType.NONE;
    }


    /**
     * 打开网络设置界面
     */
    public static void openSetting(Context context, int requestCode) {

        context.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
    }

}
