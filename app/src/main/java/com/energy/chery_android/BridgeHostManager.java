package com.energy.chery_android;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.jd.plugins.QXBridgePluginRegister;
import com.jd.plugins.QXWebViewHostDelegate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public final class BridgeHostManager {
    private static final String TAG = "BridgeHostManager";

    /** FR 确认支付页回调 code,与 iOS / H5 约定一致 */
    public static final String FR_CODE_SUCCESS = "0";
    public static final String FR_CODE_CANCEL = "1";
    public static final String FR_CODE_FAIL = "2";

    private static QXWebViewHostDelegate hostDelegate;
    private static boolean delegateRegistered = false;
    private static Context appContext;
    /** 已拉起 FR 确认支付页、等待页面回传结果的 completion */
    private static Function1<Object, Unit> pendingFRConfirmPayCompletion;

    private BridgeHostManager() {
    }

    /**
     * 带 Context 的初始化：Context 用于从桥接回调里拉起原生页面(如 FR 确认支付页)
     */
    public static synchronized void init(Context context) {
        if (context != null && appContext == null) {
            appContext = context.getApplicationContext();
        }
        init();
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
                    case "app://openFRConfirmPay":
                        Object orderSeqObj = safeParams.get("orderSeq");
                        String orderSeq = orderSeqObj == null ? "" : orderSeqObj.toString();           // 绿能侧充电订单号
                        Object stationNameObj = safeParams.get("stationName");
                        String stationName = stationNameObj == null ? "" : stationNameObj.toString();  // 充电站名称
                        Object connectorObj = safeParams.get("powerConnectorId");
                        String powerConnectorId = connectorObj == null ? "" : connectorObj.toString(); // 充电桩编号
                        // 真实宿主在这里拉起自己的 FR 下单页；Demo 用模拟页面代替：
                        // 「确认支付」-> code 0，「取消」/ 返回 -> code 1，拉不起页面 -> code 2
                        openFRConfirmPay(orderSeq, stationName, powerConnectorId, completion);
                        break;
                    case "app://login":
                        // 返回用户在主界面输入的信息
                        completion.invoke(UserSession.getInstance().userInfoPayload());
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
                        // 返回用户在主界面输入的信息
                        completion.invoke(UserSession.getInstance().userInfoPayload());
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
        };

        QXBridgePluginRegister.INSTANCE.setHostDelegate(hostDelegate);
        delegateRegistered = true;
        Log.d(TAG, "HostDelegate registered");
    }

    /**
     * 拉起 FR 确认支付页，页面关闭时由 {@link #deliverFRConfirmPayResult} 回传结果
     */
    private static synchronized void openFRConfirmPay(String orderSeq, String stationName,
                                                      String powerConnectorId,
                                                      Function1<Object, Unit> completion) {
        if (appContext == null) {
            Log.e(TAG, "appContext 为空，请改用 BridgeHostManager.init(context)");
            completion.invoke(frConfirmPayResult(FR_CODE_FAIL, "宿主未初始化 Context", orderSeq));
            return;
        }
        // 上一次的调用若还挂着（页面异常未回传），先按失败结束，避免 H5 永远等不到
        if (pendingFRConfirmPayCompletion != null) {
            pendingFRConfirmPayCompletion.invoke(frConfirmPayResult(FR_CODE_FAIL, "已被新的支付请求取代", orderSeq));
        }
        pendingFRConfirmPayCompletion = completion;

        Intent intent = new Intent(appContext, FRConfirmPayActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(FRConfirmPayActivity.EXTRA_ORDER_SEQ, orderSeq);
        intent.putExtra(FRConfirmPayActivity.EXTRA_STATION_NAME, stationName);
        intent.putExtra(FRConfirmPayActivity.EXTRA_POWER_CONNECTOR_ID, powerConnectorId);
        try {
            appContext.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "拉起 FRConfirmPayActivity 失败", e);
            pendingFRConfirmPayCompletion = null;
            completion.invoke(frConfirmPayResult(FR_CODE_FAIL, "跳转失败: " + e.getMessage(), orderSeq));
        }
    }

    /**
     * FR 确认支付页的结果回传入口，结果只会回调一次
     */
    public static synchronized void deliverFRConfirmPayResult(String code, String message, String orderSeq) {
        Function1<Object, Unit> completion = pendingFRConfirmPayCompletion;
        pendingFRConfirmPayCompletion = null;
        if (completion == null) {
            Log.d(TAG, "FR 确认支付结果已回传过，忽略: " + code);
            return;
        }
        Log.d(TAG, "FR 确认支付结果: code=" + code + ", message=" + message + ", orderSeq=" + orderSeq);
        completion.invoke(frConfirmPayResult(code, message, orderSeq));
    }

    private static Map<String, Object> frConfirmPayResult(String code, String message, String orderSeq) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", code);
        result.put("message", message == null ? "" : message);
        result.put("orderSeq", orderSeq == null ? "" : orderSeq);
        return result;
    }
}
