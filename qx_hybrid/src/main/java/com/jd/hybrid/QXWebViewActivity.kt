package com.jd.hybrid

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsetsController
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jd.plugins.ClosureRegistry
import com.jd.plugins.QXBridgePluginRegister
import org.json.JSONArray
import org.json.JSONObject


open class QXWebViewActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "QXWebViewActivity"
        private const val NAV_BAR_HEIGHT_DP = 48
        private const val STATUS_BAR_DEFAULT_DP = 24

        const val EXTRA_URL = "extra_url"
        const val EXTRA_IMMERSIVE = "extra_immersive"
        const val EXTRA_SHOW_NAV_BAR = "extra_show_nav_bar"
        const val EXTRA_NAV_TITLE = "extra_nav_title"
    }

    private lateinit var root: FrameLayout
    protected lateinit var webView: JDWebView
        private set
    private lateinit var navBar: RelativeLayout
    private lateinit var titleView: TextView
    private lateinit var backBtn: TextView

    private var isImmersive = false
    private var isNavBarVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        setContentView(root)
        initViews()
        setupBackPressedHandler()
        // 从 Intent 读取配置
        applyIntentConfig()
        QXBridgePluginRegister.registerAllPlugins(webView)
    }


    private fun applyIntentConfig() {
        val immersiveMode = intent.getBooleanExtra(EXTRA_IMMERSIVE, true)
        val showNav = intent.getBooleanExtra(EXTRA_SHOW_NAV_BAR, false)

        setImmersiveStatusBar(immersiveMode, lightStatusBar = true)
        setNavBarVisible(showNav)
        intent.getStringExtra(EXTRA_NAV_TITLE)?.let { setNavBarTitle(it) }
        intent.getStringExtra(EXTRA_URL)?.let { loadUrl(it) }
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    private fun initViews() {
        webView = createWebView()
        setupUA()
        setupWebChromeClient()
        root.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        initNavBar()
        updateLayout()
    }

    /**
     * 创建 WebView
     */
    protected open fun createWebView(): JDWebView = JDWebView(this)

    private fun setupWebChromeClient() {
        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                return this@QXWebViewActivity.onJsAlert(message, result)
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                return this@QXWebViewActivity.onJsConfirm(message, result)
            }

            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                return this@QXWebViewActivity.onJsPrompt(message, defaultValue, result)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                this@QXWebViewActivity.onReceivedTitle(title)
            }
        }
    }

    // region JS 对话框处理
    protected open fun onJsAlert(message: String?, result: JsResult?): Boolean {
        AlertDialog.Builder(this)
            .setTitle("提示")
            .setMessage(message)
            .setPositiveButton("确定") { _, _ -> result?.confirm() }
            .setCancelable(false)
            .show()
        return true
    }

    protected open fun onJsConfirm(message: String?, result: JsResult?): Boolean {
        AlertDialog.Builder(this)
            .setTitle("确认")
            .setMessage(message)
            .setPositiveButton("确定") { _, _ -> result?.confirm() }
            .setNegativeButton("取消") { _, _ -> result?.cancel() }
            .setCancelable(false)
            .show()
        return true
    }

    protected open fun onJsPrompt(message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
        val input = EditText(this).apply { setText(defaultValue) }
        AlertDialog.Builder(this)
            .setTitle("输入")
            .setMessage(message)
            .setView(input)
            .setPositiveButton("确定") { _, _ -> result?.confirm(input.text.toString()) }
            .setNegativeButton("取消") { _, _ -> result?.cancel() }
            .setCancelable(false)
            .show()
        return true
    }

    protected open fun onReceivedTitle(title: String?) {
        // 子类可重写处理标题变化
    }

    // endregion


    // region 导航栏
    private fun initNavBar() {
        navBar = RelativeLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            visibility = View.GONE
        }

        titleView = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.BLACK)
        }
        navBar.addView(titleView, RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        ).apply { addRule(RelativeLayout.CENTER_IN_PARENT) })

        backBtn = TextView(this).apply {
            text = "返回"
            gravity = Gravity.CENTER
            setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }
        navBar.addView(backBtn, RelativeLayout.LayoutParams(dp2px(48f), dp2px(48f)).apply {
            addRule(RelativeLayout.ALIGN_PARENT_START)
            addRule(RelativeLayout.CENTER_VERTICAL)
        })

        root.addView(navBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dp2px(NAV_BAR_HEIGHT_DP.toFloat())
        ))
    }

    private fun updateLayout() {
        (navBar.layoutParams as FrameLayout.LayoutParams).apply {
            topMargin = if (isImmersive) getStatusBarHeight() else 0
            navBar.layoutParams = this
        }

        (webView.layoutParams as FrameLayout.LayoutParams).apply {
            topMargin = if (isNavBarVisible) dp2px(NAV_BAR_HEIGHT_DP.toFloat()) else 0
            webView.layoutParams = this
        }
        navBar.bringToFront()
    }

    protected fun setNavBarVisible(visible: Boolean) {
        isNavBarVisible = visible
        navBar.visibility = if (visible) View.VISIBLE else View.GONE
        updateLayout()
    }

    protected fun setNavBarTitle(title: String) {
        titleView.text = title
    }

    protected fun setNavBarBackgroundColor(color: Int) {
        navBar.setBackgroundColor(color)
    }

    // region 状态栏

    /**
     * 设置沉浸式状态栏
     * @param enable 是否启用沉浸式
     * @param lightStatusBar true=深色图标（浅色背景用），false=浅色图标（深色背景用）
     */
    protected fun setImmersiveStatusBar(enable: Boolean, lightStatusBar: Boolean) {
        isImmersive = enable
        WindowCompat.setDecorFitsSystemWindows(window, !enable)

        if (enable) {
            window.statusBarColor = Color.TRANSPARENT
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = lightStatusBar
            }
        } else {
            window.statusBarColor = Color.WHITE
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = true
            }
        }
        updateLayout()
    }

    // region 工具方法
    private fun setupUA() {
        webView.settings.userAgentString = buildString {
            append(webView.settings.userAgentString)
            append(" StatusBarHeight/").append(getStatusBarHeight())
            append(" NavBarHeight/").append(dp2px(NAV_BAR_HEIGHT_DP.toFloat()))
        }
    }

    private fun dp2px(dp: Float): Int =
        (dp * resources.displayMetrics.density + 0.5f).toInt()

    @SuppressLint("InternalInsetResource")
    private fun getStatusBarHeight(): Int {
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else dp2px(STATUS_BAR_DEFAULT_DP.toFloat())
    }

    // region 权限回调
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        try {
            val json = JSONObject().apply {
                put("requestCode", requestCode)
                put("permissions", JSONArray(permissions))
                put("grantResults", JSONArray(grantResults.toList()))
            }
            ClosureRegistry.invoke("onRequestPermissionsResult", json.toString())
        } catch (e: Exception) {
            Log.d(TAG, "onRequestPermissionsResult error: $e")
        }
    }

}
