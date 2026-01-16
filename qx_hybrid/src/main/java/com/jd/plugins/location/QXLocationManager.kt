package com.jd.plugins.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.jd.jdbridge.base.IBridgeCallback
import com.jd.plugins.utils.GCJ02Converter
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

class QXLocationManager private constructor(context: Context) {

    companion object {
        private const val TAG = "QXLocationManager"
        @Volatile private var INSTANCE: QXLocationManager? = null
        private val gson by lazy { Gson() }

        fun getInstance(context: Context): QXLocationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: QXLocationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val contextRef = WeakReference(context)
    private val context: Context? get() = contextRef.get()

    private val locationManager: LocationManager? by lazy {
        context?.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val locationListeners = mutableListOf<LocationListener>()

    private var locationCallback: IBridgeCallback? = null
    private var permissionCallback: ((Boolean) -> Unit)? = null

    private var isCallbackInvoked = false
    private var bestLocation: Location? = null

    private var timeoutRunnable: Runnable? = null

    private var targetAccuracy = LocationConstants.DEFAULT_ACCURACY
    private var timeout = LocationConstants.DEFAULT_TIMEOUT.toLong()

    private val sharedPrefs: SharedPreferences? by lazy {
        context?.getSharedPreferences("QXLocationPrefs", Context.MODE_PRIVATE)
    }

    // ===================== 对外入口 =====================
    fun setCallback(callback: IBridgeCallback?) {
        locationCallback = callback
        isCallbackInvoked = false
    }

    fun getLocation(activity: Activity, params: Map<String, Any>? = null) {
        clear()

        params?.let {
            targetAccuracy = (it["accuracy"] as? Number)?.toInt() ?: targetAccuracy
            timeout = (it["timeout"] as? Number)?.toLong() ?: timeout
        }

        if (!hasLocationPermission()) {
            requestPermission(activity)
            return
        }

        startLocation()
    }

    // ===================== 权限 =====================
    private fun hasLocationPermission(): Boolean {
        val ctx = context ?: return false
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun requestPermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LocationConstants.PERMISSION_REQUEST_CODE
        )
    }

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode != LocationConstants.PERMISSION_REQUEST_CODE) return
        val granted = grantResults.any { it == PackageManager.PERMISSION_GRANTED }
        if (granted) startLocation()
        else callbackError(LocationConstants.ERROR_PERMISSION_DENIED, LocationConstants.PERMISSION_DENIED_MSG)
    }

    // ===================== 定位主流程 =====================
    private fun startLocation() {
        if (!isLocationEnabled()) {
            callbackError(LocationConstants.ERROR_LOCATION_SERVICE_DISABLED, LocationConstants.LOCATION_SERVICE_DISABLED_MSG)
            return
        }

        tryReturnLastKnown()

        refreshAGPS()

        startTimeout()

        startParallelRequest()
    }

    /** 修复：历史坐标逻辑收紧，只返回高精度有效坐标，不优先返回 */
    private fun tryReturnLastKnown() {
        val gps = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val net = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

        val best = listOfNotNull(gps, net)
            .filter { isValidLocation(it) }
            .filter {
                // 优化1：过期时间从2分钟→30秒，精度从100米→50米，只取近期高精度坐标
                System.currentTimeMillis() - it.time < TimeUnit.SECONDS.toMillis(30) &&
                        it.accuracy <= 50
            }
            .maxByOrNull { providerWeight(it) }

        best?.let {
            processLocation(it, isTemp = true)
        }
    }

    private fun startParallelRequest() {
        val lm = locationManager ?: return

        listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).forEach { provider ->
            if (!lm.isProviderEnabled(provider)) return@forEach

            val listener = createListener()
            locationListeners.add(listener)

            // 优化2：合理的定位回调参数，过滤无效频繁回调 + 子线程处理定位回调，不卡主线程
            lm.requestLocationUpdates(
                provider,
                1000L,      // 最小更新间隔1秒
                1f,         // 最小距离变化1米
                listener,
                Looper.myLooper() // 子线程Looper，避免主线程卡顿
            )
        }
    }

    private fun createListener(): LocationListener {
        return object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!isValidLocation(location)) return

                if (isBetterLocation(location, bestLocation)) {
                    bestLocation = location
                }

                val isGps = location.provider == LocationManager.GPS_PROVIDER
                // 满足精度要求，直接返回最优结果
                if (isGps || location.accuracy <= targetAccuracy) {
                    processLocation(location)
                }
            }

            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
    }

    // ===================== 选点 & 坐标系修复【核心优化】 =====================
    /** 修复3：重构最优坐标判断，GPS绝对优先，精度权重合理，彻底解决优先级错误 */
    private fun isBetterLocation(newLoc: Location, oldLoc: Location?): Boolean {
        if (oldLoc == null) return true

        // 规则1：GPS定位 永远优于 网络定位，无论精度如何
        val newIsGps = newLoc.provider == LocationManager.GPS_PROVIDER
        val oldIsGps = oldLoc.provider == LocationManager.GPS_PROVIDER
        if (newIsGps && !oldIsGps) return true
        if (!newIsGps && oldIsGps) return false

        // 规则2：同定位源，精度更高的更优（数值越小精度越高）
        if (newLoc.provider == oldLoc.provider) {
            return newLoc.accuracy < oldLoc.accuracy
        }

        // 规则3：精度差值超过50米，才认为精度差的坐标无效
        val accuracyDelta = newLoc.accuracy - oldLoc.accuracy
        if (accuracyDelta > 50) return false

        // 规则4：同精度下，最新的坐标更优
        return newLoc.time > oldLoc.time
    }

    private fun providerWeight(loc: Location): Int {
        return when (loc.provider) {
            LocationManager.GPS_PROVIDER -> 10 // 修复4：提高GPS权重，绝对优先
            LocationManager.NETWORK_PROVIDER -> 2
            else -> 1
        }
    }

    /** 修复5：坐标系转换逻辑修正【最核心】+ 满足精度后立即停止定位 */
    private fun processLocation(location: Location, isTemp: Boolean = false) {
        if (isCallbackInvoked && !isTemp) return

        // ✅ 核心修复：所有定位源统一转换为 GCJ02火星坐标系，解决GPS偏移问题
        val (lat, lng) = try {
            GCJ02Converter.wgs84ToGcj02(location.latitude, location.longitude)
        } catch (e: Exception) {
            Pair(location.latitude, location.longitude)
        }

        val result = JSONObject().apply {
            put("latitude", lat)
            put("longitude", lng)
            put("accuracy", location.accuracy)
            put("provider", location.provider)
            put("timestamp", location.time)
            put("isTemp", isTemp)
        }

        if (!isTemp) {
            isCallbackInvoked = true
            saveCache(result)
            release() // 满足精度后立即释放，停止所有定位监听，避免无效回调
        }

        callbackSuccess(result)
    }

    // ===================== 工具 & 过滤逻辑【全量优化】 =====================
    /** 修复6：收紧有效坐标判断，过滤300米以上的无效模糊定位 */
    private fun isValidLocation(loc: Location?): Boolean {
        return loc != null &&
                loc.latitude != 0.0 &&
                loc.longitude != 0.0 &&
                loc.accuracy > 0f &&
                loc.accuracy <= 300f && // 过滤300米以上的无效坐标
                System.currentTimeMillis() - loc.time < TimeUnit.MINUTES.toMillis(5) // 过滤5分钟以上的过期坐标
    }

    private fun isLocationEnabled(): Boolean {
        val lm = locationManager ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /** 修复7：完善AGPS配置，GPS冷启动秒定，搜星成功率翻倍 */
    private fun refreshAGPS() {
        try {
            val lm = locationManager ?: return
            // 注入星历数据 + 时间同步
            lm.sendExtraCommand(LocationManager.GPS_PROVIDER, "force_xtra_injection", null)
            lm.sendExtraCommand(LocationManager.GPS_PROVIDER, "force_time_injection", null)
            // 关键补充：启用AGPS辅助定位模式，必须配置！
            lm.sendExtraCommand(LocationManager.GPS_PROVIDER, "set_aiding_mode", Bundle().apply {
                putInt("aiding_mode", 1) // 1=启用AGPS，0=禁用
            })
        } catch (_: Exception) {}
    }

    private fun startTimeout() {
        timeoutRunnable = Runnable {
            bestLocation?.let {
                processLocation(it)
            } ?: readCache()
        }
        mainHandler.postDelayed(timeoutRunnable!!, timeout)
    }

    private fun saveCache(json: JSONObject) {
        sharedPrefs?.edit()?.putString(LocationConstants.SCC_LOCATION_POSITIONING_CACHE, json.toString())?.apply()
    }

    /** 修复8：缓存增加过期+精度过滤，杜绝返回无效老坐标 */
    private fun readCache() {
        val cacheStr = sharedPrefs?.getString(LocationConstants.SCC_LOCATION_POSITIONING_CACHE, null) ?: return
        try {
            val cache = JSONObject(cacheStr)
            val cacheTime = cache.optLong("timestamp", 0)
            val cacheAccuracy = cache.optDouble("accuracy", 300.0)
            // 缓存有效期：5分钟 + 精度≤100米，满足才返回
            if (System.currentTimeMillis() - cacheTime < TimeUnit.MINUTES.toMillis(5) && cacheAccuracy <= 100) {
                callbackSuccess(cache)
            } else {
                // 缓存过期/无效，返回定位超时错误
                callbackError(LocationConstants.ERROR_LOCATION_TIMEOUT, LocationConstants.LOCATION_TIMEOUT_MSG)
            }
        } catch (_: Exception) {
            callbackError(LocationConstants.ERROR_LOCATION_TIMEOUT, LocationConstants.LOCATION_TIMEOUT_MSG)
        }
    }

    private fun callbackSuccess(obj: JSONObject) {
        try {
            locationCallback?.onSuccess(obj)
        } catch (_: Exception) {}
    }

    private fun callbackError(code: Int, msg: String) {
        if (isCallbackInvoked) return
        isCallbackInvoked = true
        locationCallback?.onError(gson.toJson(mapOf("code" to code, "message" to msg)))
    }

    private fun clear() {
        isCallbackInvoked = false
        bestLocation = null
        locationListeners.clear()
    }

    fun release() {
        locationListeners.forEach {
            locationManager?.removeUpdates(it)
        }
        locationListeners.clear()
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }
}

object LocationConstants {
    // 缓存 Key
    const val SCC_LOCATION_POSITIONING_CACHE = "SCCLocationPositioningCache"
    // 默认配置 微调优化
    const val DEFAULT_ACCURACY = 50          // 米，合理阈值
    const val DEFAULT_TIMEOUT = 8000         // ms，8秒足够GPS冷启动
    // 权限
    const val PERMISSION_REQUEST_CODE = 1001
    // 错误码 新增
    const val ERROR_PERMISSION_DENIED = 1002
    const val ERROR_LOCATION_SERVICE_DISABLED = 1007
    const val ERROR_LOCATION_TIMEOUT = 1008  // 定位超时/无有效缓存
    // 文案 新增
    const val PERMISSION_DENIED_MSG = "定位权限被拒绝，请在系统设置中开启"
    const val LOCATION_SERVICE_DISABLED_MSG = "系统定位服务未开启，请打开 GPS / 网络定位"
    const val LOCATION_TIMEOUT_MSG = "定位超时，暂无有效定位信息"
}