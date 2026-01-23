package com.jd.plugins.utils

import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * BLE数据解析工具类
 * 
 * 功能：将不同格式的数据（BASE64、BUFFER、HEX、UTF8）转换为ByteArray
 * 用于蓝牙特征值写入操作
 * 
 * 作者：顾钱想
 * 日期：2025/01/23
 */
object BleDataParser {
    
    private const val TAG = "BleDataParser"
    
    /**
     * 解析数据参数
     * 
     * @param params JSON字符串，包含deviceId、serviceId、characteristicId、valueType、value
     * @return ParsedBleData 解析后的数据对象
     * @throws IllegalArgumentException 参数格式错误或必填字段缺失
     */
    fun parseData(params: String): ParsedBleData {
        val json = JSONObject(params)
        val deviceId = json.optString("deviceId", "")
        val serviceId = json.optString("serviceId", "")
        val characteristicId = json.optString("characteristicId", "")
        val valueType = json.optString("valueType", "UTF8").uppercase(Locale.getDefault())
        val value = json.opt("value")
        
        // 基础参数校验
        if (deviceId.isEmpty() || serviceId.isEmpty() || characteristicId.isEmpty()) {
            throw IllegalArgumentException(
                "deviceId/serviceId/characteristicId不能为空（deviceId=$deviceId, serviceId=$serviceId, characteristicId=$characteristicId）"
            )
        }
        
        Log.d(TAG, """
            解析基础参数成功：
            - valueType: $valueType
            - value类型: ${value?.javaClass?.simpleName ?: "null"}
            - value内容: $value
        """.trimIndent())
        
        // 解析数据
        val data = parseValueByType(valueType, value)
        
        // 空数据校验
        if (data.isEmpty()) {
            throw IllegalArgumentException("数据解析后为空：value=$value，type=$valueType")
        }
        
        return ParsedBleData(
            deviceId = deviceId,
            serviceId = serviceId,
            characteristicId = characteristicId,
            valueType = valueType,
            data = data
        )
    }
    
    /**
     * 根据类型解析数据
     * 
     * @param valueType 数据类型（BASE64、BUFFER、HEX、UTF8、TEXT等）
     * @param value 原始数据
     * @return ByteArray 解析后的字节数组
     * @throws IllegalArgumentException 解析失败
     */
    private fun parseValueByType(valueType: String, value: Any?): ByteArray {
        return when (valueType) {
            "BASE64" -> parseBase64(value)
            "BUFFER" -> parseBuffer(value)
            "HEX", "16进制" -> parseHex(value)
            "UTF8", "TEXT" -> parseUtf8(value)
            else -> {
                Log.w(TAG, "未知的valueType：$valueType，默认按UTF8解析")
                parseUtf8(value)
            }
        }
    }
    
    /**
     * 解析BASE64格式数据
     */
    private fun parseBase64(value: Any?): ByteArray {
        val valueStr = value?.toString() ?: ""
        return try {
            Base64.decode(valueStr, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Base64解码失败：${e.message}", e)
        }
    }
    
    /**
     * 解析BUFFER格式数据（支持JSONArray、JSONObject、String）
     */
    private fun parseBuffer(value: Any?): ByteArray {
        val jsonArray = when (value) {
            is JSONArray -> {
                Log.d(TAG, "BUFFER格式：JSONArray，长度=${value.length()}")
                value
            }
            is JSONObject -> {
                Log.d(TAG, "BUFFER格式：JSONObject，keys=${value.keys().asSequence().toList()}")
                if (value.length() == 0) {
                    throw IllegalArgumentException("JSONObject为空，可能ArrayBuffer没有正确传递")
                }
                convertJsonObjectToArray(value)
            }
            is String -> {
                Log.d(TAG, "BUFFER格式：String，内容=$value")
                parseBufferString(value)
            }
            null -> throw IllegalArgumentException("value为null，请检查JS端是否正确传递Buffer数据")
            else -> throw IllegalArgumentException(
                "BUFFER类型value必须是JSONArray/JSONObject/字符串，当前类型：${value.javaClass.simpleName}"
            )
        }
        
        if (jsonArray.length() == 0) {
            throw IllegalArgumentException("""
                BUFFER数据为空。提示：
                1. ArrayBuffer需有数据 2. Uint8Array不能为空 3. 建议用Array.from(uint8Array) 4. 或用HEX类型
            """.trimIndent())
        }
        
        Log.d(TAG, "BUFFER解析成功，字节数=${jsonArray.length()}")
        return convertJsonArrayToByteArray(jsonArray)
    }
    
    /**
     * 将JSONObject转换为JSONArray（按key排序）
     */
    private fun convertJsonObjectToArray(obj: JSONObject): JSONArray {
        val keys = obj.keys().asSequence().toList().sortedBy { it.toIntOrNull() ?: 0 }
        return JSONArray().apply {
            keys.forEach { key -> put(obj.getInt(key)) }
        }
    }
    
    /**
     * 解析BUFFER字符串格式
     */
    private fun parseBufferString(value: String): JSONArray {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Buffer字符串为空")
        }
        
        return when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val obj = JSONObject(trimmed)
                if (obj.length() == 0) {
                    throw IllegalArgumentException("解析后的JSONObject为空")
                }
                convertJsonObjectToArray(obj)
            }
            else -> {
                val parts = trimmed.split(",").map { it.trim() }
                JSONArray(parts)
            }
        }
    }
    
    /**
     * 将JSONArray转换为ByteArray
     */
    private fun convertJsonArrayToByteArray(jsonArray: JSONArray): ByteArray {
        return ByteArray(jsonArray.length()) { index ->
            val intValue = jsonArray.getInt(index)
            if (intValue < 0 || intValue > 255) {
                throw IllegalArgumentException("第${index}位值${intValue}超出Uint8范围（0-255）")
            }
            intValue.toByte()
        }
    }
    
    /**
     * 解析HEX格式数据
     */
    private fun parseHex(value: Any?): ByteArray {
        val valueStr = value?.toString() ?: ""
        val cleanedHex = valueStr.replace(" ", "").uppercase(Locale.getDefault())
        
        if (cleanedHex.length % 2 != 0) {
            throw IllegalArgumentException("HEX字符串长度必须是偶数（当前：${cleanedHex.length}）")
        }
        
        return try {
            ByteArray(cleanedHex.length / 2).apply {
                for (i in indices) {
                    val startIndex = i * 2
                    val hexByte = cleanedHex.substring(startIndex, startIndex + 2)
                    this[i] = hexByte.toInt(16).toByte()
                }
            }
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("HEX格式错误：${e.message}", e)
        }
    }
    
    /**
     * 解析UTF8/TEXT格式数据
     */
    private fun parseUtf8(value: Any?): ByteArray {
        val valueStr = value?.toString() ?: ""
        return valueStr.toByteArray(StandardCharsets.UTF_8)
    }
    
    /**
     * 解析后的BLE数据
     */
    data class ParsedBleData(
        val deviceId: String,
        val serviceId: String,
        val characteristicId: String,
        val valueType: String,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ParsedBleData
            if (deviceId != other.deviceId) return false
            if (serviceId != other.serviceId) return false
            if (characteristicId != other.characteristicId) return false
            if (valueType != other.valueType) return false
            if (!data.contentEquals(other.data)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = deviceId.hashCode()
            result = 31 * result + serviceId.hashCode()
            result = 31 * result + characteristicId.hashCode()
            result = 31 * result + valueType.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }
}
