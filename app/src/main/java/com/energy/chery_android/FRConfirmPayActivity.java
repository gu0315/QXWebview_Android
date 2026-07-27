package com.energy.chery_android;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 模拟的 FR 确认支付页（Demo 宿主用）。
 * H5 通过 app://openFRConfirmPay 拉起，页面结束时把结果回传给 {@link BridgeHostManager}：
 * 「确认支付」-> code 0，「取消」/ 返回 -> code 1。
 * 真实宿主替换成自己的收银台即可，只要保证结束时回传同样的 code / message / orderSeq。
 */
public class FRConfirmPayActivity extends AppCompatActivity {

    public static final String EXTRA_ORDER_SEQ = "extra_order_seq";
    public static final String EXTRA_STATION_NAME = "extra_station_name";
    public static final String EXTRA_POWER_CONNECTOR_ID = "extra_power_connector_id";

    private static final String TAG = "FRConfirmPayActivity";
    /** 模拟支付耗时 */
    private static final long MOCK_PAY_DELAY = 1500L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextView tvStationName;
    private TextView tvConnectorId;
    private TextView tvOrderSeq;
    private TextView tvStatus;
    private ProgressBar progressBar;
    private Button btnPay;
    private Button btnCancel;

    private String orderSeq;
    /** 结果是否已回传，避免 onDestroy 兜底时重复回调 */
    private boolean delivered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fr_confirm_pay);

        initViews();
        loadPayInfo();
        setupListeners();
    }

    private void initViews() {
        tvStationName = findViewById(R.id.tv_station_name);
        tvConnectorId = findViewById(R.id.tv_connector_id);
        tvOrderSeq = findViewById(R.id.tv_order_seq);
        tvStatus = findViewById(R.id.tv_status);
        progressBar = findViewById(R.id.progress_bar);
        btnPay = findViewById(R.id.btn_pay);
        btnCancel = findViewById(R.id.btn_cancel);
    }

    private void loadPayInfo() {
        orderSeq = getIntent().getStringExtra(EXTRA_ORDER_SEQ);
        String stationName = getIntent().getStringExtra(EXTRA_STATION_NAME);
        String powerConnectorId = getIntent().getStringExtra(EXTRA_POWER_CONNECTOR_ID);

        Log.d(TAG, "orderSeq: " + orderSeq + ", stationName: " + stationName
                + ", powerConnectorId: " + powerConnectorId);

        tvStationName.setText("电站名称：" + display(stationName));
        tvConnectorId.setText("充电桩编号：" + display(powerConnectorId));
        tvOrderSeq.setText("订单号：" + display(orderSeq));
    }

    private String display(String value) {
        return TextUtils.isEmpty(value) ? "-" : value;
    }

    private void setupListeners() {
        btnPay.setOnClickListener(v -> startPay());
        btnCancel.setOnClickListener(v -> {
            deliver(BridgeHostManager.FR_CODE_CANCEL, "用户取消支付");
            finish();
        });
    }

    private void startPay() {
        btnPay.setEnabled(false);
        btnCancel.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("支付处理中...");

        // 模拟支付耗时，完成后回调成功并关闭页面
        mainHandler.postDelayed(() -> {
            progressBar.setVisibility(View.GONE);
            tvStatus.setText("支付成功");
            deliver(BridgeHostManager.FR_CODE_SUCCESS, "success");
            mainHandler.postDelayed(this::finish, 500L);
        }, MOCK_PAY_DELAY);
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        // 兜底：返回键 / 被系统回收等情况按用户取消回传，避免 H5 一直等
        if (isFinishing()) {
            deliver(BridgeHostManager.FR_CODE_CANCEL, "用户取消支付");
        }
        super.onDestroy();
    }

    private void deliver(String code, String message) {
        if (delivered) {
            return;
        }
        delivered = true;
        BridgeHostManager.deliverFRConfirmPayResult(code, message, orderSeq);
    }
}
