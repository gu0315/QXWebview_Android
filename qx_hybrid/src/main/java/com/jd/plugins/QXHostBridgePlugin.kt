package com.jd.plugins

import android.util.Log
import com.jd.jdbridge.base.IBridgeCallback
import com.jd.jdbridge.base.IBridgePlugin
import com.jd.jdbridge.base.IBridgeWebView
import com.jd.jdbridge.base.runOnMain
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

class QXHostBridgePlugin : IBridgePlugin {

    companion object {
        private const val TAG = "QXHostBridgePlugin"
        private const val METHOD_OPEN_PAGE = "openPage"
    }

    @Volatile
    private var globalHostDelegate: QXWebViewHostDelegate? = null

    @Volatile
    private var localHostDelegate: QXWebViewHostDelegate? = null

    fun setHostDelegate(delegate: QXWebViewHostDelegate?) {
        globalHostDelegate = delegate
        Log.d(TAG, "Global host delegate ${if (delegate != null) "已设置" else "已清除"}")
    }

    fun setLocalHostDelegate(delegate: QXWebViewHostDelegate?) {
        localHostDelegate = delegate
        Log.d(TAG, "Local host delegate ${if (delegate != null) "已设置" else "已清除"}")
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
                parseParams(params)
            }
        } catch (e: Exception) {
            Log.e(TAG, "参数解析失败", e)
            callbackError(callback, QXBridgeErrorCode.INVALID_PARAMS, "参数错误: ${e.message}")
            return true
        }

        when (method) {
            METHOD_OPEN_PAGE -> openPage(webView, paramsMap, callback)
            else -> callCustomMethod(webView, method ?: "", paramsMap, callback)
        }

        return true
    }

    private fun openPage(
        webView: IBridgeWebView?,
        params: Map<String, Any?>,
        callback: IBridgeCallback?
    ) {
        val url = (params["url"] as? String)?.takeIf { it.isNotBlank() }
            ?: (params["pageName"] as? String)?.takeIf { it.isNotBlank() }
        if (url.isNullOrEmpty()) {
            callbackError(callback, QXBridgeErrorCode.INVALID_PARAMS, "缺少 url/pageName 参数")
            return
        }

        val pageParams = params["params"].asStringKeyMapOrNull()
        val delegate = resolveHostDelegate()
        if (delegate != null) {
            Log.d(TAG, "调用宿主 APP 打开页面: $url")
            dispatchToHost(webView) {
                delegate.webViewRequestOpenPage(url, pageParams) { result ->
                    callbackSuccess(callback, result ?: mapOf("success" to true))
                }
            }
        } else {
            callbackError(callback, QXBridgeErrorCode.UNSUPPORTED, "宿主 APP 未实现 delegate")
        }
    }

    private fun callCustomMethod(
        webView: IBridgeWebView?,
        methodName: String,
        params: Map<String, Any?>,
        callback: IBridgeCallback?
    ) {
        val delegate = resolveHostDelegate()
        if (delegate != null) {
            Log.d(TAG, "调用宿主 APP 自定义方法: $methodName")
            dispatchToHost(webView) {
                delegate.webViewRequestCustomMethod(methodName, params) { result ->
                    callbackSuccess(callback, result ?: mapOf("success" to true))
                }
            }
        } else {
            callbackError(callback, QXBridgeErrorCode.UNSUPPORTED, "宿主 APP 未实现 delegate")
        }
    }

    private fun callbackSuccess(callback: IBridgeCallback?, data: Any) {
        try {
            val jsonData = when (data) {
                is Map<*, *> -> data.toJSONObject()
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

    private fun resolveHostDelegate(): QXWebViewHostDelegate? {
        return localHostDelegate ?: globalHostDelegate
    }

    private fun dispatchToHost(webView: IBridgeWebView?, action: () -> Unit) {
        if (webView == null) {
            action()
            return
        }
        webView.runOnMain(Runnable { action() })
    }

    private fun parseParams(params: String): Map<String, Any?> {
        val parsed = JSONTokener(params).nextValue()
        if (parsed !is JSONObject) {
            throw IllegalArgumentException("params 必须是 JSON 对象")
        }
        return parsed.toMapDeep()
    }

    private fun JSONObject.toMapDeep(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        keys().forEach { key ->
            map[key] = get(key).toBridgeValue()
        }
        return map
    }

    private fun JSONArray.toListDeep(): List<Any?> {
        val list = mutableListOf<Any?>()
        for (index in 0 until length()) {
            list.add(get(index).toBridgeValue())
        }
        return list
    }

    private fun Any?.toBridgeValue(): Any? {
        return when (this) {
            null -> null
            JSONObject.NULL -> null
            is JSONObject -> toMapDeep()
            is JSONArray -> toListDeep()
            is Map<*, *> -> mapEntriesToBridgeValue()
            is Collection<*> -> map { it.toBridgeValue() }
            is Array<*> -> map { it.toBridgeValue() }
            else -> this
        }
    }

    private fun Any?.asStringKeyMapOrNull(): Map<String, Any?>? {
        val map = this as? Map<*, *> ?: return null
        return map.mapEntriesToBridgeValue()
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<*, *>.toJSONObject(): JSONObject {
        val normalized = mapEntriesToBridgeValue()
        return JSONObject(normalized as Map<String, Any?>)
    }

    private fun Map<*, *>.mapEntriesToBridgeValue(): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        entries.forEach { (key, value) ->
            val safeKey = key?.toString() ?: return@forEach
            result[safeKey] = value.toJsonSafeValue()
        }
        return result
    }

    private fun Any?.toJsonSafeValue(): Any? {
        return when (this) {
            null -> JSONObject.NULL
            is JSONObject, is JSONArray, is Number, is Boolean, is String -> this
            is Map<*, *> -> toJSONObject()
            is Collection<*> -> JSONArray(map { it.toJsonSafeValue() })
            is Array<*> -> JSONArray(map { it.toJsonSafeValue() })
            else -> this.toString()
        }
    }
}
