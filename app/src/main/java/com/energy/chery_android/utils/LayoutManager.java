package com.energy.chery_android.utils;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

/**
 * 布局管理器
 * 统一管理布局适配，包括状态栏、导航栏、WebView等布局调整
 */
public class LayoutManager {
    private static final String TAG = "LayoutManager";
    
    private Activity activity;
    private StatusBarManager statusBarManager;
    private WebViewManager webViewManager;
    private boolean isImmersiveMode = false;
    
    /**
     * 布局配置类
     */
    public static class LayoutConfig {
        public boolean adjustForStatusBar = true;
        public boolean adjustForNavigationBar = true;
        public boolean extendToStatusBar = false;
        public boolean extendToNavigationBar = false;
        public int statusBarHeight = 0;
        public int navigationBarHeight = 0;
        
        public LayoutConfig() {}
    }
    
    /**
     * 布局变化监听器
     */
    public interface OnLayoutChangeListener {
        void onLayoutChanged(boolean isImmersive, LayoutConfig config);
        void onStatusBarHeightChanged(int height);
        void onNavigationBarHeightChanged(int height);
    }
    
    private OnLayoutChangeListener layoutChangeListener;
    
    public LayoutManager(Activity activity) {
        this.activity = activity;
        this.statusBarManager = new StatusBarManager(activity);
        this.webViewManager = new WebViewManager(activity);
    }
    
    /**
     * 设置布局变化监听器
     */
    public void setOnLayoutChangeListener(OnLayoutChangeListener listener) {
        this.layoutChangeListener = listener;
    }
    
    /**
     * 设置WebView管理器
     */
    public void setWebViewManager(WebViewManager webViewManager) {
        this.webViewManager = webViewManager;
    }
    
    /**
     * 调整布局以适配沉浸式模式
     */
    public void adjustLayoutForImmersiveMode(LayoutConfig config) {
        if (config == null) {
            config = new LayoutConfig();
        }
        
        this.isImmersiveMode = true;
        
        // 获取状态栏高度
        int statusBarHeight = statusBarManager.getStatusBarHeight();
        config.statusBarHeight = statusBarHeight;
        
        // 调整WebView布局
        if (webViewManager != null) {
            int topMargin = webViewManager.isNavigationBarVisible() ? 0 : 0; // 沉浸式模式下从顶部开始
            int bottomMargin = config.extendToNavigationBar ? 0 : 0; // 延伸到底部
            webViewManager.adjustWebViewLayout(topMargin, bottomMargin);
        }
        
        // 更新UserAgent
        updateUserAgentWithLayoutInfo(config);
        
        // 通知布局变化
        notifyLayoutChanged(config);
        
        Log.d(TAG, "Layout adjusted for immersive mode");
    }
    
    /**
     * 调整布局以适配普通模式
     */
    public void adjustLayoutForNormalMode(LayoutConfig config) {
        if (config == null) {
            config = new LayoutConfig();
        }
        
        this.isImmersiveMode = false;
        
        // 获取状态栏高度
        int statusBarHeight = statusBarManager.getStatusBarHeight();
        config.statusBarHeight = statusBarHeight;
        
        // 调整WebView布局
        if (webViewManager != null) {
            int topMargin = webViewManager.isNavigationBarVisible() ? 
                statusBarHeight + dpToPx(48) : statusBarHeight; // 从状态栏下方开始
            int bottomMargin = config.extendToNavigationBar ? 0 : 0; // 延伸到底部
            webViewManager.adjustWebViewLayout(topMargin, bottomMargin);
        }
        
        // 更新UserAgent
        updateUserAgentWithLayoutInfo(config);
        
        // 通知布局变化
        notifyLayoutChanged(config);
        
        Log.d(TAG, "Layout adjusted for normal mode");
    }
    
    /**
     * 动态调整布局
     */
    public void adjustLayout(boolean isImmersive, LayoutConfig config) {
        if (isImmersive) {
            adjustLayoutForImmersiveMode(config);
        } else {
            adjustLayoutForNormalMode(config);
        }
    }
    
    /**
     * 更新UserAgent中的布局信息
     */
    private void updateUserAgentWithLayoutInfo(LayoutConfig config) {
        if (webViewManager != null) {
            String originalUserAgent = getOriginalUserAgent();
            String customUserAgent = originalUserAgent + 
                " StatusBarHeight/" + config.statusBarHeight + 
                " NavigationBarHeight/" + config.navigationBarHeight + 
                " ImmersiveMode/" + (isImmersiveMode ? "true" : "false");
            
            webViewManager.updateUserAgent(customUserAgent);
            Log.d(TAG, "Updated UserAgent: " + customUserAgent);
        }
    }
    
    /**
     * 获取原始UserAgent
     */
    private String getOriginalUserAgent() {
        if (webViewManager != null && webViewManager.getWebView() != null) {
            return webViewManager.getWebView().getSettings().getUserAgentString();
        }
        return "";
    }
    
    /**
     * 计算状态栏高度
     */
    public int calculateStatusBarHeight() {
        return statusBarManager.getStatusBarHeight();
    }
    
    /**
     * 计算导航栏高度
     */
    public int calculateNavigationBarHeight() {
        return statusBarManager.getNavigationBarHeight();
    }
    
    /**
     * 检查是否为沉浸式模式
     */
    public boolean isImmersiveMode() {
        return isImmersiveMode;
    }
    
    /**
     * 设置沉浸式模式
     */
    public void setImmersiveMode(boolean immersive) {
        this.isImmersiveMode = immersive;
    }
    
    /**
     * 获取状态栏管理器
     */
    public StatusBarManager getStatusBarManager() {
        return statusBarManager;
    }
    
    /**
     * 获取WebView管理器
     */
    public WebViewManager getWebViewManager() {
        return webViewManager;
    }
    
    /**
     * 通知布局变化
     */
    private void notifyLayoutChanged(LayoutConfig config) {
        if (layoutChangeListener != null) {
            layoutChangeListener.onLayoutChanged(isImmersiveMode, config);
        }
    }
    
    /**
     * 通知状态栏高度变化
     */
    public void notifyStatusBarHeightChanged(int height) {
        if (layoutChangeListener != null) {
            layoutChangeListener.onStatusBarHeightChanged(height);
        }
    }
    
    /**
     * 通知导航栏高度变化
     */
    public void notifyNavigationBarHeightChanged(int height) {
        if (layoutChangeListener != null) {
            layoutChangeListener.onNavigationBarHeightChanged(height);
        }
    }
    
    /**
     * 获取屏幕尺寸信息
     */
    public ScreenInfo getScreenInfo() {
        return new ScreenInfo(activity);
    }
    
    /**
     * 屏幕信息类
     */
    public static class ScreenInfo {
        public int width;
        public int height;
        public int statusBarHeight;
        public int navigationBarHeight;
        public float density;
        public int densityDpi;
        
        public ScreenInfo(Activity activity) {
            android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
            this.width = metrics.widthPixels;
            this.height = metrics.heightPixels;
            this.density = metrics.density;
            this.densityDpi = metrics.densityDpi;
            
            // 获取状态栏高度
            int resourceId = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                this.statusBarHeight = activity.getResources().getDimensionPixelSize(resourceId);
            } else {
                this.statusBarHeight = (int) (24 * density + 0.5f);
            }
            
            // 获取导航栏高度
            resourceId = activity.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            if (resourceId > 0) {
                this.navigationBarHeight = activity.getResources().getDimensionPixelSize(resourceId);
            } else {
                this.navigationBarHeight = (int) (48 * density + 0.5f);
            }
        }
    }
    
    /**
     * dp转px
     */
    private int dpToPx(int dp) {
        return (int) (dp * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
