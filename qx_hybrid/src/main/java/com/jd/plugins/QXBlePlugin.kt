import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import cn.com.heaton.blelibrary.ble.Ble
import cn.com.heaton.blelibrary.ble.BleRequestImpl
import cn.com.heaton.blelibrary.ble.Options
import cn.com.heaton.blelibrary.ble.callback.BleConnectCallback
import cn.com.heaton.blelibrary.ble.callback.BleNotifyCallback
import cn.com.heaton.blelibrary.ble.callback.BleScanCallback
import cn.com.heaton.blelibrary.ble.callback.BleWriteCallback
import cn.com.heaton.blelibrary.ble.model.BleDevice
import cn.com.heaton.blelibrary.ble.utils.ByteUtils
import cn.com.heaton.blelibrary.ble.utils.UuidUtils
import com.jd.hybrid.JDWebView
import com.jd.jdbridge.base.IBridgeCallback
import com.jd.jdbridge.base.IBridgePlugin
import com.jd.jdbridge.base.IBridgeWebView
import com.jd.jdbridge.base.callJS
import com.jd.plugins.ClosureRegistry
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.UUID

class QXBlePlugin : IBridgePlugin {

    // 插件名称
    public val NAME = "QXBlePlugin"

    // 请求码
    private val REQUEST_CODE_BLE_PERMISSIONS: Int = 1001
    private val REQUEST_ENABLE_BT = 0x101

    // 蓝牙实例 & 上下文
    private var ble: Ble<BleDevice>? = null
    private var currentActivity: WeakReference<Activity>? = null
    private val scannedDevices = mutableListOf<BleDevice>()
    
    // 扩展的设备信息存储
    private data class BluetoothDeviceInfo(
        val device: BleDevice,
        val rssi: Int,
        val scanRecord: ByteArray?,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    private val scannedDevicesInfo = mutableListOf<BluetoothDeviceInfo>()

    // 事件类型枚举（对齐iOS）
    enum class QXBLEventType(val value: String) {
        ON_BLUETOOTH_DEVICE_FOUND("onBluetoothDeviceFound"),
        ON_BLE_CONNECTION_STATE_CHANGE("onBLEConnectionStateChange"),
        ON_BLE_CHARACTERISTIC_VALUE_CHANGE("onBLECharacteristicValueChange"),
        ON_BLE_NOTIFICATION_STATE_CHANGE("onBLENotificationStateChange"),
        ON_BLE_WRITE_CHARACTERISTIC_VALUE_RESULT("onBLEWriteCharacteristicValueResult");
        val prefix: String get() = this.value
    }

    // 错误码枚举（对齐uni-app标准）
    enum class QXBleErrorCode(val code: Int, val message: String) {
        SUCCESS(0, "ok"),
        NOT_INIT(10000, "not init"),
        NOT_AVAILABLE(10001, "not available"),
        NO_DEVICE(10002, "no device"),
        CONNECTION_FAIL(10003, "connection fail"),
        NO_SERVICE(10004, "no service"),
        NO_CHARACTERISTIC(10005, "no characteristic"),
        NO_CONNECTION(10006, "no connection"),
        PROPERTY_NOT_SUPPORT(10007, "property not support"),
        SYSTEM_ERROR(10008, "system error"),
        SYSTEM_NOT_SUPPORT(10009, "system not support"),
        ALREADY_CONNECT(10010, "already connect"),
        NEED_PIN(10011, "need pin"),
        OPERATE_TIME_OUT(10012, "operate time out"),
        INVALID_DATA(10013, "invalid_data"),
        
        // 保留旧的错误码以兼容现有代码
        BLUETOOTH_NOT_OPEN(-1, "蓝牙未开启"),
        PERMISSION_DENIED(-2, "蓝牙权限被拒绝，请前往设置开启"),
        DEVICE_NOT_FOUND(-3, "未找到指定设备"),
        CONNECT_TIMEOUT(-4, "设备连接超时"),
        CHARACTERISTIC_NOT_FOUND(-5, "未找到指定特征"),
        WRITE_NOT_SUPPORTED(-6, "特征不支持写入"),
        PERMISSION_NOT_DETERMINED(-7, "蓝牙权限未授权，请先授权"),
        SCAN_NOT_AVAILABLE(-8, "当前无法扫描蓝牙设备"),
        PERIPHERAL_NIL(-9, "蓝牙外设对象为空"),
        UNKNOWN_ERROR(-99, "未知错误");

        companion object {
            fun fromCode(code: Int): QXBleErrorCode {
                return entries.find { it.code == code } ?: UNKNOWN_ERROR
            }
        }
    }

    override fun execute(
        webView: IBridgeWebView?,
        method: String?,
        params: String?,
        callback: IBridgeCallback?
    ): Boolean {
        // 修复：安全获取Activity
        val activity = (webView as? JDWebView)?.context as? Activity
        currentActivity = activity?.let { WeakReference(it) }

        return when (method) {
            // 初始化蓝牙管理器
            "openBluetoothAdapter" -> {
                initBle(callback)
                true
            }
            // 开始扫描蓝牙设备
            "startBluetoothDevicesDiscovery" -> {
                startBleScan(webView, callback)
                true
            }
            // 停止扫描蓝牙设备
            "stopBluetoothDevicesDiscovery" -> {
                stopBleScan(callback)
                true
            }
            // 连接蓝牙设备
            "createBLEConnection" -> {
                params?.let {
                    try {
                        val json = JSONObject(it)
                        val deviceId = json.getString("deviceId")
                        connectBle(it, webView, callback)
                    } catch (e: Exception) {
                        callback?.onError("参数解析失败: ${e.message}")
                    }
                }
                true
            }
            // 获取设备服务列表
            "getBLEDeviceServices" -> {
                params?.let {
                    try {
                        val json = JSONObject(it)
                        val deviceId = json.getString("deviceId")
                        if (deviceId.isEmpty()) {
                            sendFailCallback(
                                callback,
                                QXBleErrorCode.DEVICE_NOT_FOUND,
                                "设备ID（MAC地址）不能为空"
                            )
                        } else {
                            getBLEDeviceServices(it, callback)
                        }
                    } catch (e: Exception) {
                        callback?.onError("参数解析失败: ${e.message}")
                    }
                }
                true
            }
            // 获取服务下的特征值列表
            "getBLEDeviceCharacteristics" ->{
                params?.let {
                    try {
                        val json = JSONObject(it)
                        val deviceId = json.getString("deviceId")
                        if (deviceId.isEmpty()) {
                            sendFailCallback(
                                callback,
                                QXBleErrorCode.DEVICE_NOT_FOUND,
                                "设备ID（MAC地址）不能为空"
                            )
                        } else {
                            getDeviceCharacteristics(it, callback)
                        }
                    } catch (e: Exception) {
                        callback?.onError("参数解析失败: ${e.message}")
                    }
                }
                true
            }
            // 断开蓝牙设备连接
            "closeBLEConnection" -> {
                params?.let {
                    try {
                        disconnectBle(it, callback)
                    } catch (e: Exception) {
                        callback?.onError("参数解析失败: ${e.message}")
                    }
                }
                true
            }
            // 向特征值写入数据
            "writeBLECharacteristicValue" -> {
                params?.let {
                    try {
                        sendBleData(it, callback)
                    } catch (e: Exception) {
                        callback?.onError("参数解析失败: ${e.message}")
                    }
                }
                true
            }
            // 开启/关闭特征值通知
            "notifyBLECharacteristicValueChange" -> {
                params?.let {
                    try {
                        notifyBLECharacteristicValueChange(it, callback, webView)
                    } catch (e: Exception) {
                        callback?.onError("参数解析失败: ${e.message}")
                    }
                }
                true
            }
            // 关闭蓝牙适配器
            "closeBluetoothAdapter" -> {
                closeBluetoothAdapter(callback)
                true
            }
            // 获取蓝牙适配器状态
            "getBluetoothAdapterState" -> {
                getBluetoothAdapterState(callback)
                true
            }
            // 获取已发现的蓝牙设备
            "getBluetoothDevices" -> {
                getBluetoothDevices(callback)
                true
            }
            else -> false
        }
    }

    /**
     * 初始化蓝牙
     */
    private fun initBle(callback: IBridgeCallback?) {
        val activity = currentActivity?.get() ?: run {
            sendFailCallback(callback, QXBleErrorCode.PERIPHERAL_NIL, "当前Activity为空")
            return
        }

        if (checkBlePermissions(activity)) {
            val options = Options().apply {
                logBleEnable = true
                throwBleException = true
                autoConnect = true
                connectFailedRetryCount = 10
                connectTimeout = 10000L
                scanPeriod = 12000L
                uuidService = UUID.fromString(UuidUtils.uuid16To128("ff00"))
                uuidWriteCha = UUID.fromString(UuidUtils.uuid16To128("ff01"))
                uuidNotifyCha = UUID.fromString(UuidUtils.uuid16To128("ff02"))
            }

            ble = Ble.create(activity.applicationContext, options, object : Ble.InitCallback {
                override fun success() {
                    sendSuccessCallback(callback, null, "蓝牙初始化成功")
                    checkBluetoothEnable(activity)
                }
                override fun failed(failedCode: Int) {
                    sendFailCallback(callback, QXBleErrorCode.UNKNOWN_ERROR, "蓝牙初始化失败: $failedCode")
                }
            })
        } else {
            requestBlePermissions(activity, callback)
            Toast.makeText(activity, "请授予APP蓝牙权限", Toast.LENGTH_SHORT).show()
        }
    }
    /**
     * 开始扫描
     */
    private fun startBleScan(webView: IBridgeWebView?, callback: IBridgeCallback?) {
        val bleInstance = ble ?: run {
            sendFailCallback(callback, QXBleErrorCode.PERIPHERAL_NIL, "蓝牙未初始化")
            return
        }

        scannedDevices.clear()
        scannedDevicesInfo.clear()
        
        bleInstance.startScan(object : BleScanCallback<BleDevice>() {
            override fun onLeScan(device: BleDevice, rssi: Int, scanRecord: ByteArray?) {
                if (!scannedDevices.any { d -> d.bleAddress == device.bleAddress }) {
                    scannedDevices.add(device)
                    
                    // 保存详细的设备信息
                    scannedDevicesInfo.add(BluetoothDeviceInfo(device, rssi, scanRecord))
                    
                    // 发送设备发现事件
                    sendBleEvent(
                        webView,
                        QXBLEventType.ON_BLUETOOTH_DEVICE_FOUND,
                        JSONObject().apply {
                            put("name", device.bleName ?: "")
                            put("RSSI", rssi)
                            put("rssi", rssi)
                            put("deviceId", device.bleAddress)
                            put("advertisData", scanRecord?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) } ?: "")
                            put("localName", device.bleName ?: "")
                        }
                    )
                }
            }

            override fun onStop() {
                sendSuccessCallback(
                    callback,
                    JSONObject().apply { put("deviceCount", scannedDevices.size) },
                    "扫描结束，共发现${scannedDevices.size}台设备"
                )
            }

            override fun onScanFailed(errorCode: Int) {
                sendFailCallback(
                    callback,
                    QXBleErrorCode.SCAN_NOT_AVAILABLE,
                    "扫描失败: $errorCode"
                )
            }
        })
        sendSuccessCallback(callback, null, "开始扫描蓝牙设备")
    }

    /**
     * 停止扫描
     */
    private fun stopBleScan(callback: IBridgeCallback?) {
        ble?.stopScan()
        sendSuccessCallback(callback, null, "已停止扫描")
    }

    /**
     * 连接设备
     */
    private fun connectBle(params: String, webView: IBridgeWebView?, callback: IBridgeCallback?) {
        val json = JSONObject(params)
        val deviceId = json.getString("deviceId")
        val device = scannedDevices.firstOrNull { it.bleAddress == deviceId } ?: run {
            sendFailCallback(callback, QXBleErrorCode.DEVICE_NOT_FOUND, "未扫描到该设备（MAC：$deviceId）")
            return
        }

        ble?.connect(device, object : BleConnectCallback<BleDevice>() {
            override fun onServicesDiscovered(device: BleDevice, gatt: BluetoothGatt) {
                super.onServicesDiscovered(device, gatt)
                Log.d(NAME, "设备 ${device.bleName} 服务发现完成")
            }

            override fun onConnectionChanged(device: BleDevice) {
                sendBleEvent(
                    webView,
                    QXBLEventType.ON_BLE_CONNECTION_STATE_CHANGE,
                    JSONObject().apply {
                        put("isConnecting", device.isConnecting)
                        put("isConnected", device.isConnected)
                        put("deviceId", device.bleAddress)
                    }
                )
            }

            override fun onConnectFailed(device: BleDevice, errorCode: Int) {
                sendFailCallback(callback, QXBleErrorCode.CONNECT_TIMEOUT, "连接失败: $errorCode")
            }

            override fun onReady(device: BleDevice) {
                sendSuccessCallback(
                    callback,
                    JSONObject().apply { put("deviceId", device.bleAddress) },
                    "设备连接成功"
                )
                // 启用通知
                ble?.enableNotify(device, true, object : BleNotifyCallback<BleDevice>() {
                    override fun onChanged(device: BleDevice, characteristic: BluetoothGattCharacteristic) {
                        val data = characteristic.value
                        sendBleEvent(
                            webView,
                            QXBLEventType.ON_BLE_CHARACTERISTIC_VALUE_CHANGE,
                            JSONObject().apply {
                                put("deviceId", device.bleAddress)
                                put("data", data?.let { ByteUtils.toHexString(it) } ?: "")
                                put("characteristicId", characteristic.uuid.toString())
                            }
                        )
                    }

                    override fun onNotifyFailed(device: BleDevice, errorCode: Int) {
                        Log.e(NAME, "通知开启失败: $errorCode")
                    }
                })
            }
        })
    }

    /**
     * 获取BLE设备的所有服务（对齐iOS的getBLEDeviceServices逻辑）
     * @param params JSON参数：{"deviceId":"设备MAC地址"}
     */
    private fun getBLEDeviceServices(params: String, callback: IBridgeCallback?) {
        try {
            val jsonParams = JSONObject(params)
            val deviceId = jsonParams.getString("deviceId").trim()

            val bleClass = Ble::class.java
            val bleRequestImplField = bleClass.getDeclaredField("bleRequestImpl")
            bleRequestImplField.isAccessible = true
            val bleRequestImpl = bleRequestImplField.get(ble) as? BleRequestImpl<*> ?: run {
                sendFailCallback(
                    callback,
                    QXBleErrorCode.UNKNOWN_ERROR,
                    "反射获取BleRequestImpl失败，无法获取服务"
                )
                return
            }

            // 获取Gatt实例
            val gatt = bleRequestImpl.getBluetoothGatt(deviceId) ?: run {
                sendFailCallback(
                    callback,
                    QXBleErrorCode.PERIPHERAL_NIL,
                    "设备[$deviceId]的Gatt实例为空，服务获取失败"
                )
                return
            }

            if (gatt.services.isEmpty()) {
                Log.d(NAME, "设备[$deviceId]未发现服务，主动触发服务发现")
                val activity = currentActivity?.get() ?: run {
                    sendFailCallback(callback, QXBleErrorCode.PERIPHERAL_NIL, "当前Activity为空")
                    return
                }

                if (ActivityCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    sendFailCallback(
                        callback,
                        QXBleErrorCode.PERMISSION_DENIED,
                        "缺少BLUETOOTH_CONNECT权限，无法发现服务"
                    )
                    return
                }

                gatt.discoverServices()
                // 延迟1秒后重新获取服务（服务发现需要时间）
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    getServicesAfterDiscovery(gatt, deviceId, callback)
                }, 1000)
                return
            }

            getServicesAfterDiscovery(gatt, deviceId, callback)
        } catch (e: Exception){
            // JSON解析异常
            sendFailCallback(
                callback,
                QXBleErrorCode.UNKNOWN_ERROR,
                "获取服务失败${e.message}"
            )
        }
    }

    /**
     * 服务发现后（主动/被动），格式化并返回服务数据
     */
    private fun getServicesAfterDiscovery(gatt: BluetoothGatt, deviceId: String, callback: IBridgeCallback?) {
        val servicesArray = JSONArray()
        gatt.services.forEach { service ->
            val serviceJson = JSONObject().apply {
                put("serviceId", service.uuid.toString())
                val characteristicsArray = JSONArray()
                service.characteristics.forEach { characteristic ->
                    val properties = formatCharacteristicProperties(characteristic.properties)
                    val charJson = JSONObject().apply {
                        put("characteristicId", characteristic.uuid.toString())
                        put("properties", properties)
                    }
                    characteristicsArray.put(charJson)
                }
                put("characteristics", characteristicsArray)
            }
            servicesArray.put(serviceJson)
        }

        val resultData = JSONObject().apply {
            put("deviceId", deviceId)
            put("services", servicesArray)
            put("serviceCount", gatt.services.size)
        }

        sendSuccessCallback(
            callback,
            resultData,
            "获取设备[$deviceId]服务成功，共${gatt.services.size}个服务"
        )

        Log.d(NAME, "===== 设备[$deviceId]服务列表 =====")
        Log.d(NAME, resultData.toString(2))
    }

    /**
     * 获取设备特征
     */
    private fun getDeviceCharacteristics(params: String, callback: IBridgeCallback?) {
        val json = JSONObject(params)
        val address = json.getString("deviceId").trim()
        val connectedDevices = ble?.connectedDevices ?: emptyList()
        val targetDevice = connectedDevices.find { it.bleAddress == address } ?: run {
            sendFailCallback(callback, QXBleErrorCode.DEVICE_NOT_FOUND, "未找到设备：$address")
            return
        }

        try {
            // 反射获取Gatt
            val bleClass = Ble::class.java
            val bleRequestImplField = bleClass.getDeclaredField("bleRequestImpl")
            bleRequestImplField.isAccessible = true
            val bleRequestImpl = bleRequestImplField.get(ble) as? BleRequestImpl<*> ?: run {
                sendFailCallback(callback, QXBleErrorCode.UNKNOWN_ERROR, "反射获取bleRequestImpl失败")
                return
            }

            val gatt = bleRequestImpl.getBluetoothGatt(targetDevice.bleAddress) ?: run {
                sendFailCallback(callback, QXBleErrorCode.PERIPHERAL_NIL, "Gatt实例为空")
                return
            }

            val characteristicsJson = formatGattCharacteristics(gatt.services)
            sendSuccessCallback(
                callback,
                JSONObject().apply {
                    put("characteristics", characteristicsJson)
                },
                "获取设备特征成功，共${characteristicsJson.length()}个特征"
            )
        } catch (e: Exception) {
            sendFailCallback(callback, QXBleErrorCode.UNKNOWN_ERROR, "异常：${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 断开连接
     */
    private fun disconnectBle(params: String, callback: IBridgeCallback?) {
        val json = JSONObject(params)
        val address = json.getString("deviceId")
        val targetDevice = ble?.connectedDevices?.find { it.bleAddress == address } ?: run {
            sendFailCallback(callback, QXBleErrorCode.DEVICE_NOT_FOUND, "设备未连接")
            return
        }

        ble?.disconnect(targetDevice)
        sendSuccessCallback(callback, null, "已断开连接")
    }

    /**
     * 发送数据
     */
    private fun sendBleData(params: String, callback: IBridgeCallback?) {
        try {
            val json = JSONObject(params)
            val deviceId = json.optString("deviceId", "")
            val serviceId = json.optString("serviceId", "")
            val characteristicId = json.optString("characteristicId", "")
            val valueStr = json.optString("value", "")
            val valueType = json.optString("valueType", "UTF8").uppercase(Locale.getDefault())
            if (deviceId.isEmpty() || serviceId.isEmpty() || characteristicId.isEmpty()) {
                sendFailCallback(
                    callback,
                    QXBleErrorCode.UNKNOWN_ERROR,
                    "deviceId/serviceId/characteristicId不能为空"
                )
                return
            }
            val targetDevice = ble?.connectedDevices?.find { it.bleAddress == deviceId } ?: run {
                sendFailCallback(callback, QXBleErrorCode.DEVICE_NOT_FOUND, "设备未连接")
                return
            }
            // 根据valueType解析数据
            val data: ByteArray = when (valueType) {
                "BASE64" -> {
                    try {
                        Base64.decode(valueStr, Base64.DEFAULT)
                    } catch (e: IllegalArgumentException) {
                        sendFailCallback(
                            callback,
                            QXBleErrorCode.WRITE_NOT_SUPPORTED,
                            "Base64解码失败：${e.message}"
                        )
                        return
                    }
                }
                "HEX", "16进制" -> {
                    // 清理HEX字符串
                    val cleanedHex = valueStr.replace(" ", "").uppercase(Locale.getDefault())
                    if (cleanedHex.length % 2 != 0) {
                        sendFailCallback(
                            callback,
                            QXBleErrorCode.WRITE_NOT_SUPPORTED,
                            "HEX字符串长度必须是偶数"
                        )
                        return
                    }
                    try {
                        ByteArray(cleanedHex.length / 2).apply {
                            for (i in indices) {
                                val startIndex = i * 2
                                val hexByte = cleanedHex.substring(startIndex, startIndex + 2)
                                this[i] = hexByte.toInt(16).toByte()
                            }
                        }
                    } catch (e: NumberFormatException) {
                        sendFailCallback(
                            callback,
                            QXBleErrorCode.WRITE_NOT_SUPPORTED,
                            "HEX格式错误：${e.message}"
                        )
                        return
                    }
                }
                "UTF8", "TEXT" -> {
                    valueStr.toByteArray(Charsets.UTF_8)
                }
                else -> {
                    // 未知类型，默认按UTF8处理
                    Log.w(NAME, "未知的valueType：$valueType，默认按UTF8解析")
                    valueStr.toByteArray(Charsets.UTF_8)
                }
            }
            if (data.isEmpty()) {
                sendFailCallback(
                    callback,
                    QXBleErrorCode.WRITE_NOT_SUPPORTED,
                    "数据解析失败：value=$valueStr，type=$valueType"
                )
                return
            }
            // 写入数据（使用指定service和characteristic）
            ble?.write(
                targetDevice,
                data,
                object : BleWriteCallback<BleDevice>() {
                    override fun onWriteSuccess(device: BleDevice, characteristic: BluetoothGattCharacteristic) {
                        sendBleEvent(
                            null,
                            QXBLEventType.ON_BLE_WRITE_CHARACTERISTIC_VALUE_RESULT,
                            JSONObject().apply {
                                put("deviceId", device.bleAddress)
                                put("serviceId", serviceId)
                                put("characteristicId", characteristic.uuid.toString())
                                put("success", true)
                            }
                        )
                        sendSuccessCallback(callback, null, "数据发送成功")
                    }
                    override fun onWriteFailed(device: BleDevice, failedCode: Int) {
                        sendFailCallback(
                            callback,
                            QXBleErrorCode.WRITE_NOT_SUPPORTED,
                            "数据发送失败: $failedCode"
                        )
                    }
                }
            )
            /*ble?.writeByUuid(
                targetDevice,
                data,
                UUID.fromString(serviceId),
                UUID.fromString(characteristicId),
                object : BleWriteCallback<BleDevice>() {
                    override fun onWriteSuccess(device: BleDevice, characteristic: BluetoothGattCharacteristic) {
                        sendBleEvent(
                            null,
                            QXBLEventType.ON_BLE_WRITE_CHARACTERISTIC_VALUE_RESULT,
                            JSONObject().apply {
                                put("deviceId", device.bleAddress)
                                put("serviceId", serviceId)
                                put("characteristicId", characteristic.uuid.toString())
                                put("success", true)
                            }
                        )
                        sendSuccessCallback(callback, null, "数据发送成功")
                    }
                    override fun onWriteFailed(device: BleDevice, failedCode: Int) {
                        sendFailCallback(
                            callback,
                            QXBleErrorCode.WRITE_NOT_SUPPORTED,
                            "数据发送失败: $failedCode"
                        )
                    }
                }
            )*/
        } catch (e: Exception) {
            sendFailCallback(
                callback,
                QXBleErrorCode.UNKNOWN_ERROR,
                "写入数据异常：${e.message}"
            )
            e.printStackTrace()
        }
    }

    private fun notifyBLECharacteristicValueChange(params: String, callback: IBridgeCallback?, webView: IBridgeWebView?) {
        try {
            val jsonParams = JSONObject(params)
            val deviceMac = jsonParams.getString("deviceId")
            val serviceUUID = jsonParams.getString("serviceId")
            val charUUID = jsonParams.getString("characteristicId")
            val enable = jsonParams.getBoolean("enable")

            val device = ble?.connectedDevices?.find { it.bleAddress == deviceMac } ?: run {
                sendFailCallback(callback, QXBleErrorCode.DEVICE_NOT_FOUND, "设备未连接")
                return
            }

            ble?.enableNotifyByUuid(device, enable, UUID.fromString(serviceUUID), UUID.fromString(charUUID), object : BleNotifyCallback<BleDevice>() {
                override fun onChanged(device: BleDevice, characteristic: BluetoothGattCharacteristic) {
                    val data = characteristic.value
                    sendBleEvent(
                        webView,
                        QXBLEventType.ON_BLE_CHARACTERISTIC_VALUE_CHANGE,
                        JSONObject().apply {
                            put("deviceId", device.bleAddress)
                            put("data", data?.let { ByteUtils.toHexString(it) } ?: "")
                            put("characteristicId", characteristic.uuid.toString())
                        }
                    )
                }

                override fun onNotifyFailed(device: BleDevice, errorCode: Int) {
                    Log.e(NAME, "通知开启失败: $errorCode")
                }
            })

            sendSuccessCallback(callback, null, if (enable) "已开启通知" else "已关闭通知")
        } catch (e: Exception) {
            sendFailCallback(callback, QXBleErrorCode.UNKNOWN_ERROR, "解析参数/调用方法异常：${e.message ?: "未知错误"}")
            e.printStackTrace()
        }
    }

    /**
     * 关闭蓝牙适配器
     * 对应uni-app的uni.closeBluetoothAdapter(OBJECT)
     * 关闭蓝牙模块，使其进入未初始化状态
     * 
     * @param callback 回调函数
     * 
     * JavaScript调用示例：
     * XWebView._callNative('QXBlePlugin', 'closeBluetoothAdapter', {}, 
     *     function(result) { 
     *         console.log('蓝牙适配器已关闭:', result); 
     *     },
     *     function(error) { 
     *         console.error('关闭失败:', error); 
     *     }
     * );
     */
    private fun closeBluetoothAdapter(callback: IBridgeCallback?) {
        try {
            // 停止扫描
            ble?.stopScan()
            
            // 断开所有连接的设备
            ble?.connectedDevices?.forEach { device ->
                ble?.disconnect(device)
            }
            
            // 清空扫描到的设备列表
            scannedDevices.clear()
            
            // 释放蓝牙资源
            ble?.released()
            // ble = null
            
            sendSuccessCallback(
                callback,
                null,
                "蓝牙适配器已关闭"
            )
            
            Log.d(NAME, "蓝牙适配器已关闭，所有连接已断开，资源已释放")
            
        } catch (e: Exception) {
            sendFailCallback(
                callback,
                QXBleErrorCode.UNKNOWN_ERROR,
                "关闭蓝牙适配器失败：${e.message}"
            )
            Log.e(NAME, "关闭蓝牙适配器异常", e)
        }
    }

    private fun getBluetoothAdapterState(callback: IBridgeCallback?) {
        try {
            val activity = currentActivity?.get() ?: run {
                sendFailCallback(callback, QXBleErrorCode.SYSTEM_ERROR, "当前Activity为空")
                return
            }

            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            
            // 检查设备是否支持蓝牙
            if (bluetoothAdapter == null) {
                sendFailCallback(callback, QXBleErrorCode.SYSTEM_NOT_SUPPORT, "设备不支持蓝牙")
                return
            }

            // 检查Android版本是否支持BLE
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
                sendFailCallback(callback, QXBleErrorCode.SYSTEM_NOT_SUPPORT, "Android 系统版本低于 4.3 不支持 BLE")
                return
            }

            // 检查蓝牙权限
            val hasPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
            }

            if (!hasPermissions) {
                sendFailCallback(callback, QXBleErrorCode.SYSTEM_ERROR, "蓝牙权限未授权")
                return
            }

            // 获取蓝牙状态
            val isEnabled = bluetoothAdapter.isEnabled
            val isDiscovering = try {
                if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothAdapter.isDiscovering
                } else {
                    false
                }
            } catch (e: SecurityException) {
                false
            }

            // 检查BLE是否初始化
            val isBleInitialized = ble != null
            
            // 根据状态返回相应的错误码
            when {
                !isEnabled -> {
                    sendFailCallback(callback, QXBleErrorCode.NOT_AVAILABLE, "当前蓝牙适配器不可用")
                    return
                }
                !isBleInitialized -> {
                    sendFailCallback(callback, QXBleErrorCode.NOT_INIT, "未初始化蓝牙适配器")
                    return
                }
                else -> {
                    // 成功情况：返回标准的 uni-app 格式
                    val stateData = JSONObject().apply {
                        put("available", true)
                        put("discovering", isDiscovering)
                    }
                    sendSuccessCallback(callback, stateData, "获取蓝牙适配器状态成功")
                }
            }

            Log.d(NAME, "蓝牙适配器状态: available=$isEnabled, discovering=$isDiscovering, bleInitialized=$isBleInitialized")
            
        } catch (e: Exception) {
            sendFailCallback(
                callback,
                QXBleErrorCode.SYSTEM_ERROR,
                "获取蓝牙适配器状态失败：${e.message}"
            )
            Log.e(NAME, "获取蓝牙适配器状态异常", e)
        }
    }

    private fun getBluetoothDevices(callback: IBridgeCallback?) {
        try {
            // 检查蓝牙是否初始化
            if (ble == null) {
                sendFailCallback(callback, QXBleErrorCode.NOT_INIT, "未初始化蓝牙适配器")
                return
            }
            val devicesArray = JSONArray()
            // 添加已扫描到的设备
            scannedDevicesInfo.forEach { deviceInfo ->
                val device = deviceInfo.device
                val deviceJson = JSONObject().apply {
                    put("name", device.bleName ?: "")
                    put("deviceId", device.bleAddress)
                    put("RSSI", deviceInfo.rssi)
                    put("rssi", deviceInfo.rssi)
                    put("advertisData", deviceInfo.scanRecord?.let { 
                        android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) 
                    } ?: "")
                    put("advertisServiceUUIDs", JSONArray()) // 需要解析 scanRecord 获取
                    put("localName", device.bleName ?: "")
                    put("serviceData", JSONObject()) // 需要解析 scanRecord 获取
                }
                devicesArray.put(deviceJson)
            }
            
            // 添加已连接的设备（如果不在扫描列表中）
            ble?.connectedDevices?.forEach { connectedDevice ->
                val isAlreadyInList = scannedDevicesInfo.any { it.device.bleAddress == connectedDevice.bleAddress }
                if (!isAlreadyInList) {
                    val deviceJson = JSONObject().apply {
                        put("name", connectedDevice.bleName ?: "")
                        put("deviceId", connectedDevice.bleAddress)
                        put("RSSI", 0) // 已连接设备没有实时 RSSI
                        put("rssi", 0)
                        put("advertisData", "")
                        put("advertisServiceUUIDs", JSONArray())
                        put("localName", connectedDevice.bleName ?: "")
                        put("serviceData", JSONObject())
                    }
                    devicesArray.put(deviceJson)
                }
            }

            val resultData = JSONObject().apply {
                put("devices", devicesArray)
            }
            sendSuccessCallback(callback, resultData, "获取已发现设备成功")
            Log.d(NAME, "获取蓝牙设备成功，共${devicesArray.length()}个设备")
        } catch (e: Exception) {
            sendFailCallback(callback, QXBleErrorCode.SYSTEM_ERROR, "获取已发现设备失败：${e.message}")
            Log.e(NAME, "获取蓝牙设备异常", e)
        }
    }

    fun getBlePermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return permissions.toTypedArray()
    }

    private fun checkBlePermissions(activity: Activity): Boolean {
        val permissions = getBlePermissions()
        return permissions.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBlePermissions(activity: Activity, callback: IBridgeCallback?) {
        ClosureRegistry.register("onRequestPermissionsResult", object : IBridgeCallback {
            override fun onSuccess(result: Any?) {
                try {
                    val resultStr = result?.toString() ?: ""
                    val json = JSONObject(resultStr)
                    val requestCode = json.getInt("requestCode")
                    if (requestCode != REQUEST_CODE_BLE_PERMISSIONS) return

                    val permissions = json.getJSONArray("permissions")
                    val grantResults = json.getJSONArray("grantResults")
                    val deniedPermissions = mutableListOf<String>()

                    for (i in 0 until grantResults.length()) {
                        if (grantResults.getInt(i) != PackageManager.PERMISSION_GRANTED) {
                            deniedPermissions.add(permissions.getString(i))
                        }
                    }

                    if (deniedPermissions.isNotEmpty()) {
                        Toast.makeText(activity, "蓝牙权限被拒绝", Toast.LENGTH_SHORT).show()
                        sendFailCallback(callback, QXBleErrorCode.PERMISSION_DENIED, "蓝牙权限被拒绝")
                    } else {
                        initBle(callback)
                    }
                } catch (e: Exception) {
                    sendFailCallback(callback, QXBleErrorCode.UNKNOWN_ERROR, "权限回调解析失败: ${e.message}")
                }
            }

            override fun onError(errMsg: String?) {
                sendFailCallback(callback, QXBleErrorCode.UNKNOWN_ERROR, "权限请求失败: $errMsg")
            }
        })

        val permissions = getBlePermissions()
        ActivityCompat.requestPermissions(activity, permissions, REQUEST_CODE_BLE_PERMISSIONS)
    }

    private fun checkBluetoothEnable(activity: Activity) {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            ActivityCompat.startActivityForResult(activity, intent, REQUEST_ENABLE_BT, null)
        }
    }

    /**
     * 发送成功回调
     */
    private fun sendSuccessCallback(callback: IBridgeCallback?, data: JSONObject?, message: String) {
        callback ?: return
        val result = JSONObject().apply {
            put("code", QXBleErrorCode.SUCCESS.code)
            put("message", message)
            put("data", data ?: JSONObject())
        }
        callback.onSuccess(result)
    }

    /**
     * 发送失败回调
     */
    private fun sendFailCallback(callback: IBridgeCallback?, errorCode: QXBleErrorCode, customMessage: String?) {
        callback ?: return
        val result = JSONObject().apply {
            put("code", errorCode.code)
            put("message", customMessage ?: errorCode.message)
            put("data", JSONObject())
        }
        callback.onError(result.toString())
    }

    /**
     * 发送蓝牙事件到JS
     */
    private fun sendBleEvent(webView: IBridgeWebView?, eventType: QXBLEventType, params: JSONObject) {
        val eventJson = params.apply {
            put("eventName", eventType.value)
        }
        webView?.callJS(NAME, eventJson, object : IBridgeCallback {
            override fun onSuccess(result: Any?) {
                Log.d(NAME, "事件[${eventType.value}]发送成功")
            }
            override fun onError(errMsg: String?) {
                Log.e(NAME, "事件[${eventType.value}]发送失败: $errMsg")
            }
        })
    }

    /**
     * 格式化特征数据
     */
    private fun formatGattCharacteristics(services: List<BluetoothGattService>?): JSONArray {
        val characteristicsArray = JSONArray()
        services?.forEach { service ->
            val serviceId = service.uuid.toString()
            service.characteristics.forEach characteristicLoop@ { characteristic ->
                try {
                    val properties = formatCharacteristicProperties(characteristic.properties)
                    val charJson = JSONObject().apply {
                        put("serviceId", serviceId)
                        put("characteristicId", characteristic.uuid.toString())
                        put("properties", properties)
                        put("value", characteristic.value?.let {
                            Base64.encodeToString(it, Base64.NO_WRAP)
                        } ?: "")
                    }
                    characteristicsArray.put(charJson)
                } catch (e: Exception) {
                    Log.w("BLE", "特征${characteristic.uuid}格式化失败：${e.message}")
                    return@characteristicLoop
                }
            }
        }
        return characteristicsArray
    }

    private fun formatCharacteristicProperties(properties: Int): JSONArray {
        val props = mutableListOf<String>()
        if (properties <= 0) return JSONArray(props)

        if ((properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) props.add("read")
        if ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) props.add("write")
        if ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) props.add("writeWithoutResponse")
        if ((properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) props.add("notify")
        if ((properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) props.add("indicate")

        return JSONArray(props)
    }

    fun onDestroy() {
        scannedDevices.clear()
        currentActivity?.clear()
    }
}