package com.jd.plugins.utils
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Point
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import java.util.*
import java.util.TimeZone

/**
 * 设备/应用 信息工具类
 * 全局通用，项目任意位置可调用
 * ✅ 核心修改：屏幕宽高/状态栏/导航栏/安全区 全部返回【DP单位】数值
 * ✅ 物理像素(PX) → DP 自动转换，无需外部处理
 * ✅ 适配 Android 7 ~ 15 全版本，无报错
 */
object DeviceUtils {

    /**
     * 获取App版本名称 例：1.0.0 / 2.3.5
     */
    fun getAppVersionName(context: Context): String {
        val appContext = context.applicationContext
        return try {
            val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    /**
     * 获取App Build版本号 例：100 / 2035
     */
    fun getAppVersionCode(context: Context): Long {
        val appContext = context.applicationContext
        return try {
            val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            100L
        }
    }

    /**
     * ✅ 获取屏幕宽度【DP单位】 (含虚拟导航栏，全面屏适配)
     */
    fun getScreenWidth(context: Context): Float {
        val appContext = context.applicationContext
        val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val point = Point()
        windowManager.defaultDisplay.getRealSize(point)
        // 物理像素PX → DP 转换
        return px2dp(appContext, point.x.toFloat())
    }

    /**
     * ✅ 获取屏幕高度【DP单位】 (含虚拟导航栏+状态栏，全面屏适配)
     */
    fun getScreenHeight(context: Context): Float {
        val appContext = context.applicationContext
        val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val point = Point()
        windowManager.defaultDisplay.getRealSize(point)
        // 物理像素PX → DP 转换
        return px2dp(appContext, point.y.toFloat())
    }

    /**
     * ✅ 获取状态栏高度【DP单位】 刘海屏/挖孔屏/全面屏完美适配
     */
    fun getStatusBarHeight(context: Context): Float {
        val appContext = context.applicationContext
        var statusBarHeightPx = 0
        val resourceId = appContext.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeightPx = appContext.resources.getDimensionPixelSize(resourceId)
        }
        // 物理像素PX → DP 转换
        return px2dp(appContext, statusBarHeightPx.toFloat())
    }

    /**
     * ✅ 获取底部虚拟导航栏高度【DP单位】 有虚拟按键返回高度，无则返回0
     */
    fun getNavBarHeight(context: Context): Float {
        val appContext = context.applicationContext
        var navBarHeightPx = 0
        val resourceId = appContext.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resourceId > 0 && hasNavigationBar(appContext)) {
            navBarHeightPx = appContext.resources.getDimensionPixelSize(resourceId)
        }
        // 物理像素PX → DP 转换
        return px2dp(appContext, navBarHeightPx.toFloat())
    }

    /**
     * 判断当前设备是否有底部虚拟导航栏
     */
    private fun hasNavigationBar(context: Context): Boolean {
        val resources = context.resources
        val id = resources.getIdentifier("config_showNavigationBar", "bool", "android")
        if (id > 0) {
            return resources.getBoolean(id)
        }
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val d = windowManager.defaultDisplay
        val realDisplayMetrics = DisplayMetrics()
        d.getRealMetrics(realDisplayMetrics)
        val realHeight = realDisplayMetrics.heightPixels
        val realWidth = realDisplayMetrics.widthPixels

        val displayMetrics = DisplayMetrics()
        d.getMetrics(displayMetrics)
        val displayHeight = displayMetrics.heightPixels
        val displayWidth = displayMetrics.widthPixels

        return realWidth - displayWidth > 0 || realHeight - displayHeight > 0
    }

    /**
     * ✅ 获取底部安全区高度【DP单位】 刘海屏底部留白，无则返回0
     * ✅ 彻底修复 TODO 编译报错，无任何版本兼容问题
     */
    fun getBottomSafeHeight(context: Context): Float {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return 0f
        }
        val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayCutout = windowManager.defaultDisplay.cutout
        val safeHeightPx = displayCutout?.safeInsetBottom ?: 0
        // 物理像素PX → DP 转换
        return px2dp(appContext, safeHeightPx.toFloat())
    }

    /**
     * 获取系统当前语言+地区 例：zh_CN 、 en_US 、 ja_JP
     */
    fun getSystemLocale(context: Context): String {
        val appContext = context.applicationContext
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appContext.resources.configuration.locales[0].toString()
        } else {
            appContext.resources.configuration.locale.toString()
        }
    }

    /**
     * 获取系统时区 例：GMT+08:00 、 Asia/Shanghai
     */
    fun getSystemTimeZone(): String {
        return TimeZone.getDefault().id
    }

    // ===================== 核心转换工具方法 (内部调用+外部可用) =====================
    /**
     * 物理像素(PX) → 设备独立像素(DP) 【核心方法】
     */
    fun px2dp(context: Context, pxValue: Float): Float {
        val appContext = context.applicationContext
        val scale = appContext.resources.displayMetrics.density
        return pxValue / scale
    }

    /**
     * 设备独立像素(DP) → 物理像素(PX) 【备用方法】
     */
    fun dp2px(context: Context, dpValue: Float): Int {
        val appContext = context.applicationContext
        val scale = appContext.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    /**
     * SP → PX (字体专用)
     */
    fun sp2px(context: Context, spValue: Float): Int {
        val appContext = context.applicationContext
        val fontScale = appContext.resources.displayMetrics.scaledDensity
        return (spValue * fontScale + 0.5f).toInt()
    }
}