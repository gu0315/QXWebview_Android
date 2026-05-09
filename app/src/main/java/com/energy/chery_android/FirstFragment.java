package com.energy.chery_android;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.navigation.fragment.NavHostFragment;
import androidx.fragment.app.Fragment;

import com.energy.chery_android.databinding.FragmentFirstBinding;
import com.jd.hybrid.QXWebViewActivity;

public class FirstFragment extends Fragment {

    private static final String BRIDGE_TEST_URL = "file:///android_asset/bridge_test.html";

    private FragmentFirstBinding binding;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.buttonWebview.setOnClickListener(v -> {
            BridgeHostManager.init();
            // 启动 WebView Activity
            Intent intent = new Intent(requireContext(), QXWebViewActivity.class);
            intent.putExtra(QXWebViewActivity.EXTRA_URL, "http://192.168.31.137:5174");
            // https://test-fr-home-charge-web.cheryge.com/#/pages/bluetooth-test/index
            startActivity(intent);
        });

        binding.buttonTest.setOnClickListener(v -> {
            BridgeHostManager.init();
            Intent intent = new Intent(requireContext(), QXWebViewActivity.class);
            intent.putExtra(QXWebViewActivity.EXTRA_URL, BRIDGE_TEST_URL);
            startActivity(intent);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
