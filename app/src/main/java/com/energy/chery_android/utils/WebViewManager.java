package com.energy.chery_android.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.jd.hybrid.JDWebView;


/**
 * WebView管理器
 * 封装WebView相关的功能，包括创建、配置、布局管理等
 */
public class WebViewManager {
    private static final String TAG = "WebViewManager";
    
    private Activity activity;
    private JDWebView baseWebView;
    private RelativeLayout navigationBar;
    private TextView titleView;
    private ImageView backButton;
    private ImageView closeButton;
    private boolean isNavigationBarVisible = false;
    
    /**
     * WebView配置类
     */
    public static class WebViewConfig {
        public boolean enableJavaScript = true;
        public boolean enableDomStorage = true;
        public boolean enableDatabase = true;
        public boolean enableAppCache = true;
        public boolean enableGeolocation = true;
        public boolean enableMixedContent = true;
        public String userAgent = "";
        public int backgroundColor = Color.WHITE;
        
        public WebViewConfig() {}
    }
    
    /**
     * 导航栏配置类
     */
    public static class NavigationBarConfig {
        public boolean visible = false;
        public String title = "";
        public int backgroundColor = Color.WHITE;
        public int titleColor = Color.BLACK;
        public int backButtonColor = Color.BLACK;
        public int closeButtonColor = Color.BLACK;
        public int height = 48; // dp
        
        public NavigationBarConfig() {}
    }

    /**
     * WebView事件监听器
     */
    public interface OnWebViewEventListener {
        void onBackClick();
        void onCloseClick();
        void onPageStarted(String url);
        void onPageFinished(String url);
        void onReceivedError(String error);
    }

    private OnWebViewEventListener eventListener;

    public WebViewManager(Activity activity) {
        this.activity = activity;
    }
    
    /**
     * 设置WebView事件监听器
     */
    public void setOnWebViewEventListener(OnWebViewEventListener listener) {
        this.eventListener = listener;
    }

    /**
     * 初始化WebView
     */
    public void initWebView(int containerId, WebViewConfig config) {
        if (config == null) {
            config = new WebViewConfig();
        }

        // 创建BaseWebView实例
        baseWebView = new JDWebView(activity);

        configureWebView(baseWebView, config);

        // 将WebView添加到容器中
        FrameLayout container = activity.findViewById(containerId);
        if (container != null) {
            // 创建导航栏
            initNavigationBar(container, new NavigationBarConfig());
            // 添加WebView到容器
            FrameLayout.LayoutParams webViewParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            );
            container.addView(baseWebView, webViewParams);
        }
        Log.d(TAG, "WebView initialized successfully");
    }
    
    /**
     * 配置WebView
     */
    private void configureWebView(JDWebView webView, WebViewConfig config) {
        // 基本设置
        baseWebView.getSettings().setJavaScriptEnabled(config.enableJavaScript);
        baseWebView.getSettings().setDomStorageEnabled(config.enableDomStorage);
        baseWebView.getSettings().setDatabaseEnabled(config.enableDatabase);
        // webViewInstance.getSettings().setAppCacheEnabled(config.enableAppCache);
        baseWebView.getSettings().setGeolocationEnabled(config.enableGeolocation);
        baseWebView.getSettings().setMixedContentMode(
                config.enableMixedContent ?
                        android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW :
                        android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        );
        if (!config.userAgent.isEmpty()) {
            baseWebView.getSettings().setUserAgentString(config.userAgent);
        }
        // 设置背景色
        baseWebView.setBackgroundColor(config.backgroundColor);
    }
    
    /**
     * 初始化导航栏
     */
    private void initNavigationBar(FrameLayout container, NavigationBarConfig config) {
        // 创建导航栏
        navigationBar = new RelativeLayout(activity);
        navigationBar.setVisibility(config.visible ? View.VISIBLE : View.GONE);
        navigationBar.setBackgroundColor(config.backgroundColor);
        
        // 创建标题视图
        titleView = new TextView(activity);
        titleView.setTextSize(16);
        titleView.setTextColor(config.titleColor);
        titleView.setText(config.title);
        
        RelativeLayout.LayoutParams titleParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        navigationBar.addView(titleView, titleParams);
        
        // 创建返回按钮
        backButton = new ImageView(activity);
        backButton.setImageResource(com.energy.chery_android.R.drawable.ic_arrow_back);
        backButton.setColorFilter(config.backButtonColor, android.graphics.PorterDuff.Mode.SRC_IN);
        backButton.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        
        RelativeLayout.LayoutParams backParams = new RelativeLayout.LayoutParams(
                dpToPx(48), dpToPx(48)
        );
        backParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        backParams.addRule(RelativeLayout.CENTER_VERTICAL);
        backButton.setOnClickListener(v -> {
            if (eventListener != null) {
                eventListener.onBackClick();
            }
        });
        navigationBar.addView(backButton, backParams);
        
        // 创建关闭按钮
        closeButton = new ImageView(activity);
        closeButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        closeButton.setColorFilter(config.closeButtonColor, android.graphics.PorterDuff.Mode.SRC_IN);
        closeButton.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        
        RelativeLayout.LayoutParams closeParams = new RelativeLayout.LayoutParams(
                dpToPx(48), dpToPx(48)
        );
        closeParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        closeParams.addRule(RelativeLayout.CENTER_VERTICAL);
        closeButton.setOnClickListener(v -> {
            if (eventListener != null) {
                eventListener.onCloseClick();
            }
        });
        navigationBar.addView(closeButton, closeParams);
        
        // 设置导航栏布局参数
        FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dpToPx(config.height)
        );
        container.addView(navigationBar, navParams);
        
        isNavigationBarVisible = config.visible;
    }
    
    /**
     * 加载URL
     */
    public void loadUrl(String url) {
        if (baseWebView != null) {
            baseWebView.loadUrl(url);
            Log.d(TAG, "Loading URL: " + url);
        }
    }
    
    /**
     * 设置导航栏可见性
     */
    public void setNavigationBarVisible(boolean visible) {
        isNavigationBarVisible = visible;
        if (navigationBar != null) {
            navigationBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        adjustWebViewLayout();
    }
    
    /**
     * 设置导航栏标题
     */
    public void setNavigationBarTitle(String title) {
        if (titleView != null) {
            titleView.setText(title);
        }
    }
    
    /**
     * 设置导航栏背景颜色
     */
    public void setNavigationBarBackgroundColor(int color) {
        if (navigationBar != null) {
            navigationBar.setBackgroundColor(color);
        }
    }
    
    /**
     * 设置导航栏标题颜色
     */
    public void setNavigationBarTitleColor(int color) {
        if (titleView != null) {
            titleView.setTextColor(color);
        }
    }
    
    /**
     * 调整WebView布局
     */
    public void adjustWebViewLayout(int topMargin, int bottomMargin) {
        if (baseWebView != null && baseWebView.getParent() instanceof FrameLayout) {
            FrameLayout.LayoutParams webViewParams = (FrameLayout.LayoutParams) baseWebView.getLayoutParams();
            if (webViewParams != null) {
                webViewParams.topMargin = topMargin;
                webViewParams.bottomMargin = bottomMargin;
                baseWebView.requestLayout();
            }
        }
    }
    
    /**
     * 调整WebView布局（自动计算）
     */
    private void adjustWebViewLayout() {
        int topMargin = isNavigationBarVisible ? dpToPx(48) : 0;
        adjustWebViewLayout(topMargin, 0);
    }
    
    /**
     * 更新UserAgent
     */
    public void updateUserAgent(String customUserAgent) {
        baseWebView.getSettings().setUserAgentString(customUserAgent);
    }
    
    /**
     * 检查是否可以返回
     */
    public boolean canGoBack() {
        return baseWebView != null && baseWebView.canGoBack();
    }
    
    /**
     * 返回上一页
     */
    public void goBack() {
        if (baseWebView != null) {
            baseWebView.goBack();
        }
    }
    
    /**
     * 刷新页面
     */
    public void reload() {
        if (baseWebView != null) {
            baseWebView.reload();
        }
    }
    
    /**
     * 暂停WebView
     */
    public void onPause() {
        if (baseWebView != null) {
            baseWebView.onPause();
        }
    }
    
    /**
     * 恢复WebView
     */
    public void onResume() {
        if (baseWebView != null) {
            baseWebView.onResume();
        }
    }
    
    /**
     * 销毁WebView
     */
    public void destroy() {
        if (baseWebView != null) {
            baseWebView.destroy();
            baseWebView = null;
        }
    }
    
    /**
     * 获取BaseWebView实例
     */
    public JDWebView getBaseWebView() {
        return baseWebView;
    }
    
    /**
     * 获取WebView实例
     */
    public WebView getWebView() {
        return null;
//        return baseWebView != null ? baseWebView.getWebView() : null;
    }
    
    /**
     * 检查导航栏是否可见
     */
    public boolean isNavigationBarVisible() {
        return isNavigationBarVisible;
    }
    
    /**
     * dp转px
     */
    private int dpToPx(int dp) {
        return (int) (dp * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
