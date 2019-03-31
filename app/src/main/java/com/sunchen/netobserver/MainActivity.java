package com.sunchen.netobserver;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.sunchen.networkobserver.NetworkManager;
import com.sunchen.networkobserver.listenter.NetChangeObserver;
import com.sunchen.networkobserver.type.NetType;

public class MainActivity extends AppCompatActivity implements NetChangeObserver {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        NetworkManager.getInstance().init(getApplication());
        NetworkManager.getInstance().setListener(this);
    }

    @Override
    public void onConnect(NetType netType) {
        Log.e("net>>>>>", netType.name());
    }

    @Override
    public void onDisConnect() {
        Log.e("net>>>>>", "没有网络");
    }
}
