package com.sunchen.networkobserver.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.provider.Settings;

import com.sunchen.networkobserver.NetworkManager;
import com.sunchen.networkobserver.type.NetType;

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
        ConnectivityManager connectivityManager = (ConnectivityManager) NetworkManager.getInstance()
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
        ConnectivityManager connectivityManager = (ConnectivityManager) NetworkManager.getInstance()
                .getApplication()
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            return NetType.NONE;
        }

//        获取当前激活的网络连接信息
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();

        if (info == null) {
            return NetType.NONE;
        }

        int type = info.getType();

        if (type == ConnectivityManager.TYPE_MOBILE) {
            if (info.getExtraInfo().toLowerCase().equals("cmnet")) {
                return NetType.CMNET;
            } else {
                return NetType.CMWAP;
            }
        } else if (type == ConnectivityManager.TYPE_WIFI) {
            return NetType.WIFI;
        }

        return NetType.NONE;
    }


    /**
     * 打开网络设置界面
     */
    public static void openSetting(Context context, int requestCode) {
//        Intent intent = new Intent("/");
//        ComponentName name = new ComponentName("com.android.settings", "com.android.settings.WirelessSettings");
//        intent.setComponent(name);
//        intent.setAction("android.intent.action.VIEW");
//        ((Activity) context).startActivityForResult(intent, requestCode);

//      context.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
        context.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
    }

}
