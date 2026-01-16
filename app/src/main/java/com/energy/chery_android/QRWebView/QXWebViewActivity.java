package com.energy.chery_android.QRWebView;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.jd.hybrid.JDWebView;
import com.jd.plugins.ClosureRegistry;
import com.jd.plugins.QXBasePlugin;
import com.jd.plugins.QXBridgePluginRegister;

import org.json.JSONArray;
import org.json.JSONObject;

public class QXWebViewActivity extends AppCompatActivity {

    private static final int NAV_BAR_HEIGHT_DP = 48;
    private static final int STATUS_BAR_DEFAULT_DP = 24;

    private FrameLayout root;
    protected JDWebView webView;
    private RelativeLayout navBar;
    private TextView titleView;
    private TextView backBtn;
    private boolean immersive = false;
    private boolean showNavBar = false;
    private String targetUrl;

    private static final String TAG = "QXWebViewActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        root = new FrameLayout(this);
        setContentView(root);
        initViews();

        setImmersiveStatusBar(true, true);
        setNavBarVisible(false);
        setTargetUrl("https://fr.dongxie.top/fr/#/");

        QXBridgePluginRegister.INSTANCE.registerAllPlugins(webView);
    }

    public void setTargetUrl(String url) {
        this.targetUrl = url;
        if (webView != null) {
            webView.loadUrl(targetUrl);
        }
    }

    private void initViews() {
        webView = new JDWebView(this);
        setupUA();
        setupWebChromeClient();
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        initNavBar();
        updateLayout();
    }

    /**
     * 配置WebChromeClient以处理JavaScript对话框
     * 设置alert、confirm、prompt的原生实现
     */
    private void setupWebChromeClient() {
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                // 使用原生AlertDialog实现JavaScript的alert功能
                AlertDialog.Builder builder = new AlertDialog.Builder(QXWebViewActivity.this);
                builder.setTitle("提示")
                        .setMessage(message)
                        .setPositiveButton("确定", (dialog, which) -> result.confirm())
                        .setCancelable(false)
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                // 使用原生AlertDialog实现JavaScript的confirm功能
                AlertDialog.Builder builder = new AlertDialog.Builder(QXWebViewActivity.this);
                builder.setTitle("确认")
                        .setMessage(message)
                        .setPositiveButton("确定", (dialog, which) -> result.confirm())
                        .setNegativeButton("取消", (dialog, which) -> result.cancel())
                        .setCancelable(false)
                        .show();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                // 使用原生AlertDialog实现JavaScript的prompt功能
                final EditText input = new EditText(QXWebViewActivity.this);
                input.setText(defaultValue);
                AlertDialog.Builder builder = new AlertDialog.Builder(QXWebViewActivity.this);
                builder.setTitle("输入")
                        .setMessage(message)
                        .setView(input)
                        .setPositiveButton("确定", (dialog, which) -> result.confirm(input.getText().toString()))
                        .setNegativeButton("取消", (dialog, which) -> result.cancel())
                        .setCancelable(false)
                        .show();
                return true;
            }
        });
    }
    private void initNavBar() {
        navBar = new RelativeLayout(this);
        navBar.setBackgroundColor(Color.WHITE);
        navBar.setVisibility(View.GONE);

        titleView = new TextView(this);
        titleView.setTextSize(16);
        titleView.setTextColor(Color.BLACK);
        RelativeLayout.LayoutParams titleLp =
                new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
        titleLp.addRule(RelativeLayout.CENTER_IN_PARENT);
        navBar.addView(titleView, titleLp);

        backBtn = new TextView(this);
        backBtn.setText("返回");
        backBtn.setGravity(Gravity.CENTER);
        backBtn.setOnClickListener(v -> onBackPressed());
        RelativeLayout.LayoutParams backLp =
                new RelativeLayout.LayoutParams(dp2px(48), dp2px(48));
        backLp.addRule(RelativeLayout.ALIGN_PARENT_START);
        backLp.addRule(RelativeLayout.CENTER_VERTICAL);
        navBar.addView(backBtn, backLp);

        root.addView(navBar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp2px(NAV_BAR_HEIGHT_DP)
        ));
    }

    private void updateLayout() {
        //int statusBarHeight = getStatusBarHeight();
        //导航栏：永远在状态栏下面
        FrameLayout.LayoutParams navLp =
                (FrameLayout.LayoutParams) navBar.getLayoutParams();
        //navLp.topMargin = statusBarHeight;
        navLp.topMargin = immersive ? getStatusBarHeight() : 0;
        navBar.setLayoutParams(navLp);
        //WebView：永远从「状态栏 + 导航栏」下面开始
        FrameLayout.LayoutParams webLp =
                (FrameLayout.LayoutParams) webView.getLayoutParams();
        webLp.topMargin = getWebViewTopOffset();
        webView.setLayoutParams(webLp);
        navBar.bringToFront();
    }

    private int getWebViewTopOffset() {
        int offset = 0;
        // 只有沉浸式时，才需要手动避让状态栏
        /* if (immersive) {
            offset += getStatusBarHeight();
        }*/
        if (showNavBar) {
            offset += dp2px(NAV_BAR_HEIGHT_DP);
        }
        return offset;
    }
    /**
     * 设置沉浸式状态栏
     *
     * @param enable 是否启用沉浸式
     * @param lightText 是否使用亮色状态栏字体（仅在 Android 6.0+ 有效）
     */
    protected void setImmersiveStatusBar(boolean enable, boolean lightText) {
        immersive = enable;
        Window window = getWindow();
        if (enable) {
            window.setStatusBarColor(Color.TRANSPARENT);
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            if (!lightText && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        } else {
            window.getDecorView().setSystemUiVisibility(0);
            window.setStatusBarColor(Color.WHITE);
        }
    }

    protected void setNavBarVisible(boolean visible) {
        showNavBar = visible;
        navBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        updateLayout();
    }

    protected void setNavBarTitle(String title) {
        titleView.setText(title);
    }

    private void setupUA() {
        String ua = webView.getSettings().getUserAgentString();
        ua += " StatusBarHeight/" + getStatusBarHeight()
                + " NavBarHeight/" + dp2px(NAV_BAR_HEIGHT_DP);
        webView.getSettings().setUserAgentString(ua);
    }

    private int dp2px(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    @SuppressLint("InternalInsetResource")
    private int getStatusBarHeight() {
        int resId = getResources().getIdentifier(
                "status_bar_height", "dimen", "android");
        return resId > 0
                ? getResources().getDimensionPixelSize(resId)
                : dp2px(STATUS_BAR_DEFAULT_DP);
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        String result = null;
        try {
            JSONObject json = new JSONObject();
            json.put("requestCode", requestCode);
            json.put("permissions", new JSONArray(permissions));
            json.put("grantResults", new JSONArray(grantResults));
            result = json.toString();
            String callbackId = "onRequestPermissionsResult";
            ClosureRegistry.INSTANCE.invoke(callbackId, result);
        } catch (Exception e) {
            Log.d(TAG, e.toString());
        }
    }
}
