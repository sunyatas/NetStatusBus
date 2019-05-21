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
        mReceiver.post(NetworkUtils.getNetType());
    }

    @Override
    public void onLost(Network network) {
        super.onLost(network);
        mReceiver.post(NetworkUtils.getNetType());
    }

    @Override
    public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
        super.onCapabilitiesChanged(network, networkCapabilities);
    }

}
