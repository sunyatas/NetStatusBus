package com.sunchen.sample;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.sunchen.netbus.annotation.NetSubscribe;
import com.sunchen.netbus.type.Mode;
import com.sunchen.netbus.type.NetType;
import com.sunchen.netbus.utils.NetworkUtils;


/**
 * 演示：
 * <ul>
 *     <li>按 {@link Mode} 过滤的 {@link NetSubscribe} 方法（在 {@link BaseActivity} 的 onStart/onStop 里注册和注销）；</li>
 *     <li>{@link NetworkUtils} 的即时查询（是否可用、VPN、移动网络制式）；</li>
 *     <li>{@link NetworkUtils#openSetting(android.content.Context, int)} 打开 Wi-Fi 设置，
 *     返回后在 onActivityResult 里刷新状态。</li>
 * </ul>
 */
public class MainActivity extends BaseActivity implements View.OnClickListener {
    private static final int REQUEST_WIFI_SETTINGS = 1;

    private TextView tvTips;
    private TextView tvDetails;
    private ImageView imgNetStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvTips = findViewById(R.id.tv_tips);
        tvDetails = findViewById(R.id.tv_details);
        imgNetStatus = findViewById(R.id.img_net_status);
        findViewById(R.id.btn_to_fragment).setOnClickListener(this);
        findViewById(R.id.btn_open_wifi_settings).setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.btn_open_wifi_settings) {
            // 传入 Activity 和非负 requestCode，返回时会回调 onActivityResult
            NetworkUtils.openSetting(this, REQUEST_WIFI_SETTINGS);
        } else if (id == R.id.btn_to_fragment) {
            startActivity(new Intent(this, Main2Activity.class));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_WIFI_SETTINGS) {
            // 系统设置页通常返回 RESULT_CANCELED，所以直接重新查询当前状态
            showDetails(NetworkUtils.getNetType(this));
        }
    }

    /**
     * 当监听到 WIFI 连接时，会调用使用该模式的所有方法
     */
    @NetSubscribe(mode = Mode.WIFI_CONNECT)
    public void onWifiConnected() {
        tvTips.setText("wifi已连接");
        imgNetStatus.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_wifi));
    }

    /**
     * 当监听到 移动网络 连接时，会调用使用该模式的所有方法
     */
    @NetSubscribe(mode = Mode.MOBILE_CONNECT)
    public void onMobileConnected() {
        tvTips.setText("移动网络已连接");
        imgNetStatus.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_mobile));
    }

    /**
     * 当失去网络连接时，会调用使用该模式的所有方法
     */
    @NetSubscribe(mode = Mode.NONE)
    public void onDisconnected() {
        tvTips.setText("网络连接中断...");
        imgNetStatus.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_no));
    }

    /**
     * 没有以太网专用的 Mode，用 AUTO 接收所有变化：这里处理以太网，并刷新详细信息
     */
    @NetSubscribe(mode = Mode.AUTO)
    public void onNetTypeChanged(NetType netType) {
        if (netType == NetType.ETHERNET) {
            tvTips.setText("以太网已连接");
            imgNetStatus.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_wifi));
        }
        showDetails(netType);
    }

    private void showDetails(NetType netType) {
        // 移动网络制式需要宿主声明并获得 READ_PHONE_STATE，否则返回 UNKNOWN（示例未申请该权限）
        String details = "类型：" + netType.name()
                + "\n可上网：" + (NetworkUtils.isNetworkAvailable(this) ? "是" : "否")
                + "\nVPN：" + (NetworkUtils.isVpnActive(this) ? "已连接" : "未连接")
                + "\n移动网络制式：" + NetworkUtils.getMobileGeneration(this).name();
        tvDetails.setText(details);
    }
}
