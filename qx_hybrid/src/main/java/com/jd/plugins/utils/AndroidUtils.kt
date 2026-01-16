package com.jd.plugins.utils

import android.app.Activity
import android.content.ContextWrapper
import android.view.View

/**
 * Android工具类，提供常见的Android开发辅助功能
 */
object AndroidUtils {
    
    /**
     * 从View对象获取对应的Activity实例
     * @param view 视图对象
     * @return 找到的Activity实例，如果无法找到则返回null
     */
    fun getActivityFromView(view: View?): Activity? {
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