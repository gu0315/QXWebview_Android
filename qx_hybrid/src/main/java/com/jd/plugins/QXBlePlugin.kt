import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.le.ScanFilter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
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
import com.jd.plugins.QXBLEventType
import com.jd.plugins.QXBleErrorCode
import com.jd.plugins.QXBleUtils
import com.jd.plugins.utils.BleDataParser
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

/**
 * 蓝牙桥接插件核心类
 * 
 * 功能概述：
 * - 作为JS与Native的桥接层，提供完整的BLE操作能力
 * - 支持蓝牙设备扫描、连接、服务发现、特征读写、通知订阅等核心功能
 * - 确保跨平台一致性
 * 
 * 架构设计：
 * - 基于Android-BLE库封装底层蓝牙操作
 * - 使用反射机制访问BluetoothGatt实例，实现高级功能
 * - 采用事件驱动模型，通过WebView回调通知JS层
 * 
 * 线程安全：
 * - 所有蓝牙操作在主线程执行
 * - 使用WeakReference避免Activity内存泄漏
 * 
 * 作者：顾钱想
 * 日期：2025/01/23
 * 版本：1.0.0
 */
class QXBlePlugin : IBridgePlugin {

    // ==================== 常量定义 ====================
    
    /** 插件名称，用于日志标记和JS调用标识 */
    val NAME = "QXBlePlugin"
    
    /** 蓝牙权限请求码 */
    private val REQUEST_CODE_BLE_PERMISSIONS: Int = 1001
    
    /** 蓝牙开启请求码 */
    private val REQUEST_ENABLE_BT = 0x101

    
    /** 
     * Android-BLE库实例，负责底层蓝牙操作
     * 生命周期：从openBluetoothAdapter初始化，到closeBluetoothAdapter释放
     */
    private var ble: Ble<BleDevice>? = null
    
    /** 
     * 当前Activity弱引用，避免内存泄漏
     * 用于权限请求、蓝牙开启等需要Activity上下文的操作
     */
    private var currentActivity: WeakReference<Activity>? = null
    
    // ==================== 设备管理 ====================
    
    /** 
     * 已扫描到的设备列表（简化版）
     * 仅存储BleDevice对象，用于快速查找和连接
     */
    private val scannedDevices = mutableListOf<BleDevice>()

    
    /**
     * 蓝牙设备扩展信息数据类
     * 
     * 用途：存储扫描过程中获取的完整设备信息，包括RSSI、广播数据等
     * 
     * @property device BLE设备对象
     * @property rssi 信号强度（Received Signal Strength Indicator）
     * @property scanRecord 原始广播数据（包含厂商数据、服务UUID等）
     * @property timestamp 扫描时间戳，用于设备去重和排序
     */
    private data class BluetoothDeviceInfo(
        val device: BleDevice,
        val rssi: Int,
        val scanRecord: ByteArray?,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        /**
         * 重写equals方法，确保设备去重逻辑正确
         * 注意：比较所有字段，包括RSSI和时间戳
         */
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as BluetoothDeviceInfo
            if (rssi != other.rssi) return false
            if (timestamp != other.timestamp) return false
            if (device != other.device) return false
            if (!scanRecord.contentEquals(other.scanRecord)) return false
            return true
        }

        /**
         * 重写hashCode方法，与equals保持一致
         */
        override fun hashCode(): Int {
            var result = rssi
            result = 31 * result + timestamp.hashCode()
            result = 31 * result + device.hashCode()
            result = 31 * result + (scanRecord?.contentHashCode() ?: 0)
            return result
        }
    }

    /** 
     * 已扫描到的设备完整信息列表
     * 用于getBluetoothDevices接口返回详细设备信息
     */
    private val scannedDevicesInfo = mutableListOf<BluetoothDeviceInfo>()


    override fun execute(
        webView: IBridgeWebView?,
        method: String?,
        params: String?,
        callback: IBridgeCallback?
    ): Boolean {
        // 获取Activity上下文并保存为弱引用
        val activity = (webView as? JDWebView)?.context as? Activity
        currentActivity = activity?.let { WeakReference(it) }
        Log.d(NAME, "execute $method")

        return when (method) {
            // 初始化蓝牙管理器
            "openBluetoothAdapter" -> {
                initBle(callback)
                true
            }
            // 开始扫描蓝牙设备
            "startBluetoothDevicesDiscovery" -> {
                params?.let {
                    try {
                        val json = JSONObject(it)
                        startBleScan(json, webView, callback)
                    } catch (e: Exception) {
                        callback?.onError("参数解析失败: ${e.message}")
                    }
                }

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

    private fun initBle(callback: IBridgeCallback?) {
        val activity = currentActivity?.get() ?: run {
            sendFailCallback(callback, QXBleErrorCode.PERIPHERAL_NIL, "当前Activity为空")
            return
        }
        if (checkBlePermissions(activity)) {
            // 配置蓝牙参数
            val options = Options().apply {
                logBleEnable = true                    // 开启日志输出，便于调试
                throwBleException = true               // 抛出异常而非静默失败
                autoConnect = false                    // 禁用自动重连（由上层控制）
                connectFailedRetryCount = 10           // 连接失败重试次数
                connectTimeout = 10000L                // 连接超时时间（10秒）
                scanPeriod = 12000L                    // 扫描周期（12秒）
                /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    scanFilter = buildScanFilter()
                }*/
                // 默认服务UUID（可被具体操作覆盖）
                uuidService = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")
                uuidWriteCha = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")
                uuidNotifyCha = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")
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

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun buildScanFilter(): ScanFilter {
        return ScanFilter.Builder().apply {
            setServiceUuid(ParcelUuid.fromString(UuidUtils.uuid16To128("180A")))
        }.build()
    }

    private fun startBleScan(jsonParams: JSONObject, webView: IBridgeWebView?, callback: IBridgeCallback?) {
        val bleInstance = ble ?: run {
            sendFailCallback(callback, QXBleErrorCode.PERIPHERAL_NIL, "蓝牙未初始化")
            return
        }
        // 清空之前的扫描结果，确保每次扫描都是全新的
        scannedDevices.clear()
        scannedDevicesInfo.clear()
        bleInstance.startScan(object : BleScanCallback<BleDevice>() {
            /**
             * 扫描到设备回调
             *
             * @param device 扫描到的BLE设备
             * @param rssi 信号强度（负数，越接近0信号越强）
             * @param scanRecord 原始广播数据
             */
            override fun onLeScan(device: BleDevice, rssi: Int, scanRecord: ByteArray?) {
                // 设备去重：根据MAC地址判断是否已存在
                if (!scannedDevices.any { d -> d.bleAddress == device.bleAddress }) {
                    scannedDevices.add(device)
                    scannedDevicesInfo.add(BluetoothDeviceInfo(device, rssi, scanRecord))
                    
                    // 发送设备发现事件到JS
                    sendBleEvent(
                        webView,
                        QXBLEventType.ON_BLUETOOTH_DEVICE_FOUND,
                        JSONObject().apply {
                            put("name", device.bleName ?: "")      // 设备名称（可能为空）
                            put("RSSI", rssi)                      // 信号强度
                            put("deviceId", device.bleAddress)     // MAC地址（Android唯一标识）
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
        callback?.onSuccess(JSONObject().apply { put("errMsg", "startBluetoothDevicesDiscovery:ok") })
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
            }
            override fun onConnectionChanged(device: BleDevice) {
                sendBleEvent(
                    webView,
                    QXBLEventType.ON_BLE_CONNECTION_STATE_CHANGE,
                    JSONObject().apply {
                        put("isConnected", device.isConnected)
                        put("deviceId", device.bleAddress)
                        put("name", device.bleName)
                    }
                )
            }

            override fun onConnectFailed(device: BleDevice, errorCode: Int) {
                sendFailCallback(callback, QXBleErrorCode.CONNECT_TIMEOUT, "连接失败: $errorCode")
            }

            override fun onReady(device: BleDevice) {
                super.onReady(device)
                // 返回连接成功结果
                sendSuccessCallback(
                    callback,
                    JSONObject().apply {
                        put("deviceId", device.bleAddress)
                        put("name", device.bleName ?: "未知设备")
                    },
                    "设备连接成功"
                )
                Log.d(NAME, "设备连接成功")
            }
        })
    }

    /**
     * 获取BLE设备的所有服务
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
        // 格式化服务数据
        val servicesArray = JSONArray()
        gatt.services.forEach { service ->
            val serviceJson = JSONObject().apply {
                put("serviceId", service.uuid.toString())
                put("isPrimary", service.type == BluetoothGattService.SERVICE_TYPE_PRIMARY)
            }
            servicesArray.put(serviceJson)
        }
        
        val resultData = JSONObject().apply {
            put("services", servicesArray)
        }
        sendSuccessCallback(
            callback,
            resultData,
            "发现服务成功，共${gatt.services.size}个服务"
        )
        // Log.d(NAME, resultData.toString(2))
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
            // 格式化特征数据
            val characteristicsJson = formatGattCharacteristics(gatt.services)
            sendSuccessCallback(
                callback,
                JSONObject().apply {
                    put("characteristics", characteristicsJson)
                },
                "获取特征成功，共${characteristicsJson.length()}个特征"
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


    private fun sendBleData(params: String, callback: IBridgeCallback?) {
        try {
            val parsedData = try {
                BleDataParser.parseData(params)
            } catch (e: IllegalArgumentException) {
                sendFailCallback(
                    callback,
                    QXBleErrorCode.UNKNOWN_ERROR,
                    e.message ?: "参数解析失败"
                )
                return
            }

            val targetDevice = ble?.connectedDevices?.find { it.bleAddress == parsedData.deviceId } ?: run {
                sendFailCallback(callback, QXBleErrorCode.DEVICE_NOT_FOUND, "设备未连接（deviceId=${parsedData.deviceId}）")
                return
            }
            
            Log.w(NAME, """
                开始写入蓝牙数据：
                - serviceId: ${parsedData.serviceId}
                - characteristicId: ${parsedData.characteristicId}
                - 数据长度: ${parsedData.data.size}字节
                - 数据: ${ByteUtils.bytes2HexStr(parsedData.data)}
            """.trimIndent())

//            val testFrameData: ByteArray = byteArrayOf(
//                0x7e.toByte(), 0xdb.toByte(), 0x01.toByte(), 0x00.toByte(),
//                0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x01.toByte(),
//                0x00.toByte(), 0x14.toByte(), 0x01.toByte(), 0x00.toByte(),
//                0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
//                0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
//                0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
//                0x00.toByte(), 0x00.toByte(), 0x69.toByte(), 0x81.toByte(),
//                0xa4.toByte(), 0xe4.toByte(), 0xa9.toByte(), 0x7e.toByte()
//            )



            ble?.writeByUuid(
                targetDevice,
                parsedData.data,
                UUID.fromString(parsedData.serviceId),
                UUID.fromString(parsedData.characteristicId),
                object : BleWriteCallback<BleDevice>() {
                    override fun onWriteSuccess(device: BleDevice, characteristic: BluetoothGattCharacteristic) {
                        Log.w(NAME, "写入成功")
                        sendSuccessCallback(
                            callback,
                            JSONObject().apply {
                                put("characteristicId", characteristic.uuid.toString())
                                put("value", characteristic.value?.let {
                                    ByteUtils.bytes2HexStr(characteristic.value)
                                } ?: "")
                            },
                            "写入特征值成功"
                        )
                    }

                    override fun onWriteFailed(device: BleDevice, failedCode: Int) {
                        Log.w(NAME, "写入失败，错误码：$failedCode")
                        sendFailCallback(
                            callback,
                            QXBleErrorCode.WRITE_NOT_SUPPORTED,
                            "数据发送失败: 错误码=$failedCode"
                        )
                    }
                }
            )
        } catch (e: Exception) {
            val errorMsg = "蓝牙数据发送异常：${e.message ?: "未知错误"}"
            Log.e(NAME, errorMsg, e)
            sendFailCallback(
                callback,
                QXBleErrorCode.UNKNOWN_ERROR,
                errorMsg
            )
        }
    }


    private fun notifyBLECharacteristicValueChange(params: String, callback: IBridgeCallback?, webView: IBridgeWebView?) {
        try {
            val jsonParams = JSONObject(params)
            val deviceMac = jsonParams.getString("deviceId")
            val serviceUUID = jsonParams.getString("serviceId")
            val characteristicUUID = jsonParams.getString("characteristicId")
            val enable = jsonParams.getBoolean("enable")
            // 验证设备连接状态
            val device = ble?.connectedDevices?.find { it.bleAddress == deviceMac } ?: run {
                sendFailCallback(callback, QXBleErrorCode.DEVICE_NOT_FOUND, "设备未连接")
                return
            }
            // 启用或关闭通知
            enableCharacteristicNotification(deviceMac, serviceUUID, characteristicUUID, enable, callback)
            // 注册通知回调监听
            ble?.enableNotifyByUuid(device, enable, UUID.fromString(serviceUUID), UUID.fromString(characteristicUUID), bleNotifyCallback(webView))
        } catch (e: Exception) {
            sendFailCallback(callback, QXBleErrorCode.UNKNOWN_ERROR, "解析参数/调用方法异常：${e.message ?: "未知错误"}")
            e.printStackTrace()
        }
    }

    /**
     * 启用或关闭特征通知/指示
     * @param deviceMac 设备MAC地址
     * @param serviceUUID 服务UUID
     * @param characteristicUUID 特征UUID
     * @param enable true=启用，false=关闭
     * @param callback 回调
     */
    private fun enableCharacteristicNotification(
        deviceMac: String,
        serviceUUID: String,
        characteristicUUID: String,
        enable: Boolean,
        callback: IBridgeCallback?
    ) {
        try {
            // 获取 Gatt 实例
            val gatt = getBluetoothGatt(deviceMac) ?: run {
                sendFailCallback(callback, QXBleErrorCode.PERIPHERAL_NIL, "Gatt实例为空")
                return
            }
            
            // 查找服务
            val service = gatt.getService(UUID.fromString(serviceUUID)) ?: run {
                sendFailCallback(callback, QXBleErrorCode.NO_SERVICE, "未找到服务: $serviceUUID")
                return
            }
            
            // 查找特征
            val characteristic = service.getCharacteristic(UUID.fromString(characteristicUUID)) ?: run {
                sendFailCallback(callback, QXBleErrorCode.NO_CHARACTERISTIC, "未找到特征: $characteristicUUID")
                return
            }
            
            // 检查权限
            val activity = currentActivity?.get() ?: run {
                sendFailCallback(callback, QXBleErrorCode.PERIPHERAL_NIL, "当前Activity为空")
                return
            }
            
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                sendFailCallback(callback, QXBleErrorCode.PERMISSION_DENIED, "缺少BLUETOOTH_CONNECT权限")
                return
            }
            
            // 启用本地通知
            val success = gatt.setCharacteristicNotification(characteristic, enable)
            if (!success) {
                sendFailCallback(callback, QXBleErrorCode.PROPERTY_NOT_SUPPORT, "启用本地通知失败")
                return
            }
            // 写入 CC'D 描述符
            writeCCCDDescriptor(gatt, characteristic, enable, deviceMac, serviceUUID, characteristicUUID, callback)
        } catch (e: Exception) {
            sendFailCallback(callback, QXBleErrorCode.UNKNOWN_ERROR, "启用通知异常：${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 写入 CC'D (Client Characteristic Configuration Descriptor) 描述符
     * @param gatt BluetoothGatt实例
     * @param characteristic 特征对象
     * @param enable true=启用，false=关闭
     * @param deviceMac 设备MAC地址
     * @param serviceUUID 服务UUID
     * @param characteristicUUID 特征UUID
     * @param callback 回调
     */
    private fun writeCCCDDescriptor(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        enable: Boolean,
        deviceMac: String,
        serviceUUID: String,
        characteristicUUID: String,
        callback: IBridgeCallback?
    ) {
        // CC'D 标准 UUID
        val ccdUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val descriptor = characteristic.getDescriptor(ccdUUID) ?: run {
            sendFailCallback(callback, QXBleErrorCode.NO_CHARACTERISTIC, "未找到CC'D描述符")
            return
        }
        // 根据特征属性选择启用通知或指示
        val value = if (enable) {
            when {
                (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 -> {
                    Log.d(NAME, "使用 NOTIFY 模式")
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                }
                (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0 -> {
                    Log.d(NAME, "使用 INDICATE 模式")
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                }
                else -> {
                    sendFailCallback(callback, QXBleErrorCode.PROPERTY_NOT_SUPPORT, "特征不支持通知或指示")
                    return
                }
            }
        } else {
            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        }
        
        // 设置描述符值
        descriptor.value = value
        
        // 写入描述符
        val activity = currentActivity?.get()
        if (activity != null && ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            sendFailCallback(callback, QXBleErrorCode.PERMISSION_DENIED, "缺少BLUETOOTH_CONNECT权限")
            return
        }
        
        val writeSuccess = gatt.writeDescriptor(descriptor)
        // TODO gu
        if (writeSuccess) {
            Log.d(NAME, "CC'D描述符写入请求已发送: enable=$enable, value=${value.contentToString()}")
            // 返回通知设置成功结果
            sendSuccessCallback(
                callback,
                JSONObject().apply {
                    put("characteristicId", characteristicUUID)
                    put("isNotifying", enable)
                },
                if (enable) "通知已启用" else "通知已关闭"
            )
        } else {
            sendFailCallback(callback, QXBleErrorCode.SYSTEM_ERROR, "CC'D描述符写入失败")
        }
    }

    /**
     * 通过反射获取 BluetoothGatt 实例
     * @param deviceMac 设备MAC地址
     * @return BluetoothGatt实例，失败返回null
     */
    private fun getBluetoothGatt(deviceMac: String): BluetoothGatt? {
        return try {
            val bleClass = Ble::class.java
            val bleRequestImplField = bleClass.getDeclaredField("bleRequestImpl")
            bleRequestImplField.isAccessible = true
            val bleRequestImpl = bleRequestImplField.get(ble) as? BleRequestImpl<*>
            bleRequestImpl?.getBluetoothGatt(deviceMac)
        } catch (e: Exception) {
            Log.e(NAME, "反射获取BluetoothGatt失败: ${e.message}")
            null
        }
    }

    private fun bleNotifyCallback(webView: IBridgeWebView?): BleNotifyCallback<BleDevice> {
        return object : BleNotifyCallback<BleDevice>(){
            override fun onChanged(device: BleDevice?, characteristic: BluetoothGattCharacteristic?) {
                Log.w(NAME, "notify-onChanged-${ByteUtils.bytes2HexStr(characteristic?.value)}")
                sendBleEvent(
                    webView,
                    QXBLEventType.ON_BLE_CHARACTERISTIC_VALUE_CHANGE,
                    JSONObject().apply {
                        put("deviceId", device?.bleAddress)
                        put("value", ByteUtils.bytes2HexStr(characteristic?.value))
                        put("characteristicId", characteristic?.uuid.toString())
                    }
                )
            }

            override fun onNotifyCanceled(device: BleDevice?) {
                Log.w(NAME, "notify-onNotifyCanceled")
                super.onNotifyCanceled(device)

            }

            override fun onNotifyFailed(device: BleDevice?, failedCode: Int) {
                Log.w(NAME, "notify-onNotifyFailed $failedCode")
                super.onNotifyFailed(device, failedCode)

            }

            override fun onNotifySuccess(device: BleDevice?) {
                Log.w(NAME, "notify-onNotifySuccess")
                super.onNotifySuccess(device)

            }
        }
    }
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
                    put("RSSI", deviceInfo.rssi)
                    put("deviceId", device.bleAddress)
                }
                devicesArray.put(deviceJson)
            }
            
            // 添加已连接的设备
            ble?.connectedDevices?.forEach { connectedDevice ->
                val isAlreadyInList = scannedDevicesInfo.any { it.device.bleAddress == connectedDevice.bleAddress }
                if (!isAlreadyInList) {
                    val deviceJson = JSONObject().apply {
                        put("name", connectedDevice.bleName ?: "")
                        put("RSSI", 0) // 已连接设备没有实时 RSSI
                        put("deviceId", connectedDevice.bleAddress)
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
        Log.d(NAME, result.toString())
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
                // Log.d(NAME, "事件[${eventType.value}]发送成功")
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
                    val properties = QXBleUtils.formatCharacteristicProperties(characteristic.properties)
                    val charJson = JSONObject().apply {
                        put("serviceId", serviceId)
                        put("characteristicId", characteristic.uuid.toString())
                        put("properties", properties)
                        put("isNotifying", false) // Android需要单独跟踪通知状态
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

    fun onDestroy() {
        scannedDevices.clear()
        currentActivity?.clear()
    }
}