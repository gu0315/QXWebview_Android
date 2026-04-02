package com.energy.chery_android;

import android.util.Log;

import com.jd.plugins.QXBridgePluginRegister;
import com.jd.plugins.QXWebViewHostDelegate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public final class BridgeHostManager {
    private static final String TAG = "BridgeHostManager";
    private static QXWebViewHostDelegate hostDelegate;
    private static boolean delegateRegistered = false;

    private BridgeHostManager() {
    }

    public static synchronized void init() {
        if (delegateRegistered && hostDelegate != null) {
            QXBridgePluginRegister.INSTANCE.setHostDelegate(hostDelegate);
            return;
        }

        hostDelegate = new QXWebViewHostDelegate() {
            @Override
            public void webViewRequestOpenPage(@NotNull String url, @Nullable Map<?, ?> params,
                                               @NotNull Function1<@Nullable Object, @NotNull Unit> completion) {
                Log.d(TAG, "webViewRequestOpenPage: " + url);
                Map<String, Object> safeParams = new HashMap<>();
                if (params != null) {
                    for (Map.Entry<?, ?> entry : params.entrySet()) {
                        if (entry.getKey() != null) {
                            safeParams.put(String.valueOf(entry.getKey()), entry.getValue());
                        }
                    }
                }

                Map<String, Object> result = new HashMap<>();
                switch (url) {
                    case "app://pay":
                        result.put("success", true);
                        result.put("action", "pay");
                        result.put("params", safeParams);
                        completion.invoke(result);
                        break;
                    case "app://login":
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
                    default:
                        Log.d(TAG, "未处理的 URL: " + url);
                        result.put("success", false);
                        result.put("message", "未处理的 URL: " + url);
                        result.put("params", safeParams);
                        completion.invoke(result);
                        break;
                }
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
        Log.d(TAG, "HostDelegate registered");
    }
}
