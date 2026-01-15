package com.energy.chery_android.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/**
 * 状态栏管理器
 * 封装所有状态栏相关的功能，包括透明状态栏、文字颜色控制、动画效果等
 */
public class StatusBarManager {
    private static final String TAG = "StatusBarManager";
    
    private Activity activity;
    private boolean isImmersiveModeEnabled = false;
    private StatusBarConfig currentConfig;
    
    /**
     * 状态栏配置类
     */
    public static class StatusBarConfig {
        public int statusBarColor = Color.TRANSPARENT;
        public boolean isLightText = false; // false为深色文字（黑色），true为浅色文字（白色）
        public boolean enableAnimation = true;
        public long animationDuration = 300;
        
        public StatusBarConfig() {}
        
        public StatusBarConfig(int statusBarColor, boolean isLightText) {
            this.statusBarColor = statusBarColor;
            this.isLightText = isLightText;
        }
        
        public StatusBarConfig(int statusBarColor, boolean isLightText, boolean enableAnimation, long animationDuration) {
            this.statusBarColor = statusBarColor;
            this.isLightText = isLightText;
            this.enableAnimation = enableAnimation;
            this.animationDuration = animationDuration;
        }
    }
    
    /**
     * 状态栏变化监听器
     */
    public interface OnStatusBarChangeListener {
        void onStatusBarChanged(boolean isImmersive, StatusBarConfig config);
        void onAnimationStart();
        void onAnimationEnd();
    }
    
    private OnStatusBarChangeListener changeListener;
    
    public StatusBarManager(Activity activity) {
        this.activity = activity;
        this.currentConfig = new StatusBarConfig();
    }
    
    /**
     * 设置状态栏变化监听器
     */
    public void setOnStatusBarChangeListener(OnStatusBarChangeListener listener) {
        this.changeListener = listener;
    }
    
    /**
     * 启用沉浸式状态栏
     */
    public void enableImmersiveStatusBar(StatusBarConfig config) {
        if (config == null) {
            config = new StatusBarConfig();
        }
        
        this.currentConfig = config;
        this.isImmersiveModeEnabled = true;
        
        if (config.enableAnimation) {
            enableImmersiveStatusBarWithAnimation(config);
        } else {
            enableImmersiveStatusBarImmediate(config);
        }
    }
    
    /**
     * 禁用沉浸式状态栏
     */
    public void disableImmersiveStatusBar(StatusBarConfig config) {
        if (config == null) {
            config = new StatusBarConfig(Color.WHITE, true);
        }
        
        this.currentConfig = config;
        this.isImmersiveModeEnabled = false;
        
        if (config.enableAnimation) {
            disableImmersiveStatusBarWithAnimation(config);
        } else {
            disableImmersiveStatusBarImmediate(config);
        }
    }
    
    /**
     * 立即启用沉浸式状态栏
     */
    private void enableImmersiveStatusBarImmediate(StatusBarConfig config) {
        Window window = activity.getWindow();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Android 5.0及以上版本
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(config.statusBarColor);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int flags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                if (config.isLightText) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
                window.getDecorView().setSystemUiVisibility(flags);
            }
        } else {
            // Android 4.4到Android 4.4版本
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }
        
        notifyStatusBarChanged();
        Log.d(TAG, "Immersive status bar enabled immediately");
    }
    
    /**
     * 立即禁用沉浸式状态栏
     */
    private void disableImmersiveStatusBarImmediate(StatusBarConfig config) {
        Window window = activity.getWindow();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.getDecorView().setSystemUiVisibility(0);
            window.setStatusBarColor(config.statusBarColor);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }
        
        notifyStatusBarChanged();
        Log.d(TAG, "Immersive status bar disabled immediately");
    }
    
    /**
     * 带动画启用沉浸式状态栏
     */
    private void enableImmersiveStatusBarWithAnimation(StatusBarConfig config) {
        if (changeListener != null) {
            changeListener.onAnimationStart();
        }
        
        // 先设置状态栏配置
        enableImmersiveStatusBarImmediate(config);
        
        // 这里可以添加颜色渐变动画
        animateStatusBarColor(config.statusBarColor, config.animationDuration);
    }
    
    /**
     * 带动画禁用沉浸式状态栏
     */
    private void disableImmersiveStatusBarWithAnimation(StatusBarConfig config) {
        if (changeListener != null) {
            changeListener.onAnimationStart();
        }
        
        // 先设置状态栏配置
        disableImmersiveStatusBarImmediate(config);
        
        // 这里可以添加颜色渐变动画
        animateStatusBarColor(config.statusBarColor, config.animationDuration);
    }
    
    /**
     * 状态栏颜色动画
     */
    private void animateStatusBarColor(int targetColor, long duration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ValueAnimator animator = ValueAnimator.ofArgb(activity.getWindow().getStatusBarColor(), targetColor);
            animator.setDuration(duration);
            animator.addUpdateListener(animation -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    activity.getWindow().setStatusBarColor((int) animation.getAnimatedValue());
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (changeListener != null) {
                        changeListener.onAnimationEnd();
                    }
                }
            });
            animator.start();
        }
    }
    
    /**
     * 获取状态栏高度
     */
    public int getStatusBarHeight() {
        int statusBarHeight = 0;
        @SuppressLint("InternalInsetResource") int resourceId = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusBarHeight = activity.getResources().getDimensionPixelSize(resourceId);
        }
        if (statusBarHeight == 0) {
            statusBarHeight = (int) (24 * activity.getResources().getDisplayMetrics().density + 0.5f);
        }
        return statusBarHeight;
    }
    
    /**
     * 获取导航栏高度
     */
    public int getNavigationBarHeight() {
        int navigationBarHeight = 0;
        @SuppressLint("InternalInsetResource") int resourceId = activity.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            navigationBarHeight = activity.getResources().getDimensionPixelSize(resourceId);
        }
        if (navigationBarHeight == 0) {
            navigationBarHeight = (int) (48 * activity.getResources().getDisplayMetrics().density + 0.5f);
        }
        return navigationBarHeight;
    }
    
    /**
     * 检查是否启用沉浸式模式
     */
    public boolean isImmersiveModeEnabled() {
        return isImmersiveModeEnabled;
    }
    
    /**
     * 获取当前配置
     */
    public StatusBarConfig getCurrentConfig() {
        return currentConfig;
    }
    
    /**
     * 通知状态栏变化
     */
    private void notifyStatusBarChanged() {
        if (changeListener != null) {
            changeListener.onStatusBarChanged(isImmersiveModeEnabled, currentConfig);
        }
    }
    
    /**
     * 切换沉浸式状态栏
     */
    public void toggleImmersiveStatusBar(StatusBarConfig immersiveConfig, StatusBarConfig normalConfig) {
        if (isImmersiveModeEnabled) {
            disableImmersiveStatusBar(normalConfig);
        } else {
            enableImmersiveStatusBar(immersiveConfig);
        }
    }
    
    /**
     * 设置状态栏文字颜色
     */
    public void setStatusBarTextColor(boolean isLight) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11及以上版本
            WindowInsetsController insetsController = activity.getWindow().getInsetsController();
            if (insetsController != null) {
                if (isLight) {
                    insetsController.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                } else {
                    insetsController.setSystemBarsAppearance(
                            0,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0到Android 10版本
            int flags = activity.getWindow().getDecorView().getSystemUiVisibility();
            if (isLight) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            activity.getWindow().getDecorView().setSystemUiVisibility(flags);
        }
        
        // 更新当前配置
        currentConfig.isLightText = isLight;
    }
    
    /**
     * 设置状态栏颜色
     */
    public void setStatusBarColor(int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.getWindow().setStatusBarColor(color);
        }
        
        // 更新当前配置
        currentConfig.statusBarColor = color;
    }
    
    /**
     * 获取默认状态栏配置
     */
    public static StatusBarConfig getDefaultStatusBarConfig() {
        StatusBarConfig config = new StatusBarConfig();
        config.statusBarColor = Color.TRANSPARENT;
        config.isLightText = false; // 黑色文字
        config.enableAnimation = true;
        config.animationDuration = 300;
        return config;
    }
}
