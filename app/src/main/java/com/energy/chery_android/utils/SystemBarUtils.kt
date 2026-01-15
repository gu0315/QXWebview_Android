package com.energy.chery_android.utils

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

/**
 * 系统栏工具类
 * 封装状态栏和导航栏相关的功能
 * 包括获取高度、设置沉浸式模式等
 */
object SystemBarUtils {

    private const val TAG = "SystemBarUtils"
    private const val DEFAULT_STATUS_BAR_HEIGHT_DP = 24

    /**
     * 获取状态栏高度（像素）
     * @param context Context对象
     * @return 状态栏高度（像素）
     */
    fun getStatusBarHeightPx(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            return context.resources.getDimensionPixelSize(resourceId)
        }

        // 获取失败时使用默认值
        return dpToPx(context, DEFAULT_STATUS_BAR_HEIGHT_DP)
    }

    /**
     * 获取状态栏高度（dp）
     * @param context Context对象
     * @return 状态栏高度（dp）
     */
    fun getStatusBarHeightDp(context: Context): Float {
        val statusBarHeightPx = getStatusBarHeightPx(context)
        return pxToDp(context, statusBarHeightPx)
    }

    /**
     * 获取导航栏高度（像素）
     * @param context Context对象
     * @return 导航栏高度（像素）
     */
    fun getNavigationBarHeightPx(context: Context): Int {
        val resourceId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }


    /**
     * dp转px
     * @param context Context对象
     * @param dp dp值
     * @return 转换后的px值
     */
    fun dpToPx(context: Context, dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }

    /**
     * px转dp
     * @param context Context对象
     * @param px px值
     * @return 转换后的dp值
     */
    fun pxToDp(context: Context, px: Int): Float {
        val density = context.resources.displayMetrics.density
        return px / density
    }

}