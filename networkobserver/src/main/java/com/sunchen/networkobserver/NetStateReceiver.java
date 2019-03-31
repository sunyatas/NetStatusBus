package com.sunchen.networkobserver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.sunchen.networkobserver.listenter.NetChangeObserver;
import com.sunchen.networkobserver.type.NetType;
import com.sunchen.networkobserver.utils.Constrants;
import com.sunchen.networkobserver.utils.NetworkUtils;

/**
 * Created by 「孙晨」 on 2019/3/31 0031   18:31.
 * <p>
 * God bless me only
 * <p>
 * NetStateReceiver
 */

public class NetStateReceiver extends BroadcastReceiver {

    private NetType netType;//网络类型

    private NetChangeObserver listener;//网络监听

    public NetStateReceiver() {
        netType = NetType.NONE;
    }

    public void setListener(NetChangeObserver listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
//        处理广播事件
        if (intent.getAction().equalsIgnoreCase(Constrants.ANDROID_NET_CHANGE_ACTION)) {
            Log.e(Constrants.LOG_TAG, "网络发生了改变");
            netType = NetworkUtils.getNetType();
            if (NetworkUtils.isNetworkAvailable()) {
                Log.e(Constrants.LOG_TAG, "网络连接成功");
                if (listener != null) {
                    listener.onConnect(netType);
                }
            } else {
                Log.e(Constrants.LOG_TAG, "没有网络连接");
                if (listener != null) {
                    listener.onDisConnect();
                }
            }
        }
    }
}
