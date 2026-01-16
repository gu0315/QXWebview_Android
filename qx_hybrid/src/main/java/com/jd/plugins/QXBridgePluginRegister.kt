package com.jd.plugins

import QXBlePlugin
import android.content.Context
import android.util.Log
import com.jd.hybrid.JDWebView
import com.jd.jdbridge.JDBridge
import com.jd.jdbridge.base.IBridgePlugin
import com.jd.jdbridge.base.registerDefaultPlugin
import com.jd.jdbridge.base.registerPlugin

/**
 * QXBridge插件注册类
 * 用于在JDBridge中注册QXBasePlugin
 */
object QXBridgePluginRegister {

    private val TAG = "RegisterPlugin"



    /**
     * 注册所有的Plugin到WebView
     * @param webView 需要注册插件的WebView实例
     */
    public fun registerAllPlugins(webView: JDWebView?) {
        // 注册QXBasePlugin
        val basePlugin = QXBasePlugin()
        val blePlugin = QXBlePlugin()
        webView?.let { registerPlugin(it, basePlugin.NAME, basePlugin) }
        webView?.let { registerPlugin(it, blePlugin.NAME, blePlugin) }
        // 注册其他插件（如有）可以在此处添加
        // 例如：registerPlugin(webView, "other", OtherPlugin())
    }

    /**
     * 注册单个Plugin
     * @param webView WebView实例
     * @param name 插件名称
     * @param plugin 插件实例
     */
    public fun registerPlugin(webView: JDWebView, name: String, plugin: IBridgePlugin) {
        showLog("register $name")
        // 根据用户提供的示例，使用registerDefaultPlugin方法注册插件
        webView.registerPlugin(name, plugin)
    }

    /**
     * 显示日志
     * @param message 日志消息
     */
    public fun showLog(message: String) {
        Log.d(TAG, message)
    }
}
