package com.jd.plugins

import com.jd.jdbridge.base.IBridgeCallback
import org.json.JSONArray
import org.json.JSONObject

/**
 * JDBridge onFail / onError 统一错误码，与 iOS `QXBridgeErrorCode`、H5 错误码速查表保持一致。
 *
 * 注意：BLE 相关错误码（10000~10013、-1~-99）继续沿用 [QXBleErrorCode]，不在此枚举内。
 */
enum class QXBridgeErrorCode(val code: Int) {
    /** 未知错误 */
    UNKNOWN(-1),
    /** 通用失败 */
    FAILURE(1),
    /** 参数错误 / 非法参数 */
    INVALID_PARAMS(1000),
    /** 没有权限（相机 / 定位 / 蓝牙 / 通知 等统一使用） */
    NO_PERMISSION(1001),
    /** 用户取消 / 结果为空 */
    CANCELLED(1002),
    /** 目标资源 / 页面 / 设备未找到 */
    NOT_FOUND(1003),
    /** 超时 */
    TIMEOUT(1004),
    /** 功能未实现 / 不支持 */
    UNSUPPORTED(1005);

    companion object {
        fun fromCode(code: Int): QXBridgeErrorCode =
            entries.find { it.code == code } ?: UNKNOWN
    }
}

/**
 * JDBridge `onError` 统一错误构造器。
 *
 * 因为 Android 侧 [IBridgeCallback.onError] 只接受 `String`，我们把
 * `{ code, message, success:false, data }` 序列化为 JSON 字符串写入，
 * H5 侧 `JSON.parse(err.message)` 即可拿到结构化字段，与 iOS `NSError.localizedDescription`
 * 下发的结构一致。
 *
 * 用法示例：
 * ```kotlin
 * callback?.onFail(QXBridgeErrorCode.NO_PERMISSION, "没有相机权限")
 * callback?.onError(QXBridgeError.noPermission("没有相机权限"))
 * callback?.onError(QXBridgeError.make(code = 10003, message = "connection fail"))
 * ```
 */
object QXBridgeError {

    /** 按原始 Int 错误码构造，供 BLE 等有自定义错误码体系的插件复用。 */
    fun make(code: Int, message: String, data: Any? = null): String {
        val payload = JSONObject().apply {
            put("code", code)
            put("message", message)
            put("success", false)
            val normalized = normalizeData(data)
            if (normalized != null) put("data", normalized)
        }
        return payload.toString()
    }

    fun make(code: QXBridgeErrorCode, message: String, data: Any? = null): String =
        make(code.code, message, data)

    fun failure(message: String, data: Any? = null): String =
        make(QXBridgeErrorCode.FAILURE, message, data)

    fun invalidParams(message: String = "参数错误", data: Any? = null): String =
        make(QXBridgeErrorCode.INVALID_PARAMS, message, data)

    fun noPermission(message: String = "没有权限", data: Any? = null): String =
        make(QXBridgeErrorCode.NO_PERMISSION, message, data)

    fun cancelled(message: String = "用户取消", data: Any? = null): String =
        make(QXBridgeErrorCode.CANCELLED, message, data)

    fun notFound(message: String = "资源未找到", data: Any? = null): String =
        make(QXBridgeErrorCode.NOT_FOUND, message, data)

    fun timeout(message: String = "超时", data: Any? = null): String =
        make(QXBridgeErrorCode.TIMEOUT, message, data)

    fun unsupported(message: String = "不支持", data: Any? = null): String =
        make(QXBridgeErrorCode.UNSUPPORTED, message, data)

    fun unknown(message: String = "未知错误", data: Any? = null): String =
        make(QXBridgeErrorCode.UNKNOWN, message, data)

    /**
     * 将任意对象归一化成可以安全写入 JSON 的形式。
     * 对 Map / Collection / Array 做一次包装，避免 `org.json` 抛异常。
     */
    private fun normalizeData(data: Any?): Any? {
        if (data == null) return null
        return when (data) {
            is JSONObject, is JSONArray, is String, is Number, is Boolean -> data
            is Map<*, *> -> JSONObject(data.mapKeys { it.key?.toString() ?: "" })
            is Collection<*> -> JSONArray(data.toList())
            is Array<*> -> JSONArray(data.toList())
            else -> data.toString()
        }
    }
}

/**
 * 语法糖：`callback?.onFail(QXBridgeErrorCode.NO_PERMISSION, "...")`
 */
fun IBridgeCallback.onFail(code: QXBridgeErrorCode, message: String, data: Any? = null) {
    onError(QXBridgeError.make(code, message, data))
}

fun IBridgeCallback.onFail(code: Int, message: String, data: Any? = null) {
    onError(QXBridgeError.make(code, message, data))
}
