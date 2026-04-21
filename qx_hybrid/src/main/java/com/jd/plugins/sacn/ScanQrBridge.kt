package com.jd.plugins.sacn

import com.jd.plugins.QXBridgeError
import com.jd.plugins.QXBridgeErrorCode
import org.json.JSONObject

/**
 * 与 iOS JDBridge 扫码回调对齐：
 * - 成功：同 Swift `callback.onSuccess(["data": qrResult, "success": true])`，使用 [JSONObject] 传参（字典语义）
 * - 失败：`IBridgeCallback.onError` 仅为 String，统一走 [QXBridgeError] 生成带 `code` 的 JSON 串，
 *         与 iOS `QXBridgeError.make` 生成的 `userInfo` 结构一致。
 */
object ScanQrBridge {

    fun successPayload(qrResult: String): JSONObject = JSONObject().apply {
        put("data", qrResult)
        put("success", true)
    }

    /**
     * 构造失败回调用的 JSON 字符串。
     * @param message 给 H5 展示的错误提示
     * @param code 与 H5 / iOS 对齐的错误码，默认按「通用失败」处理
     */
    fun failJson(
        message: String,
        code: QXBridgeErrorCode = QXBridgeErrorCode.FAILURE
    ): String = QXBridgeError.make(code, message)
}
