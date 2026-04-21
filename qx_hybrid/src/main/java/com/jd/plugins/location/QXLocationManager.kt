package com.jd.plugins.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.jd.jdbridge.base.IBridgeCallback
import com.jd.plugins.utils.GCJ02Converter
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.Locale
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
    private val workerThread: HandlerThread by lazy {
        HandlerThread("QXLocationWorker").apply { start() }
    }
    private val workerHandler: Handler by lazy { Handler(workerThread.looper) }
    private val geocoder: Geocoder? by lazy {
        context?.let { Geocoder(it, Locale.CHINA) }
    }

    private var locationCallback: IBridgeCallback? = null

    private var isCallbackInvoked = false
    private var bestLocation: Location? = null
    private var hasFreshLocation = false

    private var timeoutRunnable: Runnable? = null

    private var targetAccuracy = LocationConstants.DEFAULT_ACCURACY
    private var timeout = LocationConstants.DEFAULT_TIMEOUT.toLong()

    private var activityRef: WeakReference<Activity>? = null
    private var currentParams: Map<String, Any>? = null

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

        activityRef = WeakReference(activity)
        currentParams = params

        // 每次请求都回到默认值，避免上一笔调用的严格参数串到下一笔请求。
        targetAccuracy = LocationConstants.DEFAULT_ACCURACY
        timeout = LocationConstants.DEFAULT_TIMEOUT.toLong()

        params?.let {
            targetAccuracy = maxOf(1, (it["accuracy"] as? Number)?.toInt() ?: targetAccuracy)
            timeout = maxOf(1000L, (it["timeout"] as? Number)?.toLong() ?: timeout)
        }

        if (!hasLocationPermission()) {
            // 已被永久拒绝时不再弹系统权限框，避免打扰用户，直接回落到失败回调。
            if (isPermissionPermanentlyDenied(activity)) {
                handlePermissionDenied()
            } else {
                requestPermission(activity)
            }
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

    /**
     * 判断定位权限是否已被永久拒绝（用户勾选过"不再询问"或系统策略禁止）。
     * 判定条件：曾经请求过一次，且当前 shouldShowRequestPermissionRationale 返回 false。
     */
    private fun isPermissionPermanentlyDenied(activity: Activity): Boolean {
        val requestedOnce = sharedPrefs?.getBoolean(LocationConstants.KEY_PERMISSION_REQUESTED, false) ?: false
        if (!requestedOnce) return false
        val fineRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            activity, Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarseRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            activity, Manifest.permission.ACCESS_COARSE_LOCATION
        )
        return !fineRationale && !coarseRationale
    }

    private fun requestPermission(activity: Activity) {
        sharedPrefs?.edit()?.putBoolean(LocationConstants.KEY_PERMISSION_REQUESTED, true)?.apply()
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
        else handlePermissionDenied()
    }

    /**
     * 无权限一律回调一次失败结果，避免调用方悬挂。
     * 参数里的 requestPermission 只控制是否顺便跳到系统设置页，与回调本身解耦。
     */
    private fun handlePermissionDenied() {
        if (isCallbackInvoked) return
        isCallbackInvoked = true

        val requestPermission = (currentParams?.get("requestPermission") as? Boolean) ?: false

        val result = buildEmptyResult(
            locationType = "failure",
            hasPermission = false,
            isEnable = isLocationEnabled()
        ).apply {
            put("msg", LocationConstants.PERMISSION_DENIED_MSG)
        }
        callbackSuccess(result)

        if (requestPermission) {
            activityRef?.get()?.let { openAppSettings(it) }
        }
    }

    private fun openAppSettings(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", activity.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openAppSettings failed: $e")
        }
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

    /**
     * 预热 lastKnownLocation 作为候选点，加快超时兜底时的可用性；
     * 但不直接回调，避免前端总是先收到 locationType=cache。
     */
    private fun tryReturnLastKnown() {
        val gps = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val net = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

        val best = listOfNotNull(gps, net)
            .filter { isValidLocation(it) }
            // 临时结果优先“先给坐标”，允许精度比正式门槛稍宽一些，最终结果仍由实时定位兜底。
            .filter {
                System.currentTimeMillis() - it.time < TimeUnit.SECONDS.toMillis(120) &&
                        it.accuracy <= maxOf(targetAccuracy, 80)
            }
            .maxByOrNull { providerWeight(it) }

        best?.let {
            if (isBetterLocation(it, bestLocation)) {
                bestLocation = it
            }
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

            // minTime/minDistance 用来过滤高频无效回调；Looper 复用调用方线程。
            lm.requestLocationUpdates(
                provider,
                500L,
                0f,
                listener,
                Looper.myLooper()
            )
        }
    }

    private fun createListener(): LocationListener {
        return object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!isValidLocation(location)) return

                hasFreshLocation = true
                if (isBetterLocation(location, bestLocation)) {
                    bestLocation = location
                }

                // 统一按目标精度回调，避免 GPS 初始漂移点过早返回，导致 street 落到邻近道路。
                if (location.accuracy <= targetAccuracy) {
                    processLocation(location)
                }
            }

            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
    }

    // ===================== 选点 & 坐标系 =====================
    /**
     * 最优坐标判定优先级：GPS > 网络；同源比精度；跨源允许 50m 以内的精度差；最后比时间。
     */
    private fun isBetterLocation(newLoc: Location, oldLoc: Location?): Boolean {
        if (oldLoc == null) return true

        val newIsGps = newLoc.provider == LocationManager.GPS_PROVIDER
        val oldIsGps = oldLoc.provider == LocationManager.GPS_PROVIDER
        if (newIsGps && !oldIsGps) return true
        if (!newIsGps && oldIsGps) return false

        if (newLoc.provider == oldLoc.provider) {
            return newLoc.accuracy < oldLoc.accuracy
        }

        val accuracyDelta = newLoc.accuracy - oldLoc.accuracy
        if (accuracyDelta > 50) return false

        return newLoc.time > oldLoc.time
    }

    /** lastKnownLocation 选点时的权重：GPS 绝对优先。 */
    private fun providerWeight(loc: Location): Int {
        return when (loc.provider) {
            LocationManager.GPS_PROVIDER -> 10
            LocationManager.NETWORK_PROVIDER -> 2
            else -> 1
        }
    }

    /**
     * 核心：组装与 iOS 对齐的结果 JSON。
     * 坐标统一由 WGS84 转换为 GCJ02（解决国内 GPS 偏移）；
     * 是否返回地址字段由前端参数决定：
     * - 默认返回省/市/区；
     * - 显式传 needAddress=true 时再补充 street / streetNum。
     */
    private fun processLocation(location: Location) {
        if (isCallbackInvoked) return

        val (lat, lng) = try {
            GCJ02Converter.wgs84ToGcj02(location.latitude, location.longitude)
        } catch (e: Exception) {
            Pair(location.latitude, location.longitude)
        }

        val speed = if (location.hasSpeed()) location.speed.toDouble() else -1.0
        val altitude = if (location.hasAltitude()) location.altitude else 0.0

        val result = JSONObject().apply {
            put("latitude", lat)
            put("longitude", lng)
            put("accuracy", location.accuracy.toDouble())
            put("altitude", altitude)
            put("speed", speed)
            put("timestamp", location.time.toString())
            put("geopoint", String.format(Locale.US, "%.6f,%.6f", lat, lng))
            put("gcoord", "GCJ02")
            put("hasPermission", true)
            put("isEnable", true)
            put("locationType", "new")
            // 行政区字段先占位，逆地理回填后再回调，保证字段结构始终一致。
            put("state", "")
            put("city", "")
            put("district", "")
            put("street", "")
            put("streetNum", "")
        }

        // needAddress 三态：不传 -> 省/市/区；true -> 完整地址；false -> 仅坐标。
        val needAddress = currentParams?.get("needAddress") as? Boolean

        isCallbackInvoked = true
        release()
        if (needAddress == false) {
            saveCache(result)
            callbackSuccess(result)
            return
        }

        reverseGeocodeAsync(location.latitude, location.longitude) { address ->
            fillAddress(result, address, includeStreet = needAddress == true)
            saveCache(result)
            callbackSuccess(result)
        }
    }

    private fun reverseGeocodeAsync(
        queryLat: Double,
        queryLng: Double,
        onDone: (Address?) -> Unit
    ) {
        val gc = geocoder
        if (gc == null || !Geocoder.isPresent()) {
            onDone(null)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                gc.getFromLocation(queryLat, queryLng, 1) { list ->
                    onDone(list.firstOrNull())
                }
                return
            } catch (e: Exception) {
                Log.w(TAG, "geocoder async failed: $e")
            }
        }

        workerHandler.post {
            val address = try {
                @Suppress("DEPRECATION")
                gc.getFromLocation(queryLat, queryLng, 1)?.firstOrNull()
            } catch (e: Exception) {
                Log.w(TAG, "geocoder sync failed: $e")
                null
            }
            onDone(address)
        }
    }

    private fun fillAddress(result: JSONObject, address: Address?, includeStreet: Boolean) {
        address ?: return
        try {
            result.put("state", address.adminArea ?: "")
            result.put("city", address.locality ?: address.subAdminArea ?: "")
            result.put("district", address.subLocality ?: "")
            if (!includeStreet) return
            val streetNum = address.subThoroughfare.orEmpty()
            val streetName = address.thoroughfare.orEmpty()
            val featureName = address.featureName.orEmpty()
            val street = when {
                streetName.isNotEmpty() && streetNum.isNotEmpty() -> streetName + streetNum
                streetName.isNotEmpty() -> streetName
                featureName.isNotEmpty() -> featureName
                else -> address.getAddressLine(0).orEmpty()
            }
            result.put("street", street)
            result.put("streetNum", streetNum)
        } catch (_: Exception) {}
    }

    private fun buildEmptyResult(
        locationType: String,
        hasPermission: Boolean,
        isEnable: Boolean
    ): JSONObject {
        return JSONObject().apply {
            put("latitude", 0.0)
            put("longitude", 0.0)
            put("accuracy", 0.0)
            put("altitude", 0.0)
            put("speed", -1)
            put("timestamp", System.currentTimeMillis().toString())
            put("geopoint", "0.000000,0.000000")
            put("gcoord", "GCJ02")
            put("hasPermission", hasPermission)
            put("isEnable", isEnable)
            put("locationType", locationType)
            put("state", "")
            put("city", "")
            put("district", "")
            put("street", "")
            put("streetNum", "")
        }
    }

    // ===================== 工具 & 过滤 =====================
    /**
     * 有效坐标标准：
     *  - 非 (0,0) 空坐标；
     *  - 精度在 (0, 300m] 之间，避免返回极度模糊的定位；
     *  - 时间戳不超过 5 分钟，避免使用陈旧坐标。
     */
    private fun isValidLocation(loc: Location?): Boolean {
        return loc != null &&
                loc.latitude != 0.0 &&
                loc.longitude != 0.0 &&
                loc.accuracy > 0f &&
                loc.accuracy <= 300f &&
                System.currentTimeMillis() - loc.time < TimeUnit.MINUTES.toMillis(5)
    }

    private fun isLocationEnabled(): Boolean {
        val lm = locationManager ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * 触发 AGPS 辅助定位，加速 GPS 冷启动。
     * 这些 extra command 只有部分 ROM 支持，调用失败直接吞掉即可。
     */
    private fun refreshAGPS() {
        try {
            val lm = locationManager ?: return
            lm.sendExtraCommand(LocationManager.GPS_PROVIDER, "force_xtra_injection", null)
            lm.sendExtraCommand(LocationManager.GPS_PROVIDER, "force_time_injection", null)
            lm.sendExtraCommand(LocationManager.GPS_PROVIDER, "set_aiding_mode", Bundle().apply {
                putInt("aiding_mode", 1)
            })
        } catch (_: Exception) {}
    }

    private fun startTimeout() {
        timeoutRunnable = Runnable {
            if (hasFreshLocation) bestLocation?.let {
                processLocation(it)
            } else {
                readCache()
            }
        }
        mainHandler.postDelayed(timeoutRunnable!!, timeout)
    }

    private fun saveCache(json: JSONObject) {
        sharedPrefs?.edit()?.putString(LocationConstants.SCC_LOCATION_POSITIONING_CACHE, json.toString())?.apply()
    }

    /**
     * 超时兜底：读取本地缓存。
     * 缓存必须同时满足「5 分钟内」+「精度 ≤ 100m」才返回，否则按超时失败处理。
     */
    private fun readCache() {
        val cacheStr = sharedPrefs?.getString(LocationConstants.SCC_LOCATION_POSITIONING_CACHE, null)
        if (cacheStr.isNullOrEmpty()) {
            callbackError(LocationConstants.ERROR_LOCATION_TIMEOUT, LocationConstants.LOCATION_TIMEOUT_MSG)
            return
        }
        try {
            val cache = JSONObject(cacheStr)
            // 兼容历史缓存：timestamp 可能是 Long，也可能是字符串。
            val cacheTime = cache.optLong("timestamp", cache.optString("timestamp", "0").toLongOrNull() ?: 0)
            val cacheAccuracy = cache.optDouble("accuracy", 300.0)
            if (System.currentTimeMillis() - cacheTime < TimeUnit.MINUTES.toMillis(5) && cacheAccuracy <= 100) {
                cache.put("locationType", "cache")
                cache.put("hasPermission", hasLocationPermission())
                cache.put("isEnable", isLocationEnabled())
                if (isCallbackInvoked) return
                isCallbackInvoked = true
                callbackSuccess(cache)
            } else {
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

    /**
     * 失败统一走 onSuccess 返回 locationType=failure 的完整结构，
     * 与 iOS / handlePermissionDenied 保持一致；前端用 locationType / hasPermission / isEnable 判分支。
     */
    private fun callbackError(code: Int, msg: String) {
        if (isCallbackInvoked) return
        isCallbackInvoked = true
        val failure = buildEmptyResult(
            locationType = "failure",
            hasPermission = hasLocationPermission(),
            isEnable = isLocationEnabled()
        ).apply {
            put("code", code)
            put("msg", msg)
        }
        try {
            locationCallback?.onSuccess(failure)
        } catch (_: Exception) {}
    }

    private fun clear() {
        release()
        isCallbackInvoked = false
        bestLocation = null
        hasFreshLocation = false
        targetAccuracy = LocationConstants.DEFAULT_ACCURACY
        timeout = LocationConstants.DEFAULT_TIMEOUT.toLong()
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
    // SharedPreferences Key
    const val SCC_LOCATION_POSITIONING_CACHE = "SCCLocationPositioningCache"
    const val KEY_PERMISSION_REQUESTED = "hasRequestedLocationPermission"

    // 默认定位参数：优先首包速度；业务需要更高精度时可由前端显式传 accuracy/timeout。
    const val DEFAULT_ACCURACY = 80
    const val DEFAULT_TIMEOUT = 6000

    // 权限请求码
    const val PERMISSION_REQUEST_CODE = 1001

    // 业务错误码（与前端 / iOS 约定）
    const val ERROR_PERMISSION_DENIED = 1002
    const val ERROR_LOCATION_SERVICE_DISABLED = 1007
    const val ERROR_LOCATION_TIMEOUT = 1008

    // 失败文案
    const val PERMISSION_DENIED_MSG = "定位权限被拒绝，请在系统设置中开启"
    const val LOCATION_SERVICE_DISABLED_MSG = "系统定位服务未开启，请打开 GPS / 网络定位"
    const val LOCATION_TIMEOUT_MSG = "定位超时，暂无有效定位信息"
}