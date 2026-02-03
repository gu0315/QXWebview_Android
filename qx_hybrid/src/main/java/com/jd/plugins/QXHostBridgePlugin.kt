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
    
    /**
     * 宿主 APP 代理实例
     * 由宿主 APP 设置，用于处理 H5 的调用请求
     */
    private var hostDelegate: QXWebViewHostDelegate? = null
    
    /**
     * 当前回调实例
     * 用于在异步操作完成后返回结果给 H5
     */
    private var currentCallback: IBridgeCallback? = null
    
    /**
     * 设置宿主 APP 代理
     * 
     * @param delegate 实现了 QXWebViewHostDelegate 接口的代理对象
     */
    fun setHostDelegate(delegate: QXWebViewHostDelegate?) {
        this.hostDelegate = delegate
        Log.d(TAG, "Host delegate ${if (delegate != null) "已设置" else "已清除"}")
    }
    
    /**
     * 执行 JS 调用的方法
     * 
     * @param webView WebView 实例
     * @param method 方法名（openPage 或自定义方法名）
     * @param params JSON 格式的参数字符串
     * @param callback 回调接口
     * @return true 表示已处理该方法
     */
    override fun execute(
        webView: IBridgeWebView?,
        method: String?,
        params: String?,
        callback: IBridgeCallback?
    ): Boolean {
        this.currentCallback = callback
        
        Log.d(TAG, "执行方法: $method, 参数: $params")
        
        // 解析参数
        val paramsMap = try {
            if (params.isNullOrEmpty()) {
                emptyMap()
            } else {
                JSONObject(params).toMap()
            }
        } catch (e: Exception) {
            Log.e(TAG, "参数解析失败", e)
            callbackError("参数错误: ${e.message}")
            return true
        }
        
        // 根据方法名分发处理
        when (method) {
            METHOD_OPEN_PAGE -> openPage(paramsMap)
            else -> callCustomMethod(method ?: "", paramsMap)
        }
        
        return true
    }
    
    /**
     * 处理打开页面请求
     * 
     * @param params 参数 Map，必须包含 "url" 字段
     */
    private fun openPage(params: Map<String, Any?>) {
        val url = params["url"] as? String
        if (url.isNullOrEmpty()) {
            callbackError("缺少 url 参数")
            return
        }
        
        val pageParams = params["params"] as? Map<String, Any?>
        
        val delegate = hostDelegate
        if (delegate != null) {
            Log.d(TAG, "调用宿主 APP 打开页面: $url")
            delegate.webViewRequestOpenPage(url, pageParams) { result ->
                callbackSuccess(result ?: mapOf("success" to true))
            }
        } else {
            callbackError("宿主 APP 未实现 delegate")
        }
    }
    
    /**
     * 处理自定义方法调用
     * 
     * @param methodName 方法名
     * @param params 参数 Map
     */
    private fun callCustomMethod(methodName: String, params: Map<String, Any?>) {
        val delegate = hostDelegate
        if (delegate != null) {
            Log.d(TAG, "调用宿主 APP 自定义方法: $methodName")
            delegate.webViewRequestCustomMethod(methodName, params) { result ->
                callbackSuccess(result ?: mapOf("success" to true))
            }
        } else {
            callbackError("宿主 APP 未实现 delegate")
        }
    }
    
    /**
     * 成功回调
     * 
     * @param data 返回给 H5 的数据（支持 Map、JSONObject、基本类型等）
     */
    private fun callbackSuccess(data: Any) {
        try {
            val jsonData = when (data) {
                is Map<*, *> -> JSONObject(data as Map<String, Any?>)
                is JSONObject -> data
                else -> JSONObject().apply { put("data", data) }
            }
            
            Log.d(TAG, "回调成功: $jsonData")
            currentCallback?.onSuccess(jsonData)
        } catch (e: Exception) {
            Log.e(TAG, "回调成功时发生错误", e)
            callbackError("数据格式化失败: ${e.message}")
        }
    }
    
    /**
     * 失败回调
     * 
     * @param message 错误信息
     */
    private fun callbackError(message: String) {
        Log.e(TAG, "回调失败: $message")
        currentCallback?.onError(message)
    }
    
    /**
     * JSONObject 转 Map 扩展函数
     */
    private fun JSONObject.toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        keys().forEach { key ->
            map[key] = get(key)
        }
        return map
    }
}
