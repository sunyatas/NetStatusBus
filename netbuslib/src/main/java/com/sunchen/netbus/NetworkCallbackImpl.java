package com.sunchen.netbus;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import androidx.annotation.RequiresApi;

import com.sunchen.netbus.utils.NetworkUtils;

/**
 * 网络回调。每次回调都重新计算当前默认网络的类型，交给 {@link NetStatusReceiver} 去重后分发。
 * <p>
 * onLost 时系统的 activeNetwork 可能仍指向刚断开的网络，因此需要把断开的网络排除掉，
 * 否则断开 WIFI 后仍会得到 WIFI（#6 #9）。
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class NetworkCallbackImpl extends ConnectivityManager.NetworkCallback {
    private final NetStatusReceiver mReceiver;

    public NetworkCallbackImpl(NetStatusReceiver receiver) {
        mReceiver = receiver;
    }

    @Override
    public void onAvailable(Network network) {
        super.onAvailable(network);
        mReceiver.postIfChanged(NetworkUtils.getNetType());
    }

    @Override
    public void onLost(Network network) {
        super.onLost(network);
        mReceiver.postIfChanged(NetworkUtils.getNetType(network));
    }

    @Override
    public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
        super.onCapabilitiesChanged(network, networkCapabilities);
        mReceiver.postIfChanged(NetworkUtils.getNetType());
    }
}
