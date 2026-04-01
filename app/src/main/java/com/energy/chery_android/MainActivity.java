package com.energy.chery_android;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.jd.jdbridge.base.IBridgeCallback;
import com.jd.plugins.ClosureRegistry;
import com.jd.plugins.QXBridgePluginRegister;
import com.jd.plugins.QXWebViewHostDelegate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainHostDelegate";
    private static QXWebViewHostDelegate hostDelegate;
    private static boolean delegateRegistered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerHostDelegateIfNeeded();
        setContentView(R.layout.activity_main);
    }

    private void registerHostDelegateIfNeeded() {
        if (delegateRegistered && hostDelegate != null) {
            QXBridgePluginRegister.INSTANCE.setHostDelegate(hostDelegate);
            return;
        }

        hostDelegate = new QXWebViewHostDelegate() {
            @Override
            public void webViewRequestOpenPage(@NotNull String url, @Nullable Map<?, ?> params,
                                               @NotNull Function1<@Nullable Object, @NotNull Unit> completion) {
                Log.d(TAG, "webViewRequestOpenPage: " + url);
                if (url.contains("payment") || url.contains("pay")) {
                    startPaymentFromWebView(params, completion);
                    return;
                }
                JSONObject res = new JSONObject();
                try {
                    res.put("success", true);
                    res.put("message", "页面打开成功");
                } catch (JSONException ignored) {
                }
                completion.invoke(res);
            }

            @Override
            public void webViewRequestCustomMethod(@NotNull String methodName,
                                                   @Nullable Map<String, ?> params,
                                                   @NotNull Function1<Object, Unit> completion) {
                Log.d(TAG, "webViewRequestCustomMethod: " + methodName);
                switch (methodName) {
                    case "getToken": {
                        Map<String, Object> result = new HashMap<>();
                        result.put("token", "xxx");
                        completion.invoke(result);
                        break;
                    }
                    case "getUserInfo": {
                        List<Map<String, String>> deviceList = new ArrayList<>();
                        deviceList.add(createDevice("vin1", "mac1"));
                        deviceList.add(createDevice("vin2", "mac2"));
                        deviceList.add(createDevice("vin3", "mac3"));

                        Map<String, Object> userInfo = new HashMap<>();
                        userInfo.put("phone", "xxx");
                        userInfo.put("list", deviceList);
                        userInfo.put("userId", "xxx");
                        userInfo.put("isLogin", true);
                        userInfo.put("userName", "xxx");
                        completion.invoke(userInfo);
                        break;
                    }
                    default: {
                        Map<String, Object> error = new HashMap<>();
                        error.put("success", false);
                        error.put("message", "未知的方法: " + methodName);
                        completion.invoke(error);
                    }
                }
            }

            private Map<String, String> createDevice(String vin, String mac) {
                Map<String, String> device = new HashMap<>();
                device.put("vin", vin);
                device.put("mac", mac);
                return device;
            }
        };

        QXBridgePluginRegister.INSTANCE.setHostDelegate(hostDelegate);
        delegateRegistered = true;
        Log.d(TAG, "HostDelegate registered in MainActivity");
    }

    private void startPaymentFromWebView(@Nullable Map<?, ?> params,
                                         @NotNull Function1<@Nullable Object, @NotNull Unit> completion) {
        String amount = "99.99";
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

        String callbackId = "payment_" + System.currentTimeMillis();
        IBridgeCallback bridgeCallback = new IBridgeCallback() {
            @Override
            public void onSuccess(@org.jetbrains.annotations.Nullable Object result) {
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
                    Log.e(TAG, "Error parsing payment result", e);
                    completion.invoke(null);
                }
            }

            @Override
            public void onError(@org.jetbrains.annotations.Nullable String error) {
                JSONObject errorResult = new JSONObject();
                try {
                    errorResult.put("success", false);
                    errorResult.put("message", error != null ? error : "支付失败");
                } catch (JSONException ignored) {
                }
                completion.invoke(errorResult);
            }
        };

        ClosureRegistry.INSTANCE.register(callbackId, bridgeCallback);

        Intent intent = new Intent(MainActivity.this, PaymentActivity.class);
        intent.putExtra(PaymentActivity.EXTRA_AMOUNT, amount);
        intent.putExtra(PaymentActivity.EXTRA_ORDER_ID, orderId);
        intent.putExtra("callbackId", callbackId);

        new Handler(Looper.getMainLooper()).post(() -> {
            Log.d(TAG, "Launching PaymentActivity with callbackId: " + callbackId);
            startActivity(intent);
        });
    }
}
