package com.energy.chery_android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class PaymentActivity extends AppCompatActivity {

    public static final String EXTRA_AMOUNT = "extra_amount";
    public static final String EXTRA_ORDER_ID = "extra_order_id";
    public static final String RESULT_SUCCESS = "result_success";
    public static final String RESULT_ORDER_ID = "result_order_id";
    public static final String RESULT_TRANSACTION_ID = "result_transaction_id";

    private TextView tvAmount;
    private TextView tvOrderId;
    private Button btnPay;
    private Button btnCancel;
    private ProgressBar progressBar;
    private TextView tvStatus;

    private String orderId;
    private String amount;
    private String callbackId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);

        Log.d("PaymentActivity", "onCreate");

        initViews();
        loadPaymentInfo();
        setupListeners();
    }

    private void initViews() {
        tvAmount = findViewById(R.id.tv_amount);
        tvOrderId = findViewById(R.id.tv_order_id);
        btnPay = findViewById(R.id.btn_pay);
        btnCancel = findViewById(R.id.btn_cancel);
        progressBar = findViewById(R.id.progress_bar);
        tvStatus = findViewById(R.id.tv_status);
    }

    private void loadPaymentInfo() {
        amount = getIntent().getStringExtra(EXTRA_AMOUNT);
        orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        callbackId = getIntent().getStringExtra("callbackId");

        if (amount == null) amount = "0.00";
        if (orderId == null) orderId = "ORDER_" + System.currentTimeMillis();

        Log.d("PaymentActivity", "callbackId: " + callbackId);

        tvAmount.setText("¥ " + amount);
        tvOrderId.setText("订单号: " + orderId);
    }

    private void setupListeners() {
        btnPay.setOnClickListener(v -> startPayment());
        btnCancel.setOnClickListener(v -> cancelPayment());
    }

    private void startPayment() {
        // 显示支付中状态
        btnPay.setEnabled(false);
        btnCancel.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText("支付处理中...");
        tvStatus.setVisibility(View.VISIBLE);

        // 模拟支付处理（2秒后完成）
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            completePayment();
        }, 2000);
    }

    private void completePayment() {
        // 生成交易ID
        String transactionId = "TXN_" + System.currentTimeMillis();

        Log.d("PaymentActivity", "Payment completed - orderId: " + orderId + ", transactionId: " + transactionId);

        // 构造结果 JSON
        try {
            org.json.JSONObject result = new org.json.JSONObject();
            result.put("success", true);
            result.put("orderId", orderId);
            result.put("transactionId", transactionId);
            result.put("message", "支付成功");
            
            // 通过 ClosureRegistry 调用回调
            if (callbackId != null) {
                Log.d("PaymentActivity", "Invoking callback with result: " + result.toString());
                com.jd.plugins.ClosureRegistry.INSTANCE.invoke(callbackId, result.toString());
            }
        } catch (org.json.JSONException e) {
            Log.e("PaymentActivity", "Error creating result JSON", e);
        }

        // 显示成功状态
        progressBar.setVisibility(View.GONE);
        tvStatus.setText("支付成功！");

        // 1秒后关闭页面
        new Handler(Looper.getMainLooper()).postDelayed(this::finish, 1000);
    }

    private void cancelPayment() {
        Log.d("PaymentActivity", "Payment cancelled");
        
        // 构造取消结果 JSON
        try {
            org.json.JSONObject result = new org.json.JSONObject();
            result.put("success", false);
            result.put("orderId", orderId);
            result.put("message", "支付已取消");
            
            // 通过 ClosureRegistry 调用回调
            if (callbackId != null) {
                Log.d("PaymentActivity", "Invoking callback with cancel result");
                com.jd.plugins.ClosureRegistry.INSTANCE.invoke(callbackId, result.toString());
            }
        } catch (org.json.JSONException e) {
            Log.e("PaymentActivity", "Error creating cancel result JSON", e);
        }
        
        Log.d("PaymentActivity", "Finishing activity");
        finish();
    }
}
