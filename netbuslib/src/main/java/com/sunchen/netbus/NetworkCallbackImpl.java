package com.sunchen.netbus;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.support.annotation.RequiresApi;
import com.sunchen.netbus.type.NetType;
import com.sunchen.netbus.utils.NetworkUtils;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class NetworkCallbackImpl extends ConnectivityManager.NetworkCallback {
    private NetStatusReceiver mReceiver;

    public NetworkCallbackImpl(NetStatusReceiver receiver) {
        mReceiver = receiver;
    }

    @Override
    public void onAvailable(Network network) {
        super.onAvailable(network);
        NetType netType = NetworkUtils.getNetType();
        mReceiver.post(netType);
    }

    @Override
    public void onLost(Network network) {
        super.onLost(network);
        NetType netType = NetworkUtils.getNetType();
        mReceiver.post(netType);
    }

    @Override
    public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
        super.onCapabilitiesChanged(network, networkCapabilities);
//        if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
//            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
////                Log.d(Constrants.LOG_TAG, "网络发生变更，类型为WiFi");
//
//            } else {
////                Log.d(Constrants.LOG_TAG, "网络发生变更，类型为其他");
//            }
//        } else {
//            Log.d(Constrants.LOG_TAG, "已连接，但是无网络");
//        }
    }
}
