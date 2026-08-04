package com.jd.plugins.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jd.jdbridge.base.IBridgeCallback
import com.jd.plugins.ClosureRegistry
import com.jd.plugins.utils.GCJ02Converter
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.TimeUnit

class QXLocationManager private constructor(context: Context) {

    companion object {
        private const val TAG = "QXLocationManager"
        @Volatile private var INSTANCE: QXLocationManager? = null

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

    // 单例 + 单 callback 时，后来的定位请求会顶掉前一个的 callback，导致 H5 永久 pending；
    // 这里改为回调列表：在途请求的所有调用方共享同一次定位结果。
    private val locationCallbacks = mutableListOf<IBridgeCallback>()
    private var isLocating = false

    private var isCallbackInvoked = false
    private var bestLocation: Location? = null
    // 降级候选：实时回调里“坐标非空但精度超门槛被 isValidLocation 丢掉”的点单独留一份，
    // 只在超时兜底时使用。宁可返回一个粗坐标，也好过直接 1008 让业务拿不到位置。
    private var degradedLocation: Location? = null

    private var timeoutRunnable: Runnable? = null
    private var convergeRunnable: Runnable? = null

    private var targetAccuracy = LocationConstants.DEFAULT_ACCURACY
    private var timeout = LocationConstants.DEFAULT_TIMEOUT.toLong()

    private var currentParams: Map<String, Any>? = null

    private val sharedPrefs: SharedPreferences? by lazy {
        context?.getSharedPreferences("QXLocationPrefs", Context.MODE_PRIVATE)
    }

    // ===================== 对外入口 =====================
    /**
     * 定位状态（callback 列表 / bestLocation / 超时与收敛窗口）统一在主线程访问。
     * bridge 的 execute 不保证在主线程，入口这里做一次归一，避免跨线程竞态与可见性问题。
     */
    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    fun setCallback(callback: IBridgeCallback?) = runOnMain {
        callback?.let { if (!locationCallbacks.contains(it)) locationCallbacks.add(it) }
    }

    fun getLocation(activity: Activity, params: Map<String, Any>? = null) = runOnMain {
        startRequest(activity, params)
    }

    private fun startRequest(activity: Activity, params: Map<String, Any>?) {
        // 已有定位在途：本次调用直接搭车复用同一结果（callback 已在 setCallback 里入列），
        // 避免重复启动定位并把前一个请求的状态清掉。
        if (isLocating) return

        clear()

        currentParams = params

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

    /** 是否授予了“精确位置”(FINE)。仅授予“粗略位置”(COARSE)时系统只返回被模糊化的坐标。 */
    private fun hasPreciseLocation(): Boolean {
        val ctx = context ?: return false
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    /** 可接受的精度上限：精确定位收严到 300m；粗略定位放宽到公里级，避免一律判无效导致超时失败。 */
    private fun maxAcceptableAccuracy(): Float =
        if (hasPreciseLocation()) LocationConstants.FINE_MAX_ACCURACY else LocationConstants.COARSE_MAX_ACCURACY

    /**
     * 当前权限下真正可用的 provider。
     * GPS_PROVIDER 强制要求 ACCESS_FINE_LOCATION，仅授予“粗略位置”时无论是取 lastKnown 还是
     * requestLocationUpdates 都会抛 SecurityException，因此粗略模式必须完全不碰 GPS。
     * FUSED_PROVIDER(API 31+) 接受粗略权限，作为无 NETWORK_PROVIDER 机型的兜底。
     */
    private fun availableProviders(): List<String> {
        val providers = mutableListOf<String>()
        if (hasPreciseLocation()) {
            providers.add(LocationManager.GPS_PROVIDER)
            // 被动定位：不主动耗电，只“蹭”其它 App(高德/微信/地图等)刚产生的定位。
            // 国内无 GMS 机型室内自定位常失败，被动点往往是唯一能拿到的坐标。
            // PASSIVE_PROVIDER 需要 ACCESS_FINE_LOCATION，故仅在精确权限下加入。
            providers.add(LocationManager.PASSIVE_PROVIDER)
        }
        providers.add(LocationManager.NETWORK_PROVIDER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            providers.add(LocationConstants.FUSED_PROVIDER)
        }
        return providers.filter { isProviderUsable(it) }
    }

    /** provider 是否存在且已开启；未知 provider / 无权限一律视为不可用，避免异常打断整个定位流程。 */
    private fun isProviderUsable(provider: String): Boolean {
        val lm = locationManager ?: return false
        return try {
            lm.allProviders.contains(provider) && lm.isProviderEnabled(provider)
        } catch (e: Exception) {
            Log.w(TAG, "provider $provider unavailable: $e")
            false
        }
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
        registerPermissionClosure()
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LocationConstants.PERMISSION_REQUEST_CODE
        )
    }

    /**
     * 接收 QXWebViewActivity 透传的系统权限回调（与相机权限同一套 ClosureRegistry 机制）。
     * 不注册的话，用户在权限弹框点“允许”之后定位流程不会继续，H5 会一直等不到回调。
     */
    private fun registerPermissionClosure() {
        ClosureRegistry.register(LocationConstants.KEY_PERMISSION_CLOSURE, object : IBridgeCallback {
            override fun onSuccess(result: Any?) {
                val json = try {
                    JSONObject(result?.toString() ?: "")
                } catch (e: Exception) {
                    Log.w(TAG, "权限回调解析失败: $e")
                    null
                }
                if (json == null) {
                    handlePermissionDenied()
                    return
                }
                // ClosureRegistry.invoke 是一次性的：非本次定位的权限回调要原样重新注册，
                // 否则会把自己的监听消耗掉。
                if (json.optInt("requestCode", -1) != LocationConstants.PERMISSION_REQUEST_CODE) {
                    ClosureRegistry.register(LocationConstants.KEY_PERMISSION_CLOSURE, this)
                    return
                }
                val grantResults = json.optJSONArray("grantResults") ?: JSONArray()
                val granted = (0 until grantResults.length()).any {
                    grantResults.optInt(it, PackageManager.PERMISSION_DENIED) == PackageManager.PERMISSION_GRANTED
                }
                if (granted) startLocation() else handlePermissionDenied()
            }

            override fun onError(errMsg: String?) {
                handlePermissionDenied()
            }
        })
    }

    /** 宿主 App 若自行透传 Activity 的权限回调，可直接调用此方法。 */
    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) = runOnMain {
        if (requestCode == LocationConstants.PERMISSION_REQUEST_CODE) {
            val granted = grantResults.any { it == PackageManager.PERMISSION_GRANTED }
            if (granted) startLocation() else handlePermissionDenied()
        }
    }

    /**
     * 无权限一律回调一次失败结果，避免调用方悬挂。
     * 参数里的 requestPermission 只控制是否顺便跳到系统设置页，与回调本身解耦。
     */
    private fun handlePermissionDenied() {
        if (isCallbackInvoked) return
        isCallbackInvoked = true
        val result = buildEmptyResult(
            locationType = "failure",
            hasPermission = false,
            isEnable = isLocationEnabled()
        ).apply {
            put("code", LocationConstants.ERROR_PERMISSION_DENIED)
            put("msg", LocationConstants.PERMISSION_DENIED_MSG)
        }
        callbackSuccess(result)
    }

    // ===================== 定位主流程 =====================
    private fun startLocation() {
        isLocating = true

        if (!isLocationEnabled()) {
            callbackError(LocationConstants.ERROR_LOCATION_SERVICE_DISABLED, LocationConstants.LOCATION_SERVICE_DISABLED_MSG)
            return
        }

        // 仅授予“粗略位置”时，系统只返回模糊坐标(数百米~数公里)，按精确门槛会一直超时失败；
        // 这里放宽“可接受/可返回”的精度门槛，保证粗略模式也能返回一个坐标(带 preciseLocation=false 标记)。
        if (!hasPreciseLocation()) {
            targetAccuracy = maxOf(targetAccuracy, LocationConstants.COARSE_TARGET_ACCURACY)
        }

        tryReturnLastKnown()

        // 秒回优化：若预热到的 lastKnown 足够新鲜(1 分钟内)且精度达标，直接返回，
        // 不再等实时定位——用户刚用过地图类 App 或被动点很热时，这里能做到近乎瞬时。
        bestLocation?.let {
            if (System.currentTimeMillis() - it.time < LocationConstants.FRESH_CACHE_WINDOW &&
                it.accuracy <= targetAccuracy
            ) {
                processLocation(it)
                return
            }
        }

        val providers = availableProviders()
        if (providers.isEmpty()) {
            // 没有任何可用 provider（如粗略权限 + 无网络定位服务的机型）：
            // 有预热点就直接返回，否则按定位服务不可用失败，不必空等一次超时。
            val fallback = bestLocation
            if (fallback != null) processLocation(fallback)
            else callbackError(
                LocationConstants.ERROR_LOCATION_SERVICE_DISABLED,
                LocationConstants.LOCATION_SERVICE_DISABLED_MSG
            )
            return
        }

        refreshAGPS()

        startTimeout()

        startParallelRequest(providers)
    }

    /**
     * 预热 lastKnownLocation 作为候选点，加快超时兜底时的可用性；
     * 但不直接回调，避免前端总是先收到 locationType=cache。
     */
    private fun tryReturnLastKnown() {
        val best = availableProviders()
            .mapNotNull { safeLastKnownLocation(it) }
            .filter { isValidLocation(it) }
            // 临时结果优先“先给坐标”，允许精度比正式门槛稍宽一些，最终结果仍由实时定位兜底。
            // 弱信号下放宽时效与精度上限，让超时时更容易兜到 lastKnown，而不是直接判失败。
            .filter {
                System.currentTimeMillis() - it.time < TimeUnit.MINUTES.toMillis(5) &&
                        it.accuracy <= maxOf(targetAccuracy, 150)
            }
            .maxByOrNull { providerWeight(it) }

        best?.let {
            if (isBetterLocation(it, bestLocation)) {
                bestLocation = it
            }
        }
    }

    /**
     * 记录降级候选点：坐标非 (0,0)、精度为正即可（不卡精度上限、不卡时效）。
     * 按精度取最优保存，只在超时兜底且无正式结果时使用。
     */
    private fun keepDegraded(loc: Location) {
        if (loc.latitude == 0.0 || loc.longitude == 0.0 || loc.accuracy <= 0f) return
        val cur = degradedLocation
        if (cur == null || loc.accuracy < cur.accuracy || loc.time - cur.time > LocationConstants.SIGNIFICANT_TIME_DELTA) {
            degradedLocation = loc
        }
    }

    /**
     * 超时兜底的“放宽版” lastKnown 扫描：不卡正式精度门槛，时效放宽到 30 分钟，
     * 覆盖所有可用 provider（含 PASSIVE）。用于实时定位彻底没出点时，尽量给一个可用坐标。
     */
    private fun relaxedLastKnown(): Location? {
        return availableProviders()
            .mapNotNull { safeLastKnownLocation(it) }
            .filter {
                it.latitude != 0.0 && it.longitude != 0.0 && it.accuracy > 0f &&
                        System.currentTimeMillis() - it.time < TimeUnit.MINUTES.toMillis(30)
            }
            .maxByOrNull { providerWeight(it) }
    }

    private fun safeLastKnownLocation(provider: String): Location? {
        return try {
            locationManager?.getLastKnownLocation(provider)
        } catch (e: Exception) {
            Log.w(TAG, "getLastKnownLocation($provider) failed: $e")
            null
        }
    }

    private fun startParallelRequest(providers: List<String>) {
        val lm = locationManager ?: return

        providers.forEach { provider ->
            val listener = createListener()
            // 单个 provider 注册失败（权限不足 / ROM 不支持）不应影响其它 provider。
            try {
                // minTime/minDistance 用来过滤高频无效回调；
                // 统一在主线程回调，与超时/收敛窗口的 Handler 保持同一线程，避免竞态。
                lm.requestLocationUpdates(
                    provider,
                    500L,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
                locationListeners.add(listener)
            } catch (e: Exception) {
                Log.w(TAG, "requestLocationUpdates($provider) failed: $e")
            }
        }
    }

    private fun createListener(): LocationListener {
        return object : LocationListener {
            override fun onLocationChanged(location: Location) {
                // 先留一份降级候选：只要坐标非空、精度有效，即便超出正式精度门槛也存下来，
                // 供超时兜底使用（详见 degradedLocation 说明）。
                keepDegraded(location)

                if (!isValidLocation(location)) return

                if (isBetterLocation(location, bestLocation)) {
                    bestLocation = location
                }
                val best = bestLocation ?: return

                // 已达理想精度：直接返回最优点，无需再等收敛窗口。
                if (best.accuracy <= LocationConstants.PREFERRED_ACCURACY) {
                    processLocation(best)
                    return
                }

                // 只要拿到一个“有效点”(已过 isValidLocation，精度在上限内)就开启收敛窗口，
                // 不再要求先达到 targetAccuracy——弱信号下点精度长期达不到 80m 时，
                // 之前会一路空等到超时(20s)才回，这里改为拿到首个有效点后 CONVERGE_WINDOW 内择优返回。
                startConvergeWindow()
            }

            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
    }

    // ===================== 选点 & 坐标系 =====================
    /**
     * 最优坐标判定优先级：明显更新的坐标 > GPS > 网络；同源比精度；跨源允许 50m 以内的精度差；最后比时间。
     */
    private fun isBetterLocation(newLoc: Location, oldLoc: Location?): Boolean {
        if (oldLoc == null) return true

        // 时效优先：老点可能是几分钟前的 lastKnown，车载移动场景下已明显偏离当前位置，
        // 只要新点精度没有差太多就应该取代它。
        val timeDelta = newLoc.time - oldLoc.time
        if (timeDelta > LocationConstants.SIGNIFICANT_TIME_DELTA) {
            if (newLoc.accuracy <= oldLoc.accuracy + LocationConstants.STALE_ACCURACY_TOLERANCE) return true
        } else if (timeDelta < -LocationConstants.SIGNIFICANT_TIME_DELTA) {
            return false
        }

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
            LocationConstants.FUSED_PROVIDER -> 5
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
            GCJ02Converter.LatLng(location.latitude, location.longitude)
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
            put("preciseLocation", hasPreciseLocation())
            put("locationType", "new")
            // 行政区字段先占位，逆地理回填后再回调，保证字段结构始终一致。
            put("state", "")
            put("city", "")
            put("district", "")
            put("street", "")
            put("streetNum", "")
        }

        // needAddress 三态：不传 -> 省/市/区；true -> 完整地址；false -> 仅坐标。
        // 兼容 H5 把值序列化成字符串 "true"/"false" 的情况，避免快路径被静默绕过。
        val needAddress = coerceBoolean(currentParams?.get("needAddress"))

        isCallbackInvoked = true
        releaseInternal()
        if (needAddress == false) {
            saveCache(result)
            callbackSuccess(result)
            return
        }

        // 逆地理编码可能因网络/服务异常一直不回调，这里加超时兜底：
        // 到点就先把坐标发出去（地址字段留空），保证 H5 一定能收到结果。
        var addressEmitted = false
        val emit = { address: Address? ->
            if (!addressEmitted) {
                addressEmitted = true
                fillAddress(result, address, includeStreet = needAddress == true)
                saveCache(result)
                callbackSuccess(result)
            }
        }
        mainHandler.postDelayed({ emit(null) }, LocationConstants.GEOCODE_TIMEOUT.toLong())
        reverseGeocodeAsync(location.latitude, location.longitude) { address ->
            // 逆地理可能回在 worker 线程，统一切回主线程，保证 addressEmitted 只在单线程访问。
            mainHandler.post { emit(address) }
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

    /** 把桥参数里可能是 Boolean / "true"/"false" 字符串 / 数字的值归一为三态 Boolean?（无法识别返回 null）。 */
    private fun coerceBoolean(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is String -> value.trim().lowercase().let {
            when (it) {
                "true", "1" -> true
                "false", "0" -> false
                else -> null
            }
        }
        is Number -> value.toInt() != 0
        else -> null
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
            put("preciseLocation", hasPreciseLocation())
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
     *  - 精度在 (0, 精度上限] 之间（精确定位 300m，粗略定位放宽到公里级）；
     *  - 时间戳不超过 5 分钟，避免使用陈旧坐标。
     */
    private fun isValidLocation(loc: Location?): Boolean {
        return loc != null &&
                loc.latitude != 0.0 &&
                loc.longitude != 0.0 &&
                loc.accuracy > 0f &&
                loc.accuracy <= maxAcceptableAccuracy() &&
                System.currentTimeMillis() - loc.time < TimeUnit.MINUTES.toMillis(5)
    }

    /** 走 isProviderUsable 而非直接 isProviderEnabled：部分机型不存在某个 provider 时后者会抛异常。 */
    private fun isLocationEnabled(): Boolean {
        return isProviderUsable(LocationManager.GPS_PROVIDER) ||
                isProviderUsable(LocationManager.NETWORK_PROVIDER) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        isProviderUsable(LocationConstants.FUSED_PROVIDER))
    }

    /**
     * 触发 AGPS 辅助定位，加速 GPS 冷启动。
     * 这些 extra command 只有部分 ROM 支持，调用失败直接吞掉即可。
     */
    private fun refreshAGPS() {
        // 无精确权限时不可访问 GPS provider，直接跳过。
        if (!hasPreciseLocation()) return
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
            // 超时兜底链，尽量避免直接判失败：
            // 1) 实时最优点(含预热 lastKnown)；
            // 2) 降级候选(超精度门槛但坐标有效的实时点)；
            // 3) 放宽的 lastKnown 扫描(30 分钟内、不卡精度、含被动点)；
            // 4) 本地缓存；都没有才 1008。
            val fallback = bestLocation ?: degradedLocation ?: relaxedLastKnown()
            if (fallback != null) {
                processLocation(fallback)
            } else {
                readCache()
            }
        }
        mainHandler.postDelayed(timeoutRunnable!!, timeout)
    }

    /**
     * 收敛窗口：拿到第一个达到可接受精度的点后不立即返回，
     * 再等一小段时间让 GPS 精度收敛，窗口结束时回调期间的最优点。
     * 若期间精度已达理想值，会在 onLocationChanged 里提前返回。
     */
    private fun startConvergeWindow() {
        if (convergeRunnable != null) return
        convergeRunnable = Runnable {
            convergeRunnable = null
            bestLocation?.let { processLocation(it) }
        }
        mainHandler.postDelayed(convergeRunnable!!, LocationConstants.CONVERGE_WINDOW.toLong())
    }

    private fun saveCache(json: JSONObject) {
        sharedPrefs?.edit()?.putString(LocationConstants.SCC_LOCATION_POSITIONING_CACHE, json.toString())?.apply()
    }

    /**
     * 超时兜底：读取本地缓存。
     * 缓存必须同时满足「5 分钟内」+「精度不超过当前权限下的可接受上限」才返回，否则按超时失败处理。
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
            if (System.currentTimeMillis() - cacheTime < TimeUnit.MINUTES.toMillis(5) && cacheAccuracy <= maxAcceptableAccuracy().toDouble()) {
                cache.put("locationType", "cache")
                cache.put("hasPermission", hasLocationPermission())
                cache.put("isEnable", isLocationEnabled())
                cache.put("preciseLocation", hasPreciseLocation())
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

    /**
     * 统一出口：把结果分发给本次定位的所有调用方，并结束在途状态。
     * 单个 callback 抛异常不影响其它调用方。
     */
    private fun callbackSuccess(obj: JSONObject) {
        val callbacks = locationCallbacks.toList()
        locationCallbacks.clear()
        isLocating = false
        callbacks.forEach {
            try {
                it.onSuccess(obj)
            } catch (e: Exception) {
                Log.w(TAG, "location callback failed: $e")
            }
        }
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
        callbackSuccess(failure)
    }

    private fun clear() {
        releaseInternal()
        // 上一笔请求可能留下未触发的权限监听（用户始终没理会弹框），这里一并清掉。
        ClosureRegistry.remove(LocationConstants.KEY_PERMISSION_CLOSURE)
        isCallbackInvoked = false
        bestLocation = null
        degradedLocation = null
        targetAccuracy = LocationConstants.DEFAULT_ACCURACY
        timeout = LocationConstants.DEFAULT_TIMEOUT.toLong()
    }

    fun release() = runOnMain { releaseInternal() }

    private fun releaseInternal() {
        locationListeners.forEach {
            locationManager?.removeUpdates(it)
        }
        locationListeners.clear()
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
        convergeRunnable?.let { mainHandler.removeCallbacks(it) }
        convergeRunnable = null
    }
}

object LocationConstants {
    // SharedPreferences Key
    const val SCC_LOCATION_POSITIONING_CACHE = "SCCLocationPositioningCache"
    const val KEY_PERMISSION_REQUESTED = "hasRequestedLocationPermission"

    // QXWebViewActivity 透传系统权限回调所用的 ClosureRegistry key（与相机权限共用同一事件源）。
    const val KEY_PERMISSION_CLOSURE = "onRequestPermissionsResult"

    // 默认定位参数：优先首包速度；业务需要更高精度时可由前端显式传 accuracy/timeout。
    // 原生 GPS 冷启动/室内首个有效点常需 10s 以上，默认给足 12s 兜底，业务可显式传 timeout 覆盖。
    const val DEFAULT_ACCURACY = 80
    // 达到该精度即视为理想结果，立即返回，不再等待收敛窗口。
    const val PREFERRED_ACCURACY = 25
    // GPS 冷启动/室内首个有效点常需 15s+，默认给足 20s 兜底；拿到理想点会提前返回，
    // 只影响最坏情况。业务可显式传 timeout 覆盖。
    const val DEFAULT_TIMEOUT = 20000
    // 收敛窗口：拿到可接受精度后再等这么久，让 GPS 精度进一步收敛，提升返回坐标的准确度。
    const val CONVERGE_WINDOW = 2500
    // 逆地理编码超时：到点仍未返回地址就先把坐标发出去，避免 H5 永久等待。
    // 压到 2s：坐标是核心，地址拿不到就先出坐标，减少“定位很慢”的观感。
    const val GEOCODE_TIMEOUT = 2000

    // 预热 lastKnown 的“可秒回”新鲜度窗口：1 分钟内的点视为足够新，直接返回不等实时定位。
    const val FRESH_CACHE_WINDOW = 60_000L

    // 选点时效：新坐标比候选点新超过该阈值时优先采用（容忍一定的精度回退），避免返回陈旧坐标。
    const val SIGNIFICANT_TIME_DELTA = 30_000L
    const val STALE_ACCURACY_TOLERANCE = 100f

    // 精度上限：精确定位(FINE)门槛严；仅授“粗略位置”(COARSE)时系统只给模糊坐标，需放宽到公里级。
    // LocationManager.FUSED_PROVIDER 常量为 API 31 新增，这里用字面量避免 InlinedApi 告警。
    const val FUSED_PROVIDER = "fused"

    const val FINE_MAX_ACCURACY = 300f      // 精确定位：有效坐标的精度上限
    const val COARSE_MAX_ACCURACY = 5000f   // 粗略定位：有效坐标的精度上限
    const val COARSE_TARGET_ACCURACY = 3000 // 粗略定位：达到即可返回的目标精度

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