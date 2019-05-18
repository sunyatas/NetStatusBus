package com.sunchen.sample;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.sunchen.netbus.NetStatusBus;
import com.sunchen.netbus.annotation.NetSubscribe;
import com.sunchen.netbus.type.NetType;
import com.sunchen.netbus.utils.Constrants;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private TextView tvTips;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvTips = findViewById(R.id.tv_tips);
        findViewById(R.id.btn_to_setting).setOnClickListener(this);
        NetStatusBus.getInstance().register(this);
    }

    @Override
    public void onClick(View view) {
        startActivity(new Intent(this, Main2Activity.class));
    }

    @NetSubscribe()
    public void doSometing(NetType netType) {
        Log.d(Constrants.LOG_TAG, netType.name() + "<<<<<<<<<<activity1");
        tvTips.setText("MainActivity1当前网络状态>>>>" + netType.name());

    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        NetStatusBus.getInstance().unregister(this);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
