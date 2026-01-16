package com.jd.plugins

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.jd.jdbridge.base.IBridgeCallback
import com.jd.jdbridge.base.IBridgeWebView
import org.json.JSONObject

/**
 * 系统信息处理器
 * 提供设备基本信息和安全区计算功能（px转dp）
 */
class SystemInfoHandler {
    // 类内常量
    private val NAME = "SystemInfoHandler"
    // 全局Application Context（最终兜底）
    private var globalAppContext: Context? = null

    /**
     * 初始化：传入全局Application Context（建议在Application.onCreate中调用）
     */
    fun init(application: Application) {
        this.globalAppContext = application
    }

    /**
     * 处理系统信息（包含安全区计算：返回dp值，同时保留px值兼容）
     * @param webView IBridgeWebView实例
     * @param callback 回调接口，用于返回结果
     */
    fun handleSystemInfo(
        webView: IBridgeWebView?,
        callback: IBridgeCallback?
    ) {
        try {
            // 优先获取Activity Context
            val activityContext = getActivityFromView(webView?.view)
            val context = activityContext ?: webView?.view?.context ?: globalAppContext
            if (context == null) {
                callback?.onError("Context为空，无法计算安全区")
                return
            }

            // 计算px值
            val topSafeAreaPx = getTopSafeArea(context, activityContext)
            val bottomSafeAreaPx = getBottomSafeArea(context, activityContext)

            // 转换为dp值（保留1位小数）
            val topSafeAreaDp = px2dp(context, topSafeAreaPx)
            val bottomSafeAreaDp = px2dp(context, bottomSafeAreaPx)

            val systemInfo = JSONObject().apply {
                put("systemVersion", Build.VERSION.RELEASE)
                put("osName", "Android")
                put("deviceModel", Build.MODEL)
                // 安全区：返回dp值（前端常用）
                put("bottomSafeHeight", bottomSafeAreaDp)
                put("statusBarHeight", topSafeAreaDp)
                // 兼容：保留px值（可选）
                put("bottomSafeHeightPx", bottomSafeAreaPx)
                put("statusBarHeightPx", topSafeAreaPx)
            }
            Log.d(NAME, "安全区：top=${topSafeAreaDp}dp(${topSafeAreaPx}px)，bottom=${bottomSafeAreaDp}dp(${bottomSafeAreaPx}px)")
            callback?.onSuccess(systemInfo.toString())
        } catch (e: Exception) {
            Log.e(NAME, "获取系统信息失败", e)
            callback?.onError("获取系统信息失败：${e.message}")
        }
    }

    /**
     * 核心工具：px 转 dp（保留1位小数）
     * @param context 上下文（用于获取屏幕密度）
     * @param px 像素值
     * @return dp值（保留1位小数）
     */
    private fun px2dp(context: Context, px: Int): Float {
        val density = context.resources.displayMetrics.density // 屏幕密度（如1.0/1.5/2.0/3.0/4.0）
        return if (density == 0f) {
            px.toFloat() // 异常兜底
        } else {
            String.format("%.1f", px / density).toFloat() // 保留1位小数，避免精度冗余
        }
    }

    /**
     * 可选工具：dp 转 px（备用）
     */
    private fun dp2px(context: Context, dp: Float): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density + 0.5f).toInt() // +0.5f 四舍五入
    }

    // ========== 以下为原有逻辑（无修改，仅依赖px值计算） ==========
    private fun getTopSafeArea(context: Context?, activityContext: Activity?): Int {
        if (context == null) return 0

        activityContext?.let { activity ->
            return try {
                val decorView = activity.window.decorView
                val windowInsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    decorView.rootWindowInsets ?: return getStatusBarHeight(context)
                } else {
                    TODO("VERSION.SDK_INT < M")
                }

                var topInset = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    topInset = getSystemBarsInsetTop(windowInsets)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val cutout = windowInsets.displayCutout
                    topInset = cutout?.safeInsetTop ?: 0
                }

                if (topInset == 0) getStatusBarHeight(context) else topInset
            } catch (e: Exception) {
                Log.w(NAME, "获取刘海屏顶部安全区失败，降级为状态栏高度", e)
                getStatusBarHeight(context)
            }
        }

        return getStatusBarHeight(context)
    }

    private fun getBottomSafeArea(context: Context?, activityContext: Activity?): Int {
        if (context == null) return 0

        var bottomInset = 0
        activityContext?.let { activity ->
            bottomInset = try {
                val decorView = activity.window.decorView
                val windowInsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    decorView.rootWindowInsets ?: 0
                } else {
                    TODO("VERSION.SDK_INT < M")
                }

                if (windowInsets != 0) {
                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                            getSystemBarsInsetBottom(windowInsets as WindowInsets)
                        }
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                            val cutout = (windowInsets as WindowInsets).displayCutout
                            cutout?.safeInsetBottom ?: 0
                        }
                        else -> 0
                    }
                } else {
                    0
                }
            } catch (e: Exception) {
                Log.w(NAME, "获取刘海屏底部安全区失败", e)
                0
            }
        }

        return if (bottomInset == 0) {
            if (isNavigationBarShow(context)) getNavigationBarHeight(context) else 0
        } else {
            bottomInset
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun getSystemBarsInsetTop(windowInsets: WindowInsets): Int {
        return windowInsets.getInsets(WindowInsets.Type.systemBars()).top
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun getSystemBarsInsetBottom(windowInsets: WindowInsets): Int {
        return windowInsets.getInsets(WindowInsets.Type.systemBars()).bottom
    }

    private fun getStatusBarHeight(context: Context): Int {
        var statusBarHeight = 0
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        }
        return statusBarHeight
    }

    private fun getNavigationBarHeight(context: Context): Int {
        val resName = if (isLandscape(context)) {
            "navigation_bar_height_landscape"
        } else {
            "navigation_bar_height"
        }

        var navigationBarHeight = 0
        val resourceId = context.resources.getIdentifier(
            resName, "dimen", "android"
        )
        if (resourceId > 0) {
            navigationBarHeight = context.resources.getDimensionPixelSize(resourceId)
        }
        return navigationBarHeight
    }

    private fun isNavigationBarShow(context: Context): Boolean {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay ?: return false

        val realMetrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            display.getRealMetrics(realMetrics)
        } else {
            DisplayMetrics().also { display.getMetrics(it) }
        }
        val realHeight = realMetrics.heightPixels
        val realWidth = realMetrics.widthPixels

        val displayMetrics = DisplayMetrics()
        display.getMetrics(displayMetrics)
        val displayHeight = displayMetrics.heightPixels
        val displayWidth = displayMetrics.widthPixels

        val threshold = 10
        return if (isLandscape(context)) {
            (realWidth - displayWidth) > threshold
        } else {
            (realHeight - displayHeight) > threshold
        }
    }

    private fun isLandscape(context: Context): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private fun getActivityFromView(view: View?): Activity? {
        if (view == null) return null
        var context = view.context
        while (context is ContextWrapper) {
            if (context is Activity) {
                return context
            }
            context = context.baseContext
        }
        return null
    }
}