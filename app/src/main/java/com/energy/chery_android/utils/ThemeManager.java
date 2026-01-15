package com.energy.chery_android.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Log;

/**
 * 主题管理器
 * 管理应用的主题、颜色、样式等
 */
public class ThemeManager {
    private static final String TAG = "ThemeManager";
    private static final String PREFS_NAME = "theme_config";
    
    private static ThemeManager instance;
    private Context context;
    private SharedPreferences preferences;
    
    // 主题类型
    public enum ThemeType {
        LIGHT("light"),
        DARK("dark"),
        AUTO("auto");
        
        private final String value;
        
        ThemeType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        public static ThemeType fromValue(String value) {
            for (ThemeType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            return LIGHT;
        }
    }
    
    // 主题配置类
    public static class ThemeConfig {
        public ThemeType themeType = ThemeType.LIGHT;
        public int primaryColor = Color.parseColor("#2196F3");
        public int primaryDarkColor = Color.parseColor("#1976D2");
        public int accentColor = Color.parseColor("#FF4081");
        public int backgroundColor = Color.WHITE;
        public int textColor = Color.BLACK;
        public int textColorSecondary = Color.parseColor("#757575");
        public int statusBarColor = Color.TRANSPARENT;
        public boolean statusBarLightText = false;
        public int navigationBarColor = Color.WHITE;
        public boolean navigationBarLightText = false;
        
        public ThemeConfig() {}
    }
    
    // 预定义主题
    public static class PredefinedThemes {
        public static final ThemeConfig LIGHT_THEME = new ThemeConfig() {{
            themeType = ThemeType.LIGHT;
            primaryColor = Color.parseColor("#2196F3");
            primaryDarkColor = Color.parseColor("#1976D2");
            accentColor = Color.parseColor("#FF4081");
            backgroundColor = Color.WHITE;
            textColor = Color.BLACK;
            textColorSecondary = Color.parseColor("#757575");
            statusBarColor = Color.TRANSPARENT;
            statusBarLightText = false;
            navigationBarColor = Color.WHITE;
            navigationBarLightText = false;
        }};
        
        public static final ThemeConfig DARK_THEME = new ThemeConfig() {{
            themeType = ThemeType.DARK;
            primaryColor = Color.parseColor("#2196F3");
            primaryDarkColor = Color.parseColor("#1976D2");
            accentColor = Color.parseColor("#FF4081");
            backgroundColor = Color.parseColor("#121212");
            textColor = Color.WHITE;
            textColorSecondary = Color.parseColor("#B3FFFFFF");
            statusBarColor = Color.TRANSPARENT;
            statusBarLightText = true;
            navigationBarColor = Color.parseColor("#121212");
            navigationBarLightText = true;
        }};
        
        public static final ThemeConfig BLUE_THEME = new ThemeConfig() {{
            themeType = ThemeType.LIGHT;
            primaryColor = Color.parseColor("#1976D2");
            primaryDarkColor = Color.parseColor("#0D47A1");
            accentColor = Color.parseColor("#03DAC6");
            backgroundColor = Color.WHITE;
            textColor = Color.BLACK;
            textColorSecondary = Color.parseColor("#757575");
            statusBarColor = Color.TRANSPARENT;
            statusBarLightText = false;
            navigationBarColor = Color.WHITE;
            navigationBarLightText = false;
        }};
        
        public static final ThemeConfig GREEN_THEME = new ThemeConfig() {{
            themeType = ThemeType.LIGHT;
            primaryColor = Color.parseColor("#4CAF50");
            primaryDarkColor = Color.parseColor("#388E3C");
            accentColor = Color.parseColor("#8BC34A");
            backgroundColor = Color.WHITE;
            textColor = Color.BLACK;
            textColorSecondary = Color.parseColor("#757575");
            statusBarColor = Color.TRANSPARENT;
            statusBarLightText = false;
            navigationBarColor = Color.WHITE;
            navigationBarLightText = false;
        }};
    }
    
    private ThemeConfig currentTheme;
    
    private ThemeManager(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadTheme();
    }
    
    /**
     * 获取单例实例
     */
    public static synchronized ThemeManager getInstance(Context context) {
        if (instance == null) {
            instance = new ThemeManager(context);
        }
        return instance;
    }
    
    /**
     * 加载主题配置
     */
    private void loadTheme() {
        String themeTypeValue = preferences.getString("theme_type", ThemeType.LIGHT.getValue());
        ThemeType themeType = ThemeType.fromValue(themeTypeValue);
        
        currentTheme = new ThemeConfig();
        currentTheme.themeType = themeType;
        currentTheme.primaryColor = preferences.getInt("primary_color", Color.parseColor("#2196F3"));
        currentTheme.primaryDarkColor = preferences.getInt("primary_dark_color", Color.parseColor("#1976D2"));
        currentTheme.accentColor = preferences.getInt("accent_color", Color.parseColor("#FF4081"));
        currentTheme.backgroundColor = preferences.getInt("background_color", Color.WHITE);
        currentTheme.textColor = preferences.getInt("text_color", Color.BLACK);
        currentTheme.textColorSecondary = preferences.getInt("text_color_secondary", Color.parseColor("#757575"));
        currentTheme.statusBarColor = preferences.getInt("status_bar_color", Color.TRANSPARENT);
        currentTheme.statusBarLightText = preferences.getBoolean("status_bar_light_text", false);
        currentTheme.navigationBarColor = preferences.getInt("navigation_bar_color", Color.WHITE);
        currentTheme.navigationBarLightText = preferences.getBoolean("navigation_bar_light_text", false);
        
        Log.d(TAG, "Theme loaded: " + themeType);
    }
    
    /**
     * 保存主题配置
     */
    public void saveTheme(ThemeConfig theme) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("theme_type", theme.themeType.getValue());
        editor.putInt("primary_color", theme.primaryColor);
        editor.putInt("primary_dark_color", theme.primaryDarkColor);
        editor.putInt("accent_color", theme.accentColor);
        editor.putInt("background_color", theme.backgroundColor);
        editor.putInt("text_color", theme.textColor);
        editor.putInt("text_color_secondary", theme.textColorSecondary);
        editor.putInt("status_bar_color", theme.statusBarColor);
        editor.putBoolean("status_bar_light_text", theme.statusBarLightText);
        editor.putInt("navigation_bar_color", theme.navigationBarColor);
        editor.putBoolean("navigation_bar_light_text", theme.navigationBarLightText);
        editor.apply();
        
        this.currentTheme = theme;
        Log.d(TAG, "Theme saved: " + theme.themeType);
    }
    
    /**
     * 获取当前主题
     */
    public ThemeConfig getCurrentTheme() {
        return currentTheme;
    }
    
    /**
     * 设置主题类型
     */
    public void setThemeType(ThemeType themeType) {
        ThemeConfig theme = getPredefinedTheme(themeType);
        saveTheme(theme);
    }
    
    /**
     * 获取预定义主题
     */
    public ThemeConfig getPredefinedTheme(ThemeType themeType) {
        switch (themeType) {
            case LIGHT:
                return PredefinedThemes.LIGHT_THEME;
            case DARK:
                return PredefinedThemes.DARK_THEME;
            case AUTO:
                // 自动主题根据系统设置决定
                return isSystemDarkMode() ? PredefinedThemes.DARK_THEME : PredefinedThemes.LIGHT_THEME;
            default:
                return PredefinedThemes.LIGHT_THEME;
        }
    }
    
    /**
     * 检查系统是否为深色模式
     */
    private boolean isSystemDarkMode() {
        // 这里可以根据系统设置判断是否为深色模式
        // 简化实现，实际应该检查系统主题
        return false;
    }
    
    /**
     * 应用主题到状态栏管理器
     */
    public void applyToStatusBarManager(StatusBarManager statusBarManager) {
        if (statusBarManager != null) {
            StatusBarManager.StatusBarConfig config = new StatusBarManager.StatusBarConfig();
            config.statusBarColor = currentTheme.statusBarColor;
            config.isLightText = currentTheme.statusBarLightText;
            
            if (statusBarManager.isImmersiveModeEnabled()) {
                statusBarManager.enableImmersiveStatusBar(config);
            } else {
                statusBarManager.disableImmersiveStatusBar(config);
            }
        }
    }
    
    /**
     * 应用主题到WebView管理器
     */
    public void applyToWebViewManager(WebViewManager webViewManager) {
        if (webViewManager != null) {
            // 设置导航栏背景色
            webViewManager.setNavigationBarBackgroundColor(currentTheme.primaryColor);
            
            // 设置导航栏标题颜色
            int titleColor = CommonUtils.ColorUtils.getContrastColor(currentTheme.primaryColor);
            webViewManager.setNavigationBarTitleColor(titleColor);
        }
    }
    
    /**
     * 获取主色调
     */
    public int getPrimaryColor() {
        return currentTheme.primaryColor;
    }
    
    /**
     * 获取主色调深色版本
     */
    public int getPrimaryDarkColor() {
        return currentTheme.primaryDarkColor;
    }
    
    /**
     * 获取强调色
     */
    public int getAccentColor() {
        return currentTheme.accentColor;
    }
    
    /**
     * 获取背景色
     */
    public int getBackgroundColor() {
        return currentTheme.backgroundColor;
    }
    
    /**
     * 获取文字颜色
     */
    public int getTextColor() {
        return currentTheme.textColor;
    }
    
    /**
     * 获取次要文字颜色
     */
    public int getTextColorSecondary() {
        return currentTheme.textColorSecondary;
    }
    
    /**
     * 获取状态栏颜色
     */
    public int getStatusBarColor() {
        return currentTheme.statusBarColor;
    }
    
    /**
     * 检查状态栏是否为浅色文字
     */
    public boolean isStatusBarLightText() {
        return currentTheme.statusBarLightText;
    }
    
    /**
     * 获取导航栏颜色
     */
    public int getNavigationBarColor() {
        return currentTheme.navigationBarColor;
    }
    
    /**
     * 检查导航栏是否为浅色文字
     */
    public boolean isNavigationBarLightText() {
        return currentTheme.navigationBarLightText;
    }
    
    /**
     * 获取当前主题类型
     */
    public ThemeType getCurrentThemeType() {
        return currentTheme.themeType;
    }
    
    /**
     * 检查是否为深色主题
     */
    public boolean isDarkTheme() {
        return currentTheme.themeType == ThemeType.DARK || 
               (currentTheme.themeType == ThemeType.AUTO && isSystemDarkMode());
    }
    
    /**
     * 检查是否为浅色主题
     */
    public boolean isLightTheme() {
        return !isDarkTheme();
    }
    
    /**
     * 获取对比色（用于文字等）
     */
    public int getContrastColor(int backgroundColor) {
        return CommonUtils.ColorUtils.getContrastColor(backgroundColor);
    }
    
    /**
     * 获取半透明颜色
     */
    public int getTransparentColor(int color, float alpha) {
        return CommonUtils.ColorUtils.adjustAlpha(color, alpha);
    }
    
    /**
     * 重置为默认主题
     */
    public void resetToDefault() {
        saveTheme(PredefinedThemes.LIGHT_THEME);
        Log.d(TAG, "Theme reset to default");
    }
    
    /**
     * 导出主题配置
     */
    public String exportTheme() {
        return String.format(
            "{\"themeType\":\"%s\",\"primaryColor\":\"%s\",\"backgroundColor\":\"%s\",\"textColor\":\"%s\"}",
            currentTheme.themeType.getValue(),
            CommonUtils.ColorUtils.toHexString(currentTheme.primaryColor),
            CommonUtils.ColorUtils.toHexString(currentTheme.backgroundColor),
            CommonUtils.ColorUtils.toHexString(currentTheme.textColor)
        );
    }
    
    /**
     * 导入主题配置
     */
    public void importTheme(String themeJson) {
        try {
            // 这里可以添加JSON解析逻辑
            // 为了简化，这里只是示例
            Log.d(TAG, "Importing theme from JSON: " + themeJson);
        } catch (Exception e) {
            Log.e(TAG, "Failed to import theme", e);
        }
    }
}
