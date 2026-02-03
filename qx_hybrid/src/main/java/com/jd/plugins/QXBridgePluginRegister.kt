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
     * QXHostBridgePlugin 实例引用
     * 用于在外部设置 hostDelegate
     */
    private var hostBridgePluginInstance: QXHostBridgePlugin? = null
    
    /**
     * 待设置的 delegate，用于在 plugin 注册前就设置 delegate
     */
    private var pendingHostDelegate: QXWebViewHostDelegate? = null

    /**
     * 注册所有的Plugin到WebView
     * @param webView 需要注册插件的WebView实例
     */
    public fun registerAllPlugins(webView: JDWebView?) {
        // 注册QXBasePlugin
        val basePlugin = QXBasePlugin()
        val blePlugin = QXBlePlugin()
        val hostBridgePlugin = QXHostBridgePlugin()
        
        // 保存 QXHostBridgePlugin 实例引用
        hostBridgePluginInstance = hostBridgePlugin
        
        // 如果有待设置的 delegate，立即设置
        pendingHostDelegate?.let {
            hostBridgePlugin.setHostDelegate(it)
            pendingHostDelegate = null
        }
        
        webView?.let { registerPlugin(it, basePlugin.NAME, basePlugin) }
        webView?.let { registerPlugin(it, blePlugin.NAME, blePlugin) }
        webView?.let { registerPlugin(it, "QXHostBridgePlugin", hostBridgePlugin) }
        // 注册其他插件（如有）可以在此处添加
        // 例如：registerPlugin(webView, "other", OtherPlugin())
    }
    
    /**
     * 获取 QXHostBridgePlugin 实例
     * @return QXHostBridgePlugin 实例，如果未注册则返回 null
     */
    public fun getHostBridgePlugin(): QXHostBridgePlugin? {
        return hostBridgePluginInstance
    }
    
    /**
     * 设置 Host Delegate
     * 如果 plugin 已注册，立即设置；否则保存待注册时设置
     * @param delegate QXWebViewHostDelegate 实例
     */
    public fun setHostDelegate(delegate: QXWebViewHostDelegate?) {
        if (hostBridgePluginInstance != null) {
            // plugin 已注册，立即设置
            hostBridgePluginInstance?.setHostDelegate(delegate)
        } else {
            // plugin 未注册，保存待注册时设置
            pendingHostDelegate = delegate
        }
    }
    
    /**
     * 清理插件实例引用
     * 建议在 WebView 销毁时调用
     */
    public fun clearPlugins() {
        hostBridgePluginInstance?.setHostDelegate(null)
        hostBridgePluginInstance = null
        pendingHostDelegate = null
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
