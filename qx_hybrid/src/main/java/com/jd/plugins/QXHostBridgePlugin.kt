package com.jd.plugins

import android.util.Log
import com.jd.jdbridge.base.IBridgeCallback
import com.jd.jdbridge.base.IBridgePlugin
import com.jd.jdbridge.base.IBridgeWebView
import org.json.JSONObject


class QXHostBridgePlugin : IBridgePlugin {

    companion object {
        private const val TAG = "QXHostBridgePlugin"
        private const val METHOD_OPEN_PAGE = "openPage"
    }

    private var hostDelegate: QXWebViewHostDelegate? = null

    fun setHostDelegate(delegate: QXWebViewHostDelegate?) {
        this.hostDelegate = delegate
        Log.d(TAG, "Host delegate ${if (delegate != null) "已设置" else "已清除"}")
    }

    override fun execute(
        webView: IBridgeWebView?,
        method: String?,
        params: String?,
        callback: IBridgeCallback?
    ): Boolean {
        Log.d(TAG, "执行方法: $method, 参数: $params")

        val paramsMap = try {
            if (params.isNullOrEmpty()) {
                emptyMap()
            } else {
                JSONObject(params).toMap()
            }
        } catch (e: Exception) {
            Log.e(TAG, "参数解析失败", e)
            callbackError(callback, QXBridgeErrorCode.INVALID_PARAMS, "参数错误: ${e.message}")
            return true
        }

        when (method) {
            METHOD_OPEN_PAGE -> openPage(paramsMap, callback)
            else -> callCustomMethod(method ?: "", paramsMap, callback)
        }

        return true
    }

    private fun openPage(params: Map<String, Any?>, callback: IBridgeCallback?) {
        val url = params["url"] as? String
        if (url.isNullOrEmpty()) {
            callbackError(callback, QXBridgeErrorCode.INVALID_PARAMS, "缺少 url 参数")
            return
        }

        val pageParams = params["params"] as? Map<String, Any?>

        val delegate = hostDelegate
        if (delegate != null) {
            Log.d(TAG, "调用宿主 APP 打开页面: $url")
            delegate.webViewRequestOpenPage(url, pageParams) { result ->
                callbackSuccess(callback, result ?: mapOf("success" to true))
            }
        } else {
            callbackError(callback, QXBridgeErrorCode.UNSUPPORTED, "宿主 APP 未实现 delegate")
        }
    }

    private fun callCustomMethod(methodName: String, params: Map<String, Any?>, callback: IBridgeCallback?) {
        val delegate = hostDelegate
        if (delegate != null) {
            Log.d(TAG, "调用宿主 APP 自定义方法: $methodName")
            delegate.webViewRequestCustomMethod(methodName, params) { result ->
                callbackSuccess(callback, result ?: mapOf("success" to true))
            }
        } else {
            callbackError(callback, QXBridgeErrorCode.UNSUPPORTED, "宿主 APP 未实现 delegate")
        }
    }

    private fun callbackSuccess(callback: IBridgeCallback?, data: Any) {
        try {
            val jsonData = when (data) {
                is Map<*, *> -> JSONObject(data as Map<String, Any?>)
                is JSONObject -> data
                else -> JSONObject().apply { put("data", data) }
            }

            Log.d(TAG, "回调成功: $jsonData")
            callback?.onSuccess(jsonData)
        } catch (e: Exception) {
            Log.e(TAG, "回调成功时发生错误", e)
            callbackError(callback, QXBridgeErrorCode.FAILURE, "数据格式化失败: ${e.message}")
        }
    }

    private fun callbackError(
        callback: IBridgeCallback?,
        code: QXBridgeErrorCode,
        message: String
    ) {
        Log.e(TAG, "回调失败: [${code.code}] $message")
        callback?.onError(QXBridgeError.make(code, message))
    }

    private fun JSONObject.toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        keys().forEach { key ->
            map[key] = get(key)
        }
        return map
    }
}
