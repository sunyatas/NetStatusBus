package com.sunyata.sample;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.sunyata.netbus.NetStatusBus;
import com.sunyata.netbus.annotation.Network;
import com.sunyata.netbus.type.NetType;
import com.sunyata.netbus.utils.Constrants;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.btn_to_setting).setOnClickListener(this);
        NetStatusBus.getDefault().register(this);
    }

    @Override
    public void onClick(View view) {
        startActivity(new Intent(this, Main2Activity.class));
    }

//    @Network(netType = NetType.WIFI)
//    public void network(NetType netType) {
//        Log.e(Constrants.LOG_TAG, netType.name() + "<<<<<<<<<<activity1");
//    }
//
//    @Network(netType = NetType.MOBILE)
//    public void networkMobile(NetType netType) {
//        Log.e(Constrants.LOG_TAG, netType.name() + "<<<<<<<<<<activity1");
//    }

    @Network(netType = NetType.AUTO)
    public void doNet2(NetType netType) {
        Toast.makeText(this, "mainActivity1" + netType.name(), Toast.LENGTH_SHORT).show();
        Log.d(Constrants.LOG_TAG, netType.name() + "<<<<<<<<<<activity1");
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        NetStatusBus.getDefault().unregister(this);
    }
}
