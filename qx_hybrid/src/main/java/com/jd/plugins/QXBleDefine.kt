package com.jd.plugins

import android.bluetooth.BluetoothGattCharacteristic
import org.json.JSONArray
import org.json.JSONObject

/**
 * 蓝牙常量定义文件
 * 功能：定义蓝牙相关的枚举、错误码、工具类等
 * 作者：顾钱想
 * 日期：2025/01/23
 */

// MARK: - 蓝牙事件类型枚举
/**
 * 蓝牙事件类型，用于标识不同的蓝牙回调事件
 */
enum class QXBLEventType(val value: String) {
    /** 发现蓝牙设备事件 */
    ON_BLUETOOTH_DEVICE_FOUND("onBluetoothDeviceFound"),
    
    /** BLE连接状态变化事件 */
    ON_BLE_CONNECTION_STATE_CHANGE("onBLEConnectionStateChange"),
    
    /** BLE特征值变化事件（接收设备推送的数据） */
    ON_BLE_CHARACTERISTIC_VALUE_CHANGE("onBLECharacteristicValueChange"),
    
    /** BLE通知状态变化事件 */
    ON_BLE_NOTIFICATION_STATE_CHANGE("onBLENotificationStateChange"),
    
    /** BLE写入特征值结果事件 */
    ON_BLE_WRITE_CHARACTERISTIC_VALUE_RESULT("onBLEWriteCharacteristicValueResult");
    
    /** 获取回调Key前缀（用于生成唯一的回调标识） */
    val prefix: String get() = this.value
}

// MARK: - 蓝牙错误码枚举
/**
 * 蓝牙操作错误码，遵循uni-app标准错误码规范
 */
enum class QXBleErrorCode(val code: Int, val message: String) {
    // MARK: uni-app标准错误码（0, 10000-10013）
    /** 操作成功 */
    SUCCESS(0, "ok"),
    
    /** 未初始化蓝牙适配器 */
    NOT_INIT(10000, "not init"),
    
    /** 当前蓝牙适配器不可用（蓝牙未开启或不支持） */
    NOT_AVAILABLE(10001, "not available"),
    
    /** 没有找到指定设备 */
    NO_DEVICE(10002, "no device"),
    
    /** 连接失败 */
    CONNECTION_FAIL(10003, "connection fail"),
    
    /** 没有找到指定服务 */
    NO_SERVICE(10004, "no service"),
    
    /** 没有找到指定特征值 */
    NO_CHARACTERISTIC(10005, "no characteristic"),
    
    /** 当前连接已断开 */
    NO_CONNECTION(10006, "no connection"),
    
    /** 当前特征值不支持此操作 */
    PROPERTY_NOT_SUPPORT(10007, "property not support"),
    
    /** 其余所有系统上报的异常 */
    SYSTEM_ERROR(10008, "system error"),
    
    /** 系统版本不支持BLE（Android 4.3以下） */
    SYSTEM_NOT_SUPPORT(10009, "system not support"),
    
    /** 设备已连接 */
    ALREADY_CONNECT(10010, "already connect"),
    
    /** 配对设备需要配对码 */
    NEED_PIN(10011, "need pin"),
    
    /** 操作超时 */
    OPERATE_TIME_OUT(10012, "operate time out"),
    
    /** deviceId为空或格式不正确 */
    INVALID_DATA(10013, "invalid_data"),

    // MARK: 自定义扩展错误码（负数区间）
    /** 蓝牙未开启 */
    BLUETOOTH_NOT_OPEN(-1, "蓝牙未开启"),
    
    /** 蓝牙权限被拒绝 */
    PERMISSION_DENIED(-2, "蓝牙权限被拒绝，请前往设置开启"),
    
    /** 设备未找到 */
    DEVICE_NOT_FOUND(-3, "未找到指定设备"),
    
    /** 连接超时 */
    CONNECT_TIMEOUT(-4, "设备连接超时"),
    
    /** 特征值未找到 */
    CHARACTERISTIC_NOT_FOUND(-5, "未找到指定特征"),
    
    /** 特征值不支持写入 */
    WRITE_NOT_SUPPORTED(-6, "特征不支持写入"),
    
    /** 蓝牙权限未确定 */
    PERMISSION_NOT_DETERMINED(-7, "蓝牙权限未授权，请先授权"),
    
    /** 扫描不可用 */
    SCAN_NOT_AVAILABLE(-8, "当前无法扫描蓝牙设备"),
    
    /** 外设对象为空 */
    PERIPHERAL_NIL(-9, "蓝牙外设对象为空"),
    
    /** 未知错误 */
    UNKNOWN_ERROR(-99, "未知错误");

    companion object {
        /**
         * 根据错误码获取对应的枚举
         */
        fun fromCode(code: Int): QXBleErrorCode {
            return entries.find { it.code == code } ?: UNKNOWN_ERROR
        }
    }
}

// MARK: - 蓝牙工具类
/**
 * 蓝牙工具类，提供数据格式化、回调管理等通用功能
 */
object QXBleUtils {
    
    /**
     * 格式化特征属性为字符串数组
     * @param properties 特征属性位掩码
     * @return 属性名称数组
     */
    fun formatCharacteristicProperties(properties: Int): JSONArray {
        val props = mutableListOf<String>()
        if (properties <= 0) return JSONArray(props)
        
        if ((properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
            props.add("read")
        }
        if ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
            props.add("write")
        }
        if ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            props.add("writeWithoutResponse")
        }
        if ((properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
            props.add("notify")
        }
        if ((properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
            props.add("indicate")
        }
        if ((properties and BluetoothGattCharacteristic.PROPERTY_BROADCAST) != 0) {
            props.add("broadcast")
        }
        if ((properties and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE) != 0) {
            props.add("authenticatedSignedWrites")
        }
        if ((properties and BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS) != 0) {
            props.add("extendedProperties")
        }
        
        return JSONArray(props)
    }
}

// MARK: - 回调结果构造器
/**
 * 蓝牙操作结果构造器，用于统一格式化成功/失败结果
 */
object QXBleResult {
    
    /**
     * 构造成功结果
     * @param data 返回的数据对象（可选）
     * @param message 成功提示信息
     * @return 格式化的成功结果对象
     */
    fun success(data: JSONObject? = null, message: String = "操作成功"): JSONObject {
        return JSONObject().apply {
            put("code", QXBleErrorCode.SUCCESS.code)
            put("message", message)
            put("data", data ?: JSONObject())
        }
    }
    
    /**
     * 构造失败结果
     * @param errorCode 错误码枚举
     * @param customMessage 自定义错误信息（可选，默认使用错误码对应的message）
     * @return 格式化的失败结果对象
     */
    fun failure(errorCode: QXBleErrorCode, customMessage: String? = null): JSONObject {
        return JSONObject().apply {
            put("code", errorCode.code)
            put("message", customMessage ?: errorCode.message)
            put("data", JSONObject())
        }
    }
}
