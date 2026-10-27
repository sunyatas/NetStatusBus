package com.sunchen.sample;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.sunchen.netbus.NetStatusBus;
import com.sunchen.netbus.type.NetType;
import com.sunchen.netbus.utils.Constrants;

/**
 * 演示两种不需要注解、也不走反射的用法：
 * <ul>
 *     <li>{@link NetStatusBus#addListener(com.sunchen.netbus.OnNetTypeChangeListener, androidx.lifecycle.LifecycleOwner)}：
 *     立即收到当前网络类型，之后每次变化都在主线程回调，Activity 销毁时自动移除；</li>
 *     <li>{@link NetStatusBus#getNetTypeLiveData()}：标准 LiveData，只在界面可见时分发。</li>
 * </ul>
 * 页面里的 {@link BlankFragment} 则演示注解订阅 + 生命周期自动注销。
 */
public class Main2Activity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.frame_layout, BlankFragment.newInstance())
                    .commit();
        }

        // 无注解监听：在标题栏副标题里显示当前网络，onDestroy 时自动移除
        NetStatusBus.getInstance().addListener(this::showNetTypeInActionBar, this);

        // LiveData 用法：Kotlin 可以直接 asFlow()
        NetStatusBus.getInstance().getNetTypeLiveData().observe(this, netType ->
                Log.d(Constrants.LOG_TAG, netType.name() + "<<<<<<<<<<Main2Activity(LiveData)"));
    }

    private void showNetTypeInActionBar(NetType netType) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setSubtitle(describe(netType));
        }
    }

    private static String describe(NetType netType) {
        switch (netType) {
            case WIFI:
                return "当前网络：Wi-Fi";
            case MOBILE:
                return "当前网络：移动网络";
            case ETHERNET:
                return "当前网络：以太网";
            case NONE:
            default:
                return "当前无网络";
        }
    }
}
