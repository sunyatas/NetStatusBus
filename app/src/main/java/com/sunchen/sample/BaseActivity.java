package com.sunchen.sample;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.sunchen.netbus.NetStatusBus;

/**
 * Created by 「孙晨」 on 2019/5/20 0020   10:48.
 * <p>
 * God bless me only
 * <p>
 * BaseActivity
 */

public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onStart() {
        super.onStart();
        NetStatusBus.getInstance().register(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        NetStatusBus.getInstance().unregister(this);
    }
}
