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
}
