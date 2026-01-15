package com.energy.chery_android.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Log;

import com.energy.chery_android.utils.StatusBarManager.StatusBarConfig;
import com.energy.chery_android.utils.WebViewManager.WebViewConfig;
import com.energy.chery_android.utils.WebViewManager.NavigationBarConfig;
import com.energy.chery_android.utils.LayoutManager.LayoutConfig;

/**
 * 应用配置管理器
 * 统一管理应用的所有配置，包括状态栏、WebView、布局等配置
 */
public class AppConfigManager {
    private static final String TAG = "AppConfigManager";
    private static final String PREFS_NAME = "app_config";
    
    private static AppConfigManager instance;
    private Context context;
    private SharedPreferences preferences;
    
    // 配置键名
    private static final String KEY_IMMERSIVE_MODE = "immersive_mode";
    private static final String KEY_STATUS_BAR_COLOR = "status_bar_color";
    private static final String KEY_STATUS_BAR_LIGHT_TEXT = "status_bar_light_text";
    private static final String KEY_NAVIGATION_BAR_VISIBLE = "navigation_bar_visible";
    private static final String KEY_NAVIGATION_BAR_TITLE = "navigation_bar_title";
    private static final String KEY_NAVIGATION_BAR_BG_COLOR = "navigation_bar_bg_color";
    private static final String KEY_NAVIGATION_BAR_TITLE_COLOR = "navigation_bar_title_color";
    private static final String KEY_WEBVIEW_JS_ENABLED = "webview_js_enabled";
    private static final String KEY_WEBVIEW_DOM_STORAGE = "webview_dom_storage";
    private static final String KEY_WEBVIEW_CACHE_ENABLED = "webview_cache_enabled";
    private static final String KEY_ANIMATION_ENABLED = "animation_enabled";
    private static final String KEY_ANIMATION_DURATION = "animation_duration";
    
    private AppConfigManager(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * 获取单例实例
     */
    public static synchronized AppConfigManager getInstance(Context context) {
        if (instance == null) {
            instance = new AppConfigManager(context);
        }
        return instance;
    }
    
    /**
     * 获取状态栏配置
     */
    public StatusBarConfig getStatusBarConfig() {
        StatusBarConfig config = new StatusBarConfig();
        config.statusBarColor = preferences.getInt(KEY_STATUS_BAR_COLOR, Color.TRANSPARENT);
        config.isLightText = preferences.getBoolean(KEY_STATUS_BAR_LIGHT_TEXT, false);
        config.enableAnimation = preferences.getBoolean(KEY_ANIMATION_ENABLED, true);
        config.animationDuration = preferences.getLong(KEY_ANIMATION_DURATION, 300);
        return config;
    }
    
    /**
     * 保存状态栏配置
     */
    public void saveStatusBarConfig(StatusBarConfig config) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(KEY_STATUS_BAR_COLOR, config.statusBarColor);
        editor.putBoolean(KEY_STATUS_BAR_LIGHT_TEXT, config.isLightText);
        editor.putBoolean(KEY_ANIMATION_ENABLED, config.enableAnimation);
        editor.putLong(KEY_ANIMATION_DURATION, config.animationDuration);
        editor.apply();
        Log.d(TAG, "Status bar config saved");
    }
    
    /**
     * 获取WebView配置
     */
    public WebViewConfig getWebViewConfig() {
        WebViewConfig config = new WebViewConfig();
        config.enableJavaScript = preferences.getBoolean(KEY_WEBVIEW_JS_ENABLED, true);
        config.enableDomStorage = preferences.getBoolean(KEY_WEBVIEW_DOM_STORAGE, true);
        config.enableAppCache = preferences.getBoolean(KEY_WEBVIEW_CACHE_ENABLED, true);
        return config;
    }
    
    /**
     * 保存WebView配置
     */
    public void saveWebViewConfig(WebViewConfig config) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(KEY_WEBVIEW_JS_ENABLED, config.enableJavaScript);
        editor.putBoolean(KEY_WEBVIEW_DOM_STORAGE, config.enableDomStorage);
        editor.putBoolean(KEY_WEBVIEW_CACHE_ENABLED, config.enableAppCache);
        editor.apply();
        Log.d(TAG, "WebView config saved");
    }
    
    /**
     * 获取导航栏配置
     */
    public NavigationBarConfig getNavigationBarConfig() {
        NavigationBarConfig config = new NavigationBarConfig();
        config.visible = preferences.getBoolean(KEY_NAVIGATION_BAR_VISIBLE, false);
        config.title = preferences.getString(KEY_NAVIGATION_BAR_TITLE, "");
        config.backgroundColor = preferences.getInt(KEY_NAVIGATION_BAR_BG_COLOR, Color.WHITE);
        config.titleColor = preferences.getInt(KEY_NAVIGATION_BAR_TITLE_COLOR, Color.BLACK);
        return config;
    }
    
    /**
     * 保存导航栏配置
     */
    public void saveNavigationBarConfig(NavigationBarConfig config) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(KEY_NAVIGATION_BAR_VISIBLE, config.visible);
        editor.putString(KEY_NAVIGATION_BAR_TITLE, config.title);
        editor.putInt(KEY_NAVIGATION_BAR_BG_COLOR, config.backgroundColor);
        editor.putInt(KEY_NAVIGATION_BAR_TITLE_COLOR, config.titleColor);
        editor.apply();
        Log.d(TAG, "Navigation bar config saved");
    }
    
    /**
     * 获取布局配置
     */
    public LayoutConfig getLayoutConfig() {
        LayoutConfig config = new LayoutConfig();
        config.adjustForStatusBar = true;
        config.adjustForNavigationBar = true;
        config.extendToStatusBar = preferences.getBoolean(KEY_IMMERSIVE_MODE, false);
        config.extendToNavigationBar = false;
        return config;
    }
    
    /**
     * 保存布局配置
     */
    public void saveLayoutConfig(LayoutConfig config) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(KEY_IMMERSIVE_MODE, config.extendToStatusBar);
        editor.apply();
        Log.d(TAG, "Layout config saved");
    }
    
    /**
     * 检查是否启用沉浸式模式
     */
    public boolean isImmersiveModeEnabled() {
        return preferences.getBoolean(KEY_IMMERSIVE_MODE, false);
    }
    
    /**
     * 设置沉浸式模式
     */
    public void setImmersiveModeEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_IMMERSIVE_MODE, enabled).apply();
        Log.d(TAG, "Immersive mode set to: " + enabled);
    }
    
    /**
     * 检查是否启用动画
     */
    public boolean isAnimationEnabled() {
        return preferences.getBoolean(KEY_ANIMATION_ENABLED, true);
    }
    
    /**
     * 设置动画启用状态
     */
    public void setAnimationEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_ANIMATION_ENABLED, enabled).apply();
        Log.d(TAG, "Animation enabled set to: " + enabled);
    }
    
    /**
     * 获取动画持续时间
     */
    public long getAnimationDuration() {
        return preferences.getLong(KEY_ANIMATION_DURATION, 300);
    }
    
    /**
     * 设置动画持续时间
     */
    public void setAnimationDuration(long duration) {
        preferences.edit().putLong(KEY_ANIMATION_DURATION, duration).apply();
        Log.d(TAG, "Animation duration set to: " + duration);
    }
    
    /**
     * 重置所有配置为默认值
     */
    public void resetToDefaults() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.apply();
        Log.d(TAG, "All configs reset to defaults");
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
    
    /**
     * 获取默认WebView配置
     */
    public static WebViewConfig getDefaultWebViewConfig() {
        WebViewConfig config = new WebViewConfig();
        config.enableJavaScript = true;
        config.enableDomStorage = true;
        config.enableDatabase = true;
        config.enableAppCache = true;
        config.enableGeolocation = true;
        config.enableMixedContent = true;
        config.backgroundColor = Color.WHITE;
        return config;
    }
    
    /**
     * 获取默认导航栏配置
     */
    public static NavigationBarConfig getDefaultNavigationBarConfig() {
        NavigationBarConfig config = new NavigationBarConfig();
        config.visible = false;
        config.title = "";
        config.backgroundColor = Color.WHITE;
        config.titleColor = Color.BLACK;
        config.backButtonColor = Color.BLACK;
        config.closeButtonColor = Color.BLACK;
        config.height = 48;
        return config;
    }
    
    /**
     * 获取默认布局配置
     */
    public static LayoutConfig getDefaultLayoutConfig() {
        LayoutConfig config = new LayoutConfig();
        config.adjustForStatusBar = true;
        config.adjustForNavigationBar = true;
        config.extendToStatusBar = false;
        config.extendToNavigationBar = false;
        return config;
    }
    
    /**
     * 导出配置为JSON字符串
     */
    public String exportConfig() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"immersiveMode\":").append(isImmersiveModeEnabled()).append(",");
        json.append("\"statusBarColor\":").append(preferences.getInt(KEY_STATUS_BAR_COLOR, Color.TRANSPARENT)).append(",");
        json.append("\"statusBarLightText\":").append(preferences.getBoolean(KEY_STATUS_BAR_LIGHT_TEXT, false)).append(",");
        json.append("\"navigationBarVisible\":").append(preferences.getBoolean(KEY_NAVIGATION_BAR_VISIBLE, false)).append(",");
        json.append("\"animationEnabled\":").append(preferences.getBoolean(KEY_ANIMATION_ENABLED, true)).append(",");
        json.append("\"animationDuration\":").append(preferences.getLong(KEY_ANIMATION_DURATION, 300));
        json.append("}");
        return json.toString();
    }
    
    /**
     * 从JSON字符串导入配置
     */
    public void importConfig(String jsonConfig) {
        try {
            // 这里可以添加JSON解析逻辑
            // 为了简化，这里只是示例
            Log.d(TAG, "Importing config from JSON: " + jsonConfig);
        } catch (Exception e) {
            Log.e(TAG, "Failed to import config", e);
        }
    }
}
