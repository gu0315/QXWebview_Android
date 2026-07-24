package com.energy.chery_android;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.energy.chery_android.databinding.FragmentFirstBinding;
import com.jd.hybrid.QXWebViewActivity;

import java.util.ArrayList;
import java.util.List;

public class FirstFragment extends Fragment {

    private static final String BRIDGE_TEST_URL = "file:///android_asset/bridge_test.html";
    private static final String DEFAULT_H5_URL = "http://172.20.10.4:3000/#/?token=4b7fcc7c-da9e-45cf-aa8c-773a0e90abbc";

    private FragmentFirstBinding binding;
    private final List<View> vehicleRows = new ArrayList<>();

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.editUrl.setText(DEFAULT_H5_URL);

        // 默认填充一行车辆输入
        addVehicleRow();

        binding.buttonAddVehicle.setOnClickListener(v -> addVehicleRow());

        binding.buttonEnterH5.setOnClickListener(v -> enterH5());

//        binding.buttonTest.setOnClickListener(v -> {
//            BridgeHostManager.init();
//            Intent intent = new Intent(requireContext(), QXWebViewActivity.class);
//            intent.putExtra(QXWebViewActivity.EXTRA_URL, BRIDGE_TEST_URL);
//            startActivity(intent);
//        });
    }

    private void addVehicleRow() {
        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_vehicle_input, binding.containerVehicles, false);
        row.findViewById(R.id.button_remove_vehicle).setOnClickListener(v -> removeVehicleRow(row));
        vehicleRows.add(row);
        binding.containerVehicles.addView(row);
        refreshRowTitles();
    }

    private void removeVehicleRow(View row) {
        // 至少保留一行
        if (vehicleRows.size() <= 1) {
            return;
        }
        vehicleRows.remove(row);
        binding.containerVehicles.removeView(row);
        refreshRowTitles();
    }

    private void refreshRowTitles() {
        for (int i = 0; i < vehicleRows.size(); i++) {
            TextView title = vehicleRows.get(i).findViewById(R.id.text_vehicle_title);
            title.setText("车辆 " + (i + 1));
        }
    }

    private void enterH5() {
        String phone = binding.editPhone.getText().toString().trim();
        if (TextUtils.isEmpty(phone)) {
            Toast.makeText(requireContext(), "请输入手机号", Toast.LENGTH_SHORT).show();
            return;
        }

        List<UserSession.Vehicle> vehicles = new ArrayList<>();
        for (View row : vehicleRows) {
            EditText vinEdit = row.findViewById(R.id.edit_vin);
            EditText macEdit = row.findViewById(R.id.edit_mac);
            String vin = vinEdit.getText().toString().trim();
            String mac = macEdit.getText().toString().trim();
            if (TextUtils.isEmpty(vin) && TextUtils.isEmpty(mac)) {
                continue;
            }
            vehicles.add(new UserSession.Vehicle(vin, mac));
        }

        // 写入会话，供 H5 通过桥接读取
        UserSession.getInstance().update(phone, vehicles);

        String url = binding.editUrl.getText().toString().trim();
        if (TextUtils.isEmpty(url)) {
            url = DEFAULT_H5_URL;
        }

        BridgeHostManager.init();
        Intent intent = new Intent(requireContext(), QXWebViewActivity.class);
        intent.putExtra(QXWebViewActivity.EXTRA_URL, url);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        vehicleRows.clear();
        binding = null;
    }
}
