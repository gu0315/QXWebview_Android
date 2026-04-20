package com.jd.plugins

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jd.jdbridge.base.IBridgeCallback
import com.jd.jdbridge.base.IBridgePlugin
import com.jd.jdbridge.base.IBridgeWebView
import com.jd.plugins.location.QXLocationManager
import com.jd.plugins.sacn.QRScannerActivity
import com.jd.plugins.sacn.ScanQrBridge
import com.jd.plugins.utils.DeviceUtils
import org.json.JSONObject
import android.os.Handler
import android.os.Looper
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.net.HttpURLConnection
import androidx.core.content.FileProvider
import android.os.StrictMode
import android.provider.Settings
import android.webkit.MimeTypeMap
import com.jd.hybrid.QXWebViewActivity
import com.jd.plugins.utils.OpenMapAppUtils

/**
 * QX基础插件类
 * 实现IBridgePlugin接口，提供常用功能如扫码、返回、关闭WebView、获取设备信息和定位
 */
class QXBasePlugin : IBridgePlugin {
    private val TAG = "QXBasePlugin"
    val NAME = "QXBasePlugin"

    // 保存权限请求回调的映射
    private val permissionCallbackMap = mutableMapOf<Int, (Boolean) -> Unit>()
    private var context: Context? = null

    /**
     * 初始化插件
     * @param webView WebView实例
     * @param context 上下文
     */
    fun init(webView: IBridgeWebView?, context: Context?) {
        this.context = context
    }

    /**
     * 销毁插件，释放资源
     */
    fun destroy() {
        context = null
        permissionCallbackMap.clear()
    }


    /**
     * 执行JS调用的方法
     * @param webView WebView实例
     * @param method 方法名
     * @param params 参数
     * @param callback 回调
     * @return 是否处理了该方法
     */
    override fun execute(
        webView: IBridgeWebView?,
        method: String?,
        params: String?,
        callback: IBridgeCallback?
    ): Boolean {
        when (method) {
            "scanQRCode" -> {
                handleScanQRCode(webView, params, callback)
                return true
            }

            "goBack" -> {
                handleBackClick(webView, callback)
                return true
            }

            "closeWebView" -> {
                handleCloseWebView(webView, callback)
                return true
            }

            "getDeviceInfo" -> {
                handleGetDeviceInfo(webView, callback)
                return true
            }

            "location" -> {
                handleLocationRequest(webView, params, callback)
                return true
            }

            "downloadAndOpenFile" -> {
                handleDownloadAndOpenFile(webView, params, callback)
                return true
            }

            "openMap" -> {
                handleOpenMap(webView, params, callback)
                return true
            }

            "setNavigationBarStyle" -> {
                handleSetNavigationBarStyle(webView, params, callback)
                return true
            }

            "openWebView" -> {
                handleOpenWebView(webView, params, callback)
                return true
            }

            "openUrl" -> {
                handleOpenUrl(webView, params, callback)
                return true
            }
            else -> {
                return false
            }
        }
    }

    /**
     * 获取设备信息
     */
    private fun handleGetDeviceInfo(webView: IBridgeWebView?, callback: IBridgeCallback?) {
        try {
            val systemInfo = JSONObject()
            // 原有基础字段
            systemInfo.put("model", Build.MODEL)          // 设备型号 例：MI 14、Mate60 Pro
            systemInfo.put("brand", Build.BRAND)          // 设备品牌 例：xiaomi、huawei
            systemInfo.put("systemVersion", Build.VERSION.RELEASE) // Android系统版本 例：14、13
            systemInfo.put("systemSDK", Build.VERSION.SDK_INT)      // Android SDK版本 例：34、33
            systemInfo.put("deviceModel", Build.MANUFACTURER)       // 设备厂商 例：Xiaomi、HUAWEI
            systemInfo.put("appPlatform", "android")
            systemInfo.put("OSVersion", Build.VERSION.RELEASE)
            if (context == null) {
                context = webView?.view?.context
            }
            if (context == null) {
                callback?.onError("获取设备信息失败")
                return
            }
            systemInfo.put("appPlatform", "android")
            systemInfo.put("OSVersion", Build.VERSION.RELEASE)
            systemInfo.put("appVersion", DeviceUtils.getAppVersionName(context!!))
            systemInfo.put("buildVersion", DeviceUtils.getAppVersionCode(context!!))
            systemInfo.put("screenWidth", DeviceUtils.getScreenWidth(context!!))
            systemInfo.put("screenHeight", DeviceUtils.getScreenHeight(context!!))
            systemInfo.put("statusBarHeight", DeviceUtils.getStatusBarHeight(context!!))
            systemInfo.put("navBarHeight", DeviceUtils.getNavBarHeight(context!!))
            systemInfo.put("bottomSafeHeight", DeviceUtils.getBottomSafeHeight(context!!))
            systemInfo.put("locale", DeviceUtils.getSystemLocale(context!!))
            systemInfo.put("timezone", DeviceUtils.getSystemTimeZone())
            callback?.onSuccess(systemInfo)
        } catch (e: Exception) {
            Log.e(TAG, "获取设备信息失败", e)
            callback?.onError("获取设备信息失败")
        }
    }

    /**
     * 处理关闭WebView请求
     */
    private fun handleCloseWebView(
        webView: IBridgeWebView?,
        callback: IBridgeCallback?
    ) {
        try {
            val activity = getActivityFromWebView(webView)
            activity?.finish()
            callback?.onSuccess(null)
        } catch (e: Exception) {
            Log.e(TAG, "关闭WebView失败", e)
            callback?.onError("关闭WebView失败")
        }
    }

    /**
     * 处理返回按钮点击
     */
    private fun handleBackClick(
        webView: IBridgeWebView?,
        callback: IBridgeCallback?
    ) {
        try {
            val activity = getActivityFromWebView(webView)
            if (activity != null) {
                processBackClick(webView, activity, callback)
            } else {
                callback?.onError("获取Activity失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理返回失败", e)
            callback?.onError("处理返回失败")
        }
    }

    /**
     * 处理返回逻辑
     */
    private fun processBackClick(
        webView: IBridgeWebView?,
        activity: Activity,
        callback: IBridgeCallback?
    ) {
        try {
            // 确保在主线程中执行
            if (Looper.myLooper() == Looper.getMainLooper()) {
                executeBackLogic(webView, activity, callback)
            } else {
                Handler(Looper.getMainLooper()).post {
                    executeBackLogic(webView, activity, callback)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理返回逻辑失败", e)
            callback?.onError("处理返回逻辑失败")
        }
    }

    /**
     * 执行返回逻辑的具体实现
     */
    private fun executeBackLogic(
        webView: IBridgeWebView?,
        activity: Activity,
        callback: IBridgeCallback?
    ) {
        try {
            if (webView != null && webView.canGoBack()) {
                webView.goBack()
                callback?.onSuccess(true)
            } else {
                activity.finish()
                callback?.onSuccess(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "执行返回逻辑失败", e)
            callback?.onError("执行返回逻辑失败")
        }
    }

    /**
     * 判断当前是否在主线程
     */
    private fun Context.isMainThread(): Boolean {
        return Thread.currentThread().name == "main"
    }

    /**
     * 处理扫描二维码请求
     */
    private fun handleScanQRCode(
        webView: IBridgeWebView?,
        params: String?,
        callback: IBridgeCallback?
    ) {
        try {
            val activity = getActivityFromWebView(webView)
            if (activity == null) {
                callback?.onError(ScanQrBridge.failJson("无法获取页面"))
                return
            }

            // 检查相机权限
            if (ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // 请求权限
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.CAMERA),
                    1001
                )
                // 保存回调，等待权限结果
                permissionCallbackMap[1001] = { granted ->
                    if (granted) {
                        // 权限已授予，启动扫描
                        startScanActivity(activity, callback)
                    } else {
                        callback?.onError(ScanQrBridge.failJson("没有相机权限"))
                    }
                }
            } else {
                // 权限已授予，直接启动扫描
                startScanActivity(activity, callback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理扫描请求失败", e)
            callback?.onError(ScanQrBridge.failJson("未知错误"))
        }
    }

    /**
     * 启动扫描活动
     */
    private fun startScanActivity(activity: Activity, callback: IBridgeCallback?) {
        try {
            val intent = Intent(activity, QRScannerActivity::class.java)
            val callbackId = "scanQRCode"
            intent.putExtra(
                "params",
                JSONObject().put("callbackId", callbackId).toString(),
            )
            intent.putExtra("callbackId", callbackId)
            ClosureRegistry.register(callbackId, callback)
            activity.startActivityForResult(intent, 1002)
        } catch (e: Exception) {
            Log.e(TAG, "启动扫描活动失败", e)
            callback?.onError(ScanQrBridge.failJson("未知错误"))
        }
    }

    /**
     * 处理定位请求
     */
    private fun handleLocationRequest(
        webView: IBridgeWebView?,
        params: String?,
        callback: IBridgeCallback?
    ) {
        try {
            val activity = getActivityFromWebView(webView)
            if (activity == null) {
                callback?.onError("获取Activity失败")
                return
            }
            // 解析参数，透传 requestPermission / accuracy / timeout / needAddress 等字段
            val paramsMap = parseParamsToMap(params)
            // 使用QXLocationManager获取位置信息
            val locationManager = QXLocationManager.getInstance(activity)
            // 设置回调
            locationManager.setCallback(callback)
            locationManager.getLocation(activity, paramsMap)
        } catch (e: Exception) {
            Log.e(TAG, "处理定位请求失败", e)
            callback?.onError("处理定位请求失败")
        }
    }

    private fun handleDownloadAndOpenFile(
        webView: IBridgeWebView?,
        params: String?,
        callback: IBridgeCallback?
    ) {
        var urlStr = ""
        var isOpen = true
        try {
            val jsonObj = org.json.JSONObject(params ?: "")
            urlStr = jsonObj.optString("url", "")
            isOpen = jsonObj.optBoolean("isOpen", true)
        } catch (e: Exception) {
            val obj = org.json.JSONObject().apply {
                put("code", 500)
                put("msg", "参数解析失败")
            }
            callback?.onSuccess(obj)
            return
        }

        if (urlStr.isBlank() || !urlStr.startsWith("http")) {
            val obj = org.json.JSONObject().apply {
                put("code", 400)
                put("msg", "链接无效")
            }
            callback?.onSuccess(obj)
            return
        }

        // 1. 先获取上下文+判空，彻底杜绝文件路径为空的问题【核心修复】
        val mContext = context ?: run {
            callback?.onSuccess(org.json.JSONObject().apply {
                put("code", 500)
                put("msg", "上下文为空，无法下载")
            })
            return
        }

        Log.e(TAG, "开始下载->" + urlStr)
        Thread {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(urlStr)
                conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                if (conn.responseCode == 200) {
                    // 获取文件名
                    val fileName = urlStr.substringAfterLast("/")
                    Log.e(TAG, "文件名->" + fileName)
                    // 非空上下文创建文件
                    val saveFile = File(mContext.filesDir, fileName)
                    // 写入文件
                    conn.inputStream.copyTo(FileOutputStream(saveFile))
                    if (saveFile.exists() && saveFile.length() > 0) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            callback?.onSuccess(org.json.JSONObject().apply {
                                put("code", 200)
                                put("msg", "下载成功")
                                put("filePath", saveFile.absolutePath)
                            })
                            Log.e(TAG, "下载成功->" + urlStr)
                            if (isOpen) {
                                openFileWithNoCrash(saveFile)
                            }
                        }
                    } else {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            callback?.onSuccess(org.json.JSONObject().apply {
                                put("code", 500)
                                put("msg", "下载失败，文件为空")
                            })
                            Log.e(TAG, "失败->文件为空或不存在")
                        }
                    }
                } else {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        callback?.onSuccess(org.json.JSONObject().apply {
                            put("code", 500)
                            put("msg", "下载失败，服务器返回异常:${conn?.responseCode}")
                        })
                    }
                }
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    callback?.onSuccess(org.json.JSONObject().apply {
                        put("code", 500)
                        put("msg", "下载失败：${e.message ?: "未知异常"}")
                    })
                }
                Log.e(TAG, "失败->" + e.toString())
            } finally {
                conn?.disconnect()
            }
        }.start()
    }

    private fun openFileWithNoCrash(file: File) {
        val ctx = context ?: return
        if (!file.exists() || file.length() <= 0) {
            return
        }
        val mimeType = getFileMimeType(file) ?: "*/*"
        val uri = FileProvider.getUriForFile(
            ctx,
            ctx.packageName + ".fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开文件异常", e)
        }
    }


    private fun getFileMimeType(file: File): String? {
        val extension = MimeTypeMap.getFileExtensionFromUrl(file.absolutePath)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }


    private fun handleOpenMap(
        webView: IBridgeWebView?,
        params: String?,
        callback: IBridgeCallback?
    ) {
        var lat = ""
        var lng = ""
        var name = "目的地"
        try {
            val jsonObj = org.json.JSONObject(params ?: "")
            lat = jsonObj.optString("latitude", "")
            lng = jsonObj.optString("longitude", "")
            name = jsonObj.optString("name", "")
        } catch (e: Exception) {
            callback?.onError("参数解析失败")
            return
        }
        if (lat.isEmpty() || lng.isEmpty()) {
            callback?.onError("经纬度缺失")
            return
        }
        val activity = getActivityFromWebView(webView)
        if (activity == null) {
            callback?.onError("获取Activity失败")
            return
        }
        Log.e(TAG, name)
        OpenMapAppUtils.instance.showMapSelectSheet(
            activity,
            lat.toDoubleOrNull() ?: 0.0,
            lng.toDoubleOrNull() ?: 0.0,
            name
        )
    }

    private fun handleSetNavigationBarStyle(
        webView: IBridgeWebView?,
        params: String?,
        callback: IBridgeCallback?
    ) {
        val activity = getActivityFromWebView(webView)
        if (activity !is QXWebViewActivity) {
            callback?.onError("当前页面不支持设置导航栏样式")
            return
        }

        val jsonObj = try {
            JSONObject(params ?: "{}")
        } catch (e: Exception) {
            callback?.onError("参数解析失败")
            return
        }

        val styleValue = when {
            jsonObj.has("style") -> jsonObj.opt("style")
            jsonObj.has("barStyle") -> jsonObj.opt("barStyle")
            else -> null
        }
        val style = parseNavigationBarStyle(styleValue)
        if (style == null) {
            callback?.onError("style 参数仅支持 default/black 或 0/1")
            return
        }

        val applyStyle: () -> Unit = {
            activity.setNavigationBarStyle(style)
            callback?.onSuccess(JSONObject().apply {
                put("code", 0)
                put("msg", "navigationBar.barStyle 设置成功")
                put("style", style.jsValue)
                put("rawValue", style.rawValue)
            })
            Unit
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyStyle()
        } else {
            Handler(Looper.getMainLooper()).post(applyStyle)
        }
    }

    private fun parseNavigationBarStyle(styleValue: Any?): QXWebViewActivity.NavigationBarStyle? {
        if (styleValue == null || styleValue == JSONObject.NULL) {
            return null
        }
        return when (styleValue) {
            is Number -> QXWebViewActivity.NavigationBarStyle.fromRawValue(styleValue.toInt())
            is String -> {
                styleValue.toIntOrNull()?.let { QXWebViewActivity.NavigationBarStyle.fromRawValue(it) }
                    ?: QXWebViewActivity.NavigationBarStyle.fromJsValue(styleValue)
            }
            else -> null
        }
    }

    /**
     * 打开新的 WebView 页面
     * H5 调用示例：
     * QXBasePlugin.openWebView({
     *   url: "https://xxx.com/page",
     *   query: { id: 1, from: "h5" },   // 可选，会自动拼到 url 的 query 上
     *   navHidden: true,                 // 可选，是否隐藏导航栏
     *   immersive: true,                 // 可选，是否沉浸式状态栏
     *   navTitle: "页面标题",            // 可选，原生导航栏标题
     *   presentStyle: "push"             // iOS 兼容字段，Android 忽略
     * })
     */
    private fun handleOpenWebView(
        webView: IBridgeWebView?,
        params: String?,
        callback: IBridgeCallback?
    ) {
        val jsonObj = try {
            JSONObject(params ?: "{}")
        } catch (e: Exception) {
            callback?.onError("参数解析失败")
            return
        }

        val rawUrl = jsonObj.optString("url").trim()
        if (rawUrl.isEmpty()) {
            callback?.onError("url 不能为空")
            return
        }

        val queryObj = jsonObj.optJSONObject("query")
        val finalUrl = appendQueryParams(rawUrl, queryObj)

        val navHidden = if (jsonObj.has("navHidden") && !jsonObj.isNull("navHidden")) {
            jsonObj.optBoolean("navHidden", false)
        } else {
            null
        }
        val immersive = if (jsonObj.has("immersive") && !jsonObj.isNull("immersive")) {
            jsonObj.optBoolean("immersive", true)
        } else {
            null
        }
        val navTitle = jsonObj.optString("navTitle").takeIf { it.isNotBlank() }

        val activity = getActivityFromWebView(webView)
        val launchContext: Context? = activity ?: context
        if (launchContext == null) {
            callback?.onError("获取上下文失败")
            return
        }

        val intent = Intent(launchContext, QXWebViewActivity::class.java).apply {
            putExtra(QXWebViewActivity.EXTRA_URL, finalUrl)
            if (immersive != null) {
                putExtra(QXWebViewActivity.EXTRA_IMMERSIVE, immersive)
            }
            if (navHidden != null) {
                // navHidden=true 表示隐藏导航栏，对应 EXTRA_SHOW_NAV_BAR=false
                putExtra(QXWebViewActivity.EXTRA_SHOW_NAV_BAR, !navHidden)
            }
            if (navTitle != null) {
                putExtra(QXWebViewActivity.EXTRA_NAV_TITLE, navTitle)
            }
            if (launchContext !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        val launch: () -> Unit = {
            try {
                launchContext.startActivity(intent)
                callback?.onSuccess(JSONObject().apply {
                    put("code", 0)
                    put("msg", "打开 WebView 成功")
                    put("url", finalUrl)
                })
            } catch (e: Exception) {
                Log.e(TAG, "打开 WebView 失败", e)
                callback?.onError("打开 WebView 失败: ${e.message ?: "未知异常"}")
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            launch()
        } else {
            Handler(Looper.getMainLooper()).post(launch)
        }
    }

    /**
     * 打开 URL（系统设置、定位、蓝牙、电话、邮件、第三方 App 等）
     * H5 调用示例：
     * QXBasePlugin.openUrl({ type: "settings" })                   // 本 App 的设置页
     * QXBasePlugin.openUrl({ type: "location" })                   // 定位设置
     * QXBasePlugin.openUrl({ type: "notification" })               // 通知设置
     * QXBasePlugin.openUrl({ type: "bluetooth" })                  // 蓝牙
     * QXBasePlugin.openUrl({ type: "wifi" })                       // 无线局域网
     * QXBasePlugin.openUrl({ url: "tel:10086" })                   // 拨号
     * QXBasePlugin.openUrl({ url: "mailto:a@b.com" })              // 发邮件
     * QXBasePlugin.openUrl({ url: "https://xxx.com", query: {a:1}}) // 任意 URL + 拼参数
     */
    private fun handleOpenUrl(
        webView: IBridgeWebView?,
        params: String?,
        callback: IBridgeCallback?
    ) {
        val jsonObj = try {
            JSONObject(params ?: "{}")
        } catch (e: Exception) {
            callback?.onError("参数解析失败")
            return
        }

        val typeString = jsonObj.optString("type").trim().lowercase()
        val inputUrl = jsonObj.optString("url").trim()
        val queryObj = jsonObj.optJSONObject("query") ?: jsonObj.optJSONObject("params")

        val activity = getActivityFromWebView(webView)
        val launchContext: Context? = activity ?: context
        if (launchContext == null) {
            callback?.onError("获取上下文失败")
            return
        }

        val intent = resolveSystemIntent(launchContext, typeString, inputUrl, queryObj)
        if (intent == null) {
            callback?.onError("url 或 type 不能同时为空")
            return
        }
        if (launchContext !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val finalUrl = intent.dataString ?: inputUrl

        val launch: () -> Unit = launchBlock@{
            try {
                if (intent.resolveActivity(launchContext.packageManager) == null) {
                    callback?.onError("当前设备无法打开此 URL")
                    return@launchBlock
                }
                launchContext.startActivity(intent)
                callback?.onSuccess(JSONObject().apply {
                    put("code", 0)
                    put("msg", "打开成功")
                    if (finalUrl.isNotEmpty()) put("url", finalUrl)
                })
            } catch (e: Exception) {
                Log.e(TAG, "打开 URL 失败", e)
                callback?.onError("打开失败: ${e.message ?: "未知异常"}")
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            launch()
        } else {
            Handler(Looper.getMainLooper()).post(launch)
        }
    }

    /**
     * 根据 type 解析系统页面 Intent，未匹配时按 url 兜底
     */
    private fun resolveSystemIntent(
        ctx: Context,
        type: String,
        fallbackUrl: String,
        queryObj: JSONObject?
    ): Intent? {
        return when (type) {
            "settings", "app-settings", "appsettings" ->
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", ctx.packageName, null)
                }
            "notification", "notifications", "notification-settings" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", ctx.packageName, null)
                    }
                }
            }
            "location", "location-settings" ->
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "bluetooth", "ble" ->
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "wifi", "wlan" ->
                Intent(Settings.ACTION_WIFI_SETTINGS)
            "cellular", "mobile-data" ->
                Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
            "general" ->
                Intent(Settings.ACTION_SETTINGS)
            "privacy" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Intent(Settings.ACTION_PRIVACY_SETTINGS)
                } else {
                    Intent(Settings.ACTION_SETTINGS)
                }
            }
            "", "url", "custom" -> buildUrlIntent(fallbackUrl, queryObj)
            else -> buildUrlIntent(fallbackUrl, queryObj)
        }
    }

    private fun buildUrlIntent(url: String, queryObj: JSONObject?): Intent? {
        if (url.isBlank()) return null
        val finalUrl = appendQueryParams(url, queryObj)
        val uri = try {
            Uri.parse(finalUrl)
        } catch (e: Exception) {
            return null
        }
        return Intent(Intent.ACTION_VIEW, uri)
    }

    /**
     * 将 JSON 参数拼到已有 URL 的 query 上（保留原 query，自动做百分号编码）
     */
    private fun appendQueryParams(urlString: String, params: JSONObject?): String {
        if (params == null || params.length() == 0) return urlString
        return try {
            val uri = Uri.parse(urlString)
            val builder = uri.buildUpon()
            val keys = params.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = params.opt(key)
                if (value == null || value == JSONObject.NULL) continue
                builder.appendQueryParameter(key, value.toString())
            }
            builder.build().toString()
        } catch (e: Exception) {
            Log.d(TAG, "appendQueryParams failed: $e")
            urlString
        }
    }

    /**
     * 将 JSON 字符串安全地解析为 Map<String, Any>，供插件透传参数使用
     */
    private fun parseParamsToMap(params: String?): Map<String, Any>? {
        if (params.isNullOrBlank()) return null
        return try {
            val json = JSONObject(params)
            val map = mutableMapOf<String, Any>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.opt(key) ?: continue
                map[key] = value
            }
            map
        } catch (e: Exception) {
            Log.d(TAG, "parseParamsToMap failed: $e")
            null
        }
    }

    /**
     * 从WebView获取Activity实例
     */
    private fun getActivityFromWebView(webView: IBridgeWebView?): Activity? {
        val view = webView?.view ?: return null
        val context = view.context
        if (context is Activity) {
            return context
        } else if (context is android.content.ContextWrapper && context.baseContext is Activity) {
            return context.baseContext as Activity
        }
        return null
    }
}
