package com.energy.chery_android.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/**
 * 通用工具类
 * 提供常用的工具方法，包括单位转换、颜色处理、设备信息等
 */
public class CommonUtils {
    private static final String TAG = "CommonUtils";
    
    /**
     * dp转px
     */
    public static int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
    
    /**
     * px转dp
     */
    public static int pxToDp(Context context, int px) {
        return (int) (px / context.getResources().getDisplayMetrics().density + 0.5f);
    }
    
    /**
     * sp转px
     */
    public static int spToPx(Context context, int sp) {
        return (int) (sp * context.getResources().getDisplayMetrics().scaledDensity + 0.5f);
    }
    
    /**
     * 获取状态栏高度
     */
    public static int getStatusBarHeight(Context context) {
        int statusBarHeight = 0;
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusBarHeight = context.getResources().getDimensionPixelSize(resourceId);
        }
        if (statusBarHeight == 0) {
            statusBarHeight = dpToPx(context, 24);
        }
        return statusBarHeight;
    }
    
    /**
     * 获取导航栏高度
     */
    public static int getNavigationBarHeight(Context context) {
        int navigationBarHeight = 0;
        int resourceId = context.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            navigationBarHeight = context.getResources().getDimensionPixelSize(resourceId);
        }
        if (navigationBarHeight == 0) {
            navigationBarHeight = dpToPx(context, 48);
        }
        return navigationBarHeight;
    }
    
    /**
     * 获取屏幕宽度
     */
    public static int getScreenWidth(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return metrics.widthPixels;
    }
    
    /**
     * 获取屏幕高度
     */
    public static int getScreenHeight(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return metrics.heightPixels;
    }
    
    /**
     * 获取屏幕密度
     */
    public static float getScreenDensity(Context context) {
        return context.getResources().getDisplayMetrics().density;
    }
    
    /**
     * 获取屏幕密度DPI
     */
    public static int getScreenDensityDpi(Context context) {
        return context.getResources().getDisplayMetrics().densityDpi;
    }
    
    /**
     * 检查是否为平板设备
     */
    public static boolean isTablet(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float widthInches = metrics.widthPixels / metrics.xdpi;
        float heightInches = metrics.heightPixels / metrics.ydpi;
        double diagonalInches = Math.sqrt(widthInches * widthInches + heightInches * heightInches);
        return diagonalInches >= 7.0;
    }
    
    /**
     * 检查是否为横屏
     */
    public static boolean isLandscape(Context context) {
        return context.getResources().getConfiguration().orientation == 
               android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }
    
    /**
     * 颜色相关工具方法
     */
    public static class ColorUtils {
        /**
         * 解析颜色字符串
         */
        public static int parseColor(String colorString) {
            try {
                return Color.parseColor(colorString);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid color string: " + colorString, e);
                return Color.BLACK;
            }
        }
        
        /**
         * 获取颜色的十六进制字符串
         */
        public static String toHexString(int color) {
            return String.format("#%08X", color);
        }
        
        /**
         * 获取颜色的RGB字符串
         */
        public static String toRgbString(int color) {
            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);
            return String.format("rgb(%d, %d, %d)", r, g, b);
        }
        
        /**
         * 获取颜色的ARGB字符串
         */
        public static String toArgbString(int color) {
            int a = Color.alpha(color);
            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);
            return String.format("argb(%d, %d, %d, %d)", a, r, g, b);
        }
        
        /**
         * 调整颜色透明度
         */
        public static int adjustAlpha(int color, float alpha) {
            int a = Math.round(Color.alpha(color) * alpha);
            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);
            return Color.argb(a, r, g, b);
        }
        
        /**
         * 判断颜色是否为深色
         */
        public static boolean isDarkColor(int color) {
            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);
            // 使用相对亮度公式
            double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
            return luminance < 0.5;
        }
        
        /**
         * 获取对比色（深色背景返回白色，浅色背景返回黑色）
         */
        public static int getContrastColor(int backgroundColor) {
            return isDarkColor(backgroundColor) ? Color.WHITE : Color.BLACK;
        }
    }
    
    /**
     * 字符串相关工具方法
     */
    public static class StringUtils {
        /**
         * 检查字符串是否为空
         */
        public static boolean isEmpty(String str) {
            return str == null || str.length() == 0;
        }
        
        /**
         * 检查字符串是否不为空
         */
        public static boolean isNotEmpty(String str) {
            return !isEmpty(str);
        }
        
        /**
         * 检查字符串是否为空白
         */
        public static boolean isBlank(String str) {
            return str == null || str.trim().length() == 0;
        }
        
        /**
         * 检查字符串是否不为空白
         */
        public static boolean isNotBlank(String str) {
            return !isBlank(str);
        }
        
        /**
         * 安全地获取字符串，如果为null则返回默认值
         */
        public static String safeString(String str, String defaultValue) {
            return isEmpty(str) ? defaultValue : str;
        }
        
        /**
         * 截取字符串到指定长度
         */
        public static String truncate(String str, int maxLength) {
            if (isEmpty(str) || str.length() <= maxLength) {
                return str;
            }
            return str.substring(0, maxLength) + "...";
        }
    }
    
    /**
     * 设备信息工具类
     */
    public static class DeviceUtils {
        /**
         * 获取设备型号
         */
        public static String getDeviceModel() {
            return Build.MODEL;
        }
        
        /**
         * 获取设备品牌
         */
        public static String getDeviceBrand() {
            return Build.BRAND;
        }
        
        /**
         * 获取Android版本
         */
        public static String getAndroidVersion() {
            return Build.VERSION.RELEASE;
        }
        
        /**
         * 获取API级别
         */
        public static int getApiLevel() {
            return Build.VERSION.SDK_INT;
        }
        
        /**
         * 检查是否为Android 6.0及以上
         */
        public static boolean isAndroid6OrAbove() {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
        }
        
        /**
         * 检查是否为Android 11及以上
         */
        public static boolean isAndroid11OrAbove() {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
        }
        
        /**
         * 获取设备信息字符串
         */
        public static String getDeviceInfo() {
            return String.format("%s %s (Android %s, API %d)", 
                getDeviceBrand(), getDeviceModel(), getAndroidVersion(), getApiLevel());
        }
    }
    
    /**
     * 视图相关工具方法
     */
    public static class ViewUtils {
        /**
         * 设置视图可见性
         */
        public static void setVisibility(View view, boolean visible) {
            if (view != null) {
                view.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        }
        
        /**
         * 设置视图透明度
         */
        public static void setAlpha(View view, float alpha) {
            if (view != null) {
                view.setAlpha(alpha);
            }
        }
        
        /**
         * 设置视图背景颜色
         */
        public static void setBackgroundColor(View view, int color) {
            if (view != null) {
                view.setBackgroundColor(color);
            }
        }
        
        /**
         * 获取视图的屏幕位置
         */
        public static int[] getViewLocation(View view) {
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            return location;
        }
        
        /**
         * 检查视图是否在屏幕上可见
         */
        public static boolean isViewVisible(View view) {
            if (view == null) {
                return false;
            }
            return view.getVisibility() == View.VISIBLE && view.getAlpha() > 0;
        }
    }
    
    /**
     * 日志工具类
     */
    public static class LogUtils {
        private static final String DEFAULT_TAG = "App";
        
        public static void d(String message) {
            Log.d(DEFAULT_TAG, message);
        }
        
        public static void d(String tag, String message) {
            Log.d(tag, message);
        }
        
        public static void i(String message) {
            Log.i(DEFAULT_TAG, message);
        }
        
        public static void i(String tag, String message) {
            Log.i(tag, message);
        }
        
        public static void w(String message) {
            Log.w(DEFAULT_TAG, message);
        }
        
        public static void w(String tag, String message) {
            Log.w(tag, message);
        }
        
        public static void e(String message) {
            Log.e(DEFAULT_TAG, message);
        }
        
        public static void e(String tag, String message) {
            Log.e(tag, message);
        }
        
        public static void e(String tag, String message, Throwable throwable) {
            Log.e(tag, message, throwable);
        }
    }
    
    /**
     * 资源工具类
     */
    public static class ResourceUtils {
        /**
         * 获取颜色资源
         */
        public static int getColor(Context context, int colorResId) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return context.getResources().getColor(colorResId, null);
            } else {
                return context.getResources().getColor(colorResId);
            }
        }
        
        /**
         * 获取尺寸资源
         */
        public static int getDimensionPixelSize(Context context, int dimenResId) {
            return context.getResources().getDimensionPixelSize(dimenResId);
        }
        
        /**
         * 获取字符串资源
         */
        public static String getString(Context context, int stringResId) {
            return context.getResources().getString(stringResId);
        }
        
        /**
         * 获取字符串资源（带参数）
         */
        public static String getString(Context context, int stringResId, Object... formatArgs) {
            return context.getResources().getString(stringResId, formatArgs);
        }
    }
}
