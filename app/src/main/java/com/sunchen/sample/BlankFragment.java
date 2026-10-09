package com.sunchen.sample;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.sunchen.netbus.NetStatusBus;
import com.sunchen.netbus.annotation.NetSubscribe;
import com.sunchen.netbus.type.NetType;
import com.sunchen.netbus.utils.Constrants;

public class BlankFragment extends Fragment {
    private TextView tvTips;
    private ImageView imgNetStatus;

    public BlankFragment() {
    }

    public static BlankFragment newInstance() {
        BlankFragment fragment = new BlankFragment();
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_blank, container, false);
        tvTips = view.findViewById(R.id.tv_tips_2);
        imgNetStatus = view.findViewById(R.id.img_net_status);
        NetStatusBus.getInstance().register(this);
        return view;
    }

    @NetSubscribe()
    public void netChange(NetType netType) {
        Log.d(Constrants.LOG_TAG, netType.name() + "<<<<<<<<<<BlankFragment");
        switch (netType) {
            case NONE:
                tvTips.setText("网络连接中断...");
                imgNetStatus.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_no));
                break;

            case WIFI:
                tvTips.setText("wifi已连接");
                imgNetStatus.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_wifi));
                break;

            case MOBILE:
                tvTips.setText("移动网络已连接");
                imgNetStatus.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_mobile));
                break;

            default:
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        NetStatusBus.getInstance().unregister(this);
    }
}
