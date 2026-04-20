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
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
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

    // 逆地理在部分机型属于阻塞 IO，放到独立 HandlerThread 跑，避免阻塞主线程。
    private val workerThread: HandlerThread by lazy {
        HandlerThread("QXLocationWorker").apply { start() }
    }
    private val workerHandler: Handler by lazy { Handler(workerThread.looper) }
    private val geocoder: Geocoder? by lazy {
        context?.let { Geocoder(it, Locale.CHINA) }
    }

    private var amapLocationClient: AMapLocationClient? = null
    private var locationCallback: IBridgeCallback? = null
    private var permissionCallback: ((Boolean) -> Unit)? = null

    private var isCallbackInvoked = false
    private var bestAmapLocation: AMapLocation? = null
    private var bestLocation: Location? = null
    private var hasStartedSystemFallback = false

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

        params?.let {
            targetAccuracy = (it["accuracy"] as? Number)?.toInt() ?: targetAccuracy
            timeout = (it["timeout"] as? Number)?.toLong() ?: timeout
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

        startTimeout()
        if (shouldUseAmapLocation()) {
            startAmapLocation()
        } else {
            startSystemLocation()
        }
    }

    private fun startSystemLocation() {
        tryReturnLastKnown()
        refreshAGPS()
        startParallelRequest()
    }

    /**
     * 尝试用 lastKnownLocation 先快速给一份临时结果（isTemp=true），
     * 不会终止后续实时定位，确保最终仍会回调一份更精准的坐标。
     */
    private fun tryReturnLastKnown() {
        val gps = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val net = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

        val best = listOfNotNull(gps, net)
            .filter { isValidLocation(it) }
            // 历史点也要满足当前目标精度，避免临时结果先把 street 落到附近道路上。
            .filter {
                System.currentTimeMillis() - it.time < TimeUnit.SECONDS.toMillis(30) &&
                        it.accuracy <= targetAccuracy
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

            // minTime/minDistance 用来过滤高频无效回调；Looper 复用调用方线程。
            lm.requestLocationUpdates(
                provider,
                1000L,
                1f,
                listener,
                Looper.myLooper()
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

    private fun startAmapLocation() {
        val ctx = context ?: run {
            startSystemFallback()
            return
        }
        try {
            AMapLocationClient.updatePrivacyShow(ctx, true, true)
            AMapLocationClient.updatePrivacyAgree(ctx, true)

            destroyAmapLocationClient()
            val option = AMapLocationClientOption().apply {
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                isNeedAddress = true
                isMockEnable = false
                isOnceLocation = false
                interval = 1000L
                httpTimeOut = timeout
            }

            amapLocationClient = AMapLocationClient(ctx).apply {
                setLocationOption(option)
                setLocationListener(amapLocationListener)
                startLocation()
            }
        } catch (e: Exception) {
            Log.e(TAG, "startAmapLocation failed: $e")
            startSystemFallback()
        }
    }

    private val amapLocationListener = AMapLocationListener { amapLocation ->
        if (!isValidAmapLocation(amapLocation)) {
            if (amapLocation != null) {
                Log.w(
                    TAG,
                    "amap location invalid, code=${amapLocation.errorCode}, info=${amapLocation.errorInfo}"
                )
            }
            startSystemFallback()
            return@AMapLocationListener
        }

        if (isBetterAmapLocation(amapLocation!!, bestAmapLocation)) {
            bestAmapLocation = amapLocation
        }

        if (amapLocation.accuracy <= targetAccuracy) {
            processAmapLocation(amapLocation)
        }
    }

    private fun startSystemFallback() {
        if (hasStartedSystemFallback || isCallbackInvoked) return
        hasStartedSystemFallback = true
        startSystemLocation()
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

    private fun isBetterAmapLocation(newLoc: AMapLocation, oldLoc: AMapLocation?): Boolean {
        if (oldLoc == null) return true
        if (newLoc.accuracy < oldLoc.accuracy) return true
        if (newLoc.accuracy > oldLoc.accuracy + 20f) return false
        return newLoc.time > oldLoc.time
    }

    /**
     * 核心：组装与 iOS 对齐的结果 JSON。
     * 坐标统一由 WGS84 转换为 GCJ02（解决国内 GPS 偏移）；
     * isTemp=true 表示这份结果来自 lastKnownLocation，不会终止实时监听，也不会写缓存。
     */
    private fun processLocation(location: Location, isTemp: Boolean = false) {
        if (isCallbackInvoked && !isTemp) return

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
            put("locationType", if (isTemp) "cache" else "new")
            // 行政区字段先占位，逆地理回填后再回调，保证字段结构始终一致。
            put("state", "")
            put("city", "")
            put("district", "")
            put("street", "")
            put("streetNum", "")
        }

        if (!isTemp) {
            isCallbackInvoked = true
            release()
        }

        // 返回给前端的是 GCJ02，这里也使用同一坐标系做逆地理，避免国内场景街道偏移。
        reverseGeocodeAsync(lat, lng) { address ->
            fillAddress(result, address)
            if (!isTemp) saveCache(result)
            callbackSuccess(result)
        }
    }

    private fun processAmapLocation(location: AMapLocation, isTemp: Boolean = false) {
        if (isCallbackInvoked && !isTemp) return

        val speed = if (location.hasSpeed()) location.speed.toDouble() else -1.0
        val altitude = if (location.hasAltitude()) location.altitude else 0.0
        val timestamp = if (location.time > 0) location.time else System.currentTimeMillis()

        val result = JSONObject().apply {
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            put("accuracy", location.accuracy.toDouble())
            put("altitude", altitude)
            put("speed", speed)
            put("timestamp", timestamp.toString())
            put("geopoint", String.format(Locale.US, "%.6f,%.6f", location.latitude, location.longitude))
            put("gcoord", "GCJ02")
            put("hasPermission", true)
            put("isEnable", true)
            put("locationType", if (isTemp) "cache" else "new")
            put("state", "")
            put("city", "")
            put("district", "")
            put("street", "")
            put("streetNum", "")
        }

        fillAmapAddress(result, location)

        if (!isTemp) {
            isCallbackInvoked = true
            release()
            saveCache(result)
        }

        callbackSuccess(result)
    }

    /**
     * 逆地理：Android 13+ 用系统异步 API，其余走 worker 线程同步 API。
     * Geocoder 在部分机型/无网络时会失败或超时，失败时传 null，由上层兜底为空字段。
     */
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
                // 异步 API 异常时，降级为下面的同步实现。
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

    private fun fillAddress(result: JSONObject, address: Address?) {
        address ?: return
        try {
            result.put("state", address.adminArea ?: "")
            result.put("city", address.locality ?: address.subAdminArea ?: "")
            result.put("district", address.subLocality ?: "")
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

    private fun fillAmapAddress(result: JSONObject, location: AMapLocation) {
        try {
            result.put("state", location.province.orEmpty())
            result.put("city", location.city.orEmpty())
            result.put("district", location.district.orEmpty())
            val streetNum = location.streetNum.orEmpty()
            val streetName = location.street.orEmpty()
            val road = location.road.orEmpty()
            val poiName = location.poiName.orEmpty()
            val aoiName = location.aoiName.orEmpty()
            val formattedAddress = location.address.orEmpty()
            val street = when {
                streetName.isNotEmpty() && streetNum.isNotEmpty() -> streetName + streetNum
                streetName.isNotEmpty() -> streetName
                road.isNotEmpty() -> road
                poiName.isNotEmpty() -> poiName
                aoiName.isNotEmpty() -> aoiName
                else -> formattedAddress
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

    private fun isValidAmapLocation(loc: AMapLocation?): Boolean {
        return loc != null &&
                loc.errorCode == 0 &&
                loc.latitude != 0.0 &&
                loc.longitude != 0.0 &&
                loc.accuracy > 0f &&
                loc.accuracy <= 300f
    }

    private fun shouldUseAmapLocation(): Boolean {
        val apiKey = getAmapApiKey()
        if (apiKey.isBlank()) {
            Log.w(TAG, "AMap api key missing, fallback to system location")
            return false
        }
        return true
    }

    private fun getAmapApiKey(): String {
        val ctx = context ?: return ""
        return try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.packageManager.getApplicationInfo(
                    ctx.packageName,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                ctx.packageManager.getApplicationInfo(ctx.packageName, PackageManager.GET_META_DATA)
            }
            appInfo.metaData?.getString("com.amap.api.v2.apikey").orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "read AMap api key failed: $e")
            ""
        }
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
            bestAmapLocation?.let {
                processAmapLocation(it)
            } ?: bestLocation?.let {
                processLocation(it)
            } ?: readCache()
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
        isCallbackInvoked = false
        bestAmapLocation = null
        bestLocation = null
        hasStartedSystemFallback = false
        destroyAmapLocationClient()
        locationListeners.clear()
    }

    fun release() {
        destroyAmapLocationClient()
        locationListeners.forEach {
            locationManager?.removeUpdates(it)
        }
        locationListeners.clear()
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    private fun destroyAmapLocationClient() {
        try {
            amapLocationClient?.stopLocation()
            amapLocationClient?.onDestroy()
        } catch (_: Exception) {}
        amapLocationClient = null
    }
}

object LocationConstants {
    // SharedPreferences Key
    const val SCC_LOCATION_POSITIONING_CACHE = "SCCLocationPositioningCache"
    const val KEY_PERMISSION_REQUESTED = "hasRequestedLocationPermission"

    // 默认定位参数：20m 进一步偏向街道级精度；8s 提高高精度命中率。
    const val DEFAULT_ACCURACY = 20
    const val DEFAULT_TIMEOUT = 8000

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