package com.jd.plugins

import QXBlePlugin
import android.util.Log
import com.jd.hybrid.JDWebView
import com.jd.jdbridge.base.IBridgePlugin
import com.jd.jdbridge.base.registerPlugin

/** 向 [JDWebView] 注册 QX Bridge 插件，并管理 Host 与 [QXWebViewHostDelegate]。 */
object QXBridgePluginRegister {

    private val TAG = "RegisterPlugin"
    private val hostBridgePluginsLock = Any()
    private val hostBridgePlugins = mutableSetOf<QXHostBridgePlugin>()

    @Volatile
    private var currentHostDelegate: QXWebViewHostDelegate? = null

    /** 注册 Base / BLE / Host / Lifecycle；返回 [QXHostBridgePlugin] 供 [unregisterHostBridgePlugin]。 */
    public fun registerAllPlugins(webView: JDWebView?): QXHostBridgePlugin? {
        return registerAllPlugins(webView, null)
    }

    /**
     * 注册 Base / BLE / Host / Lifecycle，并为当前 WebView 绑定局部 Host delegate。
     * 未传 [hostDelegate] 时仍沿用全局 delegate，兼容旧接入方式。
     */
    public fun registerAllPlugins(
        webView: JDWebView?,
        hostDelegate: QXWebViewHostDelegate?
    ): QXHostBridgePlugin? {
        val basePlugin = QXBasePlugin()
        val blePlugin = QXBlePlugin()
        val hostBridgePlugin = QXHostBridgePlugin()
        val lifecyclePlugin = QXLifecyclePlugin()
        synchronized(hostBridgePluginsLock) {
            hostBridgePlugins.add(hostBridgePlugin)
        }
        currentHostDelegate?.let { hostBridgePlugin.setHostDelegate(it) }
        hostBridgePlugin.setLocalHostDelegate(hostDelegate)
        webView?.let { registerPlugin(it, basePlugin.NAME, basePlugin) }
        webView?.let { registerPlugin(it, blePlugin.NAME, blePlugin) }
        webView?.let { registerPlugin(it, "QXHostBridgePlugin", hostBridgePlugin) }
        webView?.let { registerPlugin(it, QXLifecyclePlugin.NAME, lifecyclePlugin) }
        return hostBridgePlugin
    }

    /** 从全局表移除并清空 delegate；建议在承载 WebView 的页面 [onDestroy] 调用。 */
    public fun unregisterHostBridgePlugin(plugin: QXHostBridgePlugin?) {
        plugin ?: return
        synchronized(hostBridgePluginsLock) {
            hostBridgePlugins.remove(plugin)
        }
        plugin.setLocalHostDelegate(null)
        plugin.setHostDelegate(null)
    }

    /** 最近一次 [registerAllPlugins] 对应的 Host 插件。 */
    public fun getHostBridgePlugin(): QXHostBridgePlugin? {
        synchronized(hostBridgePluginsLock) {
            return hostBridgePlugins.lastOrNull()
        }
    }

    /** 设置宿主 delegate，并同步到当前已注册的全部 Host 插件。 */
    public fun setHostDelegate(delegate: QXWebViewHostDelegate?) {
        currentHostDelegate = delegate
        val snapshot = synchronized(hostBridgePluginsLock) {
            hostBridgePlugins.toList()
        }
        snapshot.forEach { it.setHostDelegate(delegate) }
    }

    /** 清空全部 Host 与 delegate；单页释放优先 [unregisterHostBridgePlugin]。 */
    public fun clearPlugins() {
        val snapshot = synchronized(hostBridgePluginsLock) {
            val copy = hostBridgePlugins.toList()
            hostBridgePlugins.clear()
            copy
        }
        snapshot.forEach { it.setHostDelegate(null) }
        currentHostDelegate = null
    }

    public fun registerPlugin(webView: JDWebView, name: String, plugin: IBridgePlugin) {
        showLog("register $name")
        webView.registerPlugin(name, plugin)
    }

    public fun showLog(message: String) {
        Log.d(TAG, message)
    }
}
