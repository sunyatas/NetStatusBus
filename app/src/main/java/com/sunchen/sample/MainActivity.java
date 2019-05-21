package com.sunchen.sample;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.sunchen.netbus.NetStatusBus;
import com.sunchen.netbus.annotation.NetSubscribe;
import com.sunchen.netbus.type.Mode;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private TextView tvTips;
    private ImageView imgNetStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvTips = findViewById(R.id.tv_tips);
        imgNetStatus = findViewById(R.id.img_net_status);
        findViewById(R.id.btn_to_setting).setOnClickListener(this);
        NetStatusBus.getInstance().register(this);
    }

    @Override
    public void onClick(View view) {
        startActivity(new Intent(this, Main2Activity.class));
    }

    /**
     * 当监听到 WIFI 连接时，会调用使用该模式的所有方法
     */
    @NetSubscribe(mode = Mode.WIFI_CONNECT)
    public void mobileChange() {
        tvTips.setText("wifi已连接");
        imgNetStatus.setImageDrawable(getResources().getDrawable(R.drawable.ic_wifi));
    }

    /**
     * 当监听到 移动网络 连接时，会调用使用该模式的所有方法
     */
    @NetSubscribe(mode = Mode.MOBILE_CONNECT)
    public void wifiChange() {
        tvTips.setText("移动网络已连接");
        imgNetStatus.setImageDrawable(getResources().getDrawable(R.drawable.ic_mobile));
    }

    /**
     * 当失去网络连接时，会调用使用该模式的所有方法
     */
    @NetSubscribe(mode = Mode.NONE)
    public void noneNet() {
        tvTips.setText("网络连接中断...");
        imgNetStatus.setImageDrawable(getResources().getDrawable(R.drawable.ic_no));
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
