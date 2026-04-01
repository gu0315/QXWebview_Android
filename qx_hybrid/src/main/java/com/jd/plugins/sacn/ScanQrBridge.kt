package com.jd.plugins.sacn

import org.json.JSONObject

/**
 * 与 iOS JDBridge 扫码回调对齐：
 * - 成功：同 Swift `callback.onSuccess(["data": qrResult, "success": true])`，使用 [JSONObject] 传参（字典语义）
 * - 失败：`IBridgeCallback.onError` 仍为 String，故提供 [failJson] 传 JSON 字符串
 */
object ScanQrBridge {

    fun successPayload(qrResult: String): JSONObject = JSONObject().apply {
        put("data", qrResult)
        put("success", true)
    }

    fun failPayload(message: String): JSONObject = JSONObject().apply {
        put("message", message)
        put("success", false)
    }

    fun failJson(message: String): String = failPayload(message).toString()
}
