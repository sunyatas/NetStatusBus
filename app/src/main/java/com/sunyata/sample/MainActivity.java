package com.sunyata.sample;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.sunyata.netbus.NetworkManager;
import com.sunyata.netbus.annotation.Network;
import com.sunyata.netbus.listenter.NetChangeObserver;
import com.sunyata.netbus.type.NetType;
import com.sunyata.netbus.utils.Constrants;
import com.sunyata.netbus.utils.NetworkUtils;

public class MainActivity extends AppCompatActivity implements NetChangeObserver, View.OnClickListener {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        NetworkManager.getInstance().registerObserver(this);

        findViewById(R.id.btn_to_setting).setOnClickListener(this);
    }

    @Override
    public void onConnect(NetType netType) {
        Log.e("net>>>>>", netType.name());
    }

    @Override
    public void onDisConnect() {
        Log.e("net>>>>>", "没有网络");
    }

    @Override
    public void onClick(View view) {
        NetworkUtils.openSetting(this, Constrants.SETTING_REQUEST_CODE);
    }

    @Network(netType = NetType.WIFI)
    public void network(NetType netType) {
        switch (netType) {
            case CMWAP:
                Log.e(Constrants.LOG_TAG, "CMWAP");
                break;
            case CMNET:
                Log.e(Constrants.LOG_TAG, "CMNET");
                break;

            case WIFI:
                Log.e(Constrants.LOG_TAG, "WIFI");
                break;

            case NONE:
                Log.e(Constrants.LOG_TAG, "NONE");
                break;

            case AUTO:
                Log.e(Constrants.LOG_TAG, "AUTO");
                break;

        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        NetworkManager.getInstance().unRegisterObserver(this);
        NetworkManager.getInstance().unRegisterAllObserver();

    }
}
