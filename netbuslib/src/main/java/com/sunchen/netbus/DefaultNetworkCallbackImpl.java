package com.sunchen.netbus;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.sunchen.netbus.type.NetType;
import com.sunchen.netbus.utils.NetworkUtils;

/**
 * Android 7.0+ 使用的默认网络回调（{@link ConnectivityManager#registerDefaultNetworkCallback}）。
 * <p>
 * 直接使用回调参数里的 Network / NetworkCapabilities 判断类型，不再去查询
 * {@link ConnectivityManager#getActiveNetwork()}：后者在网络切换的瞬间可能仍是旧值，
 * 导致切换后迟迟不回调（#4），或在部分机型上反复开关 WIFI 后状态错乱（#11）。
 */
@RequiresApi(api = Build.VERSION_CODES.N)
final class DefaultNetworkCallbackImpl extends ConnectivityManager.NetworkCallback {
    private final NetStatusReceiver mReceiver;
    private final ConnectivityManager mManager;
    private final DefaultNetworkTracker mTracker = new DefaultNetworkTracker();

    DefaultNetworkCallbackImpl(NetStatusReceiver receiver, ConnectivityManager manager) {
        mReceiver = receiver;
        mManager = manager;
    }

    @Override
    public void onAvailable(Network network) {
        // Android 8.0+ 之后紧跟 onCapabilitiesChanged；7.x 需要自己查询该网络的能力
        NetworkCapabilities caps = mManager.getNetworkCapabilities(network);
        dispatch(mTracker.onAvailable(network, NetworkUtils.typeOf(caps)));
    }

    @Override
    public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
        dispatch(mTracker.onCapabilitiesChanged(network, NetworkUtils.typeOf(networkCapabilities)));
    }

    @Override
    public void onLost(Network network) {
        dispatch(mTracker.onLost(network));
    }

    private void dispatch(NetType type) {
        if (type != null) {
            mReceiver.postIfChanged(type);
        }
    }
}
