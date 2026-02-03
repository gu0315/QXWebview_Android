package com.energy.chery_android;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.energy.chery_android.databinding.FragmentFirstBinding;
import com.jd.hybrid.QXWebViewActivity;
import com.jd.plugins.QXBridgePluginRegister;
import com.jd.plugins.QXWebViewHostDelegate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public class FirstFragment extends Fragment {

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

        binding.buttonFirst.setOnClickListener(v ->
                NavHostFragment.findNavController(FirstFragment.this)
                        .navigate(R.id.action_FirstFragment_to_SecondFragment)
        );

        binding.buttonWebview.setOnClickListener(v -> {
            // 设置 Host Delegate
            setupHostDelegate();
            // 启动 WebView Activity
            Intent intent = new Intent(requireContext(), QXWebViewActivity.class);
            intent.putExtra(QXWebViewActivity.EXTRA_URL, "http://172.20.10.2:5173/");
            startActivity(intent);
        });
        
        // 测试支付按钮
        binding.buttonPayment.setOnClickListener(v -> testPayment());
    }
    
    private void setupHostDelegate() {
        QXBridgePluginRegister.INSTANCE.setHostDelegate(new QXWebViewHostDelegate() {
            @Override
            public void webViewRequestOpenPage(@NotNull String url, @Nullable Map<?, ?> params, @NotNull Function1<@Nullable Object, @NotNull Unit> completion) {
                Log.d("FirstFragment", "webViewRequestOpenPage: " + url);
                
                // 检查是否是支付相关的 URL
                if (url.contains("payment") || url.contains("pay")) {
                    // 跳转到原生支付页面
                    startPaymentFromWebView(params, completion);
                } else {
                    // 其他 URL 直接返回成功
                    JSONObject res = new JSONObject();
                    try {
                        res.put("success", true);
                        res.put("message", "页面打开成功");
                    } catch (JSONException ignored) {
                    }
                    completion.invoke(res);
                }
            }
            
            @Override
            public void webViewRequestCustomMethod(@NotNull String methodName,
                                                   @Nullable Map<String, ?> params,
                                                   @NotNull Function1<Object, Unit> completion) {
                Log.d("FirstFragment", "webViewRequestCustomMethod: " + methodName);
                // 处理自定义方法调用
                completion.invoke(null);
            }
        });
    }
    
    /**
     * 从 WebView 启动支付页面
     */
    private void startPaymentFromWebView(@Nullable Map<?, ?> params, @NotNull Function1<@Nullable Object, @NotNull Unit> completion) {
        Log.d("FirstFragment", "Starting payment from WebView");
        
        // 从参数中提取支付信息
        String amount = "99.99"; // 默认金额
        String orderId = "ORDER_" + System.currentTimeMillis();
        if (params != null) {
            Object amountObj = params.get("amount");
            Object orderIdObj = params.get("orderId");
            if (amountObj != null) {
                amount = amountObj.toString();
            }
            if (orderIdObj != null) {
                orderId = orderIdObj.toString();
            }
        }
        
        Log.d("FirstFragment", "Payment info - amount: " + amount + ", orderId: " + orderId);
        
        // 使用 ClosureRegistry 存储回调（像扫码一样）
        String callbackId = "payment_" + System.currentTimeMillis();
        
        // 将 Function1 包装成 IBridgeCallback
        com.jd.jdbridge.base.IBridgeCallback bridgeCallback = new com.jd.jdbridge.base.IBridgeCallback() {
            @Override
            public void onSuccess(@org.jetbrains.annotations.Nullable Object result) {
                Log.d("FirstFragment", "Payment callback onSuccess: " + result);
                try {
                    JSONObject jsonResult;
                    if (result instanceof String) {
                        jsonResult = new JSONObject((String) result);
                    } else if (result instanceof JSONObject) {
                        jsonResult = (JSONObject) result;
                    } else {
                        jsonResult = new JSONObject();
                    }
                    completion.invoke(jsonResult);
                } catch (JSONException e) {
                    Log.e("FirstFragment", "Error parsing payment result", e);
                    completion.invoke(null);
                }
            }
            
            @Override
            public void onError(@org.jetbrains.annotations.Nullable String error) {
                Log.d("FirstFragment", "Payment callback onError: " + error);
                JSONObject errorResult = new JSONObject();
                try {
                    errorResult.put("success", false);
                    errorResult.put("message", error != null ? error : "支付失败");
                } catch (JSONException ignored) {
                }
                completion.invoke(errorResult);
            }
        };
        
        com.jd.plugins.ClosureRegistry.INSTANCE.register(callbackId, bridgeCallback);
        
        // 准备 Intent，传递 callbackId
        Intent intent = new Intent(requireContext(), PaymentActivity.class);
        intent.putExtra(PaymentActivity.EXTRA_AMOUNT, amount);
        intent.putExtra(PaymentActivity.EXTRA_ORDER_ID, orderId);
        intent.putExtra("callbackId", callbackId);
        
        // 必须在主线程启动 Activity
        requireActivity().runOnUiThread(() -> {
            Log.d("FirstFragment", "Launching PaymentActivity with callbackId: " + callbackId);
            startActivity(intent);
        });
    }
    
    /**
     * 测试支付功能
     */
    private void testPayment() {
        Log.d("FirstFragment", "Test payment button clicked");
        
        // 模拟 WebView 的支付请求
        Map<String, Object> params = new HashMap<>();
        params.put("amount", "88.88");
        params.put("orderId", "TEST_ORDER_" + System.currentTimeMillis());
        
        // 创建一个测试回调
        Function1<Object, Unit> testCompletion = result -> {
            Log.d("FirstFragment", "Payment test result: " + result);
            
            requireActivity().runOnUiThread(() -> {
                String message;
                if (result instanceof JSONObject) {
                    try {
                        JSONObject json = (JSONObject) result;
                        boolean success = json.optBoolean("success", false);
                        String orderId = json.optString("orderId", "");
                        String transactionId = json.optString("transactionId", "");
                        
                        if (success) {
                            message = "支付成功！\n订单号: " + orderId + "\n交易号: " + transactionId;
                        } else {
                            message = "支付失败: " + json.optString("message", "未知错误");
                        }
                    } catch (Exception e) {
                        message = "结果解析失败: " + e.getMessage();
                    }
                } else {
                    message = "收到结果: " + result;
                }
                
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            });
            
            return Unit.INSTANCE;
        };
        
        // 调用支付方法
        startPaymentFromWebView(params, testCompletion);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
