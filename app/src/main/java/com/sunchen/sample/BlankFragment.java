package com.sunchen.sample;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.sunchen.netbus.NetStatusBus;
import com.sunchen.netbus.annotation.NetSubscribe;
import com.sunchen.netbus.type.NetType;
import com.sunchen.netbus.utils.Constrants;

public class BlankFragment extends Fragment {
    private TextView tvTips;

    public BlankFragment() {
    }

    public static BlankFragment newInstance() {
        BlankFragment fragment = new BlankFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_blank, container, false);
        tvTips = view.findViewById(R.id.tv_tips_2);
        NetStatusBus.getInstance().register(this);
        return view;
    }

    @NetSubscribe()
    public void netChange(NetType netType) {
        tvTips.setText("当前网络状态>>>>" + netType.name().toString());
        Log.d(Constrants.LOG_TAG, netType.name() + "<<<<<<<<<<BlankFragment");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        NetStatusBus.getInstance().unregister(this);
    }
}
