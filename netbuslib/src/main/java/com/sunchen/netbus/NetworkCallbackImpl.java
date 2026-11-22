package com.sunchen.netbus;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

import com.sunchen.netbus.utils.NetworkUtils;

/**
 * Lollipop–Marshmallow {@link ConnectivityManager.NetworkCallback} used before
 * {@link ConnectivityManager#registerDefaultNetworkCallback} exists (Android 7.0+).
 * Internal implementation detail; not part of the supported public API.
 * <p>
 * 网络回调。每次回调都重新计算当前默认网络的类型，交给 {@link NetStatusReceiver} 去重后分发。
 * <p>
 * onLost 时系统的 activeNetwork 可能仍指向刚断开的网络，因此需要把断开的网络排除掉，
 * 否则断开 WIFI 后仍会得到 WIFI（#6 #9）。
 */
final class NetworkCallbackImpl extends ConnectivityManager.NetworkCallback {
    private final NetStatusReceiver mReceiver;
    private final ConnectivityManager mManager;
    private final int mSdkInt;

    NetworkCallbackImpl(NetStatusReceiver receiver, ConnectivityManager manager) {
        this(receiver, manager, Build.VERSION.SDK_INT);
    }

    /**
     * Queries {@code manager} (the bus's own) rather than the singleton's application, so a
     * bus made with {@code new NetStatusBus()} works on its own; {@code sdkInt} is for tests.
     */
    NetworkCallbackImpl(NetStatusReceiver receiver, ConnectivityManager manager, int sdkInt) {
        mReceiver = receiver;
        mManager = manager;
        mSdkInt = sdkInt;
    }

    @Override
    public void onAvailable(Network network) {
        mReceiver.postIfChanged(NetworkUtils.getNetType(mManager, null, mSdkInt));
    }

    @Override
    public void onLost(Network network) {
        mReceiver.postIfChanged(NetworkUtils.getNetType(mManager, network, mSdkInt));
    }

    @Override
    public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
        mReceiver.postIfChanged(NetworkUtils.getNetType(mManager, null, mSdkInt));
    }
}
