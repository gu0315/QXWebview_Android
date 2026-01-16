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
import android.webkit.MimeTypeMap
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
                // 显示确认对话框
                AlertDialog.Builder(activity)
                    .setTitle("提示")
                    .setMessage("确定要退出吗？")
                    .setPositiveButton("确定") { _, _ ->
                        activity.finish()
                        callback?.onSuccess(false)
                    }
                    .setNegativeButton("取消", null)
                    .show()
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
                callback?.onError("获取Activity失败")
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
                        callback?.onError("相机权限被拒绝")
                    }
                }
            } else {
                // 权限已授予，直接启动扫描
                startScanActivity(activity, callback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理扫描请求失败", e)
            callback?.onError("处理扫描请求失败")
        }
    }

    /**
     * 启动扫描活动
     */
    private fun startScanActivity(activity: Activity, callback: IBridgeCallback?) {
        try {
            val intent = Intent(activity, QRScannerActivity::class.java)
            val callbackId = "scanQRCode"
            intent.putExtra("callbackId", callbackId)
            ClosureRegistry.register(callbackId, callback)
            activity.startActivityForResult(intent, 1002)
        } catch (e: Exception) {
            Log.e(TAG, "启动扫描活动失败", e)
            callback?.onError("启动扫描活动失败")
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
            // 使用QXLocationManager获取位置信息
            val locationManager = QXLocationManager.getInstance(activity)
            // 设置回调
            locationManager.setCallback(callback)
            locationManager.getLocation(activity)
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